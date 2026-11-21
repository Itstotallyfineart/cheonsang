package kr.inf.cheonsang.client

import kr.inf.cheonsang.L
import kr.inf.cheonsang.astro.Events
import kr.inf.cheonsang.astro.Moon
import kr.inf.cheonsang.astro.Planet
import kr.inf.cheonsang.astro.RiseSet
import kr.inf.cheonsang.astro.SkyDay
import kr.inf.cheonsang.astro.Sun
import kr.inf.cheonsang.astro.Vec3
import kr.inf.cheonsang.astro.norm180
import kr.inf.cheonsang.client.sky.CheonsangSky
import kr.inf.cheonsang.client.sky.SkyData
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

/**
 * 1.1.0 `/ctonight` (내 화면): 오늘 밤 볼거리 — 해·박명, 달, 행성, 높이 뜨는 심원천체 5개, 유성우.
 * 지금 천상 하늘(날짜·위도·압축률) 그대로 계산. 새벽(해 뜨기 전)에 치면 지금 이어지는 밤.
 */
object Tonight {
    fun register() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            // 1.10.0-dev2: `/cwhen` 의 옛 이름 — 1.10.x 동안 그대로 되지만 자동완성에는 안 보임 (2.0 에서 뺌)
            dispatcher.register(ClientCommands.literal("ctonight").requires { !CWhen.copyingTree() }.executes { c ->
                Safety.run(Safety.TONIGHT, Unit) { report().forEach { line -> c.source.sendFeedback(Component.literal(line)) } }
                1
            }.then(ClientCommands.literal("next").executes { c -> next(c.source, null) }
                .apply { for (k in kr.inf.cheonsang.NextEvents.KINDS) then(ClientCommands.literal(k).executes { c -> next(c.source, k) }) }))
        }
    }

    /** 1.2.1: 다음 천문 현상 (전 `/cele next`) — [날짜] 는 리얼타임 아니고 OP 일 때만 (1.10.1-dev2, 전엔 누구나 보이고 OP 만 됨) */
    private fun next(src: net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource, kind: String?): Int {
        Safety.run(Safety.TONIGHT, Unit) {
            val level = Minecraft.getInstance().level ?: return@run
            val jd0 = CheonsangSky.clock.jd(level.defaultClockTime.toDouble())
            val lat = CheonsangSky.latitude
            val lon = CheonsangSky.longitude
            kr.inf.cheonsang.NextEvents.lines(jd0, lat, lon, kind, CWhen.canSetDate(), kr.inf.cheonsang.SkyServer.placeText(lat, lon),
                Place.baseClock.lon)
                .forEach { src.sendFeedback(it) }
        }
        return 1
    }

    internal fun report(): List<String> {
        val level = Minecraft.getInstance().level ?: return listOf(L.t("cheonsang.tonight.1"))
        val clock = CheonsangSky.clock; val lat = CheonsangSky.latitude
        val tick = level.defaultClockTime
        var day = SkyDay(clock, lat, tick)
        val nowMin = day.minuteOf(tick.toDouble())
        val today = day.sun()
        // 해 뜨기 전이면 어제 저녁부터 이어지는 밤
        if (today.rise != null && nowMin < today.rise!!) day = SkyDay(clock, lat, tick - 24000)
        val clk = InfoCard.timeFmt(day)
        val out = ArrayList<String>()
        val date = clock.civil(day.dayStart + 6000L).toString().substring(0, 10)
        out += L.t("cheonsang.tonight.3", date, kr.inf.cheonsang.SkyServer.placeText(lat, clock.lon)) + (CheonsangSky.realtimeCity?.let { L.t("cheonsang.tonight.2", it.label) } ?: "") +
            L.t("cheonsang.tonight.4").format(AmbientLight.bortle)

        // ── 해·박명 ──
        val sunEve = day.sun(720.0, 1440.0)
        val sunMorn = day.sun(1440.0, 2160.0)
        val set = sunEve.set; val rise = sunMorn.rise
        if (set == null && rise == null && sunEve.always) return out + L.t("cheonsang.tonight.5")
        val dark = RiseSet.find(-18.0, 720.0, 2160.0) { day.sunAlt(it) }
        val darkText = when {
            dark.always -> L.t("cheonsang.tonight.6")
            else -> L.t("cheonsang.tonight.7", dark.set?.let { clk(it) } ?: "-", dark.rise?.let { clk(it) } ?: "-")
        }
        out += L.t("cheonsang.tonight.8", set?.let { clk(it) } ?: "-", rise?.let { clk(it) } ?: "-", darkText)
        val n0 = set ?: 1080.0; val n1 = rise ?: 1800.0

        // ── 달 ──
        val jdMid = day.jd(1440.0)
        val mp = Moon.phase(Moon.position(jdMid), Sun.position(jdMid))
        val phaseName = when (mp.elongation) {
            in 0.0..20.0, in 340.0..360.0 -> L.t("cheonsang.tonight.12"); in 20.0..80.0 -> L.t("cheonsang.tonight.11"); in 80.0..100.0 -> L.t("cheonsang.tonight.10"); in 100.0..170.0 -> L.t("cheonsang.tonight.9")
            in 170.0..190.0 -> L.t("cheonsang.tonight.16"); in 190.0..260.0 -> L.t("cheonsang.tonight.15"); in 260.0..280.0 -> L.t("cheonsang.tonight.14"); else -> L.t("cheonsang.tonight.13")
        }
        val moonDay = day.moon(720.0, 2160.0)
        // 달 없는 어둠: 해 -18° 아래이면서 달이 지평선 아래인 가장 긴 구간
        var bestA = -1.0; var bestB = -1.0; var a = -1.0
        var t = n0
        while (t <= n1) {
            val ok = day.sunAlt(t) < -18 && day.altOfDate(t, Moon.position(day.jd(t)).dir) < 0
            if (ok && a < 0) a = t
            if ((!ok || t + 10 > n1) && a >= 0) { if (t - a > bestB - bestA) { bestA = a; bestB = t }; a = -1.0 }
            t += 10.0
        }
        val moonFree = if (bestA >= 0) L.t("cheonsang.tonight.18", clk(bestA), clk(bestB)) else L.t("cheonsang.tonight.17")
        out += L.t("cheonsang.tonight.19", phaseName).format(mp.illuminated * 100) + RiseSet.text(moonDay, "moon", clk) + moonFree

        // ── 행성 ──
        val naked = CheonsangSky.nakedLimit
        val planets = Planet.entries.mapNotNull { p ->
            var best = -90.0; var bt = n0; var tt = n0
            while (tt <= n1) { val al = day.planetAlt(p, tt); if (al > best) { best = al; bt = tt }; tt += 15.0 }
            if (best < 5) null else {
                val mag = kr.inf.cheonsang.astro.Planets.position(p, day.jd(bt)).mag
                L.t("cheonsang.tonight.21", p.label).format(mag, best, clk(bt)) + if (mag > naked) L.t("cheonsang.tonight.20") else ""
            }
        }
        out += L.t("cheonsang.tonight.23") + planets.ifEmpty { listOf(L.t("cheonsang.tonight.22")) }.joinToString(" · ")

        // ── 심원천체: 7.5등보다 밝고, 어두운 동안 30° 넘게 뜨는 것 중 밝은 5개 ──
        val d0 = dark.set ?: n0; val d1 = dark.rise ?: n1
        val picks = SkyData.dso.filter { it.mag <= 7.5 && it.type != "dark" }.mapNotNull { o ->
            val dir = Vec3.fromSpherical(o.ra, o.dec)
            var best = -90.0; var bt = d0; var tt = d0
            while (tt <= d1) { val al = day.altJ2000(tt, dir); if (al > best) { best = al; bt = tt }; tt += 30.0 }
            if (best < 30) null else Triple(o, best, bt)
        }.sortedBy { it.first.mag }.take(5)
        out += L.t("cheonsang.tonight.24") + picks.ifEmpty { null }?.joinToString(" · ") { (o, alt, tm) ->
            val name = L.name(o.ko, o.en)
            L.t("cheonsang.tonight.25", o.id, if (name.isNotEmpty()) " $name" else "").format(o.mag, alt, clk(tm))
        }.orEmpty().ifEmpty { L.t("cheonsang.tonight.26") }

        // ── 유성우 ──
        val sunLon = Sun.position(jdMid).lon
        val active = SkyData.showers.filter { kotlin.math.abs(norm180(sunLon - it.peakLon)) < 2 * it.sigma }
        out += if (active.isNotEmpty()) L.t("cheonsang.tonight.27") + active.joinToString(" · ") { s ->
            val days = norm180(s.peakLon - sunLon) / 0.9856
            val peak = clock.civil(day.dayStart + 6000L + (days / clock.rate * 24000).toLong()).toString().substring(5, 10)
            L.t("cheonsang.tonight.28", s.label, peak).format(s.zhr)
        } else {
            val nx = Events.meteor(jdMid, lat, clock.lon, SkyData.showers.map { Events.Shower(it.ko, it.peakLon, it.ra, it.dec, it.zhr, it.en1) })
            L.t("cheonsang.tonight.30") + (nx?.let { L.t("cheonsang.tonight.29") + it.text.substringBefore(" |") } ?: "")
        }
        return out
    }
}
