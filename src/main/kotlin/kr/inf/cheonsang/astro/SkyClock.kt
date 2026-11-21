package kr.inf.cheonsang.astro

import kotlin.math.floor

/**
 * 타임랩스 천문 시계.
 *
 * - 느린 것(해의 황경·달·행성·날짜): jd = epochJd + (tick - epochTick) / 24000 × rate
 * - 하루 회전(별이 도는 것): 1.3.0 부터 **지방평균시** — 마크 시계 = 경도 lon 의 평균 태양시 (06:00 = 0틱).
 *   지방항성시 = 평균 태양 적경 + 평균 태양 시간각 → 압축률 1 이면 GMST + 경도와 정확히 같음 (해 남중은 균시차만큼 ±16분).
 *   압축률을 바꿔도 낮밤 시각은 마크 시계 그대로. (1.2.x 까지는 참태양시·KST 고정)
 * - rate = 1.0 이면 실제 속도(스텔라리움).
 * - lon: 경도 (동 +). 날짜·시각 표시 = 현지 평균시 (경도/15 시간대)
 */
data class SkyClock(
    val epochTick: Long = 0L,
    val epochJd: Double = DEFAULT_EPOCH_JD,
    val rate: Double = DEFAULT_RATE,
    val lon: Double = DEFAULT_LON,
) {
    /** 현지 시간대 (시) = 경도/15 */
    val zoneHours get() = lon / 15.0

    /** 하늘의 율리우스일(UT) */
    fun jd(tick: Double): Double = epochJd + (tick - epochTick) / TICKS_PER_DAY * rate

    /** 지방항성시(도) = 평균 태양 적경 + 마크 시각의 평균 태양 시간각 (1.3.0) */
    fun lst(tick: Double): Double = norm360(meanSunRa(jd(tick)) + sunHourAngle(tick))

    /**
     * 지금 하늘 달력: 날짜 = 그날(자정~자정) 아침 06:00 의 하늘 날짜, 시:분 = 마크 시각.
     * 압축률이 1보다 크면 날짜가 하루에 여러 칸씩 넘어간다 (3.261 → 3~4일씩).
     */
    fun civil(tick: Long): CivilTime {
        val morning = floor((tick + 6000).toDouble() / TICKS_PER_DAY) * TICKS_PER_DAY
        val date = Calendar.toLocal(jd(morning), zoneHours)
        val tod = Math.floorMod(tick + 6000, TICKS_PER_DAY.toLong())
        val mins = (tod * 1440 / TICKS_PER_DAY.toLong()).toInt()
        return CivilTime(date.year, date.month, date.day, mins / 60, mins % 60)
    }

    /** 압축률 변경 — 지금 하늘이 튀지 않도록 기준점을 지금으로 옮김 */
    fun withRate(tick: Long, newRate: Double) = SkyClock(tick, jd(tick.toDouble()), newRate, lon)

    /** 하늘 날짜를 다른 날로 (월드 시간은 그대로) */
    fun withJd(tick: Long, newJd: Double) = SkyClock(tick, newJd, rate, lon)

    /** 마크 틱 → 현지 평균시 (시, 0~24) */
    fun localHours(tick: Double): Double = ((tick + 6000.0) / TICKS_PER_DAY * 24.0).let { it - 24.0 * floor(it / 24.0) }

    companion object {
        const val TICKS_PER_DAY = 24000.0

        /** 기본 압축률: 1.0 = 실제 속도 (11-08 지율님. 3.261 = 1년 112일은 이슬라 합칠 때 다시 정함) */
        const val DEFAULT_RATE = 1.0

        /** 1.3.0 기준점 → 1.9.0: 2026-08-11 06:00 (경도 0° 지방평균시 = UTC) = 월드 0틱 — 하루 준비 뒤 8-12 개기일식·페르세우스 극대·그믐 (11-13 지율님) */
        const val DEFAULT_LAT = 0.0
        const val DEFAULT_LON = 0.0
        val DEFAULT_EPOCH_JD = Calendar.julianDayLocal(2026, 8, 11, 6.0, 0.0)

        /** 1.2.x 까지의 기본: 2019-03-21 06:00 KST, 남위 30° — 저장 기준이 없는 옛 월드 이어 붙이기용 */
        val OLD_EPOCH_JD = Calendar.julianDayKst(2019, 3, 21, 6, 0)
        const val OLD_LAT = -30.0
        /** 옛 월드 경도 (KST 자오선, C-04) */
        const val OLD_LON = 135.0

        /** 평균 태양 적경 (도) — GMST(Meeus 12.4) 에서 정오 UT 의 값 */
        fun meanSunRa(jd: Double): Double = norm360(280.46061837 + 0.98564736629 * (jd - J2000))

        /** 그리니치 평균 항성시 (도, Meeus 12.4) */
        fun gmst(jd: Double): Double {
            val t = (jd - J2000) / 36525.0
            return norm360(280.46061837 + 360.98564736629 * (jd - J2000) + 0.000387933 * t * t - t * t * t / 38710000.0)
        }

        /** 해의 시간각(도): 0틱=06:00 → -90°, 6000틱=정오 → 0° */
        fun sunHourAngle(tick: Double): Double = norm180(tick / TICKS_PER_DAY * 360.0 - 90.0)
    }
}
