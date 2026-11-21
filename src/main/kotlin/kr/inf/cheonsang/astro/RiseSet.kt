package kr.inf.cheonsang.astro

import kr.inf.cheonsang.L
import kotlin.math.floor

/**
 * 1.1.0 출·남중·몰. 천상 하늘과 똑같은 규칙으로 계산 (마크 시계 = 그 자리 해 기준 시각, 대기 굴절 없음).
 * 시각은 그날 0시부터의 분(0~1440, 다음날로 넘어가면 1440 이상).
 */
object RiseSet {
    /** rise·set·transit = 분 (없으면 null), transitAlt = 남중 고도, always = 종일 위, never = 종일 아래 */
    data class Day(val rise: Double?, val transit: Double?, val transitAlt: Double, val set: Double?, val always: Boolean, val never: Boolean)

    /** t0~t1 분 동안 고도 함수에서 h0 를 넘는 순간·가장 높은 순간 */
    fun find(h0: Double, t0: Double = 0.0, t1: Double = 1440.0, step: Double = 10.0, alt: (Double) -> Double): Day {
        val n = ((t1 - t0) / step).toInt()
        val ts = DoubleArray(n + 1) { t0 + it * step }
        val hs = DoubleArray(n + 1) { alt(ts[it]) - h0 }
        var rise: Double? = null; var set: Double? = null
        var best = 0
        for (i in 1..n) {
            if (hs[i] > hs[best]) best = i
            if (hs[i - 1] < 0 && hs[i] >= 0 && rise == null) rise = bisect(ts[i - 1], ts[i], h0, alt)
            if (hs[i - 1] >= 0 && hs[i] < 0 && set == null) set = bisect(ts[i - 1], ts[i], h0, alt)
        }
        if (hs[0] > hs[best]) best = 0
        // 남중: 가장 높은 표본 둘레에서 황금분할
        var a = (ts[best] - step).coerceAtLeast(t0); var b = (ts[best] + step).coerceAtMost(t1)
        repeat(30) {
            val m1 = a + (b - a) * 0.382; val m2 = a + (b - a) * 0.618
            if (alt(m1) < alt(m2)) a = m1 else b = m2
        }
        val tt = (a + b) / 2
        val ta = alt(tt)
        val always = hs.all { it >= 0 }; val never = hs.all { it < 0 }
        return Day(rise, if (never) null else tt, ta, set, always, never)
    }

    private fun bisect(lo0: Double, hi0: Double, h0: Double, alt: (Double) -> Double): Double {
        var lo = lo0; var hi = hi0
        val up = alt(lo) - h0 < 0
        repeat(25) {
            val m = (lo + hi) / 2
            if ((alt(m) - h0 < 0) == up) lo = m else hi = m
        }
        return (lo + hi) / 2
    }

    /** 분 → "17:42" (다음날이면 "05:10(+1)") */
    fun hm(min: Double): String {
        val m = Math.round(min).toInt()
        val d = Math.floorDiv(m, 1440); val r = Math.floorMod(m, 1440)
        return "%02d:%02d".format(r / 60, r % 60) + if (d > 0) "(+1)" else if (d < 0) "(-1)" else ""
    }

    /** "출 17:42 · 남중 00:15 (61°) · 몰 06:48" — 해는 일출·일몰, 달은 월출·월몰 */
    fun text(d: Day, kind: String = "", fmt: (Double) -> String = ::hm): String {
        val (up, down) = when (kind) { "sun" -> L.t("cheonsang.riseset.6") to L.t("cheonsang.riseset.5"); "moon" -> L.t("cheonsang.riseset.4") to L.t("cheonsang.riseset.3"); else -> L.t("cheonsang.riseset.2") to L.t("cheonsang.riseset.1") }
        if (d.never) return if (kind == "sun") L.t("cheonsang.riseset.8") else L.t("cheonsang.riseset.7")
        val tr = d.transit?.let { L.t("cheonsang.riseset.9", fmt(it)).format(d.transitAlt) }
        if (d.always) return (if (kind == "sun") L.t("cheonsang.riseset.11") else L.t("cheonsang.riseset.10")) + (tr?.let { " · $it" } ?: "")
        return listOfNotNull(
            d.rise?.let { "$up ${fmt(it)}" } ?: L.t("cheonsang.riseset.12", up),
            tr,
            d.set?.let { "$down ${fmt(it)}" } ?: L.t("cheonsang.riseset.12", down),
        ).joinToString(" · ")
    }
}

/**
 * 하루 (그 마크 날 0시~24시) 의 하늘 — 천상 시계·위도 그대로.
 * 해·달은 그날 적도좌표, 별·행성·심원천체는 J2000 (세차 적용) — CheonsangSky 와 같은 방식.
 */
class SkyDay(val clock: SkyClock, val lat: Double, anyTick: Long) {
    /** 그날 0시 틱 */
    val dayStart: Long = (floor((anyTick + 6000).toDouble() / SkyClock.TICKS_PER_DAY) * SkyClock.TICKS_PER_DAY).toLong() - 6000

    fun tick(min: Double) = dayStart + min / 1440.0 * SkyClock.TICKS_PER_DAY
    fun jd(min: Double) = clock.jd(tick(min))
    fun minuteOf(tick: Double) = (tick - dayStart) / SkyClock.TICKS_PER_DAY * 1440.0

    fun horizon(min: Double): Mat3 {
        return Frames.horizon(clock.lst(tick(min)), lat)
    }

    /** 그날 적도좌표 방향 → 고도 */
    fun altOfDate(min: Double, dir: Vec3) = asinD((horizon(min) * dir).normalized().y)
    /** J2000 방향 → 고도 */
    fun altJ2000(min: Double, dir: Vec3) = asinD((horizon(min) * Frames.precession(jd(min)) * dir).normalized().y)

    // 출몰 기준 고도 (Stellarium·Meeus 표준, 대기 굴절 34′ 포함): 해 -0°50′(반지름 16′) · 별·행성 -0°34′ · 달 0.7275×지평시차 - 34′ (관측자 기준)
    fun sun(t0: Double = 0.0, t1: Double = 1440.0) = RiseSet.find(-0.8333, t0, t1) { altOfDate(it, Sun.position(jd(it)).dir) }
    fun sunAlt(min: Double) = altOfDate(min, Sun.position(jd(min)).dir)
    fun moon(t0: Double = 0.0, t1: Double = 1440.0) = RiseSet.find(0.0, t0, t1) {
        val m = Moon.position(jd(it))
        altOfDate(it, m.dir) - (0.7275 * asinD(Moon.EARTH_RADIUS_KM / m.distKm) - 0.5667)
    }
    fun fixed(dirJ2000: Vec3, t0: Double = 0.0, t1: Double = 1440.0) = RiseSet.find(-0.5667, t0, t1) { altJ2000(it, dirJ2000) }
    fun planet(p: Planet, t0: Double = 0.0, t1: Double = 1440.0) = RiseSet.find(-0.5667, t0, t1) { altJ2000(it, Planets.position(p, jd(it)).dir) }
    fun planetAlt(p: Planet, min: Double) = altJ2000(min, Planets.position(p, jd(min)).dir)
}
