package kr.inf.cheonsang.client

import kotlin.math.log10
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 손 망원경 광학. F비 f/7 고정: 대물 초점거리 = 구경×7, 배율 = 대물 초점 ÷ 접안 초점.
 * 한계 등급 = 맨눈 + 5·log10(유효 구경 / 눈동자 7mm) + 0.43, 유효 구경 = min(구경, 배율×7mm).
 * 배율 한계 = 구경×2 (강제 제한 없음 — 넘으면 회절·시상으로 별 상이 흐려짐).
 */
object Optics {
    val APERTURES = intArrayOf(50, 60, 70, 80, 90, 102, 114, 127, 150, 200, 254, 305)
    val EYEPIECES = doubleArrayOf(40.0, 32.0, 25.0, 20.0, 15.0, 12.0, 9.0, 7.0, 5.0, 4.0, 3.0)
    const val DEFAULT_APERTURE = 3
    const val F_RATIO = 7.0
    const val EYE_PUPIL_MM = 7.0
    /** 시상 (FWHM ″) */
    const val SEEING_ARCSEC = 1.5

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
        return 0.6 * sqrt(airy * airy + SEEING_ARCSEC * SEEING_ARCSEC)
    }
}
