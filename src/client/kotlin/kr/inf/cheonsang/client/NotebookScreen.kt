package kr.inf.cheonsang.client

import kr.inf.cheonsang.L
import kr.inf.cheonsang.client.sky.CheonsangSky
import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * 관측 수첩: 오늘 밤 · 다음 현상. 여는 법: 조작 설정 "천상 관측 수첩" 키 (기본 없음).
 */
class NotebookScreen(private var tab: Int) : Screen(Component.literal(L.t("cheonsang.notebookscreen.1"))) {
    private var lines: List<String> = emptyList()
    private var scroll = 0
    private val tabButtons = ArrayList<Button>()

    companion object {
        private val TABS get() = listOf(L.t("cheonsang.notebookscreen.11"), L.t("cheonsang.notebookscreen.10"))
        private const val LINE_H = 11
        private lateinit var notebookKey: TelescopeFilter.CheonsangKey

        fun register() {
            notebookKey = TelescopeFilter.CheonsangKey("key.cheonsang.notebook", InputConstants.UNKNOWN.value, null)
                .also { KeyMappingHelper.registerKeyMapping(it) }
            TelescopeFilter.extraKeys += notebookKey
            ClientTickEvents.END_CLIENT_TICK.register { mc ->
                Safety.run(Safety.KEYS, Unit) {
                    while (notebookKey.consumeClick()) if (mc.gui.screen() == null) open(0)
                }
            }
        }

        fun open(tab: Int) {
            val mc = Minecraft.getInstance()
            mc.setScreenAndShow(NotebookScreen(tab))
        }
    }

    override fun init() {
        tabButtons.clear()
        val w = 90
        val x0 = width / 2 - (w * TABS.size + (TABS.size - 1) * 4) / 2
        TABS.forEachIndexed { k, name ->
            tabButtons += addRenderableWidget(Button.builder(Component.literal(name)) { select(k) }.bounds(x0 + k * (w + 4), 24, w, 20).build())
        }
        addRenderableWidget(Button.builder(Component.literal(L.t("cheonsang.notebookscreen.2"))) { onClose() }.bounds(width / 2 - 50, height - 26, 100, 20).build())
        select(tab)
    }

    private fun select(k: Int) {
        tab = k.coerceIn(0, TABS.size - 1)
        scroll = 0
        tabButtons.forEachIndexed { i, b -> b.active = i != tab }
        // 계산 실패해도 그 탭만 이유 한 줄로 표시하고 나머지 기능은 그대로 둔다
        lines = try { build() } catch (e: Exception) {
            kr.inf.cheonsang.Cheonsang.LOG.error("Cheonsang: observing notebook calculation failed", e)
            listOf(L.t("cheonsang.notebookscreen.3", e.javaClass.simpleName, e.message ?: "").trim())
        }
    }

    private fun build(): List<String> = when (tab) {
        0 -> Tonight.report()
        else -> {
            val level = Minecraft.getInstance().level
            if (level == null) listOf(L.t("cheonsang.notebookscreen.4")) else {
                val lat = CheonsangSky.latitude; val lon = CheonsangSky.longitude
                listOf(L.t("cheonsang.notebookscreen.5", kr.inf.cheonsang.SkyServer.placeText(lat, lon))) +
                    kr.inf.cheonsang.NextEvents.lines(CheonsangSky.clock.jd(level.defaultClockTime.toDouble()), lat, lon, null, false, kr.inf.cheonsang.SkyServer.placeText(lat, lon))
                        .map { it.string }
            }
        }
    }

    private fun rows() = ((height - 26 - 52) / LINE_H).coerceAtLeast(1)

    override fun mouseScrolled(x: Double, y: Double, sx: Double, sy: Double): Boolean {
        scroll = (scroll - kotlin.math.sign(sy).toInt() * 3).coerceIn(0, (lines.size - rows()).coerceAtLeast(0))
        return true
    }

    override fun extractRenderState(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(g, mouseX, mouseY, partialTick)
        g.centeredText(font, title, width / 2, 10, 0xFFFFFFFF.toInt())
        val x = (width / 2 - 200).coerceAtLeast(8)
        val maxW = width - x * 2
        var y = 52
        for (s in lines.drop(scroll).take(rows())) {
            g.text(font, if (font.width(s) > maxW) font.plainSubstrByWidth(s, maxW - 6) + "…" else s, x, y, 0xFFD8D8D8.toInt(), false)
            y += LINE_H
        }
        if (lines.size > rows()) g.text(font, "${scroll + 1}~${(scroll + rows()).coerceAtMost(lines.size)} / ${lines.size}", x, height - 20, 0xFF909090.toInt(), false)
    }

    override fun isPauseScreen() = false

}
