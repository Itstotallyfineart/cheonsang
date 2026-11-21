package kr.inf.cheonsang.astro

/** 월식: 지구 그림자(본영·반영)가 달을 얼마나 덮는지 */
data class LunarShadow(
    /** 본영 반지름(도) */
    val umbra: Double,
    /** 반영 반지름(도) */
    val penumbra: Double,
    /** 달 중심 ~ 그림자 중심(해 반대점) 각거리(도) */
    val separation: Double,
    /** 달 원판이 본영에 들어간 비율 0~1 (1 = 개기월식) */
    val umbraCover: Double,
    /** 달 원판이 반영에 들어간 비율 0~1 */
    val penumbraCover: Double,
)

/**
 * 일식·월식 계산. 따로 '이벤트'를 만들지 않고 실제 위치·크기에서 저절로 나오게 한다.
 * - 일식: 달을 해보다 나중에 그려서 가림 (렌더러). 달은 관측자 기준 방향 ([topocentricMoon]).
 * - 월식: 그림자 원 두 겹 (Meeus 54장, Danjon 1.02 확대 포함).
 */
object Eclipse {
    fun lunar(moon: MoonPos, sun: SunPos): LunarShadow {
        val pm = asinD(Moon.EARTH_RADIUS_KM / moon.distKm) // 달 지평시차
        val ss = Sun.angularRadius(sun.distAu)
        val ps = 8.794 / 3600.0 / sun.distAu
        val umbra = 1.02 * (0.998340 * pm - ss + ps)
        val penumbra = 1.02 * (0.998340 * pm + ss + ps)
        val anti = sun.dir * -1.0
        val sep = moon.dir.angleTo(anti)
        val rm = Moon.angularRadius(moon.distKm)
        fun cover(r: Double) = ((r + rm - sep) / (2 * rm)).coerceIn(0.0, 1.0)
        return LunarShadow(umbra, penumbra, sep, cover(umbra), cover(penumbra))
    }

    /**
     * 1.3.0 관측자 기준 달 방향 (그날 적도좌표): 지구 중심 달 위치 − 관측자 위치 (지구 반지름, 위도·지방항성시).
     * 시차 최대 약 1° — 일식은 이 방향으로 해를 가려야만 보임 (그 장소에서 실제로 보이는 일식만, Stellarium 과 같음).
     * 1.2.x 의 "위도 위 가장 잘 보이는 경도" 탐색(경도 무시)은 폐지
     */
    fun topocentricMoon(moonEq: Vec3, distKm: Double, latDeg: Double, lstDeg: Double): Vec3 =
        (moonEq * distKm - Vec3.fromSpherical(lstDeg, latDeg) * Moon.EARTH_RADIUS_KM).normalized()

    /** 단위벡터 a 에서 b 쪽으로 대원을 따라 angle(도) 만큼 간 방향 */
    fun towards(a: Vec3, b: Vec3, angle: Double): Vec3 {
        val t = b - a * (a dot b)
        val len = t.length()
        if (len < 1e-9) return a
        val tn = t * (1.0 / len)
        return (a * cosD(angle) + tn * sinD(angle)).normalized()
    }
}
