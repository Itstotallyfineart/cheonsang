package kr.inf.cheonsang.client

import kr.inf.cheonsang.L
import kr.inf.cheonsang.client.sky.Packs
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * 천상 설정 화면. 여는 법: 조작 설정 "천상 설정" 키 (기본 없음) · ModMenu 모드 목록
 * 왼쪽 = 하늘 표시, 오른쪽 = 장노출·정보 카드·글씨. 바꾸면 바로 저장 (client.json display)
 */
class SettingsScreen(private val parent: Screen?) : Screen(Component.literal(L.t("cheonsang.settingsscreen.1"))) {
    override fun init() {
        val colW = 150
        val gap = 10
        val left = width / 2 - colW - gap / 2
        val right = width / 2 + gap / 2
        val top = TOP
        val row = rowH()
        val bh = (row - 2).coerceAtMost(20)

        // ── 왼쪽: 하늘 표시 ──
        val f = TelescopeFilter
        var y = top
        fun leftToggle(name: String, get: () -> Boolean, set: (Boolean) -> Unit) { addRenderableWidget(toggle(name, get, set, left, y, colW, bh)); y += row }
        leftToggle(L.t("cheonsang.settingsscreen.2"), { f.lines }, { f.lines = it })
        leftToggle(L.t("cheonsang.settingsscreen.3"), { f.names }, { f.names = it })
        leftToggle(L.t("cheonsang.settingsscreen.4"), { f.bodies }, { f.bodies = it })
        leftToggle(L.t("cheonsang.settingsscreen.5"), { f.aim }, { f.aim = it })
        leftToggle(L.t("cheonsang.settingsscreen.6"), { f.grid }, { f.grid = it })
        leftToggle(L.t("cheonsang.settingsscreen.7"), { f.altaz }, { f.altaz = it })
        leftToggle(L.t("cheonsang.settingsscreen.8"), { f.bounds }, { f.bounds = it })
        leftToggle(L.t("cheonsang.settingsscreen.9"), { f.art }, { f.art = it })
        addRenderableWidget(cycle(L.t("cheonsang.korean.setting"), listOf(L.t("cheonsang.korean.western"), L.t("cheonsang.korean.joseon_short")),
            { if (f.korean) 1 else 0 }, { f.korean = it == 1 }, left, y, colW, bh)); y += row

        // ── 오른쪽: 장노출·카드·글씨 ──
        y = top
        if (Packs.photoOk) {
            addRenderableWidget(toggle(L.t("cheonsang.settingsscreen.10"), { f.photo }, { f.photo = it }, right, y, colW, bh))
        } else {
            addRenderableWidget(Button.builder(Component.literal(L.t("cheonsang.settingsscreen.11", Packs.photo.label))) {}.bounds(right, y, colW, bh).build().also { it.active = false })
        }
        y += row
        addRenderableWidget(toggle(L.t("cheonsang.settingsscreen.12"), { f.card }, { f.card = it }, right, y, colW, bh)); y += row
        addRenderableWidget(cycle(L.t("cheonsang.settingsscreen.13"), CORNERS, { f.cardCorner }, { f.cardCorner = it }, right, y, colW, bh)); y += row
        addRenderableWidget(cycle(L.t("cheonsang.settingsscreen.14"), OPACITY.map { "${(it * 100).toInt()}%" }, { OPACITY.indexOfClosest(f.cardOpacity) }, { f.cardOpacity = OPACITY[it] }, right, y, colW, bh)); y += row
        addRenderableWidget(toggle(L.t("cheonsang.settingsscreen.15"), { f.cardAltAz }, { f.cardAltAz = it }, right, y, colW, bh)); y += row
        addRenderableWidget(toggle(L.t("cheonsang.settingsscreen.16"), { f.cardRaDec }, { f.cardRaDec = it }, right, y, colW, bh)); y += row
        addRenderableWidget(toggle(L.t("cheonsang.settingsscreen.17"), { f.cardRiseSet }, { f.cardRiseSet = it }, right, y, colW, bh)); y += row
        addRenderableWidget(cycle(L.t("cheonsang.settingsscreen.18"), SCALES.map { "×$it" }, { SCALES.indexOfClosest(f.uiScale) }, { f.uiScale = SCALES[it] }, right, y, colW, bh)); y += row
        addRenderableWidget(toggle(L.t("cheonsang.settingsscreen.visualday"), { f.visualDay }, { f.visualDay = it }, right, y, colW, bh)); y += row

        // 관측 위치 (누구나, 내 하늘만) — 왼쪽 칸 위도 경도 · 적용 · 기본
        val sy = top + row * 9 + 14
        val py = sy + row + 14
        val pbox = net.minecraft.client.gui.components.EditBox(font, left, py, colW, bh, Component.literal(L.t("cheonsang.settingsscreen.21")))
        pbox.setMaxLength(32)
        pbox.setValue(Place.mine?.let { "%.2f %.2f".format(java.util.Locale.ROOT, it.first, it.second) } ?: "")  // 소수점 점 고정 (다시 적용할 때 읽히게)
        pbox.setHint(Component.literal(L.t("cheonsang.place.hint")))
        addRenderableWidget(pbox)
        addRenderableWidget(Button.builder(Component.literal(L.t("cheonsang.settingsscreen.23"))) { b ->
            val p = Place.parse(pbox.value)
            if (p != null) { Place.set(p.first, p.second); b.setMessage(Component.literal(L.t("cheonsang.settingsscreen.24"))) }
            else b.setMessage(Component.literal(L.t("cheonsang.settingsscreen.25")))
        }.bounds(right, py, 70, bh).build())
        addRenderableWidget(Button.builder(Component.literal(L.t("cheonsang.place.reset"))) { _ -> Place.reset(); pbox.setValue("") }.bounds(right + 74, py, colW - 74, bh).build())
        // 서버에 천상이 없을 때만 — 날짜(현지). 서버가 있으면 서버 값 (바꾸려면 OP 가 /cele set)
        if (!kr.inf.cheonsang.client.sky.CheonsangSky.serverSky) {
            val cur = kr.inf.cheonsang.client.sky.CheonsangSky.clock.let { c -> kr.inf.cheonsang.astro.Calendar.toLocal(c.jd((minecraft.level?.defaultClockTime ?: 0L).toDouble()), c.zoneHours) }
            val date = net.minecraft.client.gui.components.EditBox(font, left, sy, colW, bh, Component.literal(L.t("cheonsang.settingsscreen.19")))
            date.setMaxLength(16); date.setValue("${cur.year} ${cur.month} ${cur.day}"); date.setHint(Component.literal(L.t("cheonsang.settingsscreen.20")))
            addRenderableWidget(date)
            addRenderableWidget(Button.builder(Component.literal(L.t("cheonsang.settingsscreen.23"))) { b ->
                val d = date.value.trim().split(" ").filter { it.isNotEmpty() }.map { it.toIntOrNull() }
                val ok = d.size == 3 && d.all { it != null } &&
                    d[1]!! in 1..12 && d[2]!! in 1..kr.inf.cheonsang.astro.Calendar.daysInMonth(d[0]!!, d[1]!!) && d[0]!! in -2999..2999
                if (ok) { ClientConfig.setStandalone(d[0]!!, d[1]!!, d[2]!!, ClientConfig.standaloneLat, ClientConfig.standaloneLon); b.setMessage(Component.literal(L.t("cheonsang.settingsscreen.24"))) }
                else b.setMessage(Component.literal(L.t("cheonsang.settingsscreen.25")))
            }.bounds(right, sy, 70, bh).build())
        }
        // 완료 버튼은 관측 위치 글줄 아래
        addRenderableWidget(Button.builder(Component.literal(L.t("cheonsang.settingsscreen.26"))) { onClose() }.bounds(width / 2 - 100, (placeTextY(py, bh) + PLACE_TEXT_H + 4).coerceAtMost(height - bh - 2), 200, bh).build())
    }

    override fun extractRenderState(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(g, mouseX, mouseY, partialTick)
        g.centeredText(font, title, width / 2, 8, 0xFFFFFFFF.toInt())
        val row = rowH()
        g.centeredText(font, Packs.summary(), width / 2, TOP + row * 9 + 2, 0xFFB0B0B0.toInt())
        val sy = TOP + row * 9 + 14
        g.centeredText(font, fit(if (kr.inf.cheonsang.client.sky.CheonsangSky.serverSky) L.t("cheonsang.settingsscreen.28") else L.t("cheonsang.settingsscreen.27")),
            width / 2, sy + row + 1, 0xFFB0B0B0.toInt())
        // 관측 위치 글줄은 " · " 마디로 끊어 두 줄까지
        val py = sy + row + 14
        val bh = (row - 2).coerceAtMost(20)
        wrap(Place.text()).take(2).forEachIndexed { i, s -> g.centeredText(font, s, width / 2, placeTextY(py, bh) + i * 10, 0xFFB0B0B0.toInt()) }
    }

    override fun onClose() {
        minecraft.gui.setScreen(parent)
    }

    private fun onOff(b: Boolean) = if (b) L.t("cheonsang.settingsscreen.30") else L.t("cheonsang.settingsscreen.29")

    /** 화면 높이에 맞춘 줄 간격 (GUI 배율이 커서 높이가 작으면 촘촘히, 최소 14) */
    private fun rowH(): Int = ((height - TOP - 90) / 10).coerceIn(14, 22)

    private fun placeTextY(py: Int, bh: Int) = py + bh + 3

    /** 화면 폭에 맞게 " · " 마디 단위로 줄 나눔 */
    private fun wrap(s: String): List<String> {
        val maxW = width - 20
        val out = ArrayList<String>()
        var cur = ""
        for (part in s.split(" · ")) {
            val next = if (cur.isEmpty()) part else "$cur · $part"
            if (cur.isNotEmpty() && font.width(next) > maxW) { out += cur; cur = part } else cur = next
        }
        if (cur.isNotEmpty()) out += cur
        return out.map { fit(it) }
    }

    /** 화면 폭보다 길면 끝을 줄임 */
    private fun fit(s: String): String { val maxW = width - 20; return if (font.width(s) > maxW) font.plainSubstrByWidth(s, maxW - 6) + "…" else s }

    private fun toggle(name: String, get: () -> Boolean, set: (Boolean) -> Unit, x: Int, y: Int, w: Int, h: Int = 20): Button =
        Button.builder(Component.literal("$name: ${onOff(get())}")) { b ->
            set(!get())
            b.setMessage(Component.literal("$name: ${onOff(get())}"))
            ClientConfig.saveDisplay()
        }.bounds(x, y, w, h).build()

    private fun cycle(name: String, labels: List<String>, get: () -> Int, set: (Int) -> Unit, x: Int, y: Int, w: Int, h: Int = 20): Button =
        Button.builder(Component.literal("$name: ${labels[get().coerceIn(0, labels.size - 1)]}")) { b ->
            val next = (get() + 1) % labels.size
            set(next)
            b.setMessage(Component.literal("$name: ${labels[next]}"))
            ClientConfig.saveDisplay()
        }.bounds(x, y, w, h).build()

    companion object {
        private const val TOP = 22
        private const val PLACE_TEXT_H = 20
        // 열 때마다 번역
        val CORNERS get() = listOf(L.t("cheonsang.settingsscreen.34"), L.t("cheonsang.settingsscreen.33"), L.t("cheonsang.settingsscreen.32"), L.t("cheonsang.settingsscreen.31"))
        val OPACITY = listOf(0.25f, 0.5f, 0.69f, 0.85f, 1f)
        val SCALES = listOf(0.75f, 1f, 1.25f, 1.5f)

        private fun List<Float>.indexOfClosest(v: Float) = indices.minBy { kotlin.math.abs(this[it] - v) }

        fun open() {
            val mc = Minecraft.getInstance()
            mc.setScreenAndShow(SettingsScreen(mc.gui.screen()))
        }
    }
}
