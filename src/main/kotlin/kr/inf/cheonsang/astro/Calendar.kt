package kr.inf.cheonsang.astro

import kotlin.math.floor

/** 달력 날짜·시각 (하늘 달력, KST 기준) */
data class CivilTime(val year: Int, val month: Int, val day: Int, val hour: Int, val minute: Int) {
    override fun toString() = "%04d-%02d-%02d %02d:%02d".format(year, month, day, hour, minute)
}

/**
 * 율리우스일 ↔ 달력 (Meeus 7장).
 * 1582-10-15 이후 그레고리력, 그 전은 율리우스력 (천문학 관례, 스텔라리움과 같음).
 */
object Calendar {
    /** 하늘 달력 시간대: KST (UTC+9) */
    const val ZONE_HOURS = 9.0

    fun isGregorian(year: Int, month: Int, day: Int): Boolean =
        year > 1582 || (year == 1582 && (month > 10 || (month == 10 && day >= 15)))

    /** 윤년: 그레고리력 400/100/4 규칙, 1582년 이전은 4년마다 */
    fun isLeapYear(year: Int): Boolean =
        if (year > 1582) (year % 4 == 0 && year % 100 != 0) || year % 400 == 0
        else Math.floorMod(year, 4) == 0

    fun daysInMonth(year: Int, month: Int): Int = when (month) {
        2 -> if (isLeapYear(year)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }

    /** UT 기준 날짜 (day 에 소수 포함) → 율리우스일 */
    fun julianDay(year: Int, month: Int, day: Double): Double {
        var y = year
        var m = month
        if (m <= 2) { y -= 1; m += 12 }
        val b = if (isGregorian(year, month, floor(day).toInt())) {
            val a = floor(y / 100.0)
            2 - a + floor(a / 4)
        } else 0.0
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    /** KST 날짜·시각 → 율리우스일(UT) */
    fun julianDayKst(year: Int, month: Int, day: Int, hour: Int, minute: Int): Double =
        julianDayLocal(year, month, day, hour + minute / 60.0, ZONE_HOURS)

    /** 현지 날짜·시각(시, 소수) → 율리우스일(UT). zoneHours = 경도/15 (지방평균시) */
    fun julianDayLocal(year: Int, month: Int, day: Int, hours: Double, zoneHours: Double): Double =
        julianDay(year, month, day + (hours - zoneHours) / 24.0)

    /** 율리우스일(UT) → KST 날짜·시각 (분 단위 반올림) */
    fun toKst(jd: Double): CivilTime = toLocal(jd, ZONE_HOURS)

    /** 율리우스일(UT) → 현지 날짜·시각 (zoneHours = 경도/15) */
    fun toLocal(jd: Double, zoneHours: Double): CivilTime {
        // 분 단위로 먼저 반올림해 23:59.99 → 24:00 같은 넘침 방지
        val j = floor((jd + zoneHours / 24.0) * 1440.0 + 0.5) / 1440.0
        val z = floor(j + 0.5)
        val f = j + 0.5 - z
        val a = if (z < 2299161) z else {
            val alpha = floor((z - 1867216.25) / 36524.25)
            z + 1 + alpha - floor(alpha / 4)
        }
        val b = a + 1524
        val c = floor((b - 122.1) / 365.25)
        val d = floor(365.25 * c)
        val e = floor((b - d) / 30.6001)
        val day = (b - d - floor(30.6001 * e)).toInt()
        val month = if (e < 14) e.toInt() - 1 else e.toInt() - 13
        val year = if (month > 2) c.toInt() - 4716 else c.toInt() - 4715
        val mins = Math.round(f * 1440.0).toInt().coerceIn(0, 1439)
        return CivilTime(year, month, day, mins / 60, mins % 60)
    }
}
