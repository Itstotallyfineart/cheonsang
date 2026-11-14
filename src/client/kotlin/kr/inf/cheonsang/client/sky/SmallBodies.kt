package kr.inf.cheonsang.client.sky

import kr.inf.cheonsang.L

import kr.inf.cheonsang.Cheonsang
import kr.inf.cheonsang.astro.Mat3
import kr.inf.cheonsang.astro.Planets
import kr.inf.cheonsang.astro.Vec3
import kr.inf.cheonsang.astro.deltaT
import kr.inf.cheonsang.astro.ttCenturies
import java.io.DataInputStream
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.tan

/** 소행성 위치 (지구에서 본 모습, J2000 적도) */
class AsteroidPos(val body: Asteroids.Body, val dir: Vec3, val distAu: Double, val sunDistAu: Double, val mag: Double, val phaseAngle: Double)

/**
 * 소행성 세레스·베스타 — JPL Horizons 해 중심 위치 표 (1900~2050, 4일 간격, sky/asteroids.bin) 를 4점 라그랑주 보간.
 * 지구는 행성 계산과 같은 JPL 근사 궤도요소. 등급 = H·G 체계 (IAU 1985). 표 밖 연도는 그리지 않음
 */
object Asteroids {
    /** H·G = JPL 소천체 자료 (세레스 3.34·0.12, 베스타 3.20·0.32) */
    class Body(val ko: String, val en: String, val num: Int, val h: Double, val g: Double, val jd0: Double, val step: Double, val xyz: FloatArray) {
        val count get() = xyz.size / 3
    }

    val bodies: List<Body> by lazy {
        val s = Asteroids::class.java.getResourceAsStream("/assets/cheonsang/sky/asteroids.bin") ?: return@lazy emptyList()
        val meta = listOf(Triple("세레스", "Ceres", 1) to (3.34 to 0.12), Triple("베스타", "Vesta", 4) to (3.20 to 0.32))
        DataInputStream(s.buffered()).use { d ->
            val n = d.readInt()
            List(n) { k ->
                val jd0 = d.readDouble(); val step = d.readDouble(); val c = d.readInt()
                val a = FloatArray(c * 3) { d.readFloat() }
                val (names, hg) = meta[k]
                Body(names.first, names.second, names.third, hg.first, hg.second, jd0, step, a)
            }.also { Cheonsang.LOG.info("Cheonsang: position tables for {} asteroids", it.size) }
        }
    }

    /** 해 중심 J2000 황도 (AU), jdTt 가 표 밖이면 null */
    private fun helio(b: Body, jdTt: Double): Vec3? {
        val x = (jdTt - b.jd0) / b.step
        val i0 = floor(x).toInt() - 1
        if (i0 < 0 || i0 + 3 >= b.count) return null
        val t = x - (i0 + 1)
        // 4점 라그랑주 (점 i0..i0+3, t 는 i0+1 기준 0~1)
        val w = doubleArrayOf(-t * (t - 1) * (t - 2) / 6, (t + 1) * (t - 1) * (t - 2) / 2, -(t + 1) * t * (t - 2) / 2, (t + 1) * t * (t - 1) / 6)
        var px = 0.0; var py = 0.0; var pz = 0.0
        for (k in 0 until 4) {
            val j = (i0 + k) * 3
            px += w[k] * b.xyz[j]; py += w[k] * b.xyz[j + 1]; pz += w[k] * b.xyz[j + 2]
        }
        return Vec3(px, py, pz)
    }

    private val ECL_TO_EQ = Mat3.rotX(23.43928)

    fun position(b: Body, jd: Double): AsteroidPos? {
        val jdTt = jd + deltaT(jd) / 86400.0
        val earth = Planets.heliocentric(2, ttCenturies(jd))
        var helio = helio(b, jdTt) ?: return null
        var geo = helio - earth
        repeat(2) {
            helio = helio(b, jdTt - geo.length() * 0.0057755) ?: return null
            geo = helio - earth
        }
        val delta = geo.length(); val r = helio.length(); val big = earth.length()
        val cosA = ((r * r + delta * delta - big * big) / (2 * r * delta)).coerceIn(-1.0, 1.0)
        val alpha = Math.acos(cosA)
        val th = tan(alpha / 2)
        val p1 = exp(-3.33 * th.pow(0.63)); val p2 = exp(-1.87 * th.pow(1.22))
        val mag = b.h + 5 * log10(r * delta) - 2.5 * log10((1 - b.g) * p1 + b.g * p2)
        return AsteroidPos(b, (ECL_TO_EQ * geo).normalized(), delta, r, mag, Math.toDegrees(alpha))
    }

    fun all(jd: Double): List<AsteroidPos> = bodies.mapNotNull { position(it, jd) }
}

/**
 * 변광성 알골·미라 — 밝기를 시각에 따라 계산 (GCVS 요소, VizieR B/gcvs 에서 확인).
 * 별 정점 버퍼에서는 빼고 따로 그림 (CheonsangSky)
 */
object VariableStars {
    class Star(val hip: Int, val ko: String, private val kindKo: String, private val rangeKo: String, val period: Double, val mag: (Double) -> Double) {
        /** 화면 이름 (ko 는 한국어 검색어) */
        val label: String get() = L.t("cheonsang.var.$hip.name")
        val kind: String get() = L.t("cheonsang.var.$hip.kind")
        val range: String get() = L.t("cheonsang.var.$hip.range")
    }

    /**
     * 알골 (β Per, EA): 극소 HJD 2445641.5135 + 2.8673043·E, 2.12~3.39등. 식 지속 약 10시간 — 주극소 깊이 1.27등, 부극소 0.06등.
     * 미라 (ο Cet, M): 극대 JD 2444839 + 331.96·E. GCVS 범위 2.0~10.1등 → 평균적인 주기 3.4~9.3등, 밝아지는 데 주기의 38% (GCVS M−m)
     */
    val list = listOf(
        Star(14576, "알골", "식쌍성", "2.1~3.4등", 2.8673043) { jd ->
            val ph = ((jd - 2445641.5135) / 2.8673043).let { it - floor(it) }
            fun dip(dt: Double, depth: Double) = depth * (1 - (dt / 0.21).pow(2)).coerceAtLeast(0.0).pow(1.5)
            val d1 = (if (ph > 0.5) ph - 1 else ph) * 2.8673043
            val d2 = (ph - 0.5) * 2.8673043
            2.12 + dip(d1, 1.27) + dip(d2, 0.06)
        },
        Star(10826, "미라", "장주기 변광성", "약 3.4~9.3등 (최대 2.0~10.1)", 331.96) { jd ->
            val ph = ((jd - 2444839.0) / 331.96).let { it - floor(it) }
            val max = 3.4; val min = 9.3; val rise = 0.38
            if (ph < 1 - rise) max + (min - max) * (1 - cos(Math.PI * ph / (1 - rise))) / 2
            else min - (min - max) * (1 - cos(Math.PI * (ph - (1 - rise)) / rise)) / 2
        },
    )

    val byHip: Map<Int, Star> by lazy { list.associateBy { it.hip } }
}
