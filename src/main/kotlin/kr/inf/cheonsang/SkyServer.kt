package kr.inf.cheonsang

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.serialization.Codec
import kr.inf.cheonsang.astro.Calendar
import kr.inf.cheonsang.astro.Events
import kr.inf.cheonsang.astro.SkyDay
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.HoverEvent
import net.minecraft.ChatFormatting
import kr.inf.cheonsang.astro.SkyClock
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.datafix.DataFixTypes
import net.minecraft.world.level.saveddata.SavedData
import net.minecraft.world.level.saveddata.SavedDataType
import java.nio.file.Files
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.math.abs
import kotlin.math.floor

/**
 * 하늘 상태 (서버가 원본, ARCH-001) — 차원마다 [시계 기준점·압축률·위도·경도]를 월드 저장 데이터에 두고 접속자에게 동기화.
 * 서버에 이 모드가 없으면 클라이언트는 기본값(2026-08-11, 0°·0°, 1배)으로 혼자 그린다.
 * 1.9.0-dev3: 여기 위치 = 월드 기본 위치. 사람마다 /cplace 로 자기 관측 위치 (클라이언트, 마크 시계 = 그 위치 현지 시각)
 * 1.3.0: 경도 추가 (마크 시계 = 그 경도의 지방평균시). 기준 있는 옛 월드는 경도 135° 로 이어 붙임 (C-04)
 *
 * 명령: /cele · set <년> <월> <일> <시> <분> <위도> <경도> (생략·~ = 유지, 위도 자리에 도시 이름) · set today · rate · reset · realtime [도시|off]
 */
object SkyServer {
    /** 서버 → 클라이언트: 지금 있는 차원의 하늘 */
    /** city = 실시간 도시 id (1.1.3, 꺼져 있으면 "") — 클라이언트가 그 도시 시계 시각을 같이 보여 줌 */
    /** lat·lon = 월드 기본 위치 (1.9.0-dev3: 사람마다 /cplace 로 자기 관측 위치를 정함 — 클라이언트) */
    data class Sky(val epochTick: Long, val epochJd: Double, val rate: Double, val lat: Double, val lon: Double, val city: String = "") : CustomPacketPayload {
        override fun type() = TYPE
        val clock get() = SkyClock(epochTick, epochJd, rate, lon)
    }
    /** 1.3.0: 경도가 들어가 패킷 형식이 바뀜 → 이름도 바꿈 (옛 클라이언트와 섞여도 엉뚱하게 읽지 않게). 1.9.0-dev1·2 sky3(위도 이동) → dev3 sky4 */
    val TYPE = CustomPacketPayload.Type<Sky>(Cheonsang.id("sky4"))
    private val CODEC: StreamCodec<RegistryFriendlyByteBuf, Sky> = StreamCodec.composite(
        ByteBufCodecs.VAR_LONG, Sky::epochTick, ByteBufCodecs.DOUBLE, Sky::epochJd,
        ByteBufCodecs.DOUBLE, Sky::rate, ByteBufCodecs.DOUBLE, Sky::lat, ByteBufCodecs.DOUBLE, Sky::lon, ByteBufCodecs.STRING_UTF8, Sky::city, ::Sky,
    )

    /**
     * 2.0.0-rc1 (11-14 19:25 지율님 "cele set 으로 위치를 바꾸면 모든 사람의 cplace 를 초기화"): 월드 기본 위치 번호.
     * /cele set 등으로 위도·경도가 바뀔 때마다 +1 → 클라이언트가 전에 받은 번호와 다르면 자기 /cplace 를 풂 (접속 안 해 있던 사람도 다음 접속 때)
     * 따로 된 패킷이라 옛 클라이언트(못 받음)에는 안 보냄
     */
    data class PlaceGen(val gen: Int) : CustomPacketPayload { override fun type() = GEN_TYPE }
    val GEN_TYPE = CustomPacketPayload.Type<PlaceGen>(Cheonsang.id("placegen"))
    private val GEN_CODEC: StreamCodec<RegistryFriendlyByteBuf, PlaceGen> = StreamCodec.composite(ByteBufCodecs.VAR_INT, PlaceGen::gen, ::PlaceGen)
    private fun genOf(level: ServerLevel): Int = data(level.server).skies["${dimId(level)}#gen"]?.firstOrNull()?.toInt() ?: 0
    private fun sendGen(p: ServerPlayer, level: ServerLevel) { if (ServerPlayNetworking.canSend(p, GEN_TYPE)) ServerPlayNetworking.send(p, PlaceGen(genOf(level))) }

    /**
     * 저장: 차원 ID → [기준틱, 기준 율리우스일, 압축률, 위도] (+ 리얼타임 켜져 있으면 [도시 번호, 켜기 전 기준틱·율리우스일·압축률·위도])
     * 1.3.0: "차원 ID#lon" → [경도, 켜기 전 경도(리얼타임)] — 이 키가 없는 기준 = 1.2.x 월드 → 경도 135°
     */
    class Data(val skies: MutableMap<String, List<Double>> = HashMap()) : SavedData()
    private val DATA = SavedDataType(
        Cheonsang.id("sky"), ::Data,
        Codec.unboundedMap(Codec.STRING, Codec.DOUBLE.listOf()).xmap({ Data(HashMap(it)) }, { it.skies }),
        DataFixTypes.SAVED_DATA_COMMAND_STORAGE, // 26.3은 null 불가 — 영향 없는 기본 타입
    )
    private fun data(server: MinecraftServer) = server.dataStorage.computeIfAbsent(DATA)

    const val NORTH = 30.0
    const val SOUTH = -30.0

    // ── 서버 설정 (config/cheonsang/server.json) ──
    // 1.3.0: 새 키 lat·lon (옛 키 latitude·dimensions 는 1.2.x 기본 −30 이 적혀 있어 무시 — 새 월드 기본 0°·0°)
    private var defaultRate = SkyClock.DEFAULT_RATE
    private var defaultLat = SkyClock.DEFAULT_LAT
    private var defaultLon = SkyClock.DEFAULT_LON
    fun loadConfig() {
        val file = FabricLoader.getInstance().configDir.resolve(Cheonsang.MOD_ID).resolve("server.json")
        val gson = GsonBuilder().setPrettyPrinting().create()
        try {
            if (Files.exists(file)) {
                val o = gson.fromJson(Files.readString(file), JsonObject::class.java)
                o["rate"]?.let { defaultRate = it.asDouble.coerceIn(0.01, 1000.0) }
                // 1.10.1-dev4: 옛 버전이 적어 둔 옛 기본값 3.261 은 지금 기본값 1.0 으로 한 번 옮김 (11-14 지율님 "기본 압축률 왜 아직도 3")
                if (o["rateMigrated"] == null) {
                    if (kotlin.math.abs(defaultRate - 3.261) < 1e-6) {
                        defaultRate = SkyClock.DEFAULT_RATE
                        o.addProperty("rate", defaultRate)
                        Cheonsang.LOG.info("Cheonsang: server.json rate 3.261 (old default) -> 1.0")
                    }
                    o.addProperty("rateMigrated", true)
                    Files.writeString(file, gson.toJson(o))
                }
                o["lat"]?.let { defaultLat = it.asDouble.coerceIn(-90.0, 90.0) }
                o["lon"]?.let { defaultLon = it.asDouble.coerceIn(-180.0, 180.0) }
                o["vanillaMoon"]?.let { VanillaSync.moonOn = it.asBoolean }
                o["vanillaNight"]?.let { VanillaSync.nightOn = it.asBoolean }
                // 1.9.0-dev3: dev1·2 의 latTravel·blocksPerDegree 는 읽지 않음 (위도 이동 → /cplace)
            } else {
                val o = JsonObject()
                o.addProperty("_note", "rate = sky days per game day (1.0 = real speed), lat/lon = default latitude/longitude for new worlds (S/W negative), vanillaMoon = vanilla moon phase & slimes follow the Cheonsang Moon, vanillaNight = mobs/night follow the real Sun altitude (-6°) (default off)")
                o.addProperty("rate", defaultRate)
                o.addProperty("lat", defaultLat)
                o.addProperty("lon", defaultLon)
                o.addProperty("vanillaMoon", VanillaSync.moonOn)
                o.addProperty("vanillaNight", VanillaSync.nightOn)
                Files.createDirectories(file.parent)
                Files.writeString(file, gson.toJson(o))
            }
        } catch (e: Exception) {
            Cheonsang.LOG.warn("Cheonsang: failed to read server.json — using defaults", e)
        }
    }

    private fun dimId(level: ServerLevel) = level.dimension().identifier().toString()

    fun get(level: ServerLevel): Sky {
        val id = dimId(level)
        val d = data(level.server)
        val v = d.skies[id]
        if (v != null && v.size >= 4) return Sky(v[0].toLong(), v[1], v[2], v[3], lonOf(level))
        // 1.3.0: 기준이 없을 때 — 이미 하루 넘게 플레이한 월드면 1.2.x 기본(2019-03-21·남위 30°)에 경도 135° 로 이어 붙이고,
        // 새 월드면 새 기본(1.9.0: 2026-08-11·0°·0°). 한 번 정하면 저장 (다음부터 판단하지 않음)
        val old = level.server.overworld().gameTime > 24000L
        val sky = if (old) Sky(0L, SkyClock.OLD_EPOCH_JD, defaultRate, SkyClock.OLD_LAT, SkyClock.OLD_LON)
            else Sky(0L, SkyClock.DEFAULT_EPOCH_JD, defaultRate, defaultLat, defaultLon)
        d.skies[id] = listOf(sky.epochTick.toDouble(), sky.epochJd, sky.rate, sky.lat)
        d.skies["$id#lon"] = listOf(sky.lon)
        d.setDirty()
        Cheonsang.LOG.info("Cheonsang: {} sky reference set ({})", id, if (old) "continuing old world, longitude 135°" else "new world default")
        return sky
    }

    /** 경도 — "#lon" 키가 없으면 1.2.x 기준이 있는 월드 → 135° (C-04) */
    private fun lonOf(level: ServerLevel): Double = data(level.server).skies["${dimId(level)}#lon"]?.firstOrNull() ?: SkyClock.OLD_LON

    /** 보내는 하늘 = 저장된 하늘 + 실시간 도시 */
    private fun outgoing(level: ServerLevel, sky: Sky) = sky.copy(city = realtimeCity(level)?.id ?: "")

    /** 리얼타임 도시 (꺼져 있으면 null) */
    fun realtimeCity(level: ServerLevel): RealTime.City? =
        data(level.server).skies[dimId(level)]?.takeIf { it.size >= 9 }?.let { RealTime.CITIES.getOrNull(it[4].toInt()) }

    private fun put(level: ServerLevel, sky: Sky) {
        val d = data(level.server)
        val old = d.skies[dimId(level)]
        val rt = if (old != null && old.size >= 9) old.subList(4, 9).toList() else emptyList()
        d.skies[dimId(level)] = listOf(sky.epochTick.toDouble(), sky.epochJd, sky.rate, sky.lat) + rt
        val oldLon = d.skies["${dimId(level)}#lon"]
        d.skies["${dimId(level)}#lon"] = listOf(sky.lon) + (oldLon?.drop(1) ?: emptyList())
        // 2.0.0-rc1: 위도·경도가 바뀌면 위치 번호 +1 (날짜·시각·압축률만 바뀌면 그대로)
        val moved = old == null || old.size < 4 || abs(old[3] - sky.lat) > 1e-9 || abs((oldLon?.firstOrNull() ?: sky.lon) - sky.lon) > 1e-9
        if (moved) d.skies["${dimId(level)}#gen"] = listOf((genOf(level) + 1).toDouble())
        d.setDirty()
        val out = outgoing(level, sky)
        for (p in level.players()) { ServerPlayNetworking.send(p, out); sendGen(p, level) }
    }

    private fun sync(p: ServerPlayer) = (p.level() as ServerLevel).let { l -> ServerPlayNetworking.send(p, outgoing(l, get(l))); sendGen(p, l) }

    private val pending = HashSet<UUID>()

    fun register() {
        loadConfig()
        PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC)
        PayloadTypeRegistry.clientboundPlay().register(GEN_TYPE, GEN_CODEC)
        // JOIN 시점엔 아직 플레이어 목록에 없어서 다음 틱에 보냄
        ServerPlayConnectionEvents.JOIN.register { h, _, _ -> pending += h.player.uuid }
        ServerTickEvents.END_SERVER_TICK.register { server ->
            if (pending.isEmpty()) return@register
            pending.removeAll { id -> server.playerList.getPlayer(id)?.also { sync(it) } != null }
        }
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register { player, _, _ -> sync(player) }
        CommandRegistrationCallback.EVENT.register { d, _, _ -> d.register(command()) }
        // 리얼타임: 5초마다 월드 시계를 현실 시각에 맞춤 (서버 렉·침대로 밤 넘기기 되돌림)
        ServerTickEvents.END_SERVER_TICK.register { server ->
            if (server.tickCount % 100 != 0) return@register
            for (level in server.allLevels) {
                val c = realtimeCity(level) ?: continue
                syncRealtime(level, c, false)
            }
        }
    }

    private fun clockOf(level: ServerLevel) = level.dimensionType().defaultClock().orElse(null)

    /** 1.3.0: 월드 시계를 그 도시의 지방평균시로, 하늘 = 그 도시 위치·지금 현실 시각·압축률 1 (C-09: 경도 예외 계산 없음) */
    private fun syncRealtime(level: ServerLevel, c: RealTime.City, force: Boolean) {
        val clock = clockOf(level) ?: return
        val cm = level.server.clockManager()
        val inst = cm.getInstance(clock)
        if (inst.rate() != RealTime.RATE) cm.setRate(clock, RealTime.RATE)
        if (inst.isPaused) cm.setPaused(clock, false)
        val jd = RealTime.jdNow()
        val now = inst.totalTicks()
        val target = RealTime.targetTotal(now, jd, c)
        if (force || abs(target - now) >= 2) cm.setTotalTicks(clock, target)
        val s = get(level)
        if (force || abs(s.clock.jd(target.toDouble()) - jd) * 86400.0 > 30.0 || s.rate != 1.0 || abs(s.lat - c.lat) > 1e-6 || abs(s.lon - c.lon) > 1e-6)
            put(level, Sky(target, jd, 1.0, c.lat, c.lon))
    }

    private fun realtimeOn(cx: CommandContext<CommandSourceStack>, c: RealTime.City): Int { return L.with(lang(cx)) {
        val level = cx.source.level
        if (clockOf(level) == null) { cx.source.sendFailure(Component.literal(L.t("cheonsang.cmd.noclock"))); return 0 }
        val d = data(level.server)
        val id = dimId(level)
        val cur = get(level)
        val wasOn = d.skies[id]?.let { it.size >= 9 } == true
        val prev = d.skies[id]?.takeIf { it.size >= 9 }?.subList(5, 9)?.toList()
            ?: listOf(cur.epochTick.toDouble(), cur.epochJd, cur.rate, cur.lat)
        val prevLon = if (wasOn) d.skies["$id#lon"]?.getOrNull(1) ?: cur.lon else cur.lon
        d.skies[id] = listOf(cur.epochTick.toDouble(), cur.epochJd, cur.rate, cur.lat, RealTime.CITIES.indexOf(c).toDouble()) + prev
        d.skies["$id#lon"] = listOf(cur.lon, prevLon)
        d.setDirty()
        syncRealtime(level, c, true)
        return reply(cx, L.t("cheonsang.cmd.rt_on", c.label, placeText(c.lat, c.lon), RealTime.localText(c)))
    } }

    private fun realtimeOff(cx: CommandContext<CommandSourceStack>): Int { return L.with(lang(cx)) {
        val level = cx.source.level
        val d = data(level.server)
        val id = dimId(level)
        val v = d.skies[id]
        if (v == null || v.size < 9) { cx.source.sendFailure(Component.literal(L.t("cheonsang.cmd.rt_isoff"))); return 0 }
        d.skies[id] = v.subList(5, 9).toList()
        d.skies["$id#lon"]?.getOrNull(1)?.let { d.skies["$id#lon"] = listOf(it) }
        d.setDirty()
        clockOf(level)?.let { level.server.clockManager().setRate(it, 1.0f) }
        for (p in level.players()) sync(p)
        val s = get(level)
        return reply(cx, L.t("cheonsang.cmd.rt_off", s.clock.civil(level.defaultClockTime)).format(s.rate, placeText(s.lat, s.lon)))
    } }

    /** 실시간 켜져 있으면 날짜·압축률·위치 바꾸기 막음 */
    private fun blockedByRealtime(cx: CommandContext<CommandSourceStack>): Boolean { return L.with(lang(cx)) {
        if (realtimeCity(cx.source.level) == null) return false
        cx.source.sendFailure(Component.literal(L.t("cheonsang.cmd.rt_block")))
        return true
    } }

    /** /cele set 에서 쓸 수 있는 장소 이름 (리얼타임 도시 + 그리니치) */
    val PLACES: List<RealTime.City> by lazy { RealTime.CITIES + RealTime.City("greenwich", "그리니치", 51.48, 0.0, "Europe/London") }

    // ── 명령 ──
    private fun command() = Commands.literal("cele")
        .executes { time(it) }
        .then(Commands.literal("set").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .then(Commands.literal("today").executes {
                val level = it.source.level
                val s = get(level)
                val t = Calendar.toLocal(RealTime.jdNow(), s.lon / 15.0)
                setCmd(it, "${t.year} ${t.month} ${t.day}")
            })
            .then(Commands.argument("args", StringArgumentType.greedyString())
                .suggests { _, b ->
                    val rem = b.remaining
                    val words = rem.split(" ")
                    if (words.size >= 6) {
                        val prefix = rem.substring(0, rem.lastIndexOf(' ') + 1)
                        val q = words.last().lowercase()
                        PLACES.filter { p -> p.id.startsWith(q) }.forEach { p -> b.suggest(prefix + p.id) }
                    }
                    b.buildFuture()
                }
                .executes { setCmd(it, StringArgumentType.getString(it, "args")) }))
        .then(Commands.literal("rate").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .then(Commands.argument("rate", DoubleArgumentType.doubleArg(0.01, 1000.0)).executes {
                if (blockedByRealtime(it)) return@executes 0
                L.with(lang(it)) {
                val level = it.source.level
                val tick = level.defaultClockTime
                val r = DoubleArgumentType.getDouble(it, "rate")
                val s = get(level)
                val c = s.clock.withRate(tick, r)
                put(level, Sky(c.epochTick, c.epochJd, r, s.lat, s.lon))
                reply(it, L.t("cheonsang.cmd.rate").format(r, 365.2422 / r)) }
            }))
        .then(Commands.literal("realtime").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .executes { realtimeOn(it, RealTime.DEFAULT) }
            .then(Commands.literal("off").executes { realtimeOff(it) })
            .apply { for (c in RealTime.CITIES) then(Commands.literal(c.id).executes { realtimeOn(it, c) }) })
        .then(Commands.literal("reset").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes {
            if (blockedByRealtime(it)) return@executes 0
            L.with(lang(it)) {
            val level = it.source.level
            put(level, Sky(0L, SkyClock.DEFAULT_EPOCH_JD, defaultRate, defaultLat, defaultLon))
            val d = get(level)
            reply(it, L.t("cheonsang.cmd.reset").format(d.rate, placeText(d.lat, d.lon))) }
        })

    private fun time(c: CommandContext<CommandSourceStack>): Int { return L.with(lang(c)) {
        val level = c.source.level
        val s = get(level)
        realtimeCity(level)?.let { city ->
            return reply(c, L.t("cheonsang.cmd.rt_time", city.label, RealTime.localText(city), s.clock.civil(level.defaultClockTime), placeText(s.lat, s.lon)))
        }
        val now = s.clock.civil(level.defaultClockTime)
        reply(c, L.t("cheonsang.cmd.time", now).format(s.rate, 365.2422 / s.rate, placeText(s.lat, s.lon)))
        // 1.9.0-dev3: 서버는 월드 기본 위치만 앎 — 내 관측 위치는 /cplace
        return reply(c, sunMoonLine(s, level.defaultClockTime) + L.t("cheonsang.cmd.world_place"))
    } }

    /** 1.1.0: "일출 06:52 · 남중 11:50 (38°) · 일몰 16:48 | 월출 … · 월몰 …" */
    private fun sunMoonLine(s: Sky, tick: Long): String {
        val day = SkyDay(s.clock, s.lat, tick)
        return kr.inf.cheonsang.astro.RiseSet.text(day.sun(), "sun") + " | " + kr.inf.cheonsang.astro.RiseSet.text(day.moon(), "moon")
    }

    /**
     * 1.3.0 `/cele set <년> <월> <일> <시> <분> <위도> <경도>` — 뒤쪽 생략, "~" = 지금 값 유지, 위도 자리에 장소 이름(seoul 등).
     * 시각 = 그 장소 지방평균시. 시·분을 적으면 월드 시계도 그 시각으로 (지금 마크 날 안), 안 적으면 월드 시계 그대로.
     * 위치만 바꾸면 마크 시계·현지 날짜 유지 → 세계시가 경도만큼 밀림
     */
    private fun setCmd(c: CommandContext<CommandSourceStack>, text: String): Int { return L.with(lang(c)) {
        if (blockedByRealtime(c)) return 0
        val level = c.source.level
        val clock = clockOf(level)
        if (clock == null) { c.source.sendFailure(Component.literal(L.t("cheonsang.cmd.noclock"))); return 0 }
        val s = get(level)
        val now = level.defaultClockTime
        val tok = text.trim().split(" ").filter { it.isNotEmpty() }
        if (tok.size > 7) { c.source.sendFailure(Component.literal(L.t("cheonsang.cmd.toomany"))); return 0 }
        fun keep(i: Int) = i >= tok.size || tok[i] == "~"
        val cur = Calendar.toLocal(s.clock.jd(now.toDouble()), s.lon / 15.0)
        fun int(i: Int, def: Int, lo: Int, hi: Int, name: String): Int? {
            if (keep(i)) return def
            val v = tok[i].toIntOrNull()
            if (v == null || v < lo || v > hi) { c.source.sendFailure(Component.literal("$name: $lo~$hi")); return null }
            return v
        }
        val y = int(0, cur.year, -2999, 2999, L.t("cheonsang.cmd.u_year")) ?: return 0
        val m = int(1, cur.month, 1, 12, L.t("cheonsang.cmd.u_month")) ?: return 0
        val d = int(2, cur.day, 1, 31, L.t("cheonsang.cmd.u_day")) ?: return 0
        if (d > Calendar.daysInMonth(y, m)) { c.source.sendFailure(Component.literal(L.t("cheonsang.cmd.daysin", y, m, Calendar.daysInMonth(y, m)))); return 0 }
        val timeGiven = !keep(3) || !keep(4)
        val curH = s.clock.localHours(now.toDouble())
        val h = int(3, floor(curH).toInt(), 0, 23, L.t("cheonsang.cmd.u_hour")) ?: return 0
        val mi = int(4, if (!keep(3)) 0 else floor((curH - floor(curH)) * 60).toInt(), 0, 59, L.t("cheonsang.cmd.u_minute")) ?: return 0
        var lat = s.lat
        var lon = s.lon
        if (!keep(5)) {
            val place = PLACES.firstOrNull { it.id.equals(tok[5], true) || it.name == tok[5] || it.label(lang(c)).equals(tok[5], true) }
            if (place != null) {
                if (tok.size > 6) { c.source.sendFailure(Component.literal(L.t("cheonsang.cmd.nolon"))); return 0 }
                lat = place.lat; lon = place.lon
            } else {
                val v = tok[5].toDoubleOrNull()
                if (v == null || v < -90.0 || v > 90.0) {
                    c.source.sendFailure(Component.literal(L.t("cheonsang.cmd.badlat", PLACES.joinToString(" ") { it.id }))); return 0
                }
                lat = v
            }
        }
        if (!keep(6)) {
            val v = tok[6].toDoubleOrNull()
            if (v == null || v < -180.0 || v > 180.0) { c.source.sendFailure(Component.literal(L.t("cheonsang.cmd.badlon"))); return 0 }
            lon = v
        }
        val tick: Long
        val hours: Double
        if (timeGiven) {
            hours = h + mi / 60.0
            val dayStart = (floor((now + 6000).toDouble() / SkyClock.TICKS_PER_DAY) * SkyClock.TICKS_PER_DAY).toLong() - 6000
            tick = dayStart + Math.round(hours / 24.0 * SkyClock.TICKS_PER_DAY)
            level.server.clockManager().setTotalTicks(clock, tick)
        } else {
            tick = now
            hours = curH
        }
        put(level, Sky(tick, Calendar.julianDayLocal(y, m, d, hours, lon / 15.0), s.rate, lat, lon))
        val warn = if (y < 1800 || y > 2050) L.t("cheonsang.cmd.warn") else ""
        val hh = floor(hours).toInt()
        val mm = floor((hours - hh) * 60 + 1e-6).toInt()
        return reply(c, (L.t("cheonsang.cmd.set") + warn).format(y, m, d, hh, mm, placeText(lat, lon), Math.floorMod(tick, 24000L)))
    } }

    fun lonText(lon: Double) = when {
        abs(lon) < 0.05 -> "0°"
        lon > 0 -> "%.1f°E".format(lon)
        else -> "%.1f°W".format(-lon)
    }

    fun placeText(lat: Double, lon: Double) = "${latText(lat)} ${lonText(lon)}"

    fun latText(lat: Double) = when {
        abs(lat) < 0.05 -> L.t("cheonsang.cmd.equator")
        lat > 0 -> "%.1f°N".format(lat)
        else -> "%.1f°S".format(-lat)
    }

    /** 1.9.0-dev2: 명령 친 사람의 클라이언트 언어 (콘솔은 null → 영어) */
    private fun lang(c: CommandContext<CommandSourceStack>): String? = c.source.player?.clientInformation()?.language()

    private fun reply(c: CommandContext<CommandSourceStack>, msg: String): Int {
        c.source.sendSuccess({ Component.literal(msg) }, false)
        return 1
    }
}
