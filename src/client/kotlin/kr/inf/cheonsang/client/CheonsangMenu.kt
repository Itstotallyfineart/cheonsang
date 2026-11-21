package kr.inf.cheonsang.client

import kr.inf.cheonsang.L
import kr.inf.cheonsang.RealTime
import kr.inf.cheonsang.SkyServer
import kr.inf.cheonsang.client.sky.CheonsangSky
import kr.inf.cheonsang.client.sky.Packs
import me.shedaniel.clothconfig2.api.ConfigBuilder
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.util.Locale

/**
 * 2.0.3-dev4 천상 설정 (Cloth Config 탭 화면) — 전 SettingsScreen (단추 두 줄) 대신.
 * 탭: 하늘 표시 · 장소·시각 · 오늘 밤 · 장비. 여는 법은 그대로 (조작 설정 "천상 설정" 키 · ModMenu).
 * 바꾼 값은 "저장" 을 눌러야 적용. 서버 하늘 칸은 OP 일 때만 보이고, 저장하면 같은 /cele 명령을 서버로 보냄 (명령어는 그대로 남음)
 */
object CheonsangMenu {
    val CORNERS get() = listOf(L.t("cheonsang.settingsscreen.34"), L.t("cheonsang.settingsscreen.33"), L.t("cheonsang.settingsscreen.32"), L.t("cheonsang.settingsscreen.31"))
    val OPACITY = listOf(0.25f, 0.5f, 0.69f, 0.85f, 1f)
    val SCALES = listOf(0.75f, 1f, 1.25f, 1.5f)
    private fun List<Float>.closest(v: Float) = minBy { kotlin.math.abs(it - v) }
    private fun t(key: String, vararg a: Any?): Component = Component.literal(L.t(key, *a))

    /** 장소 고르기 값: KEEP 그대로 · WORLD 월드 기본 · 나머지 도시 id */
    private const val KEEP = "-"
    private const val WORLD = "+"

    fun open() { val mc = Minecraft.getInstance(); mc.setScreenAndShow(create(mc.gui.screen())) }

    fun create(parent: Screen?): Screen {
        val mc = Minecraft.getInstance()
        val f = TelescopeFilter
        val b = ConfigBuilder.create().setParentScreen(parent).setTitle(t("cheonsang.settingsscreen.1"))
        val e = b.entryBuilder()

        // ── 하늘 표시 ──
        val sky = b.getOrCreateCategory(t("cheonsang.menu.tab_sky"))
        fun toggle(key: String, v: Boolean, set: (Boolean) -> Unit) =
            sky.addEntry(e.startBooleanToggle(t(key), v).setYesNoTextSupplier { Component.literal(L.t(if (it) "cheonsang.settingsscreen.30" else "cheonsang.settingsscreen.29")) }.setSaveConsumer(set).build())
        toggle("cheonsang.settingsscreen.2", f.lines) { f.lines = it }
        toggle("cheonsang.settingsscreen.3", f.names) { f.names = it }
        toggle("cheonsang.settingsscreen.4", f.bodies) { f.bodies = it }
        toggle("cheonsang.settingsscreen.5", f.aim) { f.aim = it }
        toggle("cheonsang.settingsscreen.6", f.grid) { f.grid = it }
        toggle("cheonsang.settingsscreen.7", f.altaz) { f.altaz = it }
        toggle("cheonsang.settingsscreen.8", f.bounds) { f.bounds = it }
        toggle("cheonsang.settingsscreen.9", f.art) { f.art = it }
        sky.addEntry(e.startSelector(t("cheonsang.korean.setting"), arrayOf(false, true), f.korean)
            .setNameProvider { Component.literal(L.t(if (it) "cheonsang.korean.joseon_short" else "cheonsang.korean.western")) }.setSaveConsumer { f.korean = it }.build())
        if (Packs.photoOk) toggle("cheonsang.settingsscreen.10", f.photo) { f.photo = it }
        else sky.addEntry(e.startTextDescription(t("cheonsang.settingsscreen.11", Packs.photo.label)).build())
        toggle("cheonsang.settingsscreen.visualday", f.visualDay) { f.visualDay = it }
        val card = e.startSubCategory(t("cheonsang.settingsscreen.12"))
        card.add(e.startBooleanToggle(t("cheonsang.settingsscreen.12"), f.card).setSaveConsumer { f.card = it }.build())
        card.add(e.startSelector(t("cheonsang.settingsscreen.13"), arrayOf(0, 1, 2, 3), f.cardCorner.coerceIn(0, 3))
            .setNameProvider { Component.literal(CORNERS[it]) }.setSaveConsumer { f.cardCorner = it }.build())
        card.add(e.startSelector(t("cheonsang.settingsscreen.14"), OPACITY.toTypedArray(), OPACITY.closest(f.cardOpacity))
            .setNameProvider { Component.literal("${(it * 100).toInt()}%") }.setSaveConsumer { f.cardOpacity = it }.build())
        card.add(e.startBooleanToggle(t("cheonsang.settingsscreen.15"), f.cardAltAz).setSaveConsumer { f.cardAltAz = it }.build())
        card.add(e.startBooleanToggle(t("cheonsang.settingsscreen.16"), f.cardRaDec).setSaveConsumer { f.cardRaDec = it }.build())
        card.add(e.startBooleanToggle(t("cheonsang.settingsscreen.17"), f.cardRiseSet).setSaveConsumer { f.cardRiseSet = it }.build())
        sky.addEntry(card.build())
        sky.addEntry(e.startSelector(t("cheonsang.settingsscreen.18"), SCALES.toTypedArray(), SCALES.closest(f.uiScale))
            .setNameProvider { Component.literal("×$it") }.setSaveConsumer { f.uiScale = it }.build())

        // ── 장소·시각 ──
        val place = b.getOrCreateCategory(t("cheonsang.menu.tab_place"))
        place.addEntry(e.startTextDescription(Component.literal(Place.text())).build())
        var pickCity = KEEP
        var pickText = ""
        val cityIds = arrayOf(KEEP, WORLD) + RealTime.CITIES.map { it.id }.toTypedArray()
        place.addEntry(e.startSelector(t("cheonsang.menu.city"), cityIds, KEEP)
            .setNameProvider { id -> Component.literal(when (id) { KEEP -> L.t("cheonsang.menu.keep"); WORLD -> L.t("cheonsang.place.reset"); else -> RealTime.CITIES.first { it.id == id }.label }) }
            .setSaveConsumer { pickCity = it }.build())
        place.addEntry(e.startStrField(t("cheonsang.settingsscreen.22"), "")
            .setTooltip(t("cheonsang.place.hint")).setSaveConsumer { pickText = it.trim() }.build())
        var pickDate = ""
        if (!CheonsangSky.serverSky) {
            val cur = CheonsangSky.clock.let { c -> kr.inf.cheonsang.astro.Calendar.toLocal(c.jd((mc.level?.defaultClockTime ?: 0L).toDouble()), c.zoneHours) }
            place.addEntry(e.startStrField(t("cheonsang.settingsscreen.27"), "${cur.year} ${cur.month} ${cur.day}").setSaveConsumer { pickDate = it.trim() }.build())
        } else place.addEntry(e.startTextDescription(t("cheonsang.settingsscreen.28")).build())

        // 서버 하늘 (OP): 서버가 /cele set 을 보내준 경우만 = 권한 있음
        val root = mc.connection?.commands?.root?.getChild("cele")
        var srvPlace = KEEP; var srvTime = ""; var srvRate = CheonsangSky.clock.rate; var srvReal = KEEP
        val opSet = root?.getChild("set") != null && CheonsangSky.realtimeCity == null
        val opReal = root?.getChild("realtime") != null
        if (opSet || opReal) {
            val op = e.startSubCategory(t("cheonsang.menu.server")).setExpanded(false)
            if (opSet) {
                val ids = arrayOf(KEEP) + SkyServer.PLACES.map { it.id }.toTypedArray()
                op.add(e.startSelector(t("cheonsang.menu.server_place"), ids, KEEP)
                    .setNameProvider { id -> Component.literal(if (id == KEEP) L.t("cheonsang.menu.keep") else SkyServer.PLACES.first { it.id == id }.label) }
                    .setSaveConsumer { srvPlace = it }.build())
                op.add(e.startStrField(t("cheonsang.menu.server_time"), "").setTooltip(t("cheonsang.menu.server_time_hint")).setSaveConsumer { srvTime = it.trim() }.build())
                op.add(e.startDoubleField(t("cheonsang.menu.server_rate"), CheonsangSky.clock.rate).setMin(0.01).setMax(1000.0).setSaveConsumer { srvRate = it }.build())
            }
            if (opReal) {
                val ids = arrayOf(KEEP, "off") + RealTime.CITIES.map { it.id }.toTypedArray()
                op.add(e.startSelector(t("cheonsang.menu.server_realtime"), ids, KEEP)
                    .setNameProvider { id -> Component.literal(when (id) { KEEP -> L.t("cheonsang.menu.keep"); "off" -> L.t("cheonsang.settingsscreen.29"); else -> RealTime.CITIES.first { it.id == id }.label }) }
                    .setSaveConsumer { srvReal = it }.build())
            }
            place.addEntry(op.build())
        }

        // ── 오늘 밤 ── (/ctonight · 다음 현상 — 읽기만)
        val night = b.getOrCreateCategory(t("cheonsang.menu.tab_tonight"))
        val tonight = try { Tonight.report() } catch (_: Exception) { emptyList() }
        tonight.forEach { night.addEntry(e.startTextDescription(Component.literal(it)).build()) }
        val level = mc.level
        if (level != null) try {
            val lat = CheonsangSky.latitude; val lon = CheonsangSky.longitude
            night.addEntry(e.startTextDescription(t("cheonsang.menu.next")).build())
            kr.inf.cheonsang.NextEvents.lines(CheonsangSky.clock.jd(level.defaultClockTime.toDouble()), lat, lon, null, false, SkyServer.placeText(lat, lon))
                .forEach { night.addEntry(e.startTextDescription(Component.literal(it.string)).build()) }
        } catch (_: Exception) {}

        // ── 장비 ──
        val gear = b.getOrCreateCategory(t("cheonsang.menu.tab_gear"))
        var pickAp = Optics.apertureMm.toInt()
        gear.addEntry(e.startSelector(t("cheonsang.menu.aperture"), Optics.APERTURES.toTypedArray(), Optics.apertureMm.toInt())
            .setNameProvider { Component.literal("${it}mm") }.setSaveConsumer { pickAp = it }.build())
        gear.addEntry(e.startTextDescription(Component.literal(Packs.summary())).build())

        b.setSavingRunnable {
            ClientConfig.saveDisplay()
            // 장소: 도시 > 직접 입력
            when {
                pickCity == WORLD -> Place.reset()
                pickCity != KEEP -> RealTime.CITIES.firstOrNull { it.id == pickCity }?.let { Place.set(it.lat, it.lon) }
                pickText.isNotEmpty() -> Place.parse(pickText)?.let { Place.set(it.first, it.second) }
            }
            if (pickDate.isNotEmpty() && !CheonsangSky.serverSky) {
                val d = pickDate.split(" ").filter { it.isNotEmpty() }.map { it.toIntOrNull() }
                if (d.size == 3 && d.all { it != null } && d[1]!! in 1..12 && d[0]!! in -2999..2999 && d[2]!! in 1..kr.inf.cheonsang.astro.Calendar.daysInMonth(d[0]!!, d[1]!!))
                    ClientConfig.setStandalone(d[0]!!, d[1]!!, d[2]!!, ClientConfig.standaloneLat, ClientConfig.standaloneLon)
            }
            // 장비
            while (Optics.apertureMm.toInt() < pickAp && Optics.stepAperture(1)) {}
            while (Optics.apertureMm.toInt() > pickAp && Optics.stepAperture(-1)) {}
            // 서버 하늘 (OP) — 같은 명령을 서버로
            val conn = mc.connection
            if (conn != null) {
                if (srvReal != KEEP) conn.sendCommand(if (srvReal == "off") "cele realtime off" else "cele realtime $srvReal")
                else {
                    if (srvTime.isNotEmpty() || srvPlace != KEEP) {
                        val tk = srvTime.split(" ").filter { it.isNotEmpty() }.take(5).toMutableList()
                        while (tk.size < 5) tk += "~"
                        conn.sendCommand("cele set " + tk.joinToString(" ") + (if (srvPlace != KEEP) " $srvPlace" else ""))
                    }
                    if (opSet && kotlin.math.abs(srvRate - CheonsangSky.clock.rate) > 1e-9) conn.sendCommand("cele rate " + "%.4f".format(Locale.ROOT, srvRate))
                }
            }
        }
        return b.build()
    }
}
