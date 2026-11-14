package kr.inf.cheonsang.astro

import kr.inf.cheonsang.L

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt
import kotlin.math.cos
import kotlin.math.sin

/** 행성 (지구에서 본 모습, J2000 적도 — 별과 같은 좌표계) */
data class PlanetPos(
    val planet: Planet,
    /** J2000 적도 단위벡터 (지구 → 행성, 광행시 보정) */
    val dir: Vec3,
    /** 지구까지 거리 Δ (AU) */
    val distAu: Double,
    /** 해까지 거리 r (AU) */
    val sunDistAu: Double,
    /** 겉보기 등급 */
    val mag: Double,
    /** 위상각 i (해-행성-지구), 도 */
    val phaseAngle: Double,
    /** 밝은 부분 비율 0~1 */
    val illuminated: Double,
    /** 겉보기 지름 (각초) */
    val diameterArcsec: Double,
    /** 밝은 쪽 위치각 (북→동), 도 — 금성 위상 그림 회전용 */
    val brightLimb: Double,
)

enum class Planet(
    val ko: String,
    /** 1 AU 에서의 지름 (각초, Meeus 55장) */
    val diameter1Au: Double,
    /** 색지수 B-V */
    val bv: Double,
) {
    MERCURY("수성", 6.74, 0.93),
    VENUS("금성", 16.92, 0.82),
    MARS("화성", 9.36, 1.36),
    JUPITER("목성", 196.94, 0.87),
    SATURN("토성", 165.6, 1.04),
    URANUS("천왕성", 70.481, 0.56),
    NEPTUNE("해왕성", 68.294, 0.41),;

    /** 화면 이름 (ko 는 식별자·한국어 검색어로 그대로) */
    val label: String get() = kr.inf.cheonsang.L.t("cheonsang.planet.${name.lowercase()}")
}

/** 토성 고리 모습 */
data class SaturnRings(
    /** 지구 쪽 고리 기울기 B (도, 0 = 고리가 선처럼 보임) */
    val tilt: Double,
    /** 자전축(고리 짧은 축) 위치각 P (북→동), 도 */
    val axisAngle: Double,
)

/** 지구에서 본 행성 원판: 가운데 경도(서경 +, 도)·위도·자전축 위치각 P (J2000 북 → 동) */
data class BodyView(val cmWest: Double, val lat: Double, val p: Double)

/** 토성 위성 하나: 이름·J2000 방향·토성 뒤에 숨었는지 */
data class SaturnMoon(val name: String, val num: String, val mag: Double, val dir: Vec3, val hidden: Boolean)

/** 목성 위성 하나: 목성 중심에서 하늘 위 위치 (목성 적도 반지름 단위) */
data class JovianMoon(val name: String, val east: Double, val north: Double, val inFront: Boolean)

/**
 * 행성 위치 — VSOP87D 축약 (Vsop87Data.kt, 진폭 3e-6 이상 항 1,259개).
 * PyEphem 대조 최대 오차 (_tools/vsop_check.py): 1000~3000년 수성·금성·목성·토성·천왕성 4~7″, 화성 13″, 해왕성 4″ ·
 * 기원전 1000~1000년 1~2′ · 기원전 3000~1000년 2~15′.
 */
object Planets {
    /** J2000 황도경사 (황도 → 적도) */
    private const val EPS_J2000 = 23.43928

    /** 자전축 북극 (IAU, J2000 적도) */
    private val JUPITER_POLE = Vec3.fromSpherical(268.057, 64.495)
    private val SATURN_POLE = Vec3.fromSpherical(40.589, 83.537)

    /** 표 순서: 0 수성 1 금성 2 지구 3 화성 4 목성 5 토성 6 천왕성 7 해왕성 */
    private fun index(p: Planet) = when (p) {
        Planet.MERCURY -> 0; Planet.VENUS -> 1; Planet.MARS -> 3; Planet.JUPITER -> 4; Planet.SATURN -> 5
        Planet.URANUS -> 6; Planet.NEPTUNE -> 7
    }

    /** [행성][L·B·R][τ 거듭제곱] = A, B, C 차례 배열 */
    private val series: Array<Array<Array<DoubleArray>>> by lazy {
        Array(VSOP87D.size) { i ->
            VSOP87D[i].split('|').map { s ->
                s.split(';').map { pw ->
                    if (pw.isBlank()) DoubleArray(0)
                    else pw.trim().split(' ').flatMap { it.split(',') }.map { it.toDouble() }.toDoubleArray()
                }.toTypedArray()
            }.toTypedArray()
        }
    }

    private fun sum(ser: Array<DoubleArray>, tau: Double): Double {
        var total = 0.0
        var p = 1.0
        for (c in ser) {
            var s = 0.0
            var k = 0
            while (k < c.size) { s += c[k] * cos(c[k + 1] + c[k + 2] * tau); k += 3 }
            total += s * p
            p *= tau
        }
        return total * 1e-8
    }

    /** 해 중심 J2000 황도 직교좌표 (AU). t = 역학시 율리우스 세기 */
    fun heliocentric(i: Int, t: Double): Vec3 {
        val ser = series[i]
        val tau = t / 10.0
        val l = sum(ser[0], tau); val b = sum(ser[1], tau); val r = sum(ser[2], tau)
        val cb = cos(b)
        val v = Vec3(r * cb * cos(l), r * cb * sin(l), r * sin(b))
        // 날짜 황도 → 날짜 적도 → J2000 적도 (세차 역행렬 = 전치) → J2000 황도
        val jd = J2000 + t * 36525.0
        val eq = Mat3.rotX(Frames.obliquity(jd)) * v
        val pm = Frames.precession(jd).m
        val j = Vec3(
            pm[0] * eq.x + pm[3] * eq.y + pm[6] * eq.z,
            pm[1] * eq.x + pm[4] * eq.y + pm[7] * eq.z,
            pm[2] * eq.x + pm[5] * eq.y + pm[8] * eq.z,
        )
        return Mat3.rotX(-EPS_J2000) * j
    }

    private fun eclToEq(v: Vec3) = Mat3.rotX(EPS_J2000) * v

    /** 지구에서 본 행성 (J2000 적도). jd = UT 율리우스일 */
    fun position(p: Planet, jd: Double): PlanetPos {
        val t = ttCenturies(jd)
        val earth = heliocentric(2, t)
        val i = index(p)
        // 광행시: 빛이 오는 동안 행성이 움직인 만큼 과거 위치 (0.0057755 일/AU)
        var helio = heliocentric(i, t)
        var geo = helio - earth
        repeat(2) {
            helio = heliocentric(i, t - geo.length() * 0.0057755 / 36525.0)
            geo = helio - earth
        }
        val delta = geo.length()
        val r = helio.length()
        val big = earth.length()
        // 위상각: 해-행성-지구
        val cosI = ((r * r + delta * delta - big * big) / (2 * r * delta)).coerceIn(-1.0, 1.0)
        val phase = Math.toDegrees(Math.acos(cosI))
        val dir = eclToEq(geo).normalized()
        val sunDir = eclToEq(earth * -1.0).normalized()
        return PlanetPos(
            planet = p, dir = dir, distAu = delta, sunDistAu = r,
            mag = magnitude(p, r, delta, phase, if (p == Planet.SATURN) rings(dir).tilt else 0.0),
            phaseAngle = phase, illuminated = (1 + cosI) / 2,
            diameterArcsec = p.diameter1Au / delta,
            brightLimb = positionAngle(dir, sunDir),
        )
    }

    fun all(jd: Double): List<PlanetPos> = Planet.entries.map { position(it, jd) }

    /** 겉보기 등급 (Meeus 41장 근사) */
    private fun magnitude(p: Planet, r: Double, d: Double, i: Double, ringTilt: Double): Double {
        val base = 5 * log10(r * d)
        return when (p) {
            Planet.MERCURY -> -0.42 + base + 0.0380 * i - 0.000273 * i * i + 0.000002 * i * i * i
            Planet.VENUS -> -4.40 + base + 0.0009 * i + 0.000239 * i * i - 0.00000065 * i * i * i
            Planet.MARS -> -1.52 + base + 0.016 * i
            Planet.JUPITER -> -9.40 + base + 0.005 * i
            Planet.SATURN -> { val sb = sinD(abs(ringTilt)); -8.88 + base - 2.60 * sb + 1.25 * sb * sb }
            Planet.URANUS -> -7.19 + base
            Planet.NEPTUNE -> -6.87 + base
        }
    }

    /** dir 위치에서 target 쪽 위치각 (천구 북 → 동), 도 */
    fun positionAngle(dir: Vec3, target: Vec3): Double {
        val ncp = Vec3(0.0, 0.0, 1.0)
        val north = (ncp - dir * (dir dot ncp)).normalized()
        val east = north cross dir
        val t = target - dir * (dir dot target)
        return norm360(atan2D(t dot east, t dot north))
    }

    /** 목성 자전축 위치각 (북→동), 도 — 원판 줄무늬 방향 */
    fun jupiterAxis(jupiterDir: Vec3) = positionAngle(jupiterDir, jupiterDir + JUPITER_POLE * 0.001)

    /** 토성 고리 기울기·축 방향 (자전축 북극 벡터로 직접) */
    fun rings(saturnDir: Vec3): SaturnRings {
        val tilt = asinD(-(saturnDir dot SATURN_POLE)) // 토성에서 본 지구의 고리면 위 고도
        return SaturnRings(tilt, positionAngle(saturnDir, saturnDir + SATURN_POLE * 0.001))
    }

    /**
     * 지구에서 본 원판 가운데 경도·위도·축 방향 (IAU 자전 모델, 광행시 보정).
     * 극 α0·δ0 = a0 + a1·T, d0 + d1·T (T = J2000 세기), 본초자오선 W = w0 + w1·d (d = 일).
     * Horizons 대조: 화성 경도·위도·축각 오차 0.1°대, 목성 System III 거의 일치.
     */
    fun bodyView(p: PlanetPos, jd: Double, poleRa: Double, poleRaT: Double, poleDec: Double, poleDecT: Double, w0: Double, w1: Double): BodyView {
        val d = jd + deltaT(jd) / 86400.0 - J2000 - p.distAu * 0.0057755
        val t = d / 36525.0
        val z = Vec3.fromSpherical(poleRa + poleRaT * t, poleDec + poleDecT * t)
        val node = (Vec3(0.0, 0.0, 1.0) cross z).normalized()
        val w = w0 + w1 * d
        val x = node * cosD(w) + (z cross node) * sinD(w)
        val y = z cross x
        val s = p.dir * -1.0
        val east = atan2D(s dot y, s dot x)
        return BodyView(norm360(-east), asinD(s dot z), positionAngle(p.dir, p.dir + z * 0.001))
    }

    /** 화성 (IAU 2009) */
    fun marsView(p: PlanetPos, jd: Double) = bodyView(p, jd, 317.68143, -0.1061, 52.88650, -0.0609, 176.630, 350.89198226)

    /** 목성 System II (대적점 경도 기준, IAU 극 + 870.27°/일) */
    fun jupiterView2(p: PlanetPos, jd: Double) = bodyView(p, jd, 268.056595, -0.006499, 64.495303, 0.002413, 43.3, 870.270)

    /**
     * 대적점 System II 경도 (서경, 도) — 2026-06-01 91°, 한 달 1.75° 씩 증가 (JUPOS 추세, Sky & Telescope 대적점 계산기 기준).
     * 직선 근사라 2026 에서 멀어질수록 어긋남
     */
    fun grsLongitude(jd: Double) = norm360(91.0 + 1.75 / 30.4375 * (jd - 2461192.5))

    /** 대적점 위도 (행성면, 도) */
    const val GRS_LAT = -22.5

    /**
     * 토성 위성 — JPL Horizons 1900~2050 벡터에 맞춘 식 (_tools/fit_saturn_moons.py, fit_saturn_moons2.py).
     * 토성 적도면(극 고정) 위: λ = λ0 + n·t, M = λ − (ϖ0 + ϖ'·t), u = λ + 주기항, r = a0 + 주기항. t = JD − 2451545.
     * 긴 주기 칭동(lib), 적도면 밖 높이(k), 이아페투스만 2λ 항(q, s) 추가. 오차 평균 0.02″~1.5″대.
     * 등급: 충 무렵 평균 (이아페투스는 앞뒤 반구 밝기 차 10.2~11.9 → 평균 11.0)
     */
    private class SatMoon(val name: String, val num: String, val mag: Double, val lam0: Double, val n: Double, val wd: Double,
                          val c: DoubleArray, val r: DoubleArray, val p: Double = 0.0, val lib: DoubleArray? = null, val k: DoubleArray? = null,
                          val q: DoubleArray? = null, val s: DoubleArray? = null)
    private val SAT_MOONS = listOf(
        SatMoon("타이탄", "Ⅵ", 8.3, 7.560510, 22.576976253, 0.001400, doubleArrayOf(-3.015283, 1.343775, 0.040071, -0.043730), doubleArrayOf(1222359.32, 14324.68, 32142.26)),
        SatMoon("레아", "Ⅴ", 9.7, 52.174644, 79.690046276, 0.001500, doubleArrayOf(-0.100607, 0.046767, 0.000054, -0.000039), doubleArrayOf(527063.41, 215.09, 462.71)),
        SatMoon("이아페투스", "Ⅷ", 11.0, 89.884950, 4.537951914, 0.000300, doubleArrayOf(-1.568198, 2.900322, -0.020151, -0.248376), doubleArrayOf(3500978.63, 91130.15, 47823.73),
            k = doubleArrayOf(-7.770511e-02, 2.653128e-01, -4.046132e-07, 6.317095e-07, -7.846501e-13, -7.079953e-13),
            q = doubleArrayOf(0.875942, 0.767701, 5.854819e-06, 4.405704e-06), s = doubleArrayOf(33622.00, -53357.04, 0.255131, -0.214287)),
        SatMoon("테티스", "Ⅲ", 10.2, 188.392193, 190.697911230, 0.001700, doubleArrayOf(-0.002480, 0.001267, -0.000008, -0.000009), doubleArrayOf(294646.59, 3.25, 6.37),
            26625.1, doubleArrayOf(1.618366, -1.317763, 1.048037e-05, 1.128322e-05), doubleArrayOf(1.374939e-04, 7.589174e-06, -7.428039e-09, 3.254007e-08, -1.062018e-12, 1.283537e-12)),
        SatMoon("디오네", "Ⅳ", 10.4, 176.914005, 131.534930733, 0.084250, doubleArrayOf(-0.207026, 0.143454, 0.000122, -0.000323), doubleArrayOf(377416.15, 472.17, 681.42),
            4086.2, doubleArrayOf(-0.005353, -0.019188, 3.649883e-07, -1.672223e-07), doubleArrayOf(-6.453176e-05, 1.004916e-04, 2.656043e-09, 4.770590e-09, 1.381869e-14, 4.461576e-14)),
        SatMoon("엔셀라두스", "Ⅱ", 11.7, 182.263288, 262.731897576, 0.337950, doubleArrayOf(-0.536810, -0.080780, 0.001558, 0.000479), doubleArrayOf(238038.54, -167.57, 1113.54),
            4074.1, doubleArrayOf(0.073524, 0.248972, -3.313017e-06, 1.548300e-06), doubleArrayOf(-1.551674e-05, 2.126359e-05, 2.360173e-09, 1.889059e-09, 1.064814e-14, -1.438243e-15)),
        SatMoon("미마스", "Ⅰ", 12.9, 160.428888, 381.994500545, 1.001500, doubleArrayOf(-1.720369, -0.992415, 0.007808, 0.013784), doubleArrayOf(185536.31, -1602.06, 2777.21),
            26413.3, doubleArrayOf(-33.656994, 27.487582, -1.626137e-04, -1.703648e-04), doubleArrayOf(2.639189e-05, -2.292011e-05, 9.159073e-09, 2.602181e-09, 1.079204e-13, 1.951348e-13)),
    )
    private const val SATURN_RADIUS_KM = 60268.0

    fun saturnMoons(jd: Double, saturn: PlanetPos): List<SaturnMoon> {
        val t = jd + deltaT(jd) / 86400.0 - J2000 - saturn.distAu * 0.0057755
        val z = SATURN_POLE
        val x = (Vec3(0.0, 0.0, 1.0) cross z).normalized()
        val y = z cross x
        val toSat = saturn.dir * (saturn.distAu * AU_KM)
        return SAT_MOONS.map { m ->
            val lam = m.lam0 + m.n * t
            val mm = lam - m.wd * t
            var u = lam + m.c[0] * sinD(mm) + m.c[1] * cosD(mm) + m.c[2] * sinD(2 * mm) + m.c[3] * cosD(2 * mm)
            var r = m.r[0] + m.r[1] * sinD(mm) + m.r[2] * cosD(mm)
            m.lib?.let { l -> val ph = 360.0 * t / m.p; u += (l[0] + l[2] * t) * sinD(ph) + (l[1] + l[3] * t) * cosD(ph) }
            m.q?.let { q -> u += (q[0] + q[2] * t) * sinD(2 * lam) + (q[1] + q[3] * t) * cosD(2 * lam) }
            m.s?.let { s -> r += (s[0] + s[2] * t) * sinD(2 * lam) + (s[1] + s[3] * t) * cosD(2 * lam) }
            val h = m.k?.let { k -> r * ((k[0] + k[2] * t + k[4] * t * t) * sinD(u) + (k[1] + k[3] * t + k[5] * t * t) * cosD(u)) } ?: 0.0
            val off = (x * cosD(u) + y * sinD(u)) * r + z * h
            val along = off dot saturn.dir
            val across = (off - saturn.dir * along).length()
            SaturnMoon(m.name, m.num, m.mag, (toSat + off).normalized(), along > 0 && across < SATURN_RADIUS_KM)
        }
    }

    /**
     * 목성 4대 위성 (Meeus 44장 저정밀식, 오차 약 0.5 목성 반지름).
     * 목성 적도면 좌표 X(서+)·Y(북+) 를 목성 자전축 방향으로 돌려 하늘 위 동·북 오프셋으로.
     */
    fun jovianMoons(jd: Double, jupiter: PlanetPos): List<JovianMoon> {
        val d = jd + deltaT(jd) / 86400.0 - J2000
        val v = 172.74 + 0.00111588 * d
        val m = 357.529 + 0.9856003 * d
        val n = 20.020 + 0.0830853 * d + 0.329 * sinD(v)
        val j = 66.115 + 0.9025179 * d - 0.329 * sinD(v)
        val a = 1.915 * sinD(m) + 0.020 * sinD(2 * m)
        val b = 5.555 * sinD(n) + 0.168 * sinD(2 * n)
        val k = j + a - b
        val bigR = 1.00014 - 0.01671 * cosD(m) - 0.00014 * cosD(2 * m)
        val r = 5.20872 - 0.25208 * cosD(n) - 0.00611 * cosD(2 * n)
        val delta = sqrt(r * r + bigR * bigR - 2 * r * bigR * cosD(k))
        val psi = asinD(bigR / delta * sinD(k))
        val dd = d - delta / 173.0
        var u1 = 163.8069 + 203.4058646 * dd + psi - b
        var u2 = 358.4140 + 101.2916335 * dd + psi - b
        var u3 = 5.7176 + 50.2345180 * dd + psi - b
        var u4 = 224.8092 + 21.4879800 * dd + psi - b
        val g = 331.18 + 50.310482 * dd
        val h = 87.45 + 21.569231 * dd
        val c1 = 0.473 * sinD(2 * (u1 - u2)); val c2 = 1.065 * sinD(2 * (u2 - u3))
        val r1 = 5.9057 - 0.0244 * cosD(2 * (u1 - u2))
        val r2 = 9.3966 - 0.0882 * cosD(2 * (u2 - u3))
        val r3 = 14.9883 - 0.0216 * cosD(g)
        val r4 = 26.3627 - 0.1939 * cosD(h)
        u1 += c1; u2 += c2; u3 += 0.165 * sinD(g); u4 += 0.843 * sinD(h)
        val lambda = 34.35 + 0.083091 * d + 0.329 * sinD(v) + b
        val ds = 3.12 * sinD(lambda + 42.8)
        val de = ds - 2.22 * sinD(psi) * cosD(lambda + 22) - 1.30 * (r - delta) / delta * sinD(lambda - 100.5)
        // 목성 자전축 위치각 → 적도(X) 방향
        val p = positionAngle(jupiter.dir, jupiter.dir + JUPITER_POLE * 0.001)
        fun moon(name: String, u: Double, rr: Double): JovianMoon {
            val x = rr * sinD(u)                 // 서쪽 +
            val y = -rr * cosD(u) * sinD(de)     // 북쪽 +
            // 축 북쪽(P) 기준 → 천구 북·동
            val east = -x * cosD(p) + y * sinD(p)
            val north = x * sinD(p) + y * cosD(p)
            return JovianMoon(name, east, north, cosD(u) > 0) // PyEphem 대조: cos u > 0 이 목성 앞
        }
        return listOf(moon("이오", u1, r1), moon("유로파", u2, r2), moon("가니메데", u3, r3), moon("칼리스토", u4, r4))
    }
}
