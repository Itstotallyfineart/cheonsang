package kr.inf.cheonsang.client

import kr.inf.cheonsang.L
import kr.inf.cheonsang.astro.Vec3
import kr.inf.cheonsang.client.sky.CheonsangSky
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * 통합 가시성 모델 — 하늘 그리기와 천체 표시가 같은 함수를 씀.
 *
 * 적용 순서 (고정):
 *  ① 천체 고유 밝기 (등급 m, 면광원은 표면 밝기 S 등/초각²)
 *  ② 대기 소광 — 고도에서 대기량 X(Kasten–Young) → Δm = k·(X − 1)   [이 단계에서만 적용]
 *  ③ 하늘 밝기 B (nL) = 자연 하늘(대기광, van Rhijn) + 박명 + 달빛(Krisciunas–Schaefer 1991) + 빛공해 + 황도광·대일조
 *  ④ 관측자 상태 — 암적응 손해
 *  ⑤ 장비 — 망원경 이득 2.5·log10(배율) (최대 11등, 구경 항으로 교체) · 면광원은 출사동 비율
 *  ⑥ 판정 — 점광원: m + Δm ≤ 한계 / 면광원: 표면 밝기 대비 + 겉보기 크기
 *
 * 입력·단위·기본값: 해 고도(도) · 달 밝은 비율(0~1)·달 방향 · 천체 방향(월드) · 맨눈 한계(설정, 기본 6.0등 = 어두운 하늘 천정)
 *  · 소광 계수 k = 0.172 (V) · 어두운 하늘 = 22.0 등/초각² · 손 망원경 구경 80mm · 눈동자 7mm
 * 아직 안 만든 효과(빛공해·황도광·암적응·반짝임·오로라)는 중립값 0 (끈 것과 같은 결과).
 * 효과별 개발용 켜고 끄기: `/cvis <효과> on|off` (저장 안 함)
 */
object Visibility {
    enum class Fx(val id: String, val ko: String) {
        TWILIGHT("twilight", "박명"), MOON("moon", "달빛 하늘"), AIRGLOW("airglow", "대기광"), EXTINCTION("extinction", "소광"),
        REFRACTION("refraction", "굴절"), COLORLOSS("colorloss", "색 빠짐"), LIGHTPOLLUTION("light", "빛공해"), DARKADAPT("dark", "암적응"),
        ZODIACAL("zodiacal", "황도광·대일조"), TWINKLE("twinkle", "반짝임"), AURORA("aurora", "오로라"),
        ;
        /** 화면 이름 (생성자의 한국어는 기록용) */
        val label: String get() = L.t("cheonsang.vis.$id")
    }
    private val offSet = java.util.EnumSet.noneOf(Fx::class.java)
    fun on(fx: Fx) = fx !in offSet
    fun set(fx: Fx, v: Boolean) { if (v) offSet.remove(fx) else offSet.add(fx); version++ }
    /** 효과 스위치가 바뀔 때마다 1 증가 (구워 둔 메시 다시 만들기) */
    var version = 0
        private set
    /** 구경이 바뀌어 구워 둔 메시를 다시 만들 때 */
    fun bump() { version++ }

    // ── 상수 ──
    /** V 밴드 소광 계수 (등/대기량) */
    const val K_V = 0.172
    /** 어두운 자연 하늘 천정 밝기 (등/초각²) */
    const val DARK_SKY = 22.0
    /** 손 망원경 구경 (mm) · 어두운 곳 눈동자 (mm) */
    const val APERTURE_MM = 80.0
    const val EYE_PUPIL_MM = 7.0
    fun altitude(w: Vec3): Double = Math.toDegrees(asin(w.normalized().y.coerceIn(-1.0, 1.0)))

    /** 등/초각² ↔ nL (Schaefer 1990) */
    fun nl(mag: Double): Double = 34.08 * exp(20.7233 - 0.92104 * mag)
    fun magOf(nl: Double): Double = (20.7233 - ln(max(nl, 1e-9) / 34.08)) / 0.92104
    private val DARK_NL = nl(DARK_SKY)

    /** 대기량 (Kasten–Young 1989) — 지평선 약 38 */
    fun airmass(altDeg: Double): Double {
        val h = altDeg.coerceAtLeast(-0.5)
        return min(40.0, 1.0 / (kotlin.math.sin(Math.toRadians(h)) + 0.50572 * (h + 6.07995).pow(-1.6364)))
    }

    /** ② 소광 (등) — 천정 대비 */
    fun extinction(altDeg: Double): Double = if (on(Fx.EXTINCTION)) K_V * (airmass(altDeg) - 1.0) else 0.0

    /** 대기 굴절 (도, 기하 고도 → 겉보기 고도에 더할 값, Saemundsson·1010hPa·10°C) */
    fun refraction(altDeg: Double): Double {
        if (!on(Fx.REFRACTION) || altDeg < -1.5) return 0.0
        val h = altDeg.coerceAtLeast(-0.9)
        return 1.02 / tan(Math.toRadians(h + 10.3 / (h + 5.11))) / 60.0
    }

    /** 그려진(굴절 후) 방향 → 굴절 전 방향 (Bennett, 겉보기 고도 기준) — 좌클릭 찾기 */
    fun unrefractWorld(w: Vec3): Vec3 {
        val d = w.normalized()
        if (!on(Fx.REFRACTION)) return d
        val h = altitude(d)
        if (h < -1.0) return d
        val r = 1.0 / tan(Math.toRadians(h + 7.31 / (h + 4.4))) / 60.0
        val t = Vec3(0.0, 1.0, 0.0) - d * d.y
        if (t.length() < 1e-6) return d
        val a = Math.toRadians(r)
        return (d * cos(a) - t.normalized() * kotlin.math.sin(a)).normalized()
    }

    /** 월드 방향을 굴절만큼 천정 쪽으로 (그리는 위치) */
    fun refractWorld(w: Vec3): Vec3 {
        val d = w.normalized()
        val r = refraction(altitude(d))
        if (r == 0.0) return d
        val up = Vec3(0.0, 1.0, 0.0)
        val t = up - d * d.y
        if (t.length() < 1e-6) return d
        val tn = t.normalized()
        val a = Math.toRadians(r)
        return d * cos(a) + tn * kotlin.math.sin(a)
    }

    // ── ③ 하늘 밝기 ──
    /** 박명·낮 천정 밝기 (등/초각², 해 고도별 어림 표) */
    private val TW_ALT = doubleArrayOf(-24.0, -18.0, -15.0, -12.0, -9.0, -6.0, -3.0, 0.0, 5.0, 10.0, 20.0, 40.0, 90.0)
    private val TW_MAG = doubleArrayOf(30.0, 24.0, 21.0, 18.2, 15.2, 12.2, 9.5, 7.3, 5.5, 4.6, 3.9, 3.4, 3.0)
    private fun twilightMag(sunAlt: Double): Double {
        if (sunAlt <= TW_ALT[0]) return TW_MAG[0]
        for (i in 1 until TW_ALT.size) if (sunAlt <= TW_ALT[i]) {
            val t = (sunAlt - TW_ALT[i - 1]) / (TW_ALT[i] - TW_ALT[i - 1])
            return TW_MAG[i - 1] + (TW_MAG[i] - TW_MAG[i - 1]) * t
        }
        return TW_MAG.last()
    }
    /** 박명·낮 하늘 (nL) — 박명을 끄면 해가 지평선 아래일 때 0. 일식이면 그만큼 어두움 */
    fun twilightNl(): Double {
        val s = CheonsangSky.sunAlt
        if (s < 0 && !on(Fx.TWILIGHT)) return 0.0
        return nl(twilightMag(s)) * (1.0 - 0.995 * CheonsangSky.eclipseDim)
    }

    /** 자연 하늘 (대기광 포함) — 지평선 쪽 밝아짐 (van Rhijn). 끄면 천정 값 그대로 */
    fun naturalNl(altDeg: Double): Double {
        if (!on(Fx.AIRGLOW)) return DARK_NL
        val z = Math.toRadians(90.0 - altDeg.coerceIn(0.0, 90.0))
        val vr = 1.0 / sqrt(1.0 - 0.96 * kotlin.math.sin(z).pow(2))
        // 지평선 가까이는 소광으로 다시 어두워짐
        return DARK_NL * vr * 10.0.pow(-0.4 * K_V * (airmass(altDeg) - 1.0) * 0.5)
    }

    /** 달빛 하늘 (nL, Krisciunas & Schaefer 1991) — 위상·달 고도·달과의 각거리. 월식 본영이면 거의 없음 */
    fun moonNl(dir: Vec3): Double {
        if (!on(Fx.MOON)) return 0.0
        val moon = CheonsangSky.moonWorld.normalized()
        val mAlt = altitude(moon)
        if (mAlt < -1.0) return 0.0
        val illum = CheonsangSky.moonIllum.coerceIn(0.0, 1.0)
        if (illum < 0.005) return 0.0
        val alpha = Math.toDegrees(kotlin.math.acos((2 * illum - 1).coerceIn(-1.0, 1.0)))  // 위상각
        val iStar = 10.0.pow(-0.4 * (3.84 + 0.026 * alpha + 4e-9 * alpha.pow(4)))
        val rho = dir.normalized().angleTo(moon).coerceAtLeast(0.5)
        val f = 10.0.pow(5.36) * (1.06 + cos(Math.toRadians(rho)).pow(2)) + 10.0.pow(6.15 - rho / 40.0)
        val alt = altitude(dir).coerceAtLeast(0.0)
        val xz = 1.0 / sqrt(1.0 - 0.96 * kotlin.math.sin(Math.toRadians(90.0 - alt)).pow(2))
        val xm = 1.0 / sqrt(1.0 - 0.96 * kotlin.math.sin(Math.toRadians(90.0 - mAlt.coerceAtLeast(0.0))).pow(2))
        val b = f * iStar * 10.0.pow(-0.4 * K_V * xm) * (1.0 - 10.0.pow(-0.4 * K_V * xz))
        return b * (1.0 - 0.98 * CheonsangSky.lunarUmbra) * (mAlt + 1.0).coerceIn(0.0, 1.0)
    }

    /** 빛공해 (nL) — 아직 중립 0 */
    @JvmStatic var lightPollutionNl: (Vec3) -> Double = { 0.0 }
    /** 황도광·대일조 (nL) — 아직 중립 0 */
    @JvmStatic var zodiacalNl: (Vec3) -> Double = { 0.0 }
    /** 암적응 손해 (등) — 아직 중립 0 */
    @JvmStatic var darkAdaptLoss: () -> Double = { 0.0 }

    /** 하늘 밝기는 천정 한 곳으로 고정 (고개 방향과 무관) */
    private val ZENITH = Vec3(0.0, 1.0, 0.0)

    /** ③ 하늘 밝기 (nL) — 방향 인자는 받지만 천정 값 (방향별 차이는 나중에 별마다, 확인 필요) */
    fun skyNl(@Suppress("UNUSED_PARAMETER") dir0: Vec3): Double {
        val dir = ZENITH
        val alt = altitude(dir)
        return naturalNl(alt) + twilightNl() + moonNl(dir) +
            (if (on(Fx.LIGHTPOLLUTION)) lightPollutionNl(dir) else 0.0) + (if (on(Fx.ZODIACAL)) zodiacalNl(dir) else 0.0)
    }

    /**
     * 하늘이 밝아 깎이는 한계 등급 (등) — 어두운 하늘 = 0. 점광원 문턱 ∝ B^0.6 (밝은 하늘에서 Weber 영역보다 완만)
     * 보름 밤 약 1.7 · 박명 −6° 약 5.4 · 낮 약 10.7
     */
    fun skyLoss(dir: Vec3): Double = max(0.0, 1.5 * log10((1.0 + skyNl(dir) / DARK_NL) / 2.0))

    /** 면광원이 하늘에 묻히는 정도 0~1 (어두운 하늘 = 1, 보름 하늘 약 0.15) — 은하수·빛무리 진하기 */
    fun skyWash(dir: Vec3): Double = (DARK_NL / skyNl(dir)).pow(0.6).coerceIn(0.0, 1.0)

    // ── ⑤ 장비 ──
    fun zoomNow(): Double = if (TelescopeView.active) TelescopeView.opticalZoom else 1.0
    /** 점광원 이득 — 구경 항 (Optics.gain) */
    fun gain(zoom: Double = zoomNow()): Double = Optics.gain(zoom)
    /** 면광원 밝기 비율 = (출사동 / 눈동자)², 최대 1 */
    fun pupilRatio(zoom: Double): Double = if (zoom <= 1.0) 1.0 else min(1.0, (Optics.apertureMm / zoom / EYE_PUPIL_MM).pow(2))

    /** ⑥ 점광원 한계 등급 (그 방향) — 소광은 여기 안 들어감 (천체 쪽 [effMag]) */
    fun pointLimit(dir: Vec3, zoom: Double = zoomNow()): Double =
        min(CheonsangSky.nakedLimit + gain(zoom), TelescopeView.MAX_LIMIT.toDouble()) - skyLoss(dir) - (if (on(Fx.DARKADAPT)) darkAdaptLoss() else 0.0)

    /** 하늘 손해만 뺀 그리기용 기준 한계 (별 셰이더: 소광은 정점마다) */
    fun drawLimit(center: Vec3): Float = pointLimit(center).toFloat()

    fun effMag(mag: Double, dir: Vec3): Double = mag + extinction(altitude(dir))

    /**
     * 면광원 표시 진하기 0~1.
     * 표면 밝기 S 를 출사동 비율로 어둡게 → 겉보기 크기에 따른 문턱 S_thr(a) = 26.5 − 2.5·log10(1 + (15′/a)²) 과 비교.
     * 적분 등급이 너무 어두우면(5.5 + 2.5·log 배율 + 0.5 넘음) 안 보임. 보이면 최소 0.08 (고배율에서 0 안 됨)
     */
    fun diffuseIntensity(mag: Double, surface: Double, majorArcmin: Double, zoom: Double): Double {
        val byMag = diffuseGate(mag, zoom)
        if (byMag <= 0.0) return 0.0
        val sEff = surface - 2.5 * log10(pupilRatio(zoom))
        val bySurface = ((surfaceThreshold(majorArcmin, zoom) - sEff) / 5.0).coerceIn(0.0, 1.0)
        return max(bySurface, 0.08) * byMag
    }

    /** 적분 등급 문턱 0~1 — 5.5 + 2.5·log 배율 (+0.5) 보다 어두우면 0 (은하 스케치와 같이 씀) */
    fun diffuseGate(mag: Double, zoom: Double): Double {
        val gate = min(5.5 + Optics.gain(zoom), TelescopeView.MAX_LIMIT.toDouble())
        return ((gate + 0.5 - mag) / 1.5).coerceIn(0.0, 1.0)
    }

    /** 겉보기 크기에 따른 면광원 문턱 표면 밝기 S_thr(a) = 26.5 − 2.5·log10(1 + (15′/a)²) */
    fun surfaceThreshold(majorArcmin: Double, zoom: Double): Double {
        val a = majorArcmin * zoom.coerceAtLeast(1.0)
        return 26.5 - 2.5 * log10(1.0 + (15.0 / a).pow(2))
    }

    /** 면광원 겉보기 표면 밝기 (등/초각²) — 색 빠짐 판단 */
    fun surfaceEff(surface: Double, zoom: Double) = surface - 2.5 * log10(pupilRatio(zoom))

    /**
     * 은하수(넓은 면광원) 배율 감쇄 — 출사동 비율의 0.33제곱(Stevens 밝기 지각) × (1 − 별로 풀린 몫), 최소 0.10 (0 아님).
     * 별로 풀린 몫 = 한계 등급 6 → 0, 11 → 0.35
     */
    fun milkyFactor(zoom: Double): Double {
        val lim = min(CheonsangSky.nakedLimit + gain(zoom), TelescopeView.MAX_LIMIT.toDouble())
        val resolved = ((lim - 6.0) / 5.0).coerceIn(0.0, 1.0) * 0.35
        return max(pupilRatio(zoom).pow(0.33) * (1.0 - resolved), 0.10)
    }

    /** 색 빠짐: 어두운 면광원은 회색 (표면 밝기 20 → 23 등/초각² 사이에서 0 → 1) */
    fun colorLoss(surfaceEff: Double): Double = if (on(Fx.COLORLOSS)) ((surfaceEff - 20.0) / 3.0).coerceIn(0.0, 1.0) else 0.0

    /** `/cvis` — 효과 목록 */
    fun state(): String = Fx.entries.joinToString(" | ") { L.t("cheonsang.visibility.1", it.label, it.id, if (on(it)) L.t("cheonsang.visibility.4") else L.t("cheonsang.visibility.3")) } +
        L.t("cheonsang.visibility.2").format(
            AmbientLight.bortle, AmbientLight.adapt * 100, magOf(skyNl(CheonsangSky.viewCenter())), pointLimit(CheonsangSky.viewCenter()))
}
