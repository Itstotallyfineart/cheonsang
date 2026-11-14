package kr.inf.cheonsang.client.sky

import com.google.gson.JsonParser
import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.renderpearl.api.GpuFormat
import com.mojang.renderpearl.api.textures.FilterMode
import kr.inf.cheonsang.Cheonsang
import kr.inf.cheonsang.astro.Vec3
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.AbstractTexture
import net.minecraft.resources.Identifier
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors

/**
 * 실사 사진(장노출 모드) — 목록은 초고화질팩 `pack/photos.json` 에서 읽고, 목록이 없으면(사진팩 1.0.x·초고화질팩 2.0.0) 옛 13장을 그대로 쓴다.
 * 사진 하나 = 하늘 위 직사각형: 가운데 (ra, dec), 가로·세로(°), rot = 사진 위쪽의 위치각(북→동, °), 오른쪽은 서쪽. size = 천체 크기(′), 배율로 커지면 나타남.
 * replace = 그 자리 은하수를 덮어 그림(반투명, 알파=가장자리) — 대·소마젤란처럼 은하수 그림에도 있는 천체의 중복 방지. 텍스처는 화면 근처만 별도 스레드로 올리고 [BUDGET] 넘으면 오래 안 본 것부터 내림.
 */
object PhotoSky {
    class Photo(
        val id: String?, val ra: Double, val dec: Double, val w: Double, val h: Double, val rot: Double,
        val size: Double, val replace: Boolean, val tex: Identifier,
    ) {
        val dir: Vec3 = Vec3.fromSpherical(ra, dec)
        @Volatile var state = 0   // 상태: 0=안 올림, 1=푸는 중, 2=올라감, 3=실패
        var loadedAt = 0L
        var lastUse = 0L
        var bytes = 0L
        val texId: Identifier = Cheonsang.id("photo/${tex.path.substringAfterLast('/').substringBefore('.')}")
    }

    /** 옛 13장 (값 = 사진 한 변 ÷ 천체 긴 지름, 위 = 북쪽으로 가정) */
    private val LEGACY = mapOf(
        "M31" to 1.2, "M42" to 0.45, "M45" to 0.6, "M51" to 1.6, "M57" to 1.6, "M1" to 3.0, "M16" to 1.2, "M8" to 1.4,
        "M27" to 3.0, "M104" to 1.4, "C92" to 1.0, "C80" to 1.5, "C106" to 2.0,
    )
    var BUDGET = 384L shl 20
    private const val FADE_NANOS = 300_000_000L
    private const val UPLOADS_PER_FRAME = 1

    private var list: List<Photo> = emptyList()
    private var listKey = ""
    private val pool = Executors.newFixedThreadPool(2) { r -> Thread(r, "Cheonsang photos").apply { isDaemon = true; priority = Thread.MIN_PRIORITY } }
    private class Ready(val p: Photo, val buf: ByteBuffer?, val w: Int, val h: Int)
    private val ready = ConcurrentLinkedQueue<Ready>()
    private val loaded = ArrayList<Photo>()
    var usedBytes = 0L
        private set

    /** 지금 목록 (팩 상태가 바뀌면 다시 읽음) */
    fun photos(): List<Photo> {
        val key = "${Packs.ultra}/${Packs.photo}"
        if (key != listKey) { releaseAll(); list = load(); listKey = key; Cheonsang.LOG.info("Cheonsang: {} real photos", list.size) }
        return list
    }

    private fun load(): List<Photo> {
        val rm = Minecraft.getInstance().resourceManager
        val res = rm.getResource(Cheonsang.id("pack/photos.json")).orElse(null)
        if (res != null) try {
            val arr = res.openAsReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonArray("photos") }
            return arr.map { e ->
                val o = e.asJsonObject
                Photo(
                    o.get("id")?.asString, o.get("ra").asDouble, o.get("dec").asDouble, o.get("w").asDouble, o.get("h").asDouble,
                    o.get("rot")?.asDouble ?: 0.0, o.get("size").asDouble, o.get("replace")?.asBoolean ?: false,
                    Cheonsang.id("textures/sky/" + o.get("file").asString),
                )
            }
        } catch (e: Exception) {
            Cheonsang.LOG.warn("Cheonsang: cannot read photos.json ({}) — using old photo list", e.toString())
        }
        return SkyData.dso.filter { it.id in LEGACY }.map {
            val side = it.major / 60.0 * LEGACY.getValue(it.id)
            Photo(it.id, it.ra, it.dec, side, side, 0.0, it.major, false, Cheonsang.id("textures/sky/dso/${it.id.lowercase()}_lx.png"))
        }
    }

    /** 렌더 패스 밖에서 화면 근처 사진 요청, 다 풀린 것 올리기, 예산 넘으면 내리기 */
    fun update(view: Vec3?, halfDiagDeg: Double, zoom: Double, now: Long) {
        upload(now)
        if (view != null) for (p in photos()) {
            if (p.size / 60.0 * zoom < 2.0) continue
            if (view.angleTo(p.dir) > halfDiagDeg + maxOf(p.w, p.h) * 0.75 + 1.0) continue
            p.lastUse = now
            if (p.state == 0 && loaded.size + pendingCount < 64) request(p)
        }
        evict(now)
    }

    private var pendingCount = 0

    private fun request(p: Photo) {
        p.state = 1; pendingCount++
        pool.execute {
            var buf: ByteBuffer? = null; var w = 0; var h = 0
            try {
                Minecraft.getInstance().resourceManager.getResource(p.tex).orElse(null)?.open()?.use { s ->
                    NativeImage.read(s).use { img ->
                        w = img.width; h = img.height
                        val c = img.format().components(); val n = w * h; val src = img.pointer
                        val b = MemoryUtil.memAlloc(n * 4)
                        for (i in 0 until n) {
                            val q = src + i.toLong() * c
                            val r = MemoryUtil.memGetByte(q)
                            b.put(i * 4, r)
                            b.put(i * 4 + 1, if (c >= 3) MemoryUtil.memGetByte(q + 1) else r)
                            b.put(i * 4 + 2, if (c >= 3) MemoryUtil.memGetByte(q + 2) else r)
                            b.put(i * 4 + 3, if (c == 4) MemoryUtil.memGetByte(q + 3) else if (c == 2) MemoryUtil.memGetByte(q + 1) else -1)
                        }
                        buf = b
                    }
                }
            } catch (e: Exception) {
                Cheonsang.LOG.warn("Cheonsang: cannot read photo {} ({})", p.tex, e.toString())
            }
            ready.add(Ready(p, buf, w, h))
        }
    }

    private fun upload(now: Long) {
        var n = 0
        while (n < UPLOADS_PER_FRAME) {
            val r = ready.poll() ?: break
            pendingCount--
            val buf = r.buf
            if (buf == null) { r.p.state = 3; continue }
            try {
                Minecraft.getInstance().textureManager.register(r.p.texId, PhotoTexture(r.p.texId.toString(), buf, r.w, r.h))
                r.p.state = 2; r.p.loadedAt = now; r.p.lastUse = now; r.p.bytes = r.w.toLong() * r.h * 4
                loaded.add(r.p); usedBytes += r.p.bytes
            } catch (e: Exception) {
                Cheonsang.LOG.warn("Cheonsang: cannot upload photo {} ({})", r.p.tex, e.toString()); r.p.state = 3
            } finally {
                MemoryUtil.memFree(buf)
            }
            n++
        }
    }

    private fun evict(now: Long) {
        if (usedBytes <= BUDGET) return
        loaded.sortBy { it.lastUse }
        val it = loaded.iterator()
        while (usedBytes > BUDGET && it.hasNext()) {
            val p = it.next()
            if (now - p.lastUse < 1_000_000_000L) break
            drop(p); it.remove()
        }
    }

    private fun drop(p: Photo) {
        Minecraft.getInstance().textureManager.release(p.texId)
        usedBytes -= p.bytes; p.bytes = 0; p.state = 0
    }

    fun releaseAll() {
        for (p in loaded) drop(p)
        loaded.clear()
        for (p in list) if (p.state == 3) p.state = 0
    }

    /** 올라온 뒤 0.3초에 걸쳐 나타남 */
    fun fade(p: Photo, now: Long) = if (p.state != 2) 0f else ((now - p.loadedAt).toFloat() / FADE_NANOS).coerceIn(0f, 1f)

    /** 이 천체(id)를 덮는 사진이 올라와 있는지 (은하수 조각 대신) */
    fun replaces(id: String) = list.any { it.replace && it.id == id && it.state == 2 }

    private class PhotoTexture(label: String, buf: ByteBuffer, w: Int, h: Int) : AbstractTexture() {
        init {
            val device = RenderSystem.getDevice()
            val tex = device.createTexture({ label }, 5, GpuFormat.RGBA8_UNORM, w, h, 1, 1)
            texture = tex
            textureView = device.createTextureView(tex)
            sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
            device.createCommandEncoder().writeToTexture(tex, buf, 0, 0, 0, 0, w, h)
        }
    }
}
