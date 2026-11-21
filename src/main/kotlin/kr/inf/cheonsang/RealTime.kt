package kr.inf.cheonsang

import kr.inf.cheonsang.astro.SkyClock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * 1.0.0 리얼타임 모드: 마크 하루 = 현실 하루, 하늘 = 지금 그 도시의 실제 하늘.
 * - 월드 시계 속도 1/72 (마크 시계 기능), 시각 = 그 도시의 지방평균시
 * - 하늘: 1.3.0 부터 = 도시 위치(위도·경도)로 /cele set + 압축률 1 + 지금 현실 시각 (예외 계산 없음)
 */
object RealTime {
    data class City(val id: String, val name: String, val lat: Double, val lon: Double, val zone: String) {
        /** 1.9.0-dev2: 화면 이름 (name 은 /cele set 에 한국어로 적는 이름) */
        val label: String get() = L.t("cheonsang.city.$id")
        fun label(lang: String?): String = L.tl(lang, "cheonsang.city.$id")
    }

    /** 북 → 남. 기본 서울 */
    val CITIES = listOf(
        City("longyearbyen", "롱위에아르뷔엔", 78.22, 15.65, "Arctic/Longyearbyen"),
        City("reykjavik", "레이캬비크", 64.15, -21.94, "Atlantic/Reykjavik"),
        City("london", "런던", 51.51, -0.13, "Europe/London"),
        City("newyork", "뉴욕", 40.71, -74.01, "America/New_York"),
        City("seoul", "서울", 37.57, 126.98, "Asia/Seoul"),
        City("cairo", "카이로", 30.04, 31.24, "Africa/Cairo"),
        City("hongkong", "홍콩", 22.32, 114.17, "Asia/Hong_Kong"),
        City("singapore", "싱가포르", 1.35, 103.82, "Asia/Singapore"),
        City("lima", "리마", -12.05, -77.04, "America/Lima"),
        City("sydney", "시드니", -33.87, 151.21, "Australia/Sydney"),
        City("buenosaires", "부에노스아이레스", -34.60, -58.38, "America/Argentina/Buenos_Aires"),
        City("ushuaia", "우수아이아", -54.80, -68.30, "America/Argentina/Ushuaia"),
        City("mcmurdo", "맥머도", -77.85, 166.67, "Antarctica/McMurdo"),
    )
    val DEFAULT = CITIES.first { it.id == "seoul" }

    /** 마크 시계 속도: 현실 1초 = 20틱 ÷ 72 */
    const val RATE = (1.0 / 72.0).toFloat()

    fun jdNow(): Double = System.currentTimeMillis() / 86_400_000.0 + 2440587.5

    /**
     * 1.3.0: 지금 그 도시의 지방평균시 마크 시각 (0~23999) — 천상 시계 규칙: 06:00 = 0틱, 지방평균시 = 세계시 + 경도/15.
     * (1.2.x 의 참태양시·경도 예외 계산은 없앰 — 하늘은 도시 위치 그대로 SkyClock 이 계산, C-09)
     */
    fun solarTickOfDay(jd: Double, c: City): Double {
        val ut = (jd + 0.5).let { (it - floor(it)) * 24.0 }
        val lmt = ut + c.lon / 15.0
        val t = (lmt - 6.0) / 24.0 * SkyClock.TICKS_PER_DAY
        return ((t % 24000.0) + 24000.0) % 24000.0
    }

    /** 지금 월드 총 틱에서 가장 가까운, 그 시각에 해당하는 총 틱 */
    fun targetTotal(current: Long, jd: Double, c: City): Long {
        val base = floor(current / 24000.0) * 24000.0 + solarTickOfDay(jd, c)
        return listOf(base - 24000.0, base, base + 24000.0).minBy { abs(it - current) }.roundToLong()
    }

    private val FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    fun localText(c: City): String = FMT.format(Instant.now().atZone(ZoneId.of(c.zone)))
}
