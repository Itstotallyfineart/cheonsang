package kr.inf.cheonsang.client

import kotlin.math.log10
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 1.7.0 손 망원경 광학 (11-12 지율님 확정).
 * - 대물 구경 (Shift+휠) · 접안 초점거리 (휠) 를 바꿈. F비 f/7 고정 → 대물 초점거리 = 구경 × 7, 배율 = 대물 초점 ÷ 접안 초점
 * - 켤 때마다 기본값 (80mm, ×1 부터)
 * - 한계 등급 = 맨눈 + 5·log10(유효 구경 / 눈동자 7mm) + 0.43 (맨눈 6.5등에서 7.7 + 5·log10(구경 cm) 와 같음).
 *   유효 구경 = min(구경, 배율 × 7mm) — 출사동이 눈동자보다 크면 빛이 다 안 들어옴
 * - 배율 한계 = 구경 × 2 (막지 않음 — 넘으면 별 상이 회절·시상만큼 커져 흐려짐)
 */
object Optics {
    val APERTURES = intArrayOf(50, 60, 70, 80, 90, 102, 114, 127, 150, 200, 254, 305)
    val EYEPIECES = doubleArrayOf(40.0, 32.0, 25.0, 20.0, 15.0, 12.0, 9.0, 7.0, 5.0, 4.0, 3.0)
    const val DEFAULT_APERTURE = 3
    const val F_RATIO = 7.0
    const val EYE_PUPIL_MM = 7.0
    /**
     * 2.0.2-dev1 시상 (FWHM ″): 밤마다 다름 (그 장소 현지 날짜로 정해지는 값 0.9~3.0, 1.2~2 가 흔함) × 보는 방향 대기량^0.6.
     * 전: 1.5″ 고정
     */
    fun nightSeeing(): Double {
        val jd = kr.inf.cheonsang.client.sky.CheonsangSky.clock.jd((net.minecraft.client.Minecraft.getInstance().level?.defaultClockTime ?: 0L).toDouble())
        val night = kotlin.math.floor(jd + kr.inf.cheonsang.client.sky.CheonsangSky.longitude / 360.0).toLong()
        if (night == cachedNight) return cachedSeeing
        var x = night * -0x61c8864680b583ebL; x = (x xor (x ushr 31)) * -0x40a7b892e31b1a47L; x = x xor (x ushr 29)
        val u1 = ((x ushr 11) and 0xFFFFF).toDouble() / 0xFFFFF; val u2 = ((x ushr 33) and 0xFFFFF).toDouble() / 0xFFFFF
        cachedNight = night; cachedSeeing = (0.9 + 2.1 * (u1 + u2) / 2 * (0.6 + 0.4 * u1)).coerceIn(0.9, 3.0)
        return cachedSeeing
    }
    private var cachedNight = Long.MIN_VALUE
    private var cachedSeeing = 1.5

    /** 지금 보는 방향 시상 — 고도 낮을수록 나쁨 (대기량^0.6, 고도 5° 아래는 5° 값) */
    fun seeingArcsec(): Double {
        val alt = (-(net.minecraft.client.Minecraft.getInstance().player?.xRot ?: -45f).toDouble()).coerceIn(5.0, 90.0)
        val airmass = 1.0 / kotlin.math.sin(Math.toRadians(alt + 244.0 / (165.0 + 47.0 * alt.pow(1.1))))
        return nightSeeing() * airmass.pow(0.6)
    }

    var apIdx = DEFAULT_APERTURE
        private set
    /** 구경이 바뀔 때마다 올라감 (배율 단계·심원천체 버퍼 다시 만들기) */
    var version = 0
        private set

    val apertureMm get() = APERTURES[apIdx].toDouble()
    val focalMm get() = apertureMm * F_RATIO
    fun magnification(eyepiece: Int) = focalMm / EYEPIECES[eyepiece]
    /** 쓸 만한 최대 배율 (구경 mm × 2) */
    val maxUseful get() = apertureMm * 2

    fun reset() { if (apIdx != DEFAULT_APERTURE) { apIdx = DEFAULT_APERTURE; version++ } }

    /** 구경 한 단계 (+1 = 크게). 바뀌었으면 true */
    fun stepAperture(d: Int): Boolean {
        val n = (apIdx + d).coerceIn(0, APERTURES.size - 1)
        if (n == apIdx) return false
        apIdx = n; version++
        return true
    }

    /** 점광원 한계 이득 (등) — 배율 1 이하 = 맨눈 0 */
    fun gain(zoom: Double): Double {
        if (zoom <= 1.0) return 0.0
        val eff = min(apertureMm, zoom * EYE_PUPIL_MM)
        return 5.0 * log10(eff / EYE_PUPIL_MM) + 0.43
    }

    /** 출사동 (mm) */
    fun exitPupil(zoom: Double) = if (zoom <= 1.0) EYE_PUPIL_MM else apertureMm / zoom

    /** 별 상 반지름 (″) — 회절 (에어리 첫 어둠 138/D) 과 시상을 합친 크기의 대략 */
    fun starRadiusArcsec(): Double {
        val airy = 138.0 / apertureMm
        val s = seeingArcsec()
        return 0.6 * sqrt(airy * airy + s * s)
    }
}
