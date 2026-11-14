package kr.inf.cheonsang.client.sky

import kr.inf.cheonsang.Cheonsang
import java.io.DataInputStream
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * stars.bin 읽기 (_tools/build_data.py 가 만든 형식, 빅엔디언).
 * 머리: 별 개수(int) / 별마다: RA(float, 라디안) · Dec(float, 라디안) · 등급×100(short) · B-V×1000(short) · HIP(int)
 * 밝은 별부터 정렬돼 있다. star_meta.bin (같은 순서) = 별자리 번호 · 그리스 문자 · 위첨자 · 플램스티드 번호.
 */
class StarCatalog(
    /** J2000 적도 단위벡터 x,y,z 를 3개씩 */
    val dir: FloatArray,
    val mag: FloatArray,
    val bv: FloatArray,
    val hip: IntArray,
    /** SkyData.constellations 번호, -1 = 없음 */
    val con: IntArray,
    /** 1=α ~ 24=ω, 0 = 없음 */
    val greek: ByteArray,
    /** 그리스 문자 위첨자 (ο¹ 의 1), 0 = 없음 */
    val sup: ByteArray,
    /** 플램스티드 번호, 0 = 없음 */
    val flam: ShortArray,
    /** 실제 목록(HYG) 별 수 — 그 뒤는 배경용 어두운 별 (이름·고르기 대상 아님) */
    val realCount: Int = mag.size,
) {
    val size get() = mag.size

    /** 별자리 기준 기호: "오리온자리 α" · "오리온자리 58" · null (기호 없음) */
    fun designation(i: Int, conName: String): String? {
        val g = greek[i].toInt()
        if (g in 1..24) {
            val s = sup[i].toInt()
            return "$conName ${GREEK[g - 1]}" + if (s > 0) s.toString().map { SUPERSCRIPT[it - '0'] }.joinToString("") else ""
        }
        return if (flam[i] > 0) "$conName ${flam[i]}" else null
    }

    /** 등급이 limit 이하인 별 수 (밝은 순 정렬이라 앞에서부터) */
    fun countBrighterThan(limit: Float): Int {
        var lo = 0; var hi = size
        while (lo < hi) { val m = (lo + hi) ushr 1; if (mag[m] <= limit) lo = m + 1 else hi = m }
        return lo
    }

    companion object {
        private const val GREEK = "αβγδεζηθικλμνξοπρστυφχψω"
        private const val SUPERSCRIPT = "⁰¹²³⁴⁵⁶⁷⁸⁹"

        private fun fixMags(dir: FloatArray, mag: FloatArray, bv: FloatArray, hip: IntArray, con: IntArray, greek: ByteArray, sup: ByteArray, flam: ShortArray) {
            val fixes = try { SkyData.famous.filter { it.fixMag != null } } catch (_: Exception) { emptyList() }
            if (fixes.isEmpty()) return
            val n = mag.size
            var changed = false
            for (f in fixes) {
                val lim = kotlin.math.cos(Math.toRadians(1.0 / 60.0))
                for (i in 0 until n) {
                    if (dir[i * 3] * f.dirJ.x + dir[i * 3 + 1] * f.dirJ.y + dir[i * 3 + 2] * f.dirJ.z > lim) {
                        Cheonsang.LOG.info("Cheonsang: star {} magnitude {} -> {}", f.en, mag[i], f.fixMag)
                        mag[i] = f.fixMag!!.toFloat(); changed = true
                        // 위치도 바로잡음 (용골자리 에타는 0.7′ 어긋나 있었음)
                        dir[i * 3] = f.dirJ.x.toFloat(); dir[i * 3 + 1] = f.dirJ.y.toFloat(); dir[i * 3 + 2] = f.dirJ.z.toFloat()
                        break
                    }
                }
            }
            if (!changed) return
            val order = (0 until n).sortedBy { mag[it] }
            fun <T> perm(get: (Int) -> T, set: (Int, T) -> Unit) { val tmp = order.map(get); for (i in 0 until n) set(i, tmp[i]) }
            val d = order.map { Triple(dir[it * 3], dir[it * 3 + 1], dir[it * 3 + 2]) }
            for (i in 0 until n) { dir[i * 3] = d[i].first; dir[i * 3 + 1] = d[i].second; dir[i * 3 + 2] = d[i].third }
            perm({ mag[it] }, { i, v -> mag[i] = v }); perm({ bv[it] }, { i, v -> bv[i] = v }); perm({ hip[it] }, { i, v -> hip[i] = v })
            perm({ con[it] }, { i, v -> con[i] = v }); perm({ greek[it] }, { i, v -> greek[i] = v }); perm({ sup[it] }, { i, v -> sup[i] = v })
            perm({ flam[it] }, { i, v -> flam[i] = v })
        }

        fun load(): StarCatalog {
            val path = "/assets/cheonsang/sky/stars.bin"
            val input = StarCatalog::class.java.getResourceAsStream(path) ?: error("$path missing")
            DataInputStream(input.buffered()).use { din ->
                val n = din.readInt()
                val dir = FloatArray(n * 3); val mag = FloatArray(n); val bv = FloatArray(n); val hip = IntArray(n)
                for (i in 0 until n) {
                    val ra = din.readFloat().toDouble(); val dec = din.readFloat().toDouble()
                    dir[i * 3] = (cos(dec) * cos(ra)).toFloat()
                    dir[i * 3 + 1] = (cos(dec) * sin(ra)).toFloat()
                    dir[i * 3 + 2] = sin(dec).toFloat()
                    mag[i] = din.readShort() / 100f
                    bv[i] = din.readShort() / 1000f
                    hip[i] = din.readInt()
                }
                Cheonsang.LOG.info("Cheonsang: loaded {} stars", n)
                val con = IntArray(n) { -1 }; val greek = ByteArray(n); val sup = ByteArray(n); val flam = ShortArray(n)
                StarCatalog::class.java.getResourceAsStream("/assets/cheonsang/sky/star_meta.bin")?.let { meta ->
                    DataInputStream(meta.buffered()).use { m ->
                        if (m.readInt() == n) for (i in 0 until n) {
                            val c = m.readUnsignedByte()
                            con[i] = if (c == 255) -1 else c
                            greek[i] = m.readByte(); sup[i] = m.readByte(); flam[i] = m.readShort()
                        }
                    }
                }
                // famous_stars.json 의 fixMag 로 등급이 부정확한 별을 고친 뒤 밝은 순으로 다시 정렬
                fixMags(dir, mag, bv, hip, con, greek, sup, flam)
                // 배경용 어두운 별(9~11등)은 밝은 순 정렬 뒤에 이어 붙임. 깊은 별 목록(Tycho-2, DeepStars)이 있으면 등급대가 겹쳐 안 씀
                if (StarCatalog::class.java.getResource("/assets/cheonsang/sky/deep_stars.bin") != null) return StarCatalog(dir, mag, bv, hip, con, greek, sup, flam)
                val faint = StarCatalog::class.java.getResourceAsStream("/assets/cheonsang/sky/faint_stars.bin")
                    ?: return StarCatalog(dir, mag, bv, hip, con, greek, sup, flam)
                DataInputStream(faint.buffered()).use { f ->
                    val k = f.readInt()
                    val t = n + k
                    val dir2 = dir.copyOf(t * 3); val mag2 = mag.copyOf(t); val bv2 = bv.copyOf(t); val hip2 = hip.copyOf(t)
                    for (i in n until t) {
                        val ra = f.readFloat().toDouble(); val dec = f.readFloat().toDouble()
                        dir2[i * 3] = (cos(dec) * cos(ra)).toFloat()
                        dir2[i * 3 + 1] = (cos(dec) * sin(ra)).toFloat()
                        dir2[i * 3 + 2] = sin(dec).toFloat()
                        mag2[i] = f.readShort() / 100f
                        bv2[i] = f.readShort() / 1000f
                        hip2[i] = f.readInt()
                    }
                    Cheonsang.LOG.info("Cheonsang: loaded {} background stars", k)
                    val con2 = con.copyOf(t).also { it.fill(-1, n, t) }
                    return StarCatalog(dir2, mag2, bv2, hip2, con2, greek.copyOf(t), sup.copyOf(t), flam.copyOf(t), n)
                }
            }
        }

        /**
         * 색지수 B-V → RGB (0~255). Ballesteros 식으로 온도를 구하고 흑체색 근사.
         * 실제 별색은 옅어서 흰색을 조금 섞는다 (22%).
         */
        fun color(bv: Float): Int {
            val b = bv.coerceIn(-0.4f, 2.0f).toDouble()
            val t = 4600.0 * (1 / (0.92 * b + 1.7) + 1 / (0.92 * b + 0.62)) / 100.0
            val r = if (t <= 66) 255.0 else 329.698727446 * (t - 60).pow(-0.1332047592)
            val g = if (t <= 66) 99.4708025861 * kotlin.math.ln(t) - 161.1195681661
                    else 288.1221695283 * (t - 60).pow(-0.0755148492)
            val bl = if (t >= 66) 255.0 else if (t <= 19) 0.0 else 138.5177312231 * kotlin.math.ln(t - 10) - 305.0447927307
            fun mix(c: Double) = (c.coerceIn(0.0, 255.0) * 0.78 + 255.0 * 0.22).toInt()
            return (mix(r) shl 16) or (mix(g) shl 8) or mix(bl)
        }
    }
}
