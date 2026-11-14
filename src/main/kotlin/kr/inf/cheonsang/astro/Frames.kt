package kr.inf.cheonsang.astro

/**
 * 좌표계 변환.
 * - 적도좌표(날짜 기준): x=춘분점, z=천구 북극
 * - 마크 월드 축: 동=+X, 위=+Y, 북=-Z
 */
object Frames {
    /** 평균 황도경사 ε0 (Meeus 22.2), 도 */
    fun obliquity(jd: Double): Double {
        val t = centuries(jd)
        return 23.0 + 26.0 / 60 + (21.448 - t * (46.8150 + t * (0.00059 - t * 0.001813))) / 3600.0
    }

    /** 황도좌표(λ, β) → 적도 단위벡터 */
    fun eclipticToEquatorial(lonDeg: Double, latDeg: Double, eps: Double): Vec3 =
        Mat3.rotX(eps) * Vec3.fromSpherical(lonDeg, latDeg)

    /** 세차: J2000 적도 → 날짜 적도 (Meeus 21.2·21.3) */
    fun precession(jd: Double): Mat3 {
        val t = centuries(jd)
        val zeta = (2306.2181 * t + 0.30188 * t * t + 0.017998 * t * t * t) / 3600.0
        val z = (2306.2181 * t + 1.09468 * t * t + 0.018203 * t * t * t) / 3600.0
        val theta = (2004.3109 * t - 0.42665 * t * t - 0.041833 * t * t * t) / 3600.0
        val c = cosD(theta); val s = sinD(theta)
        val ry = Mat3(doubleArrayOf(c, 0.0, -s, 0.0, 1.0, 0.0, s, 0.0, c))
        return Mat3.rotZ(z) * ry * Mat3.rotZ(zeta)
    }

    /**
     * 날짜 적도 → 마크 월드 방향. lst = 지방항성시(도), lat = 위도(도).
     * 시간각 좌표 (cosδ cosH, -cosδ sinH, sinδ) 에서 동·북·위를 구해 (동, 위, -북) 으로 배치.
     */
    fun horizon(lstDeg: Double, latDeg: Double): Mat3 {
        val sl = sinD(latDeg); val cl = cosD(latDeg)
        val toHa = Mat3.rotZ(-lstDeg)
        // 동 = y, 북 = -sinφ x + cosφ z, 위 = cosφ x + sinφ z
        val toWorld = Mat3(doubleArrayOf(
            0.0, 1.0, 0.0,
            cl, 0.0, sl,
            sl, 0.0, -cl,
        ))
        return toWorld * toHa
    }

    /** 월드 방향 → (고도, 방위각 북=0 동=90) 도 — 명령 출력·디버그용 */
    fun altAz(world: Vec3): Pair<Double, Double> {
        val v = world.normalized()
        return asinD(v.y) to norm360(atan2D(v.x, -v.z))
    }
}
