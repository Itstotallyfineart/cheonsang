package kr.inf.cheonsang.client.sky

import kr.inf.cheonsang.L
import kr.inf.cheonsang.Cheonsang
import kr.inf.cheonsang.astro.Vec3
import java.io.DataInputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.InflaterInputStream
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * 망원경용 깊은 별 (9등 초과 ~ 13등) — deep_stars.bin (Tycho-2, Gaia 짝이 있으면 그 등급·색·위치 사용). 메모리를 아끼려 시야 근처 칸만 작은 버퍼로 그림.
 * Gaia 별 팩(`cheonsang_stars/cNNNNN.bin`, Gaia 15.2등까지)이 있으면 백그라운드 스레드 하나가 칸을 읽어 통째로 바꿔 그리고(렌더 스레드는 안 기다림), 캐시가 [CACHE_STARS] 를 넘으면 오래 안 쓴 칸부터 버림.
 */
object DeepStars {
    private const val PATH = "/assets/cheonsang/sky/deep_stars.bin"
    private const val PACK_DIR = "/cheonsang_stars/"
    const val MAX_STARS = 150_000
    /** 팩 칸 캐시 상한 (별 수, 별 하나 14바이트 → 약 42MB) */
    private const val CACHE_STARS = 3_000_000

    private var loaded = false
    private var ok = false
    private lateinit var nRa: IntArray
    private lateinit var bandStart: IntArray
    private lateinit var off: IntArray
    private lateinit var dir: FloatArray
    private lateinit var mag: ShortArray
    private lateinit var bv: ShortArray

    /** 고른 결과 (렌더 스레드에서 버퍼로) */
    val outDir = FloatArray(MAX_STARS * 3)
    val outMag = FloatArray(MAX_STARS)
    val outBv = FloatArray(MAX_STARS)
    /** 마지막 collect 로 out* 에 담긴 별 수 (좌클릭 카드가 이 목록에서 고름) */
    var lastCount = 0
        private set
    /** 마지막 collect 가 [MAX_STARS] 에서 잘렸는지 */
    var truncated = false
        private set

    fun available(): Boolean { load(); return ok }

    private fun load() {
        if (loaded) return
        loaded = true
        val input = DeepStars::class.java.getResourceAsStream(PATH) ?: run { Cheonsang.LOG.warn("Cheonsang: {} missing — stars to mag 9 only", PATH); return }
        try {
            DataInputStream(input.buffered(1 shl 16)).use { d ->
                val magic = ByteArray(4); d.readFully(magic)
                require(String(magic) == "DSB2") { L.t("cheonsang.deepstars.1") }
                val nb = d.readInt()
                nRa = IntArray(nb) { d.readInt() }
                bandStart = IntArray(nb + 1); for (b in 0 until nb) bandStart[b + 1] = bandStart[b] + nRa[b]
                val c = d.readInt()
                off = IntArray(c + 1) { d.readInt() }
                val n = d.readInt()
                dir = FloatArray(n * 3); mag = ShortArray(n); bv = ShortArray(n)
                for (i in 0 until n) {
                    val ra = d.readFloat().toDouble(); val de = d.readFloat().toDouble()
                    dir[i * 3] = (cos(de) * cos(ra)).toFloat(); dir[i * 3 + 1] = (cos(de) * sin(ra)).toFloat(); dir[i * 3 + 2] = sin(de).toFloat()
                    mag[i] = d.readShort(); bv[i] = d.readShort()
                }
                ok = true
                Cheonsang.LOG.info("Cheonsang: loaded {} deep stars · {} sky cells", n, c)
            }
        } catch (e: Exception) {
            Cheonsang.LOG.error("Cheonsang: failed to read deep stars — stars to mag 9 only", e)
        }
        loadPack()
    }

    // ── Gaia 별 팩 ──
    private class Cell(val dir: FloatArray, val mag: ShortArray, val bv: ShortArray) { @Volatile var used = 0L }
    /** 읽기 실패·없는 칸 → 본체 칸으로 */
    private val FAILED = Cell(FloatArray(0), ShortArray(0), ShortArray(0))
    private var pack = false
    /** 팩 칸별 은하수 그림 속 별빛 중 V 한계까지의 몫 (0~255) — 한계 [MW_V0] 부터 0.5등 간격 [MW_N] 개 */
    private var mwFrac: ByteArray? = null
    private const val MW_V0 = 11.5f
    private var MW_N = 0
    private val cells = ConcurrentHashMap<Int, Cell>()
    private val pending = ConcurrentHashMap.newKeySet<Int>()
    private val cachedStars = AtomicInteger()
    private var useTick = 0L
    /** 칸이 새로 읽힐 때마다 올라감 — 그리는 쪽이 보고 다시 고름 */
    @Volatile var version = 0
        private set
    private val loader = Executors.newSingleThreadExecutor { r -> Thread(r, "Cheonsang Gaia stars").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 } }

    /** Gaia 별 팩이 있고 본체와 칸 나눔이 같은지 */
    fun packAvailable(): Boolean { load(); return pack }

    private fun loadPack() {
        if (!ok) return
        val input = DeepStars::class.java.getResourceAsStream(PACK_DIR + "index.bin") ?: return
        try {
            DataInputStream(input.buffered()).use { d ->
                val magic = ByteArray(4); d.readFully(magic)
                require(String(magic) == "GSP1") { L.t("cheonsang.deepstars.1") }
                val c = d.readInt()
                require(c == off.size - 1) { L.t("cheonsang.deepstars.2", c) }
                MW_N = d.readInt()
                mwFrac = ByteArray(c * MW_N).also { d.readFully(it) }
            }
            pack = true
            Cheonsang.LOG.info("Cheonsang: Gaia star pack found ({} cells)", off.size - 1)
        } catch (e: Exception) {
            Cheonsang.LOG.error("Cheonsang: failed to read Gaia star pack index — base stars only", e)
        }
    }

    /** 팩 칸 하나 읽기 (백그라운드). 형식: 별 수 int · 별마다 칸 안 위치 u16·u16 · (V−6)×25 u8 · (B−V+0.5)×80 u8 (밝은 순) */
    private fun readCell(cell: Int) {
        try {
            val input = DeepStars::class.java.getResourceAsStream(PACK_DIR + "c%05d.bin".format(cell)) ?: run { cells[cell] = FAILED; return }
            val b = cellBand(cell); val col = cell - bandStart[b]
            val w = 360.0 / nRa[b]
            val ra0 = col * w; val de0 = -90.0 + b
            val c = DataInputStream(InflaterInputStream(input).buffered(1 shl 15)).use { d ->
                val n = d.readInt()
                val dr = FloatArray(n * 3); val mg = ShortArray(n); val bb = ShortArray(n)
                for (i in 0 until n) {
                    val ra = Math.toRadians(ra0 + d.readUnsignedShort() / 65535.0 * w)
                    val de = Math.toRadians(de0 + d.readUnsignedShort() / 65535.0)
                    dr[i * 3] = (cos(de) * cos(ra)).toFloat(); dr[i * 3 + 1] = (cos(de) * sin(ra)).toFloat(); dr[i * 3 + 2] = sin(de).toFloat()
                    mg[i] = (600 + d.readUnsignedByte() * 4).toShort()
                    bb[i] = (d.readUnsignedByte() * 12.5 - 500).toInt().toShort()
                }
                Cell(dr, mg, bb)
            }
            c.used = useTick
            cells[cell] = c
            cachedStars.addAndGet(c.mag.size)
            trim()
            version++
        } catch (e: Exception) {
            Cheonsang.LOG.warn("Cheonsang: failed to read Gaia star cell {} — using base cell", cell, e)
            cells[cell] = FAILED
        } finally {
            pending.remove(cell)
        }
    }

    /** 캐시가 넘치면 오래 안 쓴 칸부터 버림 (방금 쓴 칸은 남김) */
    private fun trim() {
        if (cachedStars.get() <= CACHE_STARS) return
        val old = cells.entries.filter { it.value.used < useTick - 2 }.sortedBy { it.value.used }
        for (e in old) {
            if (cachedStars.get() <= CACHE_STARS * 3 / 4) break
            if (cells.remove(e.key, e.value)) cachedStars.addAndGet(-e.value.mag.size)
        }
    }

    private fun cellBand(cell: Int): Int {
        var lo = 0; var hi = nRa.size - 1
        while (lo < hi) { val m = (lo + hi + 1) / 2; if (bandStart[m] <= cell) lo = m else hi = m - 1 }
        return lo
    }

    /** 지금 팩 칸이 아직 읽히는 중인지 (다 읽히면 그리는 쪽이 다시 고름) */
    fun loading() = pending.isNotEmpty()

    /**
     * 은하수 그림에서 남길 몫 — 팩이 있을 때만, 그 방향 칸의 "그림 속 별빛(Tycho 에 없는 Gaia 별) 중 한계 등급까지의 몫" 을 뺌.
     * 팩이 없으면 1 (본체 Tycho 별은 그림에 안 들어 있어 겹침 없음)
     */
    fun milkyKeep(center: Vec3, limit: Float): Float {
        val t = mwFrac ?: return 1f
        if (!pack || limit <= MW_V0) return 1f
        val cell = cellOf(center)
        val x = ((limit - MW_V0) / 0.5f).coerceIn(0f, (MW_N - 1).toFloat())
        val i = x.toInt(); val j = min(i + 1, MW_N - 1); val f = x - i
        val a = (t[cell * MW_N + i].toInt() and 255) / 255f; val b = (t[cell * MW_N + j].toInt() and 255) / 255f
        return 1f - (a + (b - a) * f)
    }

    private fun cellOf(v: Vec3): Int {
        val dec = Math.toDegrees(asin(v.z.coerceIn(-1.0, 1.0)))
        val ra = (Math.toDegrees(kotlin.math.atan2(v.y, v.x)) + 360.0) % 360.0
        val b = floor(dec + 90).toInt().coerceIn(0, nRa.size - 1)
        return bandStart[b] + min((ra / 360.0 * nRa[b]).toInt(), nRa[b] - 1)
    }

    /**
     * 중심 (J2000 단위벡터) 에서 radiusDeg 안, 등급 limit 이하 별을 out* 에 담고 개수를 돌려줌.
     * 팩이 있으면 가까운 칸부터 읽기 요청, 읽힌 칸은 팩 별, 아직이면 본체 별.
     */
    fun collect(center: Vec3, radiusDeg: Double, limit: Float): Int = collectInner(center, radiusDeg, limit).also { lastCount = it }

    private fun collectInner(center: Vec3, radiusDeg: Double, limit: Float): Int {
        if (!available()) return 0
        truncated = false
        useTick++
        val decC = Math.toDegrees(asin(center.z.coerceIn(-1.0, 1.0)))
        val raC = (Math.toDegrees(kotlin.math.atan2(center.y, center.x)) + 360.0) % 360.0
        val cosR = cos(Math.toRadians(radiusDeg))
        val lim100 = (limit * 100).toInt()
        val usePack = pack && limit > 9f
        var k = 0
        val b0 = max(0, floor(decC - radiusDeg + 90).toInt() - 1)
        val b1 = min(nRa.size - 1, floor(decC + radiusDeg + 90).toInt() + 1)
        for (b in b0..b1) {
            val n = nRa[b]
            val bandDec = -90.0 + b + 0.5
            val c = cos(Math.toRadians(max(abs(bandDec) - 0.5, 0.0)))
            val half = if (c < 1e-3) 180.0 else min(180.0, (radiusDeg + 1.5) / c)
            val w = 360.0 / n
            val cols = if (half >= 179.0) (0 until n) else {
                val lo = floor((raC - half) / w).toInt(); val hi = floor((raC + half) / w).toInt()
                (lo..hi)
            }
            var prev = -1
            for (cc in cols) {
                val col = ((cc % n) + n) % n
                if (col == prev) continue
                prev = col
                val cell = bandStart[b] + col
                val pc = if (usePack) cells[cell] else null
                if (usePack && pc == null && pending.add(cell)) loader.execute { readCell(cell) }
                if (pc != null && pc !== FAILED) {
                    pc.used = useTick
                    val d = pc.dir; val m = pc.mag; val bb = pc.bv
                    for (i in m.indices) {
                        if (m[i] > lim100) break
                        val x = d[i * 3]; val y = d[i * 3 + 1]; val z = d[i * 3 + 2]
                        if (x * center.x + y * center.y + z * center.z < cosR) continue
                        if (k >= MAX_STARS) { truncated = true; return k }
                        outDir[k * 3] = x; outDir[k * 3 + 1] = y; outDir[k * 3 + 2] = z
                        outMag[k] = m[i] / 100f; outBv[k] = bb[i] / 1000f; k++
                    }
                    continue
                }
                // 본체 별 (칸 안 밝은 순)
                for (i in off[cell] until off[cell + 1]) {
                    if (mag[i] > lim100) break
                    val x = dir[i * 3]; val y = dir[i * 3 + 1]; val z = dir[i * 3 + 2]
                    if (x * center.x + y * center.y + z * center.z < cosR) continue
                    if (k >= MAX_STARS) { truncated = true; return k }
                    outDir[k * 3] = x; outDir[k * 3 + 1] = y; outDir[k * 3 + 2] = z
                    outMag[k] = mag[i] / 100f; outBv[k] = bv[i] / 1000f; k++
                }
            }
        }
        return k
    }

    private fun abs(x: Double) = kotlin.math.abs(x)
}
