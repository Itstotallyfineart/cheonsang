package kr.inf.cheonsang.client.sky

import kr.inf.cheonsang.client.Optics
import kr.inf.cheonsang.client.TelescopeView
import kr.inf.cheonsang.client.Visibility
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * 은하 관측 스케치 (shaders/core/galaxy.fsh): 사진(DSS2)이나 형태(Hubble)별 표면 밝기 모양과 보이는 밝기 문턱을 정함.
 * 형태 번호·무늬 씨앗을 정하고 셰이더와 같은 모양 식을 적분해 가장 밝은 곳의 표면 밝기를 구한 뒤, 배율·구경·겉보기 크기로 정한 문턱을 정점색에 담는다.
 */
object GalaxySketch {
    const val EXT = 1.3

    /** 형태 번호: 0 타원 · 1 렌즈형 · 2 Sa · 3 Sb · 4 Sc · 5 Sd/Sm · 6 불규칙, +8 = 막대 */
    fun code(hubble: String?): Int {
        val h = (hubble ?: "").replace(Regex("""^\(R'?\)\s*"""), "").trim().substringBefore(' ').substringBefore('/')
        if (h.isEmpty() || h.startsWith("S?")) return 3
        if (h.startsWith("cD") || h.startsWith("cE") || (h.startsWith("E") && !h.startsWith("E-S0"))) return 0
        if (h.startsWith("I")) return 6 + if (h.startsWith("IB") || h.startsWith("IAB")) 8 else 0
        if (h.startsWith("dSp")) return 4
        val barred = h.startsWith("SB") || h.startsWith("SAB")
        val stage = h.removePrefix("S").removePrefix("AB").removePrefix("A").removePrefix("B").replace(Regex("""\([^)]*\)"""), "")
        val t = when {
            h.startsWith("E-S0") || stage.startsWith("0") -> 1
            stage.startsWith("ab") || stage.startsWith("a") -> 2
            stage.startsWith("bc") || stage.startsWith("b") -> 3
            stage.startsWith("cd") || stage.startsWith("c") -> 4
            stage.startsWith("d") || stage.startsWith("m") -> 5
            else -> 3
        }
        return t + if (barred && t >= 2) 8 else 0
    }

    fun seed(id: String): Int = (id.hashCode() ushr 3) and 15

    /** galaxy.fsh 와 같은 모양 식 (잡음은 평균값: 팔 덩어리 1.0 · 불규칙 0.95) — 단위 D25 긴 반지름 */
    fun profile(x: Double, y: Double, q: Double, code: Int, seed: Int): Double {
        val type = code % 8
        val xd = x; val yd = y / q
        val rd = sqrt(xd * xd + yd * yd)
        if (type == 0) return exp(-3.67 * sqrt(rd / 0.22)) * (1.0 - smooth(0.9, 1.25, rd))
        val fb = doubleArrayOf(0.0, 1.0, 1.0, 1.0, 1.0, 0.6, 0.4)[type]
        val fd = doubleArrayOf(0.0, 0.25, 0.15, 0.2, 0.3, 0.5, 1.0)[type]
        val re = doubleArrayOf(0.0, 0.10, 0.08, 0.07, 0.06, 0.05, 0.12)[type]
        val armA = doubleArrayOf(0.0, 0.0, 0.2, 0.35, 0.45, 0.35, 0.0)[type]
        val pitch = doubleArrayOf(10.0, 10.0, 10.0, 15.0, 22.0, 28.0, 28.0)[type]
        val h = if (type < 6) 0.22 else 0.3
        val qb = max(q, 0.6)
        val rb = sqrt(x * x + (y / qb) * (y / qb))
        val bul = exp(-3.67 * sqrt(rb / re))
        var id = exp(-rd / h)
        if (armA > 0) {
            val s = 2.0 * (atan2(yd, xd) - ln(rd + 0.03) / tan(Math.toRadians(pitch))) + seed * 0.3927
            val c = 0.5 + 0.5 * cos(s)
            val arm = 1.0 + armA * (3.2 * c * c * c - 1.0)
            id *= 1.0 + (arm - 1.0) * smooth(0.05, 0.2, rd)
        }
        if (type == 6) id *= 0.95
        if (code >= 8) {
            val ang = seed * 0.7
            val xb = xd * cos(ang) + yd * sin(ang)
            val yb = -xd * sin(ang) + yd * cos(ang)
            id += 1.5 * exp(-(xb / 0.22).pow(2) - (yb / 0.06).pow(2))
        }
        var i = fb * bul + fd * id * (1.0 - smooth(0.9, 1.25, rd))
        if (type in 2..5 && q < 0.35) {
            val w = 0.03 + 0.1 * q
            i *= 1.0 - 0.75 * exp(-((y - 0.02) / w).pow(2)) * (1.0 - smooth(0.7, 1.0, kotlin.math.abs(x)))
        }
        return i
    }

    private fun smooth(a: Double, b: Double, x: Double): Double { val t = ((x - a) / (b - a)).coerceIn(0.0, 1.0); return t * t * (3 - 2 * t) }

    /** tile = 사진 판 칸 번호 (−1 = 사진 없음 → 형태 그림) */
    class Shape(val q: Double, val code: Int, val seed: Int, val peakSurface: Double, val tile: Int)

    private val cache = HashMap<String, Shape>()

    /** 모양 + 가장 밝은 곳 표면 밝기 (등/초각²) — 적분 등급이 이 모양 전체에 퍼졌다고 보고 */
    fun shape(o: Dso): Shape = cache.getOrPut(o.id) {
        val q = (if (o.minor > 0) o.minor / o.major else 1.0).coerceIn(0.05, 1.0).let { if (o.type != "galaxy") maxOf(it, 0.8) else it }   // 성운·성단 칸은 거의 둥글게 (make_galaxy_atlas.py 와 같게)
        // 구상성단 12 · 성운 13 · 행성상성운 14 (셰이더가 밝은 곳 색만 다르게)
        val code = when (o.type) { "globular" -> 12; "nebula" -> 13; "planetary" -> 14; else -> code(SkyData.dsoMorph[o.id]) }
        val seed = seed(o.id)
        val qq = max(q, 0.15)
        val rArcsec = o.major / 2.0 * 60.0
        // DSS2 사진 판이 있으면 그 빛 분포로 (가장 밝은 곳 대비 빛 면적은 판 만들 때 계산)
        SkyData.galaxySketch[o.id]?.let { (tile, k) ->
            return@getOrPut Shape(q, code, seed, o.mag + 2.5 * (k + log10(rArcsec * rArcsec)), tile)
        }
        val n = 48
        var sum = 0.0; var peak = 1e-9
        for (i in 0 until n) for (j in 0 until n) {
            val u = -1.0 + (i + 0.5) * 2.0 / n; val v = -1.0 + (j + 0.5) * 2.0 / n
            val edge = 1.0 - smooth(0.85, 1.0, max(kotlin.math.abs(u), kotlin.math.abs(v)))
            val p = profile(u * EXT, v * EXT * qq, q, code, seed) * edge
            sum += p; peak = max(peak, p)
        }
        val cell = (2.0 * EXT / n) * (2.0 * EXT * qq / n)          // D25 반지름² 단위
        Shape(q, code, seed, o.mag + 2.5 * log10(sum * cell * rArcsec * rArcsec / peak), -1)
    }

    /**
     * 이 배율에서 정점색 (r 축비 · g 형태×16+씨앗 · b 진하기 · a 문턱) 0~255.
     * 문턱 = 10^(−0.4·(문턱 표면 밝기 − 가장 밝은 곳 겉보기 표면 밝기)) (Visibility.surfaceThreshold, 빛무리와 같은 식). 가장 밝은 곳도 문턱 밖이면 0.7로 둬 흐린 얼룩은 남김.
     */
    fun colors(o: Dso, zoom: Double, strength: Double): IntArray {
        val s = shape(o)
        val gate = Visibility.diffuseGate(o.mag, zoom)
        val sEff = Visibility.surfaceEff(s.peakSurface, zoom)
        val sThr = Visibility.surfaceThreshold(o.major, zoom)
        val thr = 10.0.pow(-0.4 * (sThr - sEff)).coerceIn(10.0.pow(-2.5), 0.7)
        val a = (-log10(thr) / 2.5).coerceIn(0.0, 1.0)
        return intArrayOf((s.q * 255).toInt().coerceIn(0, 255), s.code * 16 + s.seed, (gate * strength * 255).toInt().coerceIn(0, 255), (a * 255).toInt())
    }
}
