package kr.inf.cheonsang.astro

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

const val DEG = PI / 180.0
const val RAD = 180.0 / PI

/** J2000.0 율리우스일 */
const val J2000 = 2451545.0

/** 1 AU (km) */
const val AU_KM = 149597870.7

/** 각도를 [0, 360) 으로 */
fun norm360(a: Double): Double = a - 360.0 * floor(a / 360.0)

/** 각도를 [-180, 180) 으로 */
fun norm180(a: Double): Double = norm360(a + 180.0) - 180.0

fun sinD(a: Double) = sin(a * DEG)
fun cosD(a: Double) = cos(a * DEG)
fun atan2D(y: Double, x: Double) = atan2(y, x) * RAD
fun asinD(x: Double) = asin(x.coerceIn(-1.0, 1.0)) * RAD

/** J2000 기준 율리우스 세기 */
fun centuries(jd: Double) = (jd - J2000) / 36525.0

/** UT 율리우스일 → 역학시(TT) 기준 율리우스 세기. 해·달 궤도 계산용 */
fun ttCenturies(jdUt: Double) = centuries(jdUt + deltaT(jdUt) / 86400.0)

/**
 * ΔT = TT − UT (초). Espenak·Meeus(NASA) 다항식, 1900~2150 구간별.
 * 범위 밖은 장기 포물선. 2019년 약 69초 — 빼면 달이 약 35" 어긋남.
 */
fun deltaT(jdUt: Double): Double {
    val y = 2000.0 + (jdUt - J2000) / 365.25
    val u = (y - 1820) / 100
    return when {
        y < 1900 || y >= 2150 -> -20 + 32 * u * u
        y < 1920 -> { val t = y - 1900; -2.79 + t * (1.494119 + t * (-0.0598939 + t * (0.0061966 - t * 0.000197))) }
        y < 1941 -> { val t = y - 1920; 21.20 + t * (0.84493 + t * (-0.076100 + t * 0.0020936)) }
        y < 1961 -> { val t = y - 1950; 29.07 + 0.407 * t - t * t / 233 + t * t * t / 2547 }
        y < 1986 -> { val t = y - 1975; 45.45 + 1.067 * t - t * t / 260 - t * t * t / 718 }
        y < 2005 -> { val t = y - 2000; 63.86 + t * (0.3345 + t * (-0.060374 + t * (0.0017275 + t * (0.000651814 + t * 0.00002373599)))) }
        y < 2050 -> { val t = y - 2000; 62.92 + t * (0.32217 + t * 0.005589) }
        else -> -20 + 32 * u * u - 0.5628 * (2150 - y)
    }
}

data class Vec3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Double) = Vec3(x * s, y * s, z * s)
    infix fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    fun length() = sqrt(this dot this)
    fun normalized(): Vec3 = this * (1.0 / length())

    /** 두 단위벡터 사이 각(도) */
    fun angleTo(o: Vec3): Double = atan2D((this cross o).length(), this dot o)
    infix fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)

    companion object {
        /** 구면좌표(경도 lon, 위도 lat, 도) → 단위벡터. x=경도0, z=극 */
        fun fromSpherical(lonDeg: Double, latDeg: Double): Vec3 {
            val cl = cosD(latDeg)
            return Vec3(cl * cosD(lonDeg), cl * sinD(lonDeg), sinD(latDeg))
        }
    }
}

/** 벡터 → (경도 0~360, 위도) 도 */
fun Vec3.toSpherical(): Pair<Double, Double> =
    norm360(atan2D(y, x)) to asinD(z / length())

/** 3×3 행렬 (행 우선) */
class Mat3(val m: DoubleArray) {
    init { require(m.size == 9) }

    operator fun times(v: Vec3) = Vec3(
        m[0] * v.x + m[1] * v.y + m[2] * v.z,
        m[3] * v.x + m[4] * v.y + m[5] * v.z,
        m[6] * v.x + m[7] * v.y + m[8] * v.z,
    )

    operator fun times(o: Mat3): Mat3 {
        val r = DoubleArray(9)
        for (i in 0..2) for (j in 0..2) {
            r[i * 3 + j] = m[i * 3] * o.m[j] + m[i * 3 + 1] * o.m[3 + j] + m[i * 3 + 2] * o.m[6 + j]
        }
        return Mat3(r)
    }

    companion object {
        val IDENTITY = Mat3(doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0))

        /** z축 회전 (능동, 도) */
        fun rotZ(deg: Double): Mat3 {
            val c = cosD(deg); val s = sinD(deg)
            return Mat3(doubleArrayOf(c, -s, 0.0, s, c, 0.0, 0.0, 0.0, 1.0))
        }

        /** x축 회전 (능동, 도) */
        fun rotX(deg: Double): Mat3 {
            val c = cosD(deg); val s = sinD(deg)
            return Mat3(doubleArrayOf(1.0, 0.0, 0.0, 0.0, c, -s, 0.0, s, c))
        }
    }
}
