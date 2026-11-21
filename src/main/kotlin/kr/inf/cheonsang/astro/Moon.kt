package kr.inf.cheonsang.astro

import kotlin.math.roundToInt

/** 달 위치 (지구 중심, 평균 춘분점 기준) */
data class MoonPos(val lon: Double, val lat: Double, val distKm: Double, val ra: Double, val dec: Double) {
    val dir: Vec3 get() = Vec3.fromSpherical(ra, dec)
}

/** 달 위상 정보 */
data class MoonPhase(
    /** 해와의 황경 차 0~360 (0=삭, 180=망) */
    val elongation: Double,
    /** 위상각 i (해-달-지구), 도 */
    val phaseAngle: Double,
    /** 밝은 부분 비율 0~1 */
    val illuminated: Double,
    /** 밝은 쪽 가장자리 위치각 χ (천구 북쪽에서 동쪽으로), 도 */
    val brightLimb: Double,
    /** 위상 그림 번호 0~29 (0=삭, 15=망) */
    val texture: Int,
)

/** 달 칭동 (1.2.3): 지구 쪽을 향한 셀레노 경도 l·위도 b, 달 자전축 위치각 P (천구 북쪽 → 동쪽), 도 */
data class Libration(val l: Double, val b: Double, val p: Double)

/** Meeus 47장 달 (주기항 60+60, 오차 약 10") */
object Moon {
    const val RADIUS_KM = 1737.4
    const val EARTH_RADIUS_KM = 6378.14

    fun position(jd: Double): MoonPos {
        val t = ttCenturies(jd)
        val lp = 218.3164477 + t * (481267.88123421 + t * (-0.0015786 + t * (1 / 538841.0 - t / 65194000.0)))
        val d = 297.8501921 + t * (445267.1114034 + t * (-0.0018819 + t * (1 / 545868.0 - t / 113065000.0)))
        val m = 357.5291092 + t * (35999.0502909 + t * (-0.0001536 + t / 24490000.0))
        val mp = 134.9633964 + t * (477198.8675055 + t * (0.0087414 + t * (1 / 69699.0 - t / 14712000.0)))
        val f = 93.2720950 + t * (483202.0175233 + t * (-0.0036539 + t * (-1 / 3526000.0 + t / 863310000.0)))
        val a1 = 119.75 + 131.849 * t
        val a2 = 53.09 + 479264.290 * t
        val a3 = 313.45 + 481266.484 * t
        val e = 1 - t * (0.002516 + t * 0.0000074)

        var sl = 3958 * sinD(a1) + 1962 * sinD(lp - f) + 318 * sinD(a2)
        var sr = 0.0
        var sb = -2235 * sinD(lp) + 382 * sinD(a3) + 175 * sinD(a1 - f) + 175 * sinD(a1 + f) +
            127 * sinD(lp - mp) - 115 * sinD(lp + mp)

        var i = 0
        while (i < MOON_LR.size) {
            val cm = MOON_LR[i + 1]
            val arg = d * MOON_LR[i] + m * cm + mp * MOON_LR[i + 2] + f * MOON_LR[i + 3]
            val ef = eFactor(cm, e)
            sl += MOON_LR[i + 4] * sinD(arg) * ef
            sr += MOON_LR[i + 5] * cosD(arg) * ef
            i += 6
        }
        i = 0
        while (i < MOON_B.size) {
            val cm = MOON_B[i + 1]
            val arg = d * MOON_B[i] + m * cm + mp * MOON_B[i + 2] + f * MOON_B[i + 3]
            sb += MOON_B[i + 4] * sinD(arg) * eFactor(cm, e)
            i += 5
        }

        val lon = norm360(lp + sl / 1e6)
        val lat = sb / 1e6
        val dist = 385000.56 + sr / 1000.0
        val (ra, dec) = Frames.eclipticToEquatorial(lon, lat, Frames.obliquity(jd)).toSpherical()
        return MoonPos(lon, lat, dist, ra, dec)
    }

    private fun eFactor(cm: Int, e: Double) = when (cm) {
        0 -> 1.0
        1, -1 -> e
        else -> e * e
    }

    /** 위상 (Meeus 48장) */
    fun phase(moon: MoonPos, sun: SunPos): MoonPhase {
        val elong = norm360(moon.lon - sun.lon)
        val psi = moon.dir.angleTo(sun.dir) // 지구에서 본 달-해 각거리
        val r = sun.distAu * AU_KM
        val i = atan2D(r * sinD(psi), moon.distKm - r * cosD(psi))
        val k = (1 + cosD(i)) / 2
        val dra = sun.ra - moon.ra
        val chi = norm360(atan2D(
            cosD(sun.dec) * sinD(dra),
            sinD(sun.dec) * cosD(moon.dec) - cosD(sun.dec) * sinD(moon.dec) * cosD(dra),
        ))
        val tex = (elong / 360.0 * 30).roundToInt() % 30
        return MoonPhase(elong, i, k, chi, tex)
    }

    /**
     * 광학 칭동 + 자전축 위치각 (Meeus 53장, 물리 칭동·장동은 뺌 — 0.04° 안팎).
     * 검증: Meeus 예제 53.a (1992-04-12) l −1.20° · b +4.19° · P 15.07° (책: −1.206 · 4.194 · 15.08)
     */
    fun libration(jd: Double, moon: MoonPos): Libration {
        val t = ttCenturies(jd)
        val f = 93.2720950 + t * (483202.0175233 + t * (-0.0036539 + t * (-1 / 3526000.0 + t / 863310000.0)))
        val om = 125.0445479 - 1934.1362891 * t + 0.0020754 * t * t
        val inc = 1.54242
        val w = moon.lon - om
        val a = atan2D(sinD(w) * cosD(moon.lat) * cosD(inc) - sinD(moon.lat) * sinD(inc), cosD(w) * cosD(moon.lat))
        val l = norm180(a - f)
        val b = asinD(-sinD(w) * cosD(moon.lat) * sinD(inc) - sinD(moon.lat) * cosD(inc))
        val eps = Frames.obliquity(jd)
        val x = sinD(inc) * sinD(om)
        val y = sinD(inc) * cosD(om) * cosD(eps) - cosD(inc) * sinD(eps)
        val omega = atan2D(x, y)
        val p = asinD(kotlin.math.sqrt(x * x + y * y) * cosD(moon.ra - omega) / cosD(b))
        return Libration(l, b, p)
    }

    /** 겉보기 반지름(도) — 시직경의 절반 */
    fun angularRadius(distKm: Double) = asinD(RADIUS_KM / distKm)

    /**
     * 지표면 관측(시차) 보정: 지구 중심 방향 → 관측자 기준 방향.
     * 월드 좌표에서 관측자는 지구 중심보다 위(+Y)로 지구 반지름만큼 올라가 있다.
     */
    fun topocentric(worldDir: Vec3, distKm: Double): Vec3 =
        (worldDir * distKm - Vec3(0.0, EARTH_RADIUS_KM, 0.0)).normalized()
}
