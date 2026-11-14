package kr.inf.cheonsang.client.sky

import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.renderpearl.api.GpuFormat
import net.minecraft.client.renderer.texture.SimpleTexture
import net.minecraft.resources.Identifier
import org.lwjgl.system.MemoryUtil

/**
 * 흑백 그림을 한 채널(R8)로 올리는 텍스처 — 현실 은하수. RGBA 로 올리면 8192×4096 기준 128MiB, R8 이면 32MiB.
 * gray 셰이더가 r 을 rgb 로 펼쳐서 그림 ([CheonsangSky.GRAY_PIPELINE])
 */
class GrayTexture(id: Identifier) : SimpleTexture(id) {
    override fun doLoad(image: NativeImage) {
        val device = RenderSystem.getDevice()
        close()
        val w = image.width
        val h = image.height
        val label = resourceId().toString()
        val tex = device.createTexture({ label }, 5, GpuFormat.R8_UNORM, w, h, 1, 1)
        texture = tex
        textureView = device.createTextureView(tex)
        val n = w * h
        val buf = MemoryUtil.memAlloc(n)
        try {
            val src = image.pointer
            // RGBA 순서로 픽셀당 4바이트 — 첫 바이트(빨강)만
            for (i in 0 until n) buf.put(i, MemoryUtil.memGetByte(src + i * 4L))
            device.createCommandEncoder().writeToTexture(tex, buf, 0, 0, 0, 0, w, h)
        } finally {
            MemoryUtil.memFree(buf)
        }
    }
}
