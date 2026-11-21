package kr.inf.cheonsang.client

import kr.inf.cheonsang.astro.Vec3
import kr.inf.cheonsang.client.sky.CheonsangSky
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.item.BlockItem
import net.minecraft.world.level.LightLayer
import net.minecraft.world.level.levelgen.Heightmap
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 1.5.1 주변 빛 (클라이언트 계산, PERF-001) — 빛공해(보틀 지수·도시 돔) · 암적응.
 *
 * 빛공해: 주변 ±64블록 · 8칸 간격(17×17 = 289곳) 지표면 위에서 하늘 빛 15(바깥)인 칸의 블록 빛만 거리 가중 합산
 *  → 보틀 지수 1~9 → 천정 하늘 밝기 증가분 (nL) + 빛이 몰린 방향 지평선 돔.
 *  동굴·막힌 집 안 불빛은 하늘 빛 15 가 아니라 빠지고, 창문·문으로 새어 나온 빛은 마크 빛 전파로 바깥 칸에 잡힘. 미로드 청크 = 빛 0.
 *  다시 계산 (LIGHT-001): 8블록 넘게 이동 · 2초 경과 · 청크 로드/언로드. 나눠 샘플링 (O7): 틱마다 40곳,
 *  도중에 이동·청크 변화가 생기면 그 회차 버리고 처음부터 (서로 다른 시점 샘플 안 섞음).
 *  블록 하나 바뀐 것은 따로 잡지 않고 2초 주기로 반영 (횃불 놓기·치우기 → 최대 약 3초 뒤)
 *
 * 암적응: 눈 위치 블록 빛 + 손에 든 광원(블록 빛 세기). 밝으면 바로 풀림, 어두우면 최대 30초(FIXED)에 걸쳐 회복.
 *  덜 적응한 만큼 한계 등급 손해 (최대 3등, TBD) — 별 밝기를 직접 바꾸지 않고 가시성 모델 ④ 관측자 단계에서만.
 */
object AmbientLight {
    private const val RANGE = 64
    private const val STEP = 8
    private const val PER_TICK = 40
    private const val MOVE_LIMIT = 8.0
    private const val PERIOD_TICKS = 40
    /** 빛 합 → 보틀: 1 + 8·(1 − e^(−L/L0)) (TBD) */
    private const val L0 = 800.0
    /** 보틀 1~9 → 천정 하늘 밝기 (등/초각², 어림) */
    private val BORTLE_MAG = doubleArrayOf(21.9, 21.6, 21.4, 20.8, 20.0, 19.1, 18.5, 18.0, 17.5)
    /** 암적응 최대 손해 (등, TBD) · 회복 시간 (초, FIXED) */
    private const val DARK_MAX_LOSS = 3.0
    private const val DARK_RECOVER_SEC = 30.0

    /** 지금 보틀 지수 (1~9, 실수) */
    @JvmStatic var bortle = 1.0
        private set
    /** 빛이 몰린 방향 (월드 x·z, 단위) 과 몰린 정도 0~1 */
    private var domeX = 0.0
    private var domeZ = 0.0
    private var domeK = 0.0
    /** 암적응 0~1 (1 = 다 적응) */
    @JvmStatic var adapt = 1.0
        private set

    // ── 샘플링 상태 ──
    private val cols = (RANGE * 2) / STEP + 1
    private var cursor = -1
    private var originX = 0; private var originZ = 0
    private var sumL = 0.0; private var sumX = 0.0; private var sumZ = 0.0
    private var dirty = true
    private var lastTick = 0L
    private var lastNanos = 0L

    fun register() {
        ClientChunkEvents.CHUNK_LOAD.register { _, _ -> dirty = true }
        ClientChunkEvents.CHUNK_UNLOAD.register { _, _ -> dirty = true }
        ClientTickEvents.END_CLIENT_TICK.register { _ -> Safety.run(Safety.SKY, Unit) { tick() } }
        Visibility.lightPollutionNl = { dir -> lightNl(dir) }
        Visibility.darkAdaptLoss = { DARK_MAX_LOSS * (1.0 - adapt) }
        Visibility.zodiacalNl = { dir -> zodiacalNl(dir) }
        // 반짝임: 맨눈·저배율만 (고배율은 1.7.0 시상). 지평선 쪽 강하게는 셰이더가 대기량으로 (TBD 0.35)
        CheonsangSky.twinkleAmount = {
            if (!Visibility.on(Visibility.Fx.TWINKLE)) 0f
            else (0.35 * (1.0 - kotlin.math.ln(Visibility.zoomNow()) / kotlin.math.ln(75.0)).coerceIn(0.0, 1.0)).toFloat()
        }
    }

    // ── 황도광·대일조 (어두운 하늘 기준 몇 배, TBD) ──
    /** 황도 북극 (J2000 적도) */
    private val ECL_POLE = Vec3(0.0, -kotlin.math.sin(Math.toRadians(23.4392911)), kotlin.math.cos(Math.toRadians(23.4392911)))

    /** (해와의 이각 ε, 황위 β) 도 → 어두운 하늘 대비 밝기 (황도광: 해 쪽 원뿔 · 대일조: 해 반대쪽 8° 무리) */
    fun zodiacalRel(eps: Double, beta: Double): Double {
        if (eps < 20.0) return 0.0
        val zl = (1.5 * exp(-(eps - 25.0) / 30.0) + 0.12) * exp(-(beta / (10.0 + eps / 8.0)).pow(2))
        val gg = 0.25 * exp(-((180.0 - eps) / 8.0).pow(2) - (beta / 6.0).pow(2))
        return zl + gg
    }

    fun zodiacalNl(dir: Vec3): Double {
        val d = dir.normalized()
        val eps = d.angleTo(CheonsangSky.sunWorld.normalized())
        val pole = CheonsangSky.skyMat3 * ECL_POLE
        val beta = Math.toDegrees(kotlin.math.asin((d dot pole).coerceIn(-1.0, 1.0)))
        return zodiacalRel(eps, beta) * 0.5 * Visibility.nl(Visibility.DARK_SKY)
    }

    // ── 오로라: 위도 60° 넘을 때만, 태양 활동은 밤마다 무작위 ──
    /** 오늘 밤 태양 활동 0~1 (같은 밤·같은 세계면 같은 값) */
    fun solarActivity(jd: Double): Double {
        val night = kotlin.math.floor(jd + CheonsangSky.longitude / 360.0).toLong()
        var h = night * 0x9E3779B97F4A7C15uL.toLong()
        h = (h xor (h ushr 31)) * 0x2545F4914F6CDD1DL
        return ((h ushr 11) and 0xFFFFF).toDouble() / 0xFFFFF.toDouble()
    }

    /** 1.9.0-dev2 개발판 전용 `/caurora` — 위도·태양 활동과 무관하게 이 세기로 (null = 끔, 저장 안 함, 도감·발전과제에는 안 들어감) */
    var debugAurora: Double? = null

    /** 오로라 세기 0~1 — 위도 60° 미만·활동 약함(0.4 미만)·밝은 하늘이면 0 (TBD) */
    fun auroraStrength(jd: Double): Double {
        if (!Visibility.on(Visibility.Fx.AURORA)) return 0.0
        debugAurora?.let { return it * ((-CheonsangSky.sunAlt - 10.0) / 6.0).coerceIn(0.0, 1.0) }
        val lat = kotlin.math.abs(CheonsangSky.latitude)
        if (lat < 60.0) return 0.0
        val a = solarActivity(jd)
        if (a < 0.4) return 0.0
        val byLat = when { lat < 67.0 -> 0.3 + 0.7 * (lat - 60.0) / 7.0; lat < 75.0 -> 1.0; else -> (1.0 - (lat - 75.0) / 20.0).coerceAtLeast(0.3) }
        val night = ((-CheonsangSky.sunAlt - 10.0) / 6.0).coerceIn(0.0, 1.0)
        return (a - 0.4) / 0.6 * byLat * night * (1.0 - 0.5 * CheonsangSky.moonLight)
    }

    fun reset() { bortle = 1.0; domeK = 0.0; adapt = 1.0; cursor = -1; dirty = true }

    private fun tick() {
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val p = mc.player ?: return
        adaptTick(mc)
        if (!level.dimensionType().hasSkyLight()) { bortle = 1.0; domeK = 0.0; return }
        val px = p.blockX; val pz = p.blockZ
        val moved = hypot((px - originX).toDouble(), (pz - originZ).toDouble()) > MOVE_LIMIT
        val now = level.gameTime
        // 새 회차 시작: 처음 · 이동 · 청크 변화 · 주기
        if (cursor < 0) {
            if (dirty || moved || now - lastTick >= PERIOD_TICKS) start(px, pz, now)
            else return
        } else if (moved || dirty) {
            start(px, pz, now)  // 도중 변화 → 버리고 처음부터
        }
        val total = cols * cols
        var n = 0
        val pos = BlockPos.MutableBlockPos()
        while (cursor < total && n < PER_TICK) {
            val i = cursor % cols; val j = cursor / cols
            val dx = -RANGE + i * STEP; val dz = -RANGE + j * STEP
            val x = originX + dx; val z = originZ + dz
            cursor++; n++
            if (!level.hasChunk(x shr 4, z shr 4)) continue
            val top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z)
            var light = 0
            for (dy in intArrayOf(0, 1, 3)) {
                pos.set(x, top + dy, z)
                if (level.getBrightness(LightLayer.SKY, pos) < 15) continue
                light = max(light, level.getBrightness(LightLayer.BLOCK, pos))
            }
            if (light <= 0) continue
            val d = hypot(dx.toDouble(), dz.toDouble())
            val w = light.toDouble().pow(2) / (1.0 + (d / 16.0).pow(2))
            sumL += w
            if (d > 0.5) { sumX += w * dx / d; sumZ += w * dz / d }
        }
        if (cursor >= total) finish()
    }

    private fun start(px: Int, pz: Int, now: Long) {
        cursor = 0; originX = px; originZ = pz; sumL = 0.0; sumX = 0.0; sumZ = 0.0; dirty = false; lastTick = now
    }

    private fun finish() {
        cursor = -1
        bortle = 1.0 + 8.0 * (1.0 - exp(-sumL / L0))
        val len = hypot(sumX, sumZ)
        if (sumL > 1e-6 && len > 1e-6) { domeX = sumX / len; domeZ = sumZ / len; domeK = (len / sumL).coerceIn(0.0, 1.0) } else domeK = 0.0
    }

    /** 천정 하늘 밝기 증가분 (nL) */
    fun zenithExtraNl(): Double {
        val b = bortle.coerceIn(1.0, 9.0)
        val i = b.toInt().coerceIn(1, 8)
        val t = b - i
        val mag = BORTLE_MAG[i - 1] + (BORTLE_MAG[i] - BORTLE_MAG[i - 1]) * t
        return max(0.0, Visibility.nl(mag) - Visibility.nl(BORTLE_MAG[0]))
    }

    /** 그 방향 빛공해 (nL) — 지평선 쪽 밝음 + 빛 몰린 방향 돔 */
    fun lightNl(dir: Vec3): Double {
        val z = zenithExtraNl()
        if (z <= 0.0) return 0.0
        val d = dir.normalized()
        val alt = Math.toDegrees(kotlin.math.asin(d.y.coerceIn(-1.0, 1.0))).coerceAtLeast(0.0)
        val h = hypot(d.x, d.z)
        val toward = if (h < 1e-6) 0.0 else ((d.x * domeX + d.z * domeZ) / h).coerceAtLeast(0.0)
        return z * (1.0 + 2.0 * exp(-alt / 20.0) + 4.0 * domeK * toward * exp(-alt / 12.0))
    }

    /** 돔 그리기용: (방향 x, z, 세기 0~1) — 보틀 3 아래면 0 */
    fun dome(): Triple<Float, Float, Float> {
        val k = ((bortle - 2.5) / 6.0).coerceIn(0.0, 1.0)
        return Triple(domeX.toFloat(), domeZ.toFloat(), (k * (0.3 + 0.7 * domeK)).toFloat())
    }
    /** 하늘 전체 빛공해 세기 0~1 (지평선 띠·하늘색) */
    fun glow(): Float = ((bortle - 1.5) / 7.5).coerceIn(0.0, 1.0).toFloat()

    // ── 암적응 ──
    private fun adaptTick(mc: Minecraft) {
        val level = mc.level ?: return
        val p = mc.player ?: return
        val now = System.nanoTime()
        val dt = if (lastNanos == 0L) 0.05 else ((now - lastNanos) / 1e9).coerceIn(0.0, 1.0)
        lastNanos = now
        val eye = BlockPos.containing(p.getEyePosition())
        var e = level.getBrightness(LightLayer.BLOCK, eye)
        for (st in listOf(p.mainHandItem, p.offhandItem)) {
            val item = st.item
            if (item is BlockItem) e = max(e, item.block.defaultBlockState().lightEmission)
        }
        // 낮 하늘 빛도 (해가 떠 있고 하늘이 보이면 적응 풀림)
        if (CheonsangSky.sunAlt > -4.0 && level.getBrightness(LightLayer.SKY, eye) >= 12) e = max(e, 15)
        val ceiling = 1.0 - (e / 15.0).pow(0.7)
        // 밝으면 바로 그 수준까지 풀림, 어두우면 30초에 걸쳐 회복
        adapt = if (adapt > ceiling) ceiling else (adapt + dt / DARK_RECOVER_SEC).coerceAtMost(ceiling)
    }
}
