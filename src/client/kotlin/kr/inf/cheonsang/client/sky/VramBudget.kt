package kr.inf.cheonsang.client.sky

import com.mojang.blaze3d.systems.RenderSystem
import kr.inf.cheonsang.Cheonsang

/**
 * 그래픽 메모리 예산 = 카드 메모리의 1/4 (최소 256MB), 조각:사진 = 4:3 분할. 못 구하면 512MB+384MB 기본값.
 * 렌더 스레드에서 한 번만 측정 (GL_NVX_gpu_memory_info / GL_ATI_meminfo, 리플렉션)
 */
object VramBudget {
    private var done = false

    /** 그래픽 카드 메모리 (MiB), 모르면 -1 */
    private fun totalMib(): Long = try {
        val ext = RenderSystem.getDevice().deviceInfo.underlyingExtensions()
        val gl11 = Class.forName("org.lwjgl.opengl.GL11")
        when {
            ext.contains("GL_NVX_gpu_memory_info") -> {
                // GPU_MEMORY_INFO_DEDICATED_VIDMEM_NVX (KB)
                (gl11.getMethod("glGetInteger", Int::class.javaPrimitiveType).invoke(null, 0x9047) as Int).toLong() / 1024
            }
            ext.contains("GL_ATI_meminfo") -> {
                // TEXTURE_FREE_MEMORY_ATI: [남은 KB, …] — 전체는 몰라서 남은 양 기준
                val arr = IntArray(4)
                gl11.getMethod("glGetIntegerv", Int::class.javaPrimitiveType, IntArray::class.java).invoke(null, 0x87FC, arr)
                arr[0].toLong() / 1024
            }
            else -> -1L
        }
    } catch (_: Throwable) { -1L }

    fun apply() {
        if (done) return
        done = true
        val total = totalMib()
        if (total <= 0) { Cheonsang.LOG.info("Cheonsang: graphics memory unknown — budget {} MB", (TileSky.BUDGET + PhotoSky.BUDGET) shr 20); return }
        val budget = maxOf(256L, total / 4) shl 20
        TileSky.BUDGET = budget * 4 / 7
        PhotoSky.BUDGET = budget - TileSky.BUDGET
        Cheonsang.LOG.info("Cheonsang: graphics memory {} MB → budget {} MB (tiles {} + photos {})", total, budget shr 20, TileSky.BUDGET shr 20, PhotoSky.BUDGET shr 20)
    }
}
