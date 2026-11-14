package kr.inf.cheonsang.client

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import kr.inf.cheonsang.Cheonsang
import kr.inf.cheonsang.L
import kr.inf.cheonsang.SkyServer
import kr.inf.cheonsang.astro.SkyClock
import kr.inf.cheonsang.client.sky.CheonsangSky
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import java.nio.file.Files

/**
 * 내 관측 위치 `/cplace` — 위도·경도를 블록 좌표와 떼어 각자 자유롭게 설정. OP 권한 불필요, 내 하늘만 바뀜.
 * 월드별로 config/cheonsang/places.json 에 저장되며, 마크 시계는 항상 내 위치의 현지 평균시를 따른다.
 */
object Place {
    private val file get() = FabricLoader.getInstance().configDir.resolve(Cheonsang.MOD_ID).resolve("places.json")

    /** 월드(서버 또는 단독)가 정한 하늘 — 내 위치를 넣기 전 */
    var baseClock: SkyClock = SkyClock()
        private set
    var baseLat: Double = SkyClock.DEFAULT_LAT
        private set

    /** 내 관측 위치 (null = 월드 기본 위치) */
    var mine: Pair<Double, Double>? = null
        private set
    private var loadedKey: String? = null

    private fun worldKey(): String {
        val mc = Minecraft.getInstance()
        mc.currentServer?.ip?.let { return "server:$it" }
        mc.singleplayerServer?.let { return "world:" + it.worldData.levelName }
        return "standalone"
    }

    /** 월드 하늘이 들어올 때 (서버 패킷·단독 설정) */
    fun setBase(clock: SkyClock, lat: Double) {
        baseClock = clock; baseLat = lat
        val k = worldKey()
        if (k != loadedKey) { loadedKey = k; mine = read(k) }
        apply()
    }

    /** 실제 하늘 = 월드 하늘 + 내 위치 */
    fun apply() {
        val m = mine
        val clock = if (m == null) baseClock else baseClock.copy(epochJd = baseClock.epochJd + (baseClock.lon - m.second) / 360.0, lon = m.second)
        val lat = m?.first ?: baseLat
        if (clock != CheonsangSky.clock || lat != CheonsangSky.latitude) { CheonsangSky.invalidate(); InfoCard.invalidate() }
        CheonsangSky.clock = clock
        CheonsangSky.latitude = lat
    }

    /** 서버가 보낸 월드 기본 위치 번호가 바뀌면(OP 가 /cele set 으로 위치 변경) 내 /cplace 를 해제한다. */
    fun onGen(gen: Int) {
        val k = loadedKey ?: worldKey()
        val gk = "$k#gen"
        try {
            val gson = GsonBuilder().setPrettyPrinting().create()
            val o = if (Files.exists(file)) gson.fromJson(Files.readString(file), JsonObject::class.java) ?: JsonObject() else JsonObject()
            val prev = o[gk]?.asInt
            if (prev == gen) return
            if (prev != null && k == (loadedKey ?: k)) { mine = null; o.remove(k); apply() }
            o.addProperty(gk, gen)
            Files.createDirectories(file.parent)
            Files.writeString(file, gson.toJson(o))
        } catch (e: Exception) { Cheonsang.LOG.warn("Cheonsang: failed to save places.json", e) }
    }

    fun set(lat: Double, lon: Double) { mine = lat.coerceIn(-90.0, 90.0) to normLon(lon); write(); apply() }
    fun reset() { mine = null; write(); apply() }
    fun forget() { loadedKey = null; mine = null }

    private fun normLon(lon: Double) = ((lon + 180.0) % 360.0 + 360.0) % 360.0 - 180.0

    private fun read(k: String): Pair<Double, Double>? = try {
        if (!Files.exists(file)) null else GsonBuilder().create().fromJson(Files.readString(file), JsonObject::class.java)?.getAsJsonObject(k)?.let {
            it["lat"].asDouble.coerceIn(-90.0, 90.0) to normLon(it["lon"].asDouble)
        }
    } catch (e: Exception) { Cheonsang.LOG.warn("Cheonsang: failed to read places.json", e); null }

    private fun write() {
        val k = loadedKey ?: worldKey().also { loadedKey = it }
        try {
            val gson = GsonBuilder().setPrettyPrinting().create()
            val o = if (Files.exists(file)) gson.fromJson(Files.readString(file), JsonObject::class.java) ?: JsonObject() else JsonObject()
            val m = mine
            if (m == null) o.remove(k) else o.add(k, JsonObject().apply { addProperty("lat", m.first); addProperty("lon", m.second) })
            Files.createDirectories(file.parent)
            Files.writeString(file, gson.toJson(o))
        } catch (e: Exception) { Cheonsang.LOG.warn("Cheonsang: failed to save places.json", e) }
    }

    /** "37.5 127" · "seoul" · "서울" → (위도, 경도) */
    fun parse(text: String): Pair<Double, Double>? {
        val t = text.trim()
        SkyServer.PLACES.firstOrNull { it.id.equals(t, true) || it.name == t || it.label.equals(t, true) }?.let { return it.lat to it.lon }
        val p = t.replace(",", " ").split(" ").filter { it.isNotEmpty() }.map { it.toDoubleOrNull() }
        if (p.size != 2 || p.any { it == null }) return null
        val (la, lo) = p[0]!! to p[1]!!
        if (la !in -90.0..90.0 || lo !in -180.0..180.0) return null
        return la to lo
    }

    fun text(): String {
        val lat = CheonsangSky.latitude; val lon = CheonsangSky.longitude
        val where = SkyServer.placeText(lat, lon)
        return if (mine == null) L.t("cheonsang.place.default", where) else L.t("cheonsang.place.mine", where, SkyServer.placeText(baseLat, baseClock.lon))
    }

    fun register() {
        ClientCommandRegistrationCallback.EVENT.register { d, _ ->
            d.register(ClientCommands.literal("cplace")
                .executes { c -> c.source.sendFeedback(Component.literal(text())); 1 }
                .then(ClientCommands.literal("reset").executes { c -> reset(); c.source.sendFeedback(Component.literal(text())); 1 })
                .then(ClientCommands.argument("place", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                    .suggests { _, b -> val q = b.remaining.lowercase(); SkyServer.PLACES.filter { it.id.startsWith(q) }.forEach { b.suggest(it.id) }; b.buildFuture() }
                    .executes { c ->
                        val p = parse(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "place"))
                        if (p == null) { c.source.sendError(Component.literal(L.t("cheonsang.place.bad", SkyServer.PLACES.joinToString(" ") { it.id }))); 0 }
                        else { set(p.first, p.second); c.source.sendFeedback(Component.literal(text())); 1 }
                    }))
        }
    }
}
