package kr.inf.cheonsang.client

import kr.inf.cheonsang.L
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import kr.inf.cheonsang.Cheonsang
import kr.inf.cheonsang.SkyServer
import kr.inf.cheonsang.astro.SkyClock
import kr.inf.cheonsang.client.sky.CheonsangSky
import kr.inf.cheonsang.client.sky.Meteors
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files

/** 천상 — 클라이언트 시작점: 설정 읽기, 서버 하늘 동기화 받기, 망원경 */
object CheonsangClient : ClientModInitializer {
    override fun onInitializeClient() {
        ClientConfig.load()
        ClientConfig.applyStandalone()
        TelescopeView.register()
        TelescopeHud.register()
        FindGuide.register()
        TelescopeFilter.register()
        Bench.register()
        Tonight.register()
        CWhen.register()
        LogBook.register()
        PoseSync.register()
        AmbientLight.register()
        // 1.5.0 개발용: 가시성 모델 효과별 켜고 끄기 (저장 안 함)
        net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register { d, _ ->
            d.register(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("cvis")
                .executes { c -> c.source.sendFeedback(net.minecraft.network.chat.Component.literal(Visibility.state())); 1 }
                .apply {
                    for (fx in Visibility.Fx.entries) for (v in listOf("on", "off")) then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal(fx.id)
                        .then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal(v).executes { c ->
                            Visibility.set(fx, v == "on")
                            c.source.sendFeedback(net.minecraft.network.chat.Component.literal(L.t("cheonsang.cheonsangclient.1", fx.label, if (v == "on") L.t("cheonsang.cheonsangclient.5") else L.t("cheonsang.cheonsangclient.4")))); 1
                        }))
                })
        }
        // 1.9.0-dev2: 개발판(-dev)에서만 `/caurora [on|off|0~1]` — 오로라 보기 (11-13 지율님)
        val ver = FabricLoader.getInstance().getModContainer(Cheonsang.MOD_ID).map { it.metadata.version.friendlyString }.orElse("")
        if ("-dev" in ver) net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register { d, _ ->
            fun say(c: com.mojang.brigadier.context.CommandContext<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource>) =
                c.source.sendFeedback(net.minecraft.network.chat.Component.literal(AmbientLight.debugAurora?.let { L.t("cheonsang.m.aurora_on", "%.2f".format(it)) } ?: L.t("cheonsang.m.aurora_off")))
            d.register(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("caurora")
                .executes { c -> AmbientLight.debugAurora = if (AmbientLight.debugAurora == null) 1.0 else null; say(c); 1 }
                .then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("on").executes { c -> AmbientLight.debugAurora = 1.0; say(c); 1 })
                .then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("off").executes { c -> AmbientLight.debugAurora = null; say(c); 1 })
                .then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument("strength", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0.0, 1.0)).executes { c ->
                    AmbientLight.debugAurora = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c, "strength"); say(c); 1
                }))
            // 2.0.3-dev1: 시연·촬영용 개발판 명령 (저장 안 함) — /cmeteor · /cseeing · /cdark · /chud
            fun msg(c: com.mojang.brigadier.context.CommandContext<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource>, s: String) =
                c.source.sendFeedback(net.minecraft.network.chat.Component.literal(s))
            fun meteorText(): String = kr.inf.cheonsang.client.sky.Meteors.let { m ->
                m.debugPerMin?.let { (L.t("cheonsang.dev.meteor_on", "%.0f".format(it)) + (if (m.debugFire) " " + L.t("cheonsang.dev.fire") else "") + (m.debugShower?.let { s -> " $s" } ?: "")).trim() } ?: L.t("cheonsang.dev.meteor_off") }
            val M = kr.inf.cheonsang.client.sky.Meteors
            d.register(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("cmeteor")
                .executes { c -> if (M.debugPerMin == null) M.debugPerMin = 60.0 else { M.debugPerMin = null; M.debugFire = false; M.debugShower = null }; msg(c, meteorText()); 1 }
                .then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("off").executes { c -> M.debugPerMin = null; M.debugFire = false; M.debugShower = null; msg(c, meteorText()); 1 })
                .then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("fire").executes { c -> M.debugFire = !M.debugFire; if (M.debugPerMin == null) M.debugPerMin = 20.0; msg(c, meteorText()); 1 })
                .then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument("perMin", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(1.0, 240.0)).executes { c ->
                    M.debugPerMin = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c, "perMin"); msg(c, meteorText()); 1 })
                .then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument("shower", com.mojang.brigadier.arguments.StringArgumentType.word())
                    .suggests { _, b -> kr.inf.cheonsang.client.sky.SkyData.showers.forEach { b.suggest(it.id) }; b.buildFuture() }
                    .executes { c ->
                        val id = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "shower")
                        if (kr.inf.cheonsang.client.sky.SkyData.showers.none { it.id.equals(id, true) }) { c.source.sendError(net.minecraft.network.chat.Component.literal(L.t("cheonsang.dev.no_shower", id))); return@executes 0 }
                        M.debugShower = id.uppercase(); if (M.debugPerMin == null) M.debugPerMin = 60.0; msg(c, meteorText()); 1 }))
            fun seeingText() = Optics.debugSeeing?.let { L.t("cheonsang.dev.seeing_on", "%.1f".format(it)) } ?: L.t("cheonsang.dev.seeing_off", "%.1f".format(Optics.nightSeeing()))
            d.register(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("cseeing")
                .executes { c -> msg(c, seeingText()); 1 }
                .then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("off").executes { c -> Optics.debugSeeing = null; msg(c, seeingText()); 1 })
                .then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument("arcsec", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0.3, 6.0)).executes { c ->
                    Optics.debugSeeing = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c, "arcsec"); msg(c, seeingText()); 1 }))
            fun darkText() = if (AmbientLight.debugDark) L.t("cheonsang.dev.dark_on") else L.t("cheonsang.dev.dark_off")
            d.register(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("cdark")
                .executes { c -> AmbientLight.debugDark = !AmbientLight.debugDark; msg(c, darkText()); 1 }
                .then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("on").executes { c -> AmbientLight.debugDark = true; msg(c, darkText()); 1 })
                .then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("off").executes { c -> AmbientLight.debugDark = false; msg(c, darkText()); 1 }))
            fun hudText() = if (TelescopeHud.hideAll) L.t("cheonsang.dev.hud_off") else L.t("cheonsang.dev.hud_on")
            d.register(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("chud")
                .executes { c -> TelescopeHud.hideAll = !TelescopeHud.hideAll; msg(c, hudText()); 1 }
                .then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("off").executes { c -> TelescopeHud.hideAll = true; msg(c, hudText()); 1 })
                .then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("on").executes { c -> TelescopeHud.hideAll = false; msg(c, hudText()); 1 }))
        }
        // 서버가 보낸 지금 차원의 하늘 (시계·압축률·위도)
        ClientPlayNetworking.registerGlobalReceiver(SkyServer.TYPE) { sky, _ ->
            // 1.3.0 TIME-001: 서버 시각·위치가 바뀌면 보간·캐시 즉시 버림 (Place.apply 가 확인)
            // 1.9.0-dev3: 서버 하늘 = 월드 기본, 내 관측 위치(/cplace)를 얹음
            CheonsangSky.serverSkyData = sky
            CheonsangSky.serverSky = true
            CheonsangSky.realtimeCity = kr.inf.cheonsang.RealTime.CITIES.firstOrNull { it.id == sky.city }
            Place.setBase(sky.clock, sky.lat)
        }
        ClientPlayNetworking.registerGlobalReceiver(SkyServer.GEN_TYPE) { g, _ -> Place.onGen(g.gen) }   // 2.0.0-rc1
        Place.register()
        // 서버를 나가면 기본 하늘로 (모드 없는 서버에서도 혼자 그릴 수 있게)
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            // 1.3.1 클라이언트 단독: 서버에 천상이 없으면 설정 화면에서 정한 날짜·위치 (없으면 기본 2026-08-11·0°·0°, 위도 이동 없음)
            CheonsangSky.serverSky = false
            CheonsangSky.serverSkyData = null
            Place.forget()
            ClientConfig.applyStandalone()
            CheonsangSky.realtimeCity = null
            Safety.reset()
            AmbientLight.reset()
            TelescopeHud.clearSelection()
        }
    }
}

/**
 * 내 화면 설정 (config/cheonsang/client.json) — 하늘을 어떻게 보이게 할지만. 날짜·위도는 서버가 정함.
 */
object ClientConfig {
    private val file = FabricLoader.getInstance().configDir.resolve(Cheonsang.MOD_ID).resolve("client.json")

    fun load() {
        val gson = GsonBuilder().setPrettyPrinting().create()
        try {
            if (Files.exists(file)) {
                val o = gson.fromJson(Files.readString(file), JsonObject::class.java)
                o["enabled"]?.let { CheonsangSky.enabled = it.asBoolean }
                o["nakedEyeLimit"]?.let { CheonsangSky.nakedLimit = it.asFloat.coerceIn(4.0f, 6.5f) }
                // 1.2.2: 해·달 실제 크기 고정 — 옛 client.json 의 discScale(기본 3)은 읽지 않음
                o["meteors"]?.let { Meteors.scale = it.asDouble.coerceIn(0.0, 5.0) }
                o["guideShown"]?.let { guideShown = it.asBoolean }
                o.getAsJsonObject("standalone")?.let { sa ->
                    sa["jd0"]?.let { standaloneJd0 = it.asDouble }
                    sa["lat"]?.let { standaloneLat = it.asDouble.coerceIn(-90.0, 90.0) }
                    sa["lon"]?.let { standaloneLon = it.asDouble.coerceIn(-180.0, 180.0) }
                }
                o.getAsJsonObject("display")?.let { d ->
                    d["lines"]?.let { TelescopeFilter.lines = it.asBoolean }
                    d["names"]?.let { TelescopeFilter.names = it.asBoolean }
                    d["bodies"]?.let { TelescopeFilter.bodies = it.asBoolean }
                    d["aim"]?.let { TelescopeFilter.aim = it.asBoolean }
                    d["photo"]?.let { TelescopeFilter.photo = it.asBoolean }
                    d["grid"]?.let { TelescopeFilter.grid = it.asBoolean }
                    d["altaz"]?.let { TelescopeFilter.altaz = it.asBoolean }
                    d["bounds"]?.let { TelescopeFilter.bounds = it.asBoolean }
                    d["card"]?.let { TelescopeFilter.card = it.asBoolean }
                    d["art"]?.let { TelescopeFilter.art = it.asBoolean }
                    d["korean"]?.let { TelescopeFilter.korean = it.asBoolean }
                    d["visualDay"]?.let { TelescopeFilter.visualDay = it.asBoolean }
                    d["cardCorner"]?.let { TelescopeFilter.cardCorner = it.asInt.coerceIn(0, 3) }
                    d["cardOpacity"]?.let { TelescopeFilter.cardOpacity = it.asFloat.coerceIn(0.1f, 1f) }
                    d["cardAltAz"]?.let { TelescopeFilter.cardAltAz = it.asBoolean }
                    d["cardRaDec"]?.let { TelescopeFilter.cardRaDec = it.asBoolean }
                    d["cardRiseSet"]?.let { TelescopeFilter.cardRiseSet = it.asBoolean }
                    d["uiScale"]?.let { TelescopeFilter.uiScale = it.asFloat.coerceIn(0.5f, 2f) }
                }
            } else {
                val o = JsonObject()
                o.addProperty(L.t("cheonsang.cheonsangclient.3"), L.t("cheonsang.cheonsangclient.2"))
                o.addProperty("enabled", CheonsangSky.enabled)
                o.addProperty("nakedEyeLimit", CheonsangSky.nakedLimit)
                o.addProperty("meteors", Meteors.scale)
                Files.createDirectories(file.parent)
                Files.writeString(file, gson.toJson(o))
            }
        } catch (e: Exception) {
            Cheonsang.LOG.warn("Cheonsang: failed to read client.json — using defaults", e)
        }
    }

    /** 1.7.1 U6: 망원경 조작 안내를 이미 보여 줬는지 */
    var guideShown = false
        private set

    fun markGuideShown() {
        guideShown = true
        val gson = GsonBuilder().setPrettyPrinting().create()
        try {
            val o = if (Files.exists(file)) gson.fromJson(Files.readString(file), JsonObject::class.java) ?: JsonObject() else JsonObject()
            o.addProperty("guideShown", true)
            Files.createDirectories(file.parent)
            Files.writeString(file, gson.toJson(o))
        } catch (e: Exception) {
            Cheonsang.LOG.warn("Cheonsang: failed to save client.json", e)
        }
    }

    // ── 1.3.1 클라이언트 단독 하늘 (서버에 천상이 없을 때) — client.json "standalone": jd0(월드 0틱의 율리우스일)·lat·lon ──
    var standaloneJd0 = SkyClock.DEFAULT_EPOCH_JD
    var standaloneLat = SkyClock.DEFAULT_LAT
    var standaloneLon = SkyClock.DEFAULT_LON

    fun applyStandalone() {
        if (CheonsangSky.serverSky) return
        CheonsangSky.realtimeCity = null
        Place.setBase(SkyClock(0L, standaloneJd0, 1.0, standaloneLon), standaloneLat)
    }

    /** 날짜(현지)·위치를 정함 — 지금 월드 시각은 그대로, 그 시각이 그 날짜가 되게 */
    fun setStandalone(y: Int, m: Int, d: Int, lat: Double, lon: Double) {
        val tick = net.minecraft.client.Minecraft.getInstance().level?.defaultClockTime ?: 0L
        val probe = SkyClock(0L, 0.0, 1.0, lon)
        val jd = kr.inf.cheonsang.astro.Calendar.julianDayLocal(y, m, d, probe.localHours(tick.toDouble()), lon / 15.0)
        standaloneJd0 = jd - tick / SkyClock.TICKS_PER_DAY
        standaloneLat = lat; standaloneLon = lon
        applyStandalone()
        val gson = GsonBuilder().setPrettyPrinting().create()
        try {
            val o = if (Files.exists(file)) gson.fromJson(Files.readString(file), JsonObject::class.java) ?: JsonObject() else JsonObject()
            o.add("standalone", JsonObject().apply { addProperty("jd0", standaloneJd0); addProperty("lat", lat); addProperty("lon", lon) })
            Files.createDirectories(file.parent)
            Files.writeString(file, gson.toJson(o))
        } catch (e: Exception) {
            Cheonsang.LOG.warn("Cheonsang: failed to save client.json", e)
        }
    }

    /** 망원경 표시 필터만 저장 (다른 값은 그대로 둠, 0.1.12) */
    fun saveDisplay() {
        val gson = GsonBuilder().setPrettyPrinting().create()
        try {
            val o = if (Files.exists(file)) gson.fromJson(Files.readString(file), JsonObject::class.java) ?: JsonObject() else JsonObject()
            o.add("display", JsonObject().apply {
                addProperty("lines", TelescopeFilter.lines); addProperty("names", TelescopeFilter.names)
                addProperty("bodies", TelescopeFilter.bodies); addProperty("aim", TelescopeFilter.aim); addProperty("photo", TelescopeFilter.photo)
                addProperty("grid", TelescopeFilter.grid); addProperty("altaz", TelescopeFilter.altaz)
                addProperty("bounds", TelescopeFilter.bounds); addProperty("card", TelescopeFilter.card); addProperty("art", TelescopeFilter.art); addProperty("korean", TelescopeFilter.korean)
                addProperty("visualDay", TelescopeFilter.visualDay)
                addProperty("cardCorner", TelescopeFilter.cardCorner); addProperty("cardOpacity", TelescopeFilter.cardOpacity)
                addProperty("cardAltAz", TelescopeFilter.cardAltAz); addProperty("cardRaDec", TelescopeFilter.cardRaDec)
                addProperty("cardRiseSet", TelescopeFilter.cardRiseSet); addProperty("uiScale", TelescopeFilter.uiScale)
            })
            Files.createDirectories(file.parent)
            Files.writeString(file, gson.toJson(o))
        } catch (e: Exception) {
            Cheonsang.LOG.warn("Cheonsang: failed to save client.json", e)
        }
    }
}
