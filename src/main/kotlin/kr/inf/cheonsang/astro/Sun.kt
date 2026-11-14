package kr.inf.cheonsang.astro

/** 해 위치 (평균 춘분점 기준, 광행차 포함) */
data class SunPos(val lon: Double, val distAu: Double, val ra: Double, val dec: Double) {
    val dir: Vec3 get() = Vec3.fromSpherical(ra, dec)
}

/** Meeus 25장 저정밀 해 (오차 약 0.01°) */
object Sun {
    const val RADIUS_KM = 696000.0

    fun position(jd: Double): SunPos {
        val t = ttCenturies(jd)
        val l0 = 280.46646 + t * (36000.76983 + t * 0.0003032)
        val m = 357.52911 + t * (35999.05029 - t * 0.0001537)
        val e = 0.016708634 - t * (0.000042037 + t * 0.0000001267)
        val c = (1.914602 - t * (0.004817 + t * 0.000014)) * sinD(m) +
            (0.019993 - t * 0.000101) * sinD(2 * m) + 0.000289 * sinD(3 * m)
        val nu = m + c
        val r = 1.000001018 * (1 - e * e) / (1 + e * cosD(nu))
        val lon = norm360(l0 + c - 0.00569) // 광행차
        val (ra, dec) = Frames.eclipticToEquatorial(lon, 0.0, Frames.obliquity(jd)).toSpherical()
        return SunPos(lon, r, ra, dec)
    }

    /** 겉보기 반지름(도) — 시직경의 절반 */
    fun angularRadius(distAu: Double) = asinD(RADIUS_KM / (distAu * AU_KM))
}
