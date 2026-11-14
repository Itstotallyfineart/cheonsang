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
class Streak(val tail: Vec3, val head: Vec3, val alpha: Float)

/**
 * 유성우 — 실제 유성우 달력(해 황경)과 복사점을 따르고, 별똥별 하나하나의 위치는 무작위.
 * 시간을 0.2초(4틱) 단위로 나눠 칸 번호로 난수를 만들어 같은 서버의 모든 플레이어가 같은 유성을 보게 한다.
 * 활동량 = ZHR × exp(-½(Δλ/σ)²) × sin(복사점 고도) + 산발 유성. 실제 수는 시간당 450개 = 1초에 1개로 환산한다(scale 로 조절).
 */
object Meteors {
    private const val SLOT_TICKS = 4
    private const val PER_HOUR_TO_PER_SECOND = 1.0 / 450.0

    /** 유성 양 배율 (설정은 8단계) */
    var scale = 1.0

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
        if (darkness <= 0.01f || scale <= 0.0) return emptyList()
        val radiants = SkyData.showers.associateWith { toWorld * Vec3.fromSpherical(it.ra, it.dec) }
        val alt = { s: Shower -> asinD(radiants.getValue(s).y) }
        val rate = ratePerSecond(sunLon, alt)
        val pSlot = (rate * SLOT_TICKS / 20.0).coerceAtMost(0.9)
        val out = ArrayList<Streak>()
        val now = tick / SLOT_TICKS
        // 유성 수명은 최대 1초(20틱=5칸)이므로 지난 6칸만 보면 된다
        for (k in (now.toLong() - 6)..now.toLong()) {
            val rng = SplittableRandom(k * -0x61c8864680b583ebL + 0x5DEECE66DL)
            if (rng.nextDouble() >= pSlot) continue
            // 어느 유성우에서 왔나 (활동량 비례), 아니면 산발
            var pick = rng.nextDouble() * rate / (PER_HOUR_TO_PER_SECOND * scale)
            var shower: Shower? = null
            for (s in SkyData.showers) {
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
            if (age < 0 || age > duration) continue
            val f = age / duration
            val head = rotate(start, dir, length * f)
            val tail = rotate(start, dir, max(0.0, length * f - 7.0))
            val fade = sin(PI * f).toFloat()
            out += Streak(tail, head, bright * fade * darkness)
        }
        return out
    }

    /** start 에서 접선 dir 방향으로 대원을 따라 angle(도) */
    private fun rotate(start: Vec3, dir: Vec3, angle: Double) = (start * cosD(angle) + dir * sinD(angle)).normalized()
}
