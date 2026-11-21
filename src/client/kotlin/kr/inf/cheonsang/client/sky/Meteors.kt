package kr.inf.cheonsang.client.sky

import kr.inf.cheonsang.astro.Mat3
import kr.inf.cheonsang.astro.Vec3
import kr.inf.cheonsang.astro.asinD
import kr.inf.cheonsang.astro.cosD
import kr.inf.cheonsang.astro.norm180
import kr.inf.cheonsang.astro.sinD
import java.util.SplittableRandom
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/** 지금 화면에 그릴 유성 하나 (월드 방향) */
/** 2.0.2-dev2: 색(r, g, b) · 굵기 배율 — 잔광은 초록빛, 화구는 주황빛·굵게 */
class Streak(val tail: Vec3, val head: Vec3, val alpha: Float, val r: Float = 1f, val g: Float = 1f, val b: Float = 1f, val width: Float = 1f)

/**
 * 유성우 — 실제 유성우 달력(해 황경)과 복사점을 따르고, 별똥별 하나하나의 위치만 무작위.
 *
 * - 시간을 0.2초(4틱) 칸으로 나누고 칸 번호로 난수를 만든다 → 같은 서버의 모든 플레이어가 같은 별똥별을 본다.
 * - 활동량: ZHR × exp(-½(Δλ/σ)²) × sin(복사점 고도) + 산발 유성.
 * - 마크 밤은 짧아서 실제 수를 그대로 쓰면 너무 많다 → 시간당 450개 = 1초에 1개로 줄임 (scale 로 조절).
 *   쌍둥이자리 극대(복사점 높이 뜬 밤) ≈ 3초에 하나, 평소 ≈ 45초에 하나.
 */
object Meteors {
    private const val SLOT_TICKS = 4
    private const val PER_HOUR_TO_PER_SECOND = 1.0 / 450.0

    /** 유성 양 배율 (설정은 8단계) */
    var scale = 1.0

    /** 2.0.3-dev1 개발판 `/cmeteor`: 분당 개수 (null = 끔) · 화구만 · 유성우 id (복사점). 저장 안 함 */
    var debugPerMin: Double? = null
    var debugFire = false
    var debugShower: String? = null

    /** 지금 시각의 1초당 유성 수 (밝기 무관, 하늘이 완전히 어둡다고 칠 때) */
    fun ratePerSecond(sunLon: Double, radiantAlt: (Shower) -> Double): Double {
        var perHour = SkyData.sporadicPerHour
        for (s in SkyData.showers) {
            val d = norm180(sunLon - s.peakLon)
            val activity = s.zhr * exp(-0.5 * (d / s.sigma) * (d / s.sigma))
            perHour += activity * max(0.0, sinD(radiantAlt(s)))
        }
        return perHour * PER_HOUR_TO_PER_SECOND * scale
    }

    /**
     * tick 시점에 살아 있는 유성들.
     * @param toWorld J2000 적도 → 월드 행렬 (복사점 위치용)
     * @param darkness 0~1 (박명·비·달빛 반영된 하늘 어두움)
     */
    fun visible(tick: Double, sunLon: Double, toWorld: Mat3, darkness: Float): List<Streak> {
        if (darkness <= 0.01f || (scale <= 0.0 && debugPerMin == null)) return emptyList()
        val radiants = SkyData.showers.associateWith { toWorld * Vec3.fromSpherical(it.ra, it.dec) }
        val alt = { s: Shower -> asinD(radiants.getValue(s).y) }
        val natural = ratePerSecond(sunLon, alt)
        val rate = debugPerMin?.let { it / 60.0 } ?: natural
        val forced = debugShower?.let { id -> SkyData.showers.firstOrNull { it.id.equals(id, true) } }
        val pSlot = (rate * SLOT_TICKS / 20.0).coerceAtMost(0.9)
        val out = ArrayList<Streak>()
        val now = tick / SLOT_TICKS
        // 유성 수명 최대 1초 = 20틱 = 5칸. 2.0.2-dev2: 화구 꼬리 잔광 최대 약 4초 → 지난 22칸
        for (k in (now.toLong() - 22)..now.toLong()) {
            val rng = SplittableRandom(k * -0x61c8864680b583ebL + 0x5DEECE66DL)
            if (rng.nextDouble() >= pSlot) continue
            // 어느 유성우에서 왔나 (활동량 비례), 아니면 산발
            var pick = rng.nextDouble() * natural / (PER_HOUR_TO_PER_SECOND * scale)
            var shower: Shower? = forced
            if (forced == null) for (s in SkyData.showers) {
                val d = norm180(sunLon - s.peakLon)
                pick -= s.zhr * exp(-0.5 * (d / s.sigma) * (d / s.sigma)) * max(0.0, sinD(alt(s)))
                if (pick < 0) { shower = s; break }
            }
            // 시작점: 고도 20~75°
            val a0 = 20.0 + rng.nextDouble() * 55.0
            val az = rng.nextDouble() * 360.0
            val start = Vec3(cosD(a0) * sinD(az), sinD(a0), -cosD(a0) * cosD(az))
            val dir: Vec3 = if (shower != null) {
                val r = radiants.getValue(shower)
                val away = start * (start dot r) - r // 복사점에서 멀어지는 쪽
                if (away.length() < 0.17) continue  // 복사점 10° 안은 짧아서 생략
                away.normalized()
            } else {
                val ref = if (kotlin.math.abs(start.y) < 0.95) Vec3(0.0, 1.0, 0.0) else Vec3(1.0, 0.0, 0.0)
                val e1 = (ref cross start).normalized(); val e2 = start cross e1
                val th = rng.nextDouble() * 360.0
                e1 * cosD(th) + e2 * sinD(th)
            }
            // 빠른 유성우(사자자리 71 km/s)일수록 짧고 빠르게
            val speed = shower?.speed ?: 40.0
            val duration = (0.35 + rng.nextDouble() * 0.5) * 40.0 / speed * 1.4  // 초
            val length = 6.0 + rng.nextDouble() * 14.0                            // 도
            val bright = (0.35 + rng.nextDouble() * 0.65).toFloat()
            val age = ((now - k) * SLOT_TICKS - rng.nextDouble() * SLOT_TICKS) / 20.0
            // 2.0.2-dev2 G5: 약 3% 는 화구 (길고 굵고 주황빛), 밝은 유성(밝기 0.85↑)과 화구는 지나간 자리에 초록빛 잔광이 잠깐 남음
            val fire = rng.nextDouble() < 0.03 || debugFire
            val dur = if (fire) duration * 1.6 else duration
            val len = if (fire) length * 1.5 else length
            val train = if (fire) 2.0 + rng.nextDouble() * 2.0 else if (bright > 0.85f) 0.6 + rng.nextDouble() * 0.8 else 0.0
            if (age < 0 || age > dur + train) continue
            if (age > dur) {
                val g = 1.0 - (age - dur) / train
                val a = (g * g * (if (fire) 0.45 else 0.25) * darkness).toFloat()
                out += Streak(rotate(start, dir, len * 0.25), rotate(start, dir, len * 0.95), a, 0.55f, 1f, 0.7f, if (fire) 2.2f else 1.4f)
                continue
            }
            val f = age / dur
            val head = rotate(start, dir, len * f)
            val tail = rotate(start, dir, max(0.0, len * f - (if (fire) 10.0 else 7.0)))
            val fade = sin(PI * f).toFloat()
            out += if (fire) Streak(tail, head, fade * darkness, 1f, 0.82f, 0.55f, 2.6f) else Streak(tail, head, bright * fade * darkness)
        }
        return out
    }

    /** start 에서 접선 dir 방향으로 대원을 따라 angle(도) */
    private fun rotate(start: Vec3, dir: Vec3, angle: Double) = (start * cosD(angle) + dir * sinD(angle)).normalized()
}
