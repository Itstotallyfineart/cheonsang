package kr.inf.cheonsang.client

import kr.inf.cheonsang.L
import com.mojang.blaze3d.systems.RenderSystem
import kr.inf.cheonsang.Cheonsang
import kr.inf.cheonsang.Telescope
import kr.inf.cheonsang.client.sky.CheonsangSky
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.network.chat.Component
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.asin
import kotlin.math.atan2

/**
 * 성능 측정 `/cbench`. 장면마다 2초 안정 + 10초 측정(프레임 시간), 결과는 채팅 요약 + cheonsang_bench/날짜_시각/ 폴더에 결과.txt·장면별 스크린샷으로 저장.
 * 서버 상태(시간·날씨)는 안 바꿈. 망원경을 손에 들고 실행. 기준 환경: RTX 2050·1080p·렌더 16
 */
object Bench {
    val DEV: Boolean = FabricLoader.getInstance().getModContainer(Cheonsang.MOD_ID)
        .map { it.metadata.version.friendlyString.lastOrNull()?.isLetter() == true }.orElse(false)

    private class Scene(val name: String, val telescope: Boolean, val level: Int, val aim: String?, val pitch: Float)
    private val SCENES get() = listOf(
        Scene(L.t("cheonsang.bench.1"), false, 0, null, 60f),
        Scene(L.t("cheonsang.bench.2"), false, 0, null, -85f),
        Scene(L.t("cheonsang.bench.3"), true, 1, null, -85f),
        Scene("×75 M31", true, 3, "M31", -85f),
        Scene(L.t("cheonsang.bench.5"), true, 5, L.t("cheonsang.bench.4"), -85f),
    )
    private const val WARM = 2_000_000_000L
    private const val MEASURE = 10_000_000_000L

    private class Result(val name: String, val frames: Int, val avgFps: Double, val low1: Double, val maxMs: Double, val note: String, val vramMb: Long)

    private var running = false
    private var idx = 0
    private var sceneStart = 0L
    private var last = 0L
    private val times = ArrayList<Double>()
    private val results = ArrayList<Result>()
    private var note = ""
    /** 망원경 처음 켠 뒤 3초 안의 가장 긴 프레임 (8K 은하수·사진 올리기) */
    private var firstOnStart = 0L
    private var firstOnMax = 0.0
    private var firstOnPreloaded = false
    private var yaw0 = 0f
    private var pitch0 = 0f
    /** 이번 측정 폴더 (cheonsang_bench/날짜_시각) */
    private var outDir: java.nio.file.Path? = null
    private var vram0 = -1L

    /** 그래픽 메모리 사용량(MB) — OpenGL+NVIDIA(GL_NVX_gpu_memory_info)일 때만, 아니면 -1. 리플렉션으로 불러 다른 백엔드에서도 안 튕김 */
    private fun vramUsedMb(): Long = try {
        val info = RenderSystem.getDevice().deviceInfo
        if (!info.underlyingExtensions().contains("GL_NVX_gpu_memory_info")) -1L else {
            val m = Class.forName("org.lwjgl.opengl.GL11").getMethod("glGetInteger", Int::class.javaPrimitiveType)
            val total = (m.invoke(null, 0x9048) as Int).toLong()
            val avail = (m.invoke(null, 0x9049) as Int).toLong()
            if (total <= 0) -1L else (total - avail) / 1024
        }
    } catch (_: Throwable) { -1L }

    /** 지금 화면을 장면 번호 이름으로 저장 */
    private fun shot(n: Int) {
        val dir = outDir ?: return
        try {
            Screenshot.takeScreenshot(Minecraft.getInstance().gameRenderer.mainRenderTarget()) { img ->
                try { img.writeToFile(dir.resolve("scene%d.png".format(n))) } catch (e: Exception) { Cheonsang.LOG.warn("Cheonsang: failed to save benchmark screenshot", e) } finally { img.close() }
            }
        } catch (e: Throwable) { Cheonsang.LOG.warn("Cheonsang: benchmark screenshot failed", e) }
    }

    fun register() {
        ClientCommandRegistrationCallback.EVENT.register { d, _ ->
            d.register(ClientCommands.literal("cbench")
                .executes { start(); 1 }
                .then(ClientCommands.literal("stop").executes { stop(L.t("cheonsang.bench.6")); 1 }))
        }
        HudElementRegistry.addLast(Cheonsang.id("bench")) { _, _ -> if (running) Safety.run(L.t("cheonsang.bench.7"), Unit) { frame() } }
    }

    private fun say(s: String) = Minecraft.getInstance().player?.sendSystemMessage(Component.literal(s))

    private fun start() {
        val mc = Minecraft.getInstance()
        val p = mc.player ?: return
        if (running) { say(L.t("cheonsang.bench.8")); return }
        if (!p.mainHandItem.`is`(Telescope.ITEM) && !p.offhandItem.`is`(Telescope.ITEM)) { say(L.t("cheonsang.bench.9")); return }
        val tod = Math.floorMod(mc.level!!.defaultClockTime, 24000L)
        if (tod < 12500 || tod > 23500) say(L.t("cheonsang.bench.10"))
        yaw0 = p.yRot; pitch0 = p.xRot
        results.clear(); idx = 0; running = true
        outDir = try {
            mc.gameDirectory.toPath().resolve("cheonsang_bench").resolve(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))).also { Files.createDirectories(it) }
        } catch (_: Exception) { null }
        vram0 = vramUsedMb()
        firstOnStart = 0L; firstOnMax = 0.0
        firstOnPreloaded = CheonsangSky.bigTexturesLoaded()
        enter()
        say(L.t("cheonsang.bench.11", SCENES.size))
    }

    private fun enter() {
        val s = SCENES[idx]
        val p = Minecraft.getInstance().player ?: return stop(L.t("cheonsang.bench.12"))
        val wasActive = TelescopeView.active
        TelescopeView.benchSet(s.telescope, s.level)
        if (s.telescope && !wasActive && firstOnStart == 0L) firstOnStart = System.nanoTime()
        note = ""
        val dir = s.aim?.let { FindGuide.dirOf(it) }
        if (dir != null) {
            val w = dir.normalized()
            p.setYRot(Math.toDegrees(atan2(-w.x, w.z)).toFloat())
            p.setXRot(Math.toDegrees(asin((-w.y).coerceIn(-1.0, 1.0))).toFloat())
            if (w.y < 0) note = L.t("cheonsang.bench.13")
        } else {
            p.setXRot(s.pitch)
            if (s.aim != null) note = L.t("cheonsang.bench.14")
        }
        p.yRotO = p.yRot; p.xRotO = p.xRot
        times.clear()
        sceneStart = System.nanoTime(); last = sceneStart
    }

    private fun frame() {
        val now = System.nanoTime()
        val dt = (now - last) / 1e6
        last = now
        if (firstOnStart != 0L && now - firstOnStart < 3_000_000_000L) firstOnMax = maxOf(firstOnMax, dt)
        val el = now - sceneStart
        if (el < WARM) return
        if (el < WARM + MEASURE) { times += dt; return }
        val s = SCENES[idx]
        val sorted = times.sorted()
        val total = times.sum() / 1000.0
        val p99 = sorted[((sorted.size - 1) * 0.99).toInt()]
        results += Result(s.name, times.size, times.size / total, 1000.0 / p99, sorted.last(), note, vramUsedMb())
        shot(idx + 1)
        idx++
        if (idx < SCENES.size) enter() else finish()
    }

    private fun stop(why: String) {
        if (!running) return
        running = false
        TelescopeView.benchSet(false, 1)
        Minecraft.getInstance().player?.let { it.setYRot(yaw0); it.setXRot(pitch0) }
        say(L.t("cheonsang.bench.15", why))
    }

    private fun finish() {
        stop(L.t("cheonsang.bench.16"))
        val mc = Minecraft.getInstance()
        val rt = Runtime.getRuntime()
        val lines = ArrayList<String>()
        lines += L.t("cheonsang.bench.17", FabricLoader.getInstance().getModContainer(Cheonsang.MOD_ID).map { it.metadata.version.friendlyString }.orElse("?"), LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
        lines += L.t("cheonsang.bench.18") + tryText { RenderSystem.getDevice().deviceInfo.let { "${it.name()} (${it.vendorName()}, ${it.driverInfo()})" } }
        lines += "CPU: " + tryText { oshi.SystemInfo().hardware.processor.processorIdentifier.name.trim() + L.t("cheonsang.bench.19", Runtime.getRuntime().availableProcessors()) }
        lines += L.t("cheonsang.bench.20") + tryText { "%.1fGB".format(oshi.SystemInfo().hardware.memory.total / 1073741824.0) }
        lines += L.t("cheonsang.bench.21").format((rt.totalMemory() - rt.freeMemory()) / 1048576, rt.maxMemory() / 1048576)
        lines += L.t("cheonsang.bench.23") + tryText { "${mc.window.width}×${mc.window.height}" } + L.t("cheonsang.bench.22") + tryText { mc.options.renderDistance().get().toString() } +
            L.t("cheonsang.bench.27") + tryText { mc.options.framerateLimit().get().toString() } + L.t("cheonsang.bench.26") + tryText { if (mc.options.enableVsync().get()) L.t("cheonsang.bench.25") else L.t("cheonsang.bench.24") }
        lines += L.t("cheonsang.bench.29") + if (vram0 < 0) L.t("cheonsang.bench.28") else "${vram0}MB"
        lines += L.t("cheonsang.bench.31") + if (firstOnPreloaded) L.t("cheonsang.bench.30") else "%.0fms".format(firstOnMax)
        lines += ""
        lines += L.t("cheonsang.bench.32")
        for (r in results) lines += "%s | %.1f | %.1f | %.1fms | %s | %s".format(r.name, r.avgFps, r.low1, r.maxMs,
            if (r.vramMb < 0 || vram0 < 0) "?" else "%+dMB".format(r.vramMb - vram0), r.note)
        lines += ""
        lines += L.t("cheonsang.bench.33", results.size)
        try {
            val dir = outDir ?: mc.gameDirectory.toPath().resolve("cheonsang_bench").also { Files.createDirectories(it) }
            val f = dir.resolve(L.t("cheonsang.bench.34"))
            Files.writeString(f, lines.joinToString("\n") + "\n")
            for (r in results) say(L.t("cheonsang.bench.35").format(r.name, r.avgFps, r.low1))
            say(L.t("cheonsang.bench.36", dir.fileName))
        } catch (e: Exception) {
            Cheonsang.LOG.error("Cheonsang: failed to save benchmark results", e)
            lines.forEach { say(it) }
        }
    }

    private inline fun tryText(f: () -> String): String = try { f() } catch (_: Throwable) { "?" }
}
