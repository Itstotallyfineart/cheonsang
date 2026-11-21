package kr.inf.cheonsang.astro

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 1.10.0-dev2 `/cwhen` 계산 (설계서 cwhen_설계_1.10.0 4·5·6·3절) — 글자 없이 숫자만 (번역·출력은 클라이언트 CWhen).
 * - 일식: 그림자 축(해→달)이 지구 타원체와 만나는 점 = 중심선, 축이 지구 중심에 가장 가까운 순간 = 식 최대 = 가장 좋은 곳
 * - 월식: 식 최대 순간 달 바로 아래 지점 (개기는 U2·U3 가운데)
 * - 유성우: 극대 순간 해 −18° 아래이고 복사점이 가장 높은 곳
 * - 천체: 날마다 밤 20분 간격 점수 → 가장 빠른 좋은 날 (최고의 90%) · 가장 좋은 날
 * alive() 가 false 가 되면 [Cancel] 을 던짐 (새 `/cwhen` 이 앞 계산 취소)
 */
object WhenCalc {
    class Cancel : RuntimeException(null, null, false, false)

    private const val RE = Moon.EARTH_RADIUS_KM
    private const val FLAT = 1.0 / 298.257
    private const val BA = 1.0 - FLAT
    private const val E2 = FLAT * (2.0 - FLAT)
    const val YEARS = 60.0
    /** 일식으로 칠 겹침 — 해 지름의 약 5% (도감 판정·Events 와 같음) */
    private const val SOLAR_MARGIN = 0.1

    // ── 공통 ──
    private class Geo(val jd: Double, val m: Vec3, val s: Vec3, val gst: Double, val moon: MoonPos, val sun: SunPos)

    private fun geo(jd: Double): Geo {
        val mp = Moon.position(jd); val sp = Sun.position(jd)
        return Geo(jd, mp.dir * mp.distKm, sp.dir * (sp.distAu * AU_KM), SkyClock.gmst(jd), mp, sp)
    }

    /** 지리 위도·경도(동 +) → 그 순간 날짜 적도 위치 (km, 타원체) */
    private fun site(lat: Double, lon: Double, gst: Double): Vec3 {
        val sl = sinD(lat); val cl = cosD(lat)
        val n = RE / sqrt(1 - E2 * sl * sl)
        val th = gst + lon
        return Vec3(n * cl * cosD(th), n * cl * sinD(th), n * (1 - E2) * sl)
    }

    /** 타원체 위 점 → (지리 위도, 경도) */
    private fun latLon(p: Vec3, gst: Double): Pair<Double, Double> =
        atan2D(p.z, (1 - E2) * sqrt(p.x * p.x + p.y * p.y)) to norm180(atan2D(p.y, p.x) - gst)

    /** 지구 중심에서 dir 방향으로 나간 반직선이 타원체 표면과 만나는 점 */
    private fun surface(dir: Vec3): Vec3 {
        val d = dir.normalized()
        val r = RE / sqrt(d.x * d.x + d.y * d.y + (d.z / BA) * (d.z / BA))
        return d * r
    }

    private fun scale(v: Vec3) = Vec3(v.x, v.y, v.z / BA)
    private fun unscale(v: Vec3) = Vec3(v.x, v.y, v.z * BA)

    private fun goldenMin(lo0: Double, hi0: Double, f: (Double) -> Double): Double {
        var a = lo0; var b = hi0
        repeat(50) { val m1 = a + (b - a) * 0.382; val m2 = a + (b - a) * 0.618; if (f(m1) < f(m2)) b = m2 else a = m1 }
        return (a + b) / 2
    }

    // ── 일식 (4절) ──
    /** 그 점에서 본 달-해 각거리 d, 달·해 겉보기 반지름, 해 고도 */
    class Local(val d: Double, val rm: Double, val rs: Double, val sunAlt: Double) {
        val total get() = rm > rs && d <= rm - rs
        val annular get() = rs > rm && d <= rs - rm
        val partial get() = d < rm + rs - SOLAR_MARGIN * rs
        /** 해 넓이가 가려진 비율 0~1 */
        val obscuration: Double get() {
            if (d >= rm + rs) return 0.0
            if (d <= abs(rm - rs)) return if (rm >= rs) 1.0 else (rm / rs).pow(2)
            val a1 = rs * rs * acos(((d * d + rs * rs - rm * rm) / (2 * d * rs)).coerceIn(-1.0, 1.0))
            val a2 = rm * rm * acos(((d * d + rm * rm - rs * rs) / (2 * d * rm)).coerceIn(-1.0, 1.0))
            val a3 = 0.5 * sqrt(((-d + rs + rm) * (d + rs - rm) * (d - rs + rm) * (d + rs + rm)).coerceAtLeast(0.0))
            return ((a1 + a2 - a3) / (Math.PI * rs * rs)).coerceIn(0.0, 1.0)
        }
    }

    private fun local(g: Geo, p: Vec3): Local {
        val up = Vec3(p.x, p.y, p.z / (1 - E2)).normalized() // 타원체 법선
        val tm = g.m - p; val ts = g.s - p
        return Local(tm.angleTo(ts), asinD(Moon.RADIUS_KM / tm.length()), asinD(Sun.RADIUS_KM / ts.length()), 90.0 - up.angleTo(ts))
    }

    /** 그림자 축: 축이 지구 중심에서 떨어진 정도 (지구 반지름 단위, 타원체를 구로 늘린 좌표) · 중심선 점 (없으면 축에 가장 가까운 지표점) */
    private class Axis(val gamma: Double, val point: Vec3, val central: Boolean)

    private fun axis(g: Geo): Axis {
        val m = scale(g.m)
        val u = scale(g.m - g.s).normalized()
        val mu = m dot u
        val f = m - u * mu
        val gamma = f.length() / RE
        return if (gamma < 1.0) {
            val s = -mu - sqrt(mu * mu - (m dot m) + RE * RE)
            Axis(gamma, unscale(m + u * s), true)
        } else Axis(gamma, unscale(f * (RE / f.length())), false)
    }

    /** 0 부분 · 1 금환 · 2 개기 · 3 혼성 (개기·금환 둘 다) */
    class Solar(
        val kind: Int, val jd: Double, val lat: Double, val lon: Double, val sunAlt: Double,
        /** 개기·금환 지속 (초), 부분은 0 */
        val durSec: Double,
        /** 띠 폭 (km), 부분은 0 */
        val widthKm: Double,
        /** 가장 좋은 곳의 가림 (넓이 비율) */
        val obscuration: Double,
        /** 내 관측 위치: 요청 종류로 보임 · 최대 가림 (0 = 안 보임) */
        val hereKind: Boolean, val hereObsc: Double,
        /** 중심선이 지구를 지남 (false = 비중심 — 띠 폭·지속 없음) */
        val central: Boolean = true,
    )

    /**
     * want: "total" · "annular" · "partial" (어디서도 개기·금환이 안 되는 일식, NASA 분류) · "any".
     * from 이후 식 최대가 오는 첫 것. accept(식 최대 jd, 결과 경도) 가 false 면 건너뜀 (2-1 미래 판정)
     */
    fun solar(from: Double, want: String, myLat: Double, myLon: Double, alive: () -> Boolean, accept: (Double, Double) -> Boolean): Solar? {
        var nm = Events.nextPhase(from - 0.3, 0.0)
        val end = from + YEARS * 365.25
        while (nm < end) {
            if (!alive()) throw Cancel()
            val geoSep = Moon.position(nm).dir.angleTo(Sun.position(nm).dir)
            if (geoSep < 1.6) {
                val r = solarAt(nm, want, myLat, myLon)
                if (r != null && r.jd > from - 0.3 && accept(r.jd, if (r.hereKind) myLon else r.lon)) return r
            }
            nm = Events.nextPhase(nm + 20, 0.0)
        }
        return null
    }

    private fun solarAt(nm: Double, want: String, myLat: Double, myLon: Double): Solar? {
        val tMax = goldenMin(nm - 0.3, nm + 0.3) { axis(geo(it)).gamma }
        // 식 최대 ±3시간 1분 간격: 중심선(또는 축에 가장 가까운 지표점)에서 개기·금환 여부
        var hasT = false; var hasA = false
        var bestT: Pair<Double, Double>? = null; var bestA: Pair<Double, Double>? = null // (jd, gamma)
        val step = 1.0 / 1440
        var t = tMax - 0.125
        while (t <= tMax + 0.125) {
            val g = geo(t); val ax = axis(g); val lc = local(g, ax.point)
            if (lc.total) { hasT = true; if (bestT == null || ax.gamma < bestT.second) bestT = t to ax.gamma }
            if (lc.annular) { hasA = true; if (bestA == null || ax.gamma < bestA.second) bestA = t to ax.gamma }
            t += step
        }
        val kind = when { hasT && hasA -> 3; hasT -> 2; hasA -> 1; else -> 0 }
        val ok = when (want) {
            "total" -> hasT; "annular" -> hasA; "partial" -> kind == 0; else -> true
        }
        if (!ok) return null

        if (kind == 0) {
            // 부분일식: 식 최대 순간 축에 가장 가까운 지표점에서 해 쪽으로 — 해 고도 1° 넘는 곳 중 가장 많이 가려지는 곳
            val g = geo(tMax); val ax = axis(g)
            val q = ax.point.normalized(); val sd = g.s.normalized()
            var best: Pair<Vec3, Local>? = null
            var a = 0.0
            while (a <= 30.0) {
                val p = surface(Eclipse.towards(q, sd, a)); val lc = local(g, p)
                if (lc.sunAlt > 1.0 && (best == null || lc.obscuration > best.second.obscuration)) best = p to lc
                a += 0.25
            }
            val (p, lc) = best ?: return null
            if (!lc.partial) return null
            val (la, lo) = latLon(p, g.gst)
            val here = here(tMax, myLat, myLon) { false }
            return Solar(0, tMax, la, lo, lc.sunAlt, 0.0, 0.0, lc.obscuration, false, here.first, false)
        }

        // 개기·금환: 요청 종류의 중심선 점 중 식 최대에 가장 가까운 곳 (보통 식 최대 그 점)
        val total = when (want) { "total" -> true; "annular" -> false; else -> kind != 1 }
        val tb = (if (total) bestT else bestA)!!.first
        val g = geo(tb); val ax = axis(g); val p = ax.point
        val lc = local(g, p)
        val (la, lo) = latLon(p, g.gst)
        fun covered(lc: Local) = if (total) lc.total else lc.annular
        // 지속: 그 자리(지구와 함께 돎)에서 10초 간격, 끝은 1초까지
        fun inside(jd: Double): Boolean { val gg = geo(jd); return covered(local(gg, site(la, lo, gg.gst))) }
        fun edge(dir: Double): Double {
            if (!inside(tb)) return 0.0
            var k = 0
            while (k < 120 && inside(tb + dir * (k + 1) * 10.0 / 86400)) k++
            var lo2 = k * 10.0; var hi2 = (k + 1) * 10.0
            repeat(4) { val mid = (lo2 + hi2) / 2; if (inside(tb + dir * mid / 86400)) lo2 = mid else hi2 = mid }
            return (lo2 + hi2) / 2
        }
        val dur = edge(-1.0) + edge(1.0)
        // 띠 폭: 같은 순간 중심선에 직각으로 1km 씩
        val pa = axis(geo(tb - 2.0 / 1440)).point; val pb = axis(geo(tb + 2.0 / 1440)).point
        val up = p.normalized()
        val along = (pb - pa).let { it - up * (it dot up) }
        var width = 0.0
        if (along.length() > 1e-6) {
            val perp = (up cross along).normalized()
            for (sgn in listOf(-1.0, 1.0)) {
                var k = 0
                while (k < 1500 && covered(local(g, surface(p + perp * (sgn * (k + 1)))))) k++
                width += k
            }
        }
        val (hereObsc, hereKind) = here(tb, myLat, myLon) { covered(it) }
        return Solar(if (kind == 3) 3 else if (total) 2 else 1, tb, la, lo, lc.sunAlt, if (ax.central) dur else 0.0, if (ax.central) width else 0.0,
            lc.obscuration, hereKind, hereObsc, ax.central)
    }

    /** 내 관측 위치: 식 최대 ±3시간 2분 간격, 해가 떠 있을 때 (최대 가림, want(그 종류) 만족 여부) */
    private fun here(t0: Double, lat: Double, lon: Double, want: (Local) -> Boolean): Pair<Double, Boolean> {
        var best = 0.0; var kind = false
        var t = t0 - 0.125
        while (t <= t0 + 0.125) {
            val g = geo(t); val lc = local(g, site(lat, lon, g.gst))
            if (lc.sunAlt > 0 && lc.partial) { best = maxOf(best, lc.obscuration); if (want(lc)) kind = true }
            t += 2.0 / 1440
        }
        return best to kind
    }

    // ── 월식 (5절) ──
    /** kind 0 반영 · 1 부분 · 2 개기. contacts = P1 U1 U2 U3 U4 P4 (없으면 NaN) */
    class Lunar(val kind: Int, val jd: Double, val umbraMag: Double, val penMag: Double, val contacts: DoubleArray,
                val viewJd: Double, val lat: Double, val lon: Double, val hereAlt: Double)

    fun lunarKind(u: Double, p: Double) = when { u >= 1.0 -> 2; u > 0.0 -> 1; p > 0.0 -> 0; else -> -1 }

    fun lunar(from: Double, want: String, myLat: Double, myLon: Double, alive: () -> Boolean, accept: (Double, Double) -> Boolean): Lunar? {
        var full = Events.nextPhase(from - 0.3, 180.0)
        val end = from + YEARS * 365.25
        val wantKind = when (want) { "total" -> 2; "partial" -> 1; "penumbral" -> 0; else -> -9 }
        while (full < end) {
            if (!alive()) throw Cancel()
            val t = goldenMin(full - 0.3, full + 0.3) { Moon.position(it).dir.angleTo(Sun.position(it).dir * -1.0) }
            val s = Eclipse.lunar(Moon.position(t), Sun.position(t))
            val k = lunarKind(s.umbraCover, s.penumbraCover)
            if (k >= 0 && (wantKind == -9 || k == wantKind)) {
                val c = contacts(t)
                val view = if (k == 2) (c[2] + c[3]) / 2 else t
                val mv = Moon.position(view)
                val la = mv.dec; val lo = norm180(mv.ra - SkyClock.gmst(view))
                val hereAlt = Events.altOfDate(t, myLat, myLon, Moon.position(t).dir)
                if (t > from - 0.3 && accept(t, if (hereAlt > 0) myLon else lo))
                    return Lunar(k, t, s.umbraCover, s.penumbraCover, c, view, la, lo, hereAlt)
            }
            full = Events.nextPhase(full + 20, 180.0)
        }
        return null
    }

    /** 반영·본영 접촉 시각 (1분 간격 → 1초까지) */
    private fun contacts(tMax: Double): DoubleArray {
        fun sh(jd: Double): Triple<Double, Double, Double> { // sep − 반지름 비교용 값들
            val m = Moon.position(jd); val s = Eclipse.lunar(m, Sun.position(jd)); val rm = Moon.angularRadius(m.distKm)
            return Triple(s.separation - (s.penumbra + rm), s.separation - (s.umbra + rm), s.separation - (s.umbra - rm))
        }
        fun find(sel: (Triple<Double, Double, Double>) -> Double, dir: Double): Double {
            if (sel(sh(tMax)) >= 0) return Double.NaN
            var k = 0
            val st = 1.0 / 1440
            while (k < 400 && sel(sh(tMax + dir * (k + 1) * st)) < 0) k++
            var a = tMax + dir * k * st; var b = tMax + dir * (k + 1) * st
            repeat(12) { val mid = (a + b) / 2; if (sel(sh(mid)) < 0) a = mid else b = mid }
            return (a + b) / 2
        }
        return doubleArrayOf(find({ it.first }, -1.0), find({ it.second }, -1.0), find({ it.third }, -1.0),
            find({ it.third }, 1.0), find({ it.second }, 1.0), find({ it.first }, 1.0))
    }

    // ── 유성우 (6절) ──
    fun showerPeak(from: Double, peakSunLon: Double): Double? {
        fun f(jd: Double) = norm180(Sun.position(jd).lon - peakSunLon)
        var a = from; var fa = f(a)
        while (a < from + 400) {
            val b = a + 2.0; val fb = f(b)
            if (fa < 0 && fb >= 0) {
                var lo = a; var hi = b
                repeat(40) { val m = (lo + hi) / 2; if (f(m) < 0) lo = m else hi = m }
                return (lo + hi) / 2
            }
            a = b; fa = fb
        }
        return null
    }

    /** 유성우 한 곳·한 순간: 복사점 고도, 해 고도, 달 고도, 달 밝은 비율, 시간당 예상 */
    class MeteorSpot(val jd: Double, val lat: Double, val lon: Double, val radAlt: Double, val sunAlt: Double, val moonAlt: Double, val illum: Double, val rate: Double)

    private fun moonCut(illum: Double, moonAlt: Double) = 1.0 - 0.7 * illum * ((moonAlt + 2.0) / 20.0).coerceIn(0.0, 1.0)

    private fun meteorSpot(jd: Double, lat: Double, lon: Double, radDate: Vec3, sun: SunPos, moon: MoonPos, illum: Double, zhr: Double): MeteorSpot {
        val h = Frames.horizon(norm360(SkyClock.gmst(jd) + lon), lat)
        val ra = asinD((h * radDate).y); val sa = asinD((h * sun.dir).y); val ma = asinD((h * moon.dir).y)
        val rate = if (sa < -18 && ra > 0) zhr * sinD(ra) * moonCut(illum, ma) else 0.0
        return MeteorSpot(jd, lat, lon, ra, sa, ma, illum, rate)
    }

    /** 내 위치: 극대 ±12시간 10분 간격 중 가장 많이 보일 때 */
    fun meteorHere(peak: Double, lat: Double, lon: Double, raJ: Double, decJ: Double, zhr: Double): MeteorSpot? {
        var best: MeteorSpot? = null
        var t = peak - 0.5
        val dirJ = Vec3.fromSpherical(raJ, decJ)
        while (t <= peak + 0.5) {
            val sun = Sun.position(t); val moon = Moon.position(t)
            val sp = meteorSpot(t, lat, lon, Frames.precession(t) * dirJ, sun, moon, Moon.phase(moon, sun).illuminated, zhr)
            if (sp.rate > 0 && (best == null || sp.rate > best.rate)) best = sp
            t += 10.0 / 1440
        }
        return best
    }

    /** 지구 어디든: 극대 순간 해 −18° 아래 · 시간당 예상이 가장 많은 곳 (|위도| 작은 쪽 우선) */
    fun meteorBest(peak: Double, raJ: Double, decJ: Double, zhr: Double): MeteorSpot? {
        val sun = Sun.position(peak); val moon = Moon.position(peak); val illum = Moon.phase(moon, sun).illuminated
        val rad = Frames.precession(peak) * Vec3.fromSpherical(raJ, decJ)
        var best: MeteorSpot? = null; var bs = 0.0
        for (la in -65..65) for (lo in -180 until 180) {
            val sp = meteorSpot(peak, la.toDouble(), lo.toDouble(), rad, sun, moon, illum, zhr)
            val s = sp.rate * (1.0 - 0.002 * abs(la))
            if (s > bs) { bs = s; best = sp }
        }
        return best
    }

    // ── 천체 (3절) ──
    /**
     * 천체 하나의 보기 조건 (3-1 표). pos(jd) = 그 순간 날짜 적도 단위벡터.
     * extra(jd) = 그 밖의 조건 0~1 (달 지형: 지형 위 해 고도)
     */
    class Spec(val pos: (Double) -> Vec3, val sunThr: Double, val minAlt: Double, val moonCutK: Double, val extra: ((Double) -> Double)? = null)

    class Night(val jd: Double, val score: Double, val alt: Double, val az: Double, val moonAlt: Double, val illum: Double)

    class ObjResult(val now: Night?, val earliest: Night?, val best: Night?, val maxScore: Double)

    fun score(s: Spec, jd: Double, lat: Double, lon: Double): Night {
        val sun = Sun.position(jd)
        val h = Frames.horizon(norm360(SkyClock.gmst(jd) + lon), lat)
        val sa = asinD((h * sun.dir).y)
        val w = h * s.pos(jd)
        val alt = asinD(w.y); val az = norm360(atan2D(w.x, -w.z))
        if (sa >= s.sunThr + 6 || alt <= s.minAlt) return Night(jd, 0.0, alt, az, -90.0, 0.0)
        val altScore = ((alt - s.minAlt) / (60.0 - s.minAlt)).coerceIn(0.0, 1.0).pow(0.7)
        val dark = if (sa <= s.sunThr) 1.0 else (s.sunThr + 6 - sa) / 6.0
        val moon = Moon.position(jd)
        val mw = h * moon.dir
        val ma = asinD(mw.y)
        val illum = Moon.phase(moon, sun).illuminated
        var moonScore = 1.0
        if (s.moonCutK > 0 && ma > 0) {
            val sep = mw.angleTo(w)
            moonScore = 1.0 - s.moonCutK * illum * (1.2 - sep / 90.0).coerceIn(0.3, 1.0)
        }
        val ex = s.extra?.invoke(jd) ?: 1.0
        return Night(jd, altScore * dark * moonScore * ex, alt, az, ma, illum)
    }

    /**
     * from 이후 days 일: 날마다 현지 정오~다음 정오 20분 간격 → 그 밤 최고 점수.
     * 가장 빠른 좋은 날 = 최고의 90% 넘는 첫날 · 가장 좋은 날 = 최고. 시각은 5분 간격으로 다듬음
     */
    fun objectSearch(s: Spec, from: Double, days: Int, lat: Double, lon: Double, alive: () -> Boolean): ObjResult {
        val loc = Calendar.toLocal(from, lon / 15.0)
        var noon = Calendar.julianDayLocal(loc.year, loc.month, loc.day, 12.0, lon / 15.0)
        if (noon > from) noon -= 1.0
        val dayBest = arrayOfNulls<Night>(days)
        for (i in 0 until days) {
            if (!alive()) throw Cancel()
            var b: Night? = null
            for (j in 0 until 72) {
                val t = noon + i + j / 72.0
                if (t < from) continue
                val n = score(s, t, lat, lon)
                if (n.score > 0 && (b == null || n.score > b.score)) b = n
            }
            dayBest[i] = b
        }
        val max = dayBest.maxOfOrNull { it?.score ?: 0.0 } ?: 0.0
        fun refine(n: Night): Night {
            var best = n; var t = n.jd - 20.0 / 1440
            while (t <= n.jd + 20.0 / 1440) { if (t >= from) { val x = score(s, t, lat, lon); if (x.score > best.score) best = x }; t += 5.0 / 1440 }
            return best
        }
        val nowN = score(s, from, lat, lon).takeIf { it.score > 0 }
        if (max <= 0) return ObjResult(nowN, null, null, 0.0)
        val early = dayBest.first { it != null && it.score >= 0.9 * max }!!
        val best = dayBest.maxByOrNull { it?.score ?: 0.0 }!!
        return ObjResult(nowN, refine(early), refine(best), max)
    }

    /** 3-4: 위도 추천 — 남중 고도가 (60° 근처) 넉넉한 위도 범위 중 내 위도에 가장 가까운 곳 */
    fun recommendLat(myLat: Double, dec: Double, minAlt: Double): Double {
        val span = 60.0 - minAlt
        return myLat.coerceIn(dec - span, dec + span).coerceIn(-66.0, 66.0)
    }

    /** 달 지형 위 해 고도 (칭동·해 셀레노 위도 무시 근사): 해 아래 점 경도 = 180° − 이각 */
    fun featureSunAlt(jd: Double, fLat: Double, fLon: Double): Double {
        val el = norm360(Moon.position(jd).lon - Sun.position(jd).lon)
        val subSolar = 180.0 - el
        return asinD(cosD(fLat) * cosD(fLon - subSolar))
    }
}
