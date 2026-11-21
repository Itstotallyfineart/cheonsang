package kr.inf.cheonsang.client.sky

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.platform.NativeImage
import com.mojang.renderpearl.api.GpuFormat
import com.mojang.renderpearl.api.buffers.GpuBuffer
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology
import com.mojang.renderpearl.api.textures.FilterMode
import kr.inf.cheonsang.Cheonsang
import kr.inf.cheonsang.astro.Vec3
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.AbstractTexture
import net.minecraft.resources.Identifier
import org.joml.Matrix4f
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import kotlin.math.atan
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * 1.6.1 타일 스트리밍: 초고화질팩의 큰 은하수(맨눈 16K·32K, 장노출 16K)를 1024px 조각으로 나눠 **화면에 보이는 조각만** 올림.
 * - 조각 = textures/sky/tiles/<단계>/<x>_<y>.png, 1024 + 사방 2px 여백 (바이큐빅이 경계를 넘어 읽어도 이음선 없음)
 * - 단계: 화면 한 픽셀보다 텍셀이 작아지는 가장 낮은 단계 (기본 8K 로 충분하면 안 씀)
 * - PNG 풀기는 별도 스레드, 올리기는 렌더 패스 밖(prepare)에서 한 프레임에 [UPLOADS_PER_FRAME] 장
 * - 조각이 오기 전엔 한 단계 낮은 조각(없으면 기본 8K)을 그리다 [FADE_NANOS] 동안 부드럽게 바꿈
 * - 그래픽 메모리 [BUDGET] 넘으면 오래 안 본 조각부터 내림
 */
object TileSky {
    const val TS = 1024
    const val MARGIN = 2
    const val FULL = TS + 2 * MARGIN
    private const val GRID = 8
    private const val UPLOADS_PER_FRAME = 4
    private const val MAX_PENDING = 12
    private const val FADE_NANOS = 300_000_000L
    /** 조각용 그래픽 메모리 한도 (맨눈 조각 1MiB, 장노출 4MiB) */
    var BUDGET = 512L shl 20
    /** 기본 은하수 한 텍셀 (°) — 장노출 8K, 현실 12K (1.7.0) */
    private const val BASE_TEXEL = 360.0 / 8192
    private const val EYE_BASE_TEXEL = 360.0 / 12288

    class Level(val name: String, val nx: Int, val gray: Boolean, val parent: Level?) {
        val ny = nx / 2
        val texel = 360.0 / (nx * TS)
        val bytes = FULL.toLong() * FULL * if (gray) 1 else 4
        val tiles = Array(nx * ny) { Tile(this, it % nx, it / nx) }
        fun at(tx: Int, ty: Int) = tiles[ty * nx + tx]
    }

    class Tile(val level: Level, val tx: Int, val ty: Int) {
        val id: Identifier = Cheonsang.id("tile/${level.name}/${tx}_$ty")
        val path: Identifier = Cheonsang.id("textures/sky/tiles/${level.name}/${tx}_$ty.png")
        /** 0 안 올림 · 1 푸는 중 · 2 올라감 · 3 파일 없음 */
        @Volatile var state = 0
        var loadedAt = 0L
        var lastUse = 0L
        val u0 get() = tx.toDouble() / level.nx
        val v0 get() = ty.toDouble() / level.ny
        val du get() = 1.0 / level.nx
        val dv get() = 1.0 / level.ny
        /** 가운데 방향·각반지름 (보이는지 판단) */
        val center: Vec3 by lazy { dir(u0 + du / 2, v0 + dv / 2) }
        val radius: Double by lazy {
            var r = 0.0
            for (i in 0..8) for (j in 0..8) if (i == 0 || i == 8 || j == 0 || j == 8) r = maxOf(r, center.angleTo(dir(u0 + du * i / 8, v0 + dv * j / 8)))
            r
        }
        var mesh: GpuBuffer? = null
        var meshCount = 0
        /** 바이큐빅 셰이더 값: [0].x 흑백 · [1].x 가로도 끝에서 멈춤 · [2] = 전체 uv → 이 조각 텍셀 uv (배율, 이동) */
        val params: Matrix4f by lazy {
            Matrix4f().zero().m00(if (level.gray) 1f else 0f).m10(1f)
                .m20((level.nx * TS / FULL.toDouble()).toFloat()).m21((level.ny * TS / FULL.toDouble()).toFloat())
                .m22(((MARGIN - tx * TS) / FULL.toDouble()).toFloat()).m23(((MARGIN - ty * TS) / FULL.toDouble()).toFloat())
        }
    }

    /** 전체 정거원통 uv → 하늘 방향 (J2000 적도, 은하수 그물과 같은 배치: u = 0.5 − 적경/360, v = 0.5 − 적위/180) */
    fun dir(u: Double, v: Double): Vec3 = Vec3.fromSpherical((0.5 - u) * 360.0, 90.0 - 180.0 * v)

    private val EYE16 = Level("eye16", 16, true, null)
    private val EYE32 = Level("eye32", 32, true, EYE16)
    private val LX16 = Level("lx16", 16, false, null)
    private val EYE = listOf(EYE16, EYE32)
    private val LX = listOf(LX16)
    private val ALL = EYE + LX

    private val pool = Executors.newFixedThreadPool(3) { r -> Thread(r, "Cheonsang tiles").apply { isDaemon = true; priority = Thread.MIN_PRIORITY } }
    private class Ready(val tile: Tile, val buf: ByteBuffer)
    private val ready = ConcurrentLinkedQueue<Ready>()
    private var pending = 0
    private val loaded = ArrayList<Tile>()
    var usedBytes = 0L
        private set

    /** 지금 화면에 쓸 단계 — 기본 8K 로 충분하면 null */
    fun levelFor(color: Boolean, pixelDeg: Double): Level? {
        if ((if (color) BASE_TEXEL else EYE_BASE_TEXEL) <= pixelDeg * 1.25) return null
        val ls = if (color) LX else EYE
        return ls.firstOrNull { it.texel <= pixelDeg * 1.25 } ?: ls.last()
    }

    /** 이 프레임에 그릴 조각 (보이는 것). 렌더 패스 밖에서 부름 — 요청·올리기·내리기 */
    fun update(view: Vec3, halfDiagDeg: Double, lv: Level, now: Long): List<Tile> {
        upload(now)
        val vis = lv.tiles.filter { view.angleTo(it.center) < halfDiagDeg + it.radius + 1.0 }
            .sortedBy { view.angleTo(it.center) }
        for (t in vis) {
            t.lastUse = now
            val p = parentOf(t)
            if (p != null) p.lastUse = now
            if (t.state == 0 && pending < MAX_PENDING) request(t)
            else if (t.state != 2 && p != null && p.state == 0 && pending < MAX_PENDING) request(p)
            if (t.mesh == null) buildMesh(t)
        }
        evict(now)
        return vis
    }

    /** 한 단계 낮은 조각 중 이 조각을 덮는 것 */
    fun parentOf(t: Tile): Tile? {
        val p = t.level.parent ?: return null
        val k = t.level.nx / p.nx
        return p.at(t.tx / k, t.ty / k)
    }

    /** 조각이 올라온 뒤 지난 비율 (0~1, 부드러운 교체) */
    fun fade(t: Tile, now: Long) = if (t.state != 2) 0f else ((now - t.loadedAt).toFloat() / FADE_NANOS).coerceIn(0f, 1f)

    /** 다른 조각의 텍스처로 이 조각 자리를 그릴 때 셰이더 값 (위 단계 조각 = 대신 그리기) */
    fun params(of: Tile) = of.params

    private fun request(t: Tile) {
        t.state = 1; pending++
        pool.execute {
            val buf = try {
                Minecraft.getInstance().resourceManager.getResource(t.path).orElse(null)?.open()?.use { s ->
                    NativeImage.read(s).use { img -> convert(img, t.level.gray) }
                }
            } catch (e: Exception) {
                Cheonsang.LOG.warn("Cheonsang: cannot read tile {} ({})", t.path, e.toString()); null
            }
            if (buf == null) t.state = 3
            ready.add(Ready(t, buf ?: MemoryUtil.memAlloc(0)))
        }
    }

    /** NativeImage (흑백 PNG = LUMINANCE, 컬러 = RGB) → 올릴 바이트 (흑백 R8 / 컬러 RGBA8) */
    private fun convert(img: NativeImage, gray: Boolean): ByteBuffer? {
        if (img.width != FULL || img.height != FULL) return null
        val n = FULL * FULL
        val c = img.format().components()
        val src = img.pointer
        return if (gray) {
            val b = MemoryUtil.memAlloc(n)
            for (i in 0 until n) b.put(i, MemoryUtil.memGetByte(src + i.toLong() * c))
            b
        } else {
            val b = MemoryUtil.memAlloc(n * 4)
            for (i in 0 until n) {
                val s = src + i.toLong() * c
                val r = MemoryUtil.memGetByte(s)
                b.put(i * 4, r)
                b.put(i * 4 + 1, if (c >= 3) MemoryUtil.memGetByte(s + 1) else r)
                b.put(i * 4 + 2, if (c >= 3) MemoryUtil.memGetByte(s + 2) else r)
                b.put(i * 4 + 3, -1)
            }
            b
        }
    }

    private fun upload(now: Long) {
        var n = 0
        while (n < UPLOADS_PER_FRAME) {
            val r = ready.poll() ?: break
            pending--
            val t = r.tile
            if (t.state == 3 || r.buf.capacity() == 0) { MemoryUtil.memFree(r.buf); t.state = 3; continue }
            try {
                Minecraft.getInstance().textureManager.register(t.id, TileTexture(t.id.toString(), r.buf, t.level.gray))
                t.state = 2; t.loadedAt = now; t.lastUse = now
                loaded.add(t); usedBytes += t.level.bytes
            } catch (e: Exception) {
                Cheonsang.LOG.warn("Cheonsang: cannot upload tile {} ({})", t.id, e.toString()); t.state = 3
            } finally {
                MemoryUtil.memFree(r.buf)
            }
            n++
        }
    }

    private fun evict(now: Long) {
        if (usedBytes <= BUDGET) return
        loaded.sortBy { it.lastUse }
        val it = loaded.iterator()
        while (usedBytes > BUDGET && it.hasNext()) {
            val t = it.next()
            if (t.lastUse == now) break
            drop(t); it.remove()
        }
    }

    private fun drop(t: Tile) {
        Minecraft.getInstance().textureManager.release(t.id)
        usedBytes -= t.level.bytes
        t.state = 0
    }

    /** 전부 내림 (망원경 안 쓴 지 오래 · 자원 다시 불러오기) */
    fun releaseAll() {
        for (t in loaded) drop(t)
        loaded.clear()
        for (l in ALL) for (t in l.tiles) if (t.state == 3) t.state = 0
    }

    val loadedCount get() = loaded.size

    private fun buildMesh(t: Tile) {
        val bytes = ByteBufferBuilder.exactlySized(GRID * GRID * 4 * DefaultVertexFormat.POSITION_TEX.vertexSize)
        bytes.use {
            val bb = BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX)
            fun vtx(i: Int, j: Int) {
                val u = t.u0 + t.du * i / GRID
                val v = t.v0 + t.dv * j / GRID
                val p = dir(u, v) * 100.0
                bb.addVertex(p.x.toFloat(), p.y.toFloat(), p.z.toFloat()).setUv(u.toFloat(), v.toFloat())
            }
            for (i in 0 until GRID) for (j in 0 until GRID) { vtx(i, j); vtx(i + 1, j); vtx(i + 1, j + 1); vtx(i, j + 1) }
            bb.buildOrThrow().use { mesh ->
                t.meshCount = mesh.drawState().indexCount()
                t.mesh = RenderSystem.getDevice().createBuffer({ "Cheonsang tile ${t.id}" }, GpuBuffer.USAGE_VERTEX or GpuBuffer.USAGE_COPY_DST, mesh.vertexBuffer())
            }
        }
    }

    /** 화면 대각선 반각 (°) */
    fun halfDiag(fovDeg: Double, aspect: Double) = Math.toDegrees(atan(tan(Math.toRadians(fovDeg / 2)) * sqrt(1 + aspect * aspect)))

    private class TileTexture(label: String, buf: ByteBuffer, gray: Boolean) : AbstractTexture() {
        init {
            val device = RenderSystem.getDevice()
            val tex = device.createTexture({ label }, 5, if (gray) GpuFormat.R8_UNORM else GpuFormat.RGBA8_UNORM, FULL, FULL, 1, 1)
            texture = tex
            textureView = device.createTextureView(tex)
            sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
            device.createCommandEncoder().writeToTexture(tex, buf, 0, 0, 0, 0, FULL, FULL)
        }
    }
}
