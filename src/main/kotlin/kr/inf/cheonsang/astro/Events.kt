package kr.inf.cheonsang.astro

import kr.inf.cheonsang.L
import kotlin.math.floor

/**
 * 1.1.0 `/cele next` → 1.2.1 `/ctonight next`: 다음 현상 찾기.
 * 1.3.0: 그 장소(위도·경도)에서 실제로 보이는지 판정 (지방항성시 = GMST + 경도), 시각 = 그 장소 지방평균시 (경도/15). 일식은 관측자 위치 시차 그대로 ("경도 무시" 폐지)
 */
object Events {
    /** jd = 현상 시각, view = 보러 갈 시각 (이동 버튼), text = 한 줄 정보 */
    data class Found(val jd: Double, val view: Double, val text: String)

    private const val YEARS = 60.0
    private const val SYNODIC = 29.530588

    /** 그 순간 지평 행렬 (지방항성시 = GMST + 경도) */
    private fun horizonAt(jd: Double, lat: Double, lon: Double): Mat3 = Frames.horizon(norm360(SkyClock.gmst(jd) + lon), lat)
    fun altOfDate(jd: Double, lat: Double, lon: Double, dir: Vec3) = asinD((horizonAt(jd, lat, lon) * dir).normalized().y)
    fun altJ2000(jd: Double, lat: Double, lon: Double, dir: Vec3) = asinD((horizonAt(jd, lat, lon) * Frames.precession(jd) * dir).normalized().y)
    /** 2.0.3-dev2: (고도, 방위) 도 — j2000 = true 면 dir 이 J2000 적도 (행성), 아니면 그날 적도 (해·달) */
    fun altAz(jd: Double, lat: Double, lon: Double, dir: Vec3, j2000: Boolean): Pair<Double, Double> {
        val w = (horizonAt(jd, lat, lon) * (if (j2000) Frames.precession(jd) * dir else dir)).normalized()
        return asinD(w.y) to norm360(atan2D(w.x, -w.z))
    }

    /** 그 장소 현지 날짜·시각 글자 */
    private fun local(jd: Double, lon: Double) = Calendar.toLocal(jd, lon / 15.0)

    private fun elong(jd: Double) = norm360(Moon.position(jd).lon - Sun.position(jd).lon)

    /** 달-해 이각이 target(0 = 삭, 180 = 망)이 되는 다음 순간 */
    fun nextPhase(jd0: Double, target: Double): Double {
        fun f(jd: Double) = norm180(elong(jd) - target)
        var a = jd0; var fa = f(a)
        while (true) {
            val b = a + 1.0; val fb = f(b)
            if (fa < 0 && fb >= 0 && fb - fa < 90) {
                var lo = a; var hi = b
                repeat(40) { val m = (lo + hi) / 2; if (f(m) < 0) lo = m else hi = m }
                return (lo + hi) / 2
            }
            a = b; fa = fb
        }
    }

    private fun goldenMin(lo0: Double, hi0: Double, f: (Double) -> Double): Double {
        var a = lo0; var b = hi0
        repeat(40) { val m1 = a + (b - a) * 0.382; val m2 = a + (b - a) * 0.618; if (f(m1) < f(m2)) b = m2 else a = m1 }
        return (a + b) / 2
    }

    /** 1.9.0: 반영월식은 뺌 (본영이 2% 넘게 덮는 것만 — 도감 판정과 같게) */
    const val UMBRA_MIN = 0.02

    /** 다음 보이는 월식 (본영 — 1.9.0 반영월식 제외) */
    fun lunarEclipse(jd0: Double, lat: Double, lon: Double): Found? {
        var full = nextPhase(jd0, 180.0)
        while (full < jd0 + YEARS * 365.25) {
            val t = goldenMin(full - 0.3, full + 0.3) { Moon.position(it).dir.angleTo(Sun.position(it).dir * -1.0) }
            val moon = Moon.position(t); val sun = Sun.position(t)
            val s = Eclipse.lunar(moon, sun)
            if (s.umbraCover > UMBRA_MIN && t > jd0) {
                val alt = altOfDate(t, lat, lon, moon.dir)
                if (alt > 0) {
                    val kind = when { s.umbraCover >= 1.0 -> L.t("cheonsang.events.3"); s.umbraCover > 0 -> L.t("cheonsang.events.2").format(s.umbraCover * 100); else -> L.t("cheonsang.events.1") }
                    return Found(t, t, L.t("cheonsang.events.4", kind, local(t, lon), "%.0f".format(alt)))  // 1.5.2.1: kind 에 % 가 들어 있어 다시 format 하면 오류
                }
            }
            full = nextPhase(full + 20, 180.0)
        }
        return null
    }

    /** 다음 보이는 일식 (그 장소 관측자 기준 — 1.3.0) */
    fun solarEclipse(jd0: Double, lat: Double, lon: Double): Found? {
        var nm = nextPhase(jd0, 0.0)
        while (nm < jd0 + YEARS * 365.25) {
            fun sep(jd: Double): Double {
                val m = Moon.position(jd); val s = Sun.position(jd)
                return Eclipse.topocentricMoon(m.dir, m.distKm, lat, norm360(SkyClock.gmst(jd) + lon)).angleTo(s.dir)
            }
            // 거친 거르기: 지구중심 이각이 1.6° 넘으면 어디서도 일식 아님
            val geo = Moon.position(nm).dir.angleTo(Sun.position(nm).dir)
            if (geo < 1.6) {
                var best = nm; var bs = Double.MAX_VALUE
                var x = nm - 0.6
                while (x <= nm + 0.6) { val v = sep(x); if (v < bs) { bs = v; best = x }; x += 10.0 / 1440 }
                val t = goldenMin(best - 10.0 / 1440, best + 10.0 / 1440, ::sep)
                val m = Moon.position(t); val s = Sun.position(t)
                val d = sep(t); val rm = Moon.angularRadius(m.distKm); val rs = Sun.angularRadius(s.distAu)
                val alt = altOfDate(t, lat, lon, s.dir)
                if (d < rm + rs - SOLAR_MARGIN * rs && alt > 0 && t > jd0) {
                    val kind = when {
                        rm >= rs && d <= rm - rs -> L.t("cheonsang.events.5")
                        rs > rm && d <= rs - rm -> L.t("cheonsang.events.6")
                        else -> L.t("cheonsang.events.7")
                    }
                    return Found(t, t, L.t("cheonsang.events.8", kind, local(t, lon), "%.0f".format(alt)))
                }
            }
            nm = nextPhase(nm + 20, 0.0)
        }
        return null
    }

    /** 1.9.0: 일식으로 칠 겹침 — 해 지름의 약 5% 넘게 가려짐 (도감 판정과 비슷하게) */
    private const val SOLAR_MARGIN = 0.1

    /** 1.9.0 위도 이동: 현상 + 경도 lon 에서 보이는 위도 (1° 간격, 남→북) */
    data class Band(val found: Found, val lats: List<Int>, val best: Int = lats[lats.size / 2])

    /** 다음 일식 중 경도 lon 의 어느 위도에서든 보이는 것 — 위도마다 관측자 시차로 판정 */
    fun solarEclipseBand(jd0: Double, lon: Double): Band? {
        var nm = nextPhase(jd0, 0.0)
        while (nm < jd0 + YEARS * 365.25) {
            val geo = Moon.position(nm).dir.angleTo(Sun.position(nm).dir)
            if (geo < 1.6) {
                // 10분 간격 해·달 위치 (위도와 무관) 를 먼저 구해 두고 위도마다 시차만 다시 계산
                val times = ArrayList<Double>(); var x = nm - 0.6
                while (x <= nm + 0.6) { if (x > jd0) times += x; x += 10.0 / 1440 }
                val ms = times.map { Moon.position(it) }; val ss = times.map { Sun.position(it) }
                val lst = times.map { norm360(SkyClock.gmst(it) + lon) }
                val ok = ArrayList<Int>(); var bestLat = 0; var bestD = Double.MAX_VALUE; var bestT = nm
                for (la in -89..89) {
                    for (i in times.indices) {
                        val d = Eclipse.topocentricMoon(ms[i].dir, ms[i].distKm, la.toDouble(), lst[i]).angleTo(ss[i].dir)
                        val rm = Moon.angularRadius(ms[i].distKm); val rs = Sun.angularRadius(ss[i].distAu)
                        if (d < rm + rs - SOLAR_MARGIN * rs && altOfDate(times[i], la.toDouble(), lon, ss[i].dir) > 0) {
                            if (ok.lastOrNull() != la) ok += la
                            if (d < bestD) { bestD = d; bestLat = la; bestT = times[i] }
                        }
                    }
                }
                if (ok.isNotEmpty()) {
                    val la = bestLat.toDouble()
                    fun sep(jd: Double): Double { val m = Moon.position(jd); return Eclipse.topocentricMoon(m.dir, m.distKm, la, norm360(SkyClock.gmst(jd) + lon)).angleTo(Sun.position(jd).dir) }
                    val t = goldenMin(bestT - 10.0 / 1440, bestT + 10.0 / 1440, ::sep)
                    val m = Moon.position(t); val sn = Sun.position(t)
                    val d = sep(t); val rm = Moon.angularRadius(m.distKm); val rs = Sun.angularRadius(sn.distAu)
                    val kind = when { rm >= rs && d <= rm - rs -> L.t("cheonsang.events.5"); rs > rm && d <= rs - rm -> L.t("cheonsang.events.6"); else -> L.t("cheonsang.events.7") }
                    return Band(Found(t, t, "$kind ${local(t, lon)}"), ok, bestLat)
                }
            }
            nm = nextPhase(nm + 20, 0.0)
        }
        return null
    }

    /** 다음 월식 (본영) 중 경도 lon 의 어느 위도에서든 달이 떠 있는 것 (식 최대 순간 기준) */
    fun lunarEclipseBand(jd0: Double, lon: Double): Band? {
        var full = nextPhase(jd0, 180.0)
        while (full < jd0 + YEARS * 365.25) {
            val t = goldenMin(full - 0.3, full + 0.3) { Moon.position(it).dir.angleTo(Sun.position(it).dir * -1.0) }
            val moon = Moon.position(t); val sun = Sun.position(t)
            val s = Eclipse.lunar(moon, sun)
            if (s.umbraCover > UMBRA_MIN && t > jd0) {
                val ok = (-89..89).filter { altOfDate(t, it.toDouble(), lon, moon.dir) > 0 }
                val best = ok.maxByOrNull { altOfDate(t, it.toDouble(), lon, moon.dir) }
                if (ok.isNotEmpty() && best != null) {
                    val kind = if (s.umbraCover >= 1.0) L.t("cheonsang.events.3") else L.t("cheonsang.events.2").format(s.umbraCover * 100)
                    return Band(Found(t, t, "$kind ${local(t, lon)}"), ok, best)
                }
            }
            full = nextPhase(full + 20, 180.0)
        }
        return null
    }

    /** 밤(해 고도 -12° 아래) 중 score 가 가장 큰 시각 — 그 날 18:00 ~ 다음날 06:00, 30분 간격 */
    fun bestNight(jdDay0: Double, lat: Double, lon: Double, score: (Double) -> Double): Double {
        // 1.10.1-dev4: −12° 아래가 없으면(고위도 여름) −6° 아래 중에서, 그것도 없으면 해가 가장 낮은 시각 — 전엔 22:00 으로 돌려 해가 떠 있었음 (11-14 지율님 75°N 4월)
        val slots = (0..24).map { jdDay0 + (18.0 + it * 0.5) / 24 }
        val sun = slots.map { altOfDate(it, lat, lon, Sun.position(it).dir) }
        for (lim in doubleArrayOf(-12.0, -6.0)) {
            var best = Double.NaN; var bs = -1e9
            for (i in slots.indices) if (sun[i] < lim) { val v = score(slots[i]); if (v > bs) { bs = v; best = slots[i] } }
            if (!best.isNaN()) return best
        }
        return slots[sun.indices.minBy { sun[it] }]
    }

    /** 그 장소 현지 그날 0시의 jd */
    fun localDay0(jd: Double, lon: Double): Double { val c = local(jd, lon); return Calendar.julianDayLocal(c.year, c.month, c.day, 0.0, lon / 15.0) }

    fun fullMoon(jd0: Double, lat: Double, lon: Double): Found {
        val t = nextPhase(jd0, 180.0)
        // 보러 갈 시각: 그 밤 달이 가장 높을 때 (현상 시각이 오전이면 전날 밤)
        val day0 = localDay0(t - 0.5, lon)
        val v = bestNight(day0, lat, lon) { altOfDate(it, lat, lon, Moon.position(it).dir) }
        return Found(t, v, L.t("cheonsang.events.9", local(t, lon)))
    }

    fun newMoon(jd0: Double, lat: Double, lon: Double): Found {
        val t = nextPhase(jd0, 0.0)
        val v = localDay0(t, lon) + 22.0 / 24
        return Found(t, v, L.t("cheonsang.events.10", local(t, lon)))
    }

    /** 유성우: (이름, 극대 해 황경, 복사점 적경·적위, 시간당 최대) */
    data class Shower(val ko: String, val lon: Double, val ra: Double, val dec: Double, val zhr: Double, val en: String = ko) {
        val label: String get() = if (L.ko) ko else en
    }

    fun meteor(jd0: Double, lat: Double, lon: Double, showers: List<Shower>): Found? {
        var best: Pair<Double, Shower>? = null
        for (s in showers) {
            var a = jd0
            fun f(jd: Double) = norm180(Sun.position(jd).lon - s.lon)
            var fa = f(a)
            while (a < jd0 + 400) {
                val b = a + 2.0; val fb = f(b)
                if (fa < 0 && fb >= 0) {
                    var lo = a; var hi = b
                    repeat(40) { val m = (lo + hi) / 2; if (f(m) < 0) lo = m else hi = m }
                    val t = (lo + hi) / 2
                    if (best == null || t < best.first) best = t to s
                    break
                }
                a = b; fa = fb
            }
        }
        val (t, s) = best ?: return null
        val radiant = Vec3.fromSpherical(s.ra, s.dec)
        val v = bestNight(localDay0(t - 0.5, lon), lat, lon) { altJ2000(it, lat, lon, radiant) }
        val illum = Moon.phase(Moon.position(v), Sun.position(v)).illuminated
        return Found(t, v, L.t("cheonsang.events.11", s.label, local(t, lon)).format(s.zhr, illum * 100))
    }

    /** 다음 행성 근접 (두 행성 1° 안, 해에서 15° 넘게 떨어져 보이는 것) */
    fun conjunction(jd0: Double, lat: Double, lon: Double): Found? {
        val ps = Planet.entries
        var prev: List<PlanetPos>? = null
        var prev2: List<PlanetPos>? = null
        var jd = jd0
        while (jd < jd0 + 30 * 365.25) {
            val now = ps.map { Planets.position(it, jd) }
            if (prev != null && prev2 != null) {
                val sunDir = Sun.position(jd - 1).dir
                for (i in ps.indices) for (k in i + 1 until ps.size) {
                    val d0 = prev2[i].dir.angleTo(prev2[k].dir); val d1 = prev[i].dir.angleTo(prev[k].dir); val d2 = now[i].dir.angleTo(now[k].dir)
                    if (d1 <= d0 && d1 < d2 && d1 < 1.0 && prev[i].dir.angleTo(sunDir) > 15) {
                        val a = ps[i]; val b = ps[k]
                        val t = goldenMin(jd - 2, jd) { Planets.position(a, it).dir.angleTo(Planets.position(b, it).dir) }
                        val sep = Planets.position(a, t).dir.angleTo(Planets.position(b, t).dir)
                        val mid = (Planets.position(a, t).dir + Planets.position(b, t).dir).normalized()
                        val v = bestNight(localDay0(t, lon), lat, lon) { altJ2000(it, lat, lon, mid) }
                        return Found(t, v, L.t("cheonsang.events.12", a.label, b.label, local(t, lon)).format(sep))
                    }
                }
            }
            prev2 = prev; prev = now; jd += 1.0
        }
        return null
    }
}
