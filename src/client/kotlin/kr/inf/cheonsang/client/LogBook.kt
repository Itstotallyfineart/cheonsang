package kr.inf.cheonsang.client

import kr.inf.cheonsang.L
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.mojang.blaze3d.platform.InputConstants
import kr.inf.cheonsang.Cheonsang
import kr.inf.cheonsang.LogServer
import kr.inf.cheonsang.astro.Calendar
import kr.inf.cheonsang.astro.Planet
import kr.inf.cheonsang.astro.Vec3
import kr.inf.cheonsang.astro.norm180
import kr.inf.cheonsang.client.sky.CheonsangSky
import kr.inf.cheonsang.client.sky.SkyData
import kr.inf.cheonsang.client.sky.VariableStars
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import java.nio.file.Files

/**
 * 관측 도감 + 발전과제(클라이언트). 망원경으로 고른 천체가 [Visibility.visible] 일 때 처음 한 번 등록하고, 일식·월식·유성우 극대는 보이면 자동 등록.
 * 서버에 천상이 있으면 서버([LogServer])에, 없으면 이 컴퓨터(config/cheonsang/log_local.json)에 저장하며, 발전과제는 도감 기록에서 그때그때 계산함.
 */
object LogBook {
    class Entry(val key: String, val name: String, val jd: Double, val zoom: Double)

    /** 키 → 기록 (등록 순) */
    val entries = LinkedHashMap<String, Entry>()
    /** 서버 기록을 한 번이라도 받았는지 — 첫 수신은 알림 없이 기준만 잡음 */
    private var baseline = false
    private val sent = HashSet<String>()
    private val localFile get() = FabricLoader.getInstance().configDir.resolve(Cheonsang.MOD_ID).resolve("log_local.json")
    private var localLoaded = false

    private lateinit var notebookKey: TelescopeFilter.CheonsangKey
    /** 명령(채팅 화면)에서 열면 채팅이 닫힌 다음 틱에 엶 */
    private var openNext = -1

    private fun serverMode() = ClientPlayNetworking.canSend(LogServer.ADD)

    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(LogServer.ALL) { all, _ -> receive(all.lines) }
        ClientPlayConnectionEvents.JOIN.register { _, _, _ -> reset() }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> reset() }
        notebookKey = TelescopeFilter.CheonsangKey("key.cheonsang.notebook", InputConstants.UNKNOWN.value, null).also { KeyMappingHelper.registerKeyMapping(it) }
        TelescopeFilter.extraKeys += notebookKey
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            Safety.run(Safety.KEYS, Unit) { while (notebookKey.consumeClick()) if (mc.gui.screen() == null) NotebookScreen.open(0) }
            if (openNext >= 0 && mc.gui.screen() == null) { val tab = openNext; openNext = -1; NotebookScreen.open(tab) }
            if (mc.level != null && mc.player != null && (mc.level!!.gameTime % 40L) == 0L) Safety.run(Safety.LOG, Unit) { checkEvents() }
        }
        ClientCommandRegistrationCallback.EVENT.register { d, _ ->
            d.register(ClientCommands.literal("clog").executes { openNext = 2; 1 }
                .then(ClientCommands.literal("list").executes { c ->
                    Safety.run(Safety.LOG, Unit) { summary().forEach { c.source.sendFeedback(Component.literal(it)) } }
                    1
                }))
        }
    }

    private fun reset() {
        entries.clear(); sent.clear(); sentParts.clear(); baseline = false; localLoaded = false
    }

    // ── 등록 ──

    /** 망원경으로 고른 천체 — 보일 때만, 처음만 */
    internal fun observe(t: Target) {
        val key = logKey(t) ?: return
        if (!Visibility.visible(t)) return
        // 알골 식: 알골(HIP 14576)이 3등보다 어두울 때 고름
        if (key == "star:14576") VariableStars.byHip[14576]?.let { v ->
            if (v.mag(CheonsangSky.clock.jd((Minecraft.getInstance().level?.defaultClockTime ?: 0L).toDouble())) > 3.0) tryAdd("event:algol_min", L.t("cheonsang.logbook.1"))
        }
        if (key in entries || key in sent) return
        add(key, t.short)
    }

    /** 도구 사용 등 현상 등록 (각거리 재기) */
    fun event(key: String, name: String) = Safety.run(Safety.LOG, Unit) { if (!serverMode()) loadLocal(); tryAdd(key, name) }

    /** Target 키 → 도감 키 (별은 목록 순번 대신 HIP 번호) */
    private fun logKey(t: Target): String? {
        val k = t.key
        if (!k.startsWith("star:")) return k
        val cat = CheonsangSky.catalog ?: return null
        val i = k.removePrefix("star:").toIntOrNull() ?: return null
        val hip = cat.hip.getOrNull(i) ?: return null
        return if (hip > 0) "star:$hip" else null
    }

    private fun zoomNow() = if (TelescopeView.magnifying) TelescopeView.zoom else 1.0

    private fun add(key: String, name: String) {
        val zoom = zoomNow()
        if (serverMode()) {
            sent += key
            ClientPlayNetworking.send(LogServer.Add(key, name, zoom))
        } else {
            loadLocal()
            val before = achieved()
            entries[key] = Entry(key, name, Place.baseClock.jd((Minecraft.getInstance().level?.defaultClockTime ?: 0L).toDouble()), zoom)
            saveLocal()
            announce(listOf(entries.getValue(key)), before)
        }
    }

    private fun receive(lines: List<String>) {
        val before = if (baseline) achieved() else emptySet()
        val old = entries.keys.toSet()
        entries.clear()
        for (l in lines) {
            val p = l.split("\t")
            if (p.size < 4) continue
            entries[p[0]] = Entry(p[0], p[3], p[1].toDoubleOrNull() ?: 0.0, p[2].toDoubleOrNull() ?: 1.0)
        }
        val sorted = entries.values.sortedBy { it.jd }
        entries.clear(); for (e in sorted) entries[e.key] = e
        if (baseline) announce(entries.values.filter { it.key !in old }, before)
        else grantParts()  // 처음 받을 때 이미 달성한 조건도 서버로 보냄 (서버가 중복은 무시)
        baseline = true
    }

    /** 도감 등록 메시지 없음. 새로 달성한 발전과제만 — 서버(천상 있음)면 바닐라 발전과제로, 아니면 액션바 한 줄 */
    private fun announce(added: List<Entry>, before: Set<String>) {
        for (e in added) sent.remove(e.key)
        if (serverMode()) grantParts()
        else for (a in ACHIEVEMENTS) if (a.id !in before && a.done())
            Minecraft.getInstance().player?.sendOverlayMessage(Component.literal(L.t("cheonsang.logbook.2", a.name)))
    }

    /** 서버로 보낸 조건 ("id/조건") — 같은 접속 안에서 다시 안 보냄 */
    private val sentParts = HashSet<String>()

    /** 진행도 분수: 달성한 조건마다 "adv:<id>/<조건>" (마크가 "30/110" 처럼 표시) */
    private fun grantParts() {
        if (!serverMode()) return
        // 서버 초당 제한(한꺼번에 300·초당 20)에 맞춰 한 번에 40 개까지 — 나머지는 다음 확인 때
        var n = 0
        for (a in ACHIEVEMENTS) for (c in a.doneParts()) {
            if (n >= 40) return
            val k = "${a.id}/$c"
            if (sentParts.add(k)) { ClientPlayNetworking.send(LogServer.Add("adv:$k", "", 1.0)); n++ }
        }
    }

    fun how(zoom: Double) = if (zoom <= 1.01) L.t("cheonsang.logbook.4") else L.t("cheonsang.logbook.3").format(zoom)

    // ── 현상 자동 등록 (2초마다) ──
    private fun checkEvents() {
        if (baseline && serverMode()) grantParts()   // 나눠 보내는 나머지 (2초마다 40 개)
        if (!CheonsangSky.isActive() || !Visibility.skyOpen()) return
        if (!serverMode()) loadLocal()
        val today = dateText(Place.baseClock.jd((Minecraft.getInstance().level?.defaultClockTime ?: 0L).toDouble()))
        // 일식: 해가 지평선 위에서 5% 넘게 가려짐
        if (CheonsangSky.eclipseObsc > 0.05f && CheonsangSky.sunAlt > -0.5) tryAdd("event:solar", L.t("cheonsang.logbook.5", today))
        // 월식: 본영이 달을 덮고 달이 지평선 위, 해는 지평선 아래
        if (CheonsangSky.lunarUmbra > 0.02f && Visibility.altitude(CheonsangSky.moonWorld) > -0.5 && CheonsangSky.sunAlt < -0.5) tryAdd("event:lunar", L.t("cheonsang.logbook.6", today))
        // 별똥별: 최근 3초 안에 화면 가까이 별똥별이 그려짐
        if (System.nanoTime() - CheonsangSky.meteorSeenNanos < 3_000_000_000L) tryAdd("event:meteor_seen", L.t("cheonsang.logbook.7", today))
        // 오로라·황도광: 그려지는 조건과 같게
        val jdNow = CheonsangSky.clock.jd((Minecraft.getInstance().level?.defaultClockTime ?: 0L).toDouble())
        if (AmbientLight.debugAurora == null && AmbientLight.auroraStrength(jdNow) > 0.15) tryAdd("event:aurora", L.t("cheonsang.logbook.8", today))
        if (Visibility.on(Visibility.Fx.ZODIACAL) && CheonsangSky.zodiacalDark() >= 0.5f) tryAdd("event:zodiacal", L.t("cheonsang.logbook.9", today))
        // 유성우 극대 밤: 극대 ±1일(해 황경 ±1°), 복사점이 떠 있고, 하늘이 충분히 어두움
        if (CheonsangSky.starFactor() > 0.8f) for (s in SkyData.showers) {
            if (kotlin.math.abs(norm180(CheonsangSky.sunLon - s.peakLon)) > 1.0) continue
            val rad = CheonsangSky.skyMat3 * Vec3.fromSpherical(s.ra, s.dec)
            if (Visibility.altitude(rad) > 0) tryAdd("event:meteor:${s.id}", L.t("cheonsang.logbook.10", s.label, today))
        }
    }

    private fun tryAdd(key: String, name: String) { if (key !in entries && key !in sent) add(key, name) }

    /** 도감 시각은 월드 세계시(서버 저장과 같음) — 월드 경도 현지 시각이 곧 마크 시계이자 내 관측 위치 현지 시각 */
    fun dateText(jd: Double): String {
        val t = Calendar.toLocal(jd, Place.baseClock.lon / 15.0)
        return "%04d-%02d-%02d %02d:%02d".format(t.year, t.month, t.day, t.hour, t.minute)
    }

    // ── 이 컴퓨터 저장 (서버에 천상 없을 때) ──
    private fun loadLocal() {
        if (localLoaded) return
        localLoaded = true; baseline = true
        try {
            if (!Files.exists(localFile)) return
            val o = GsonBuilder().create().fromJson(Files.readString(localFile), JsonObject::class.java) ?: return
            for ((k, v) in o.entrySet()) {
                val e = v.asJsonObject
                entries[k] = Entry(k, e["name"].asString, e["jd"].asDouble, e["zoom"].asDouble)
            }
        } catch (e: Exception) {
            Cheonsang.LOG.warn("Cheonsang: failed to read log_local.json", e)
        }
    }

    private fun saveLocal() {
        try {
            val o = JsonObject()
            for (e in entries.values) o.add(e.key, JsonObject().apply { addProperty("name", e.name); addProperty("jd", e.jd); addProperty("zoom", e.zoom) })
            Files.createDirectories(localFile.parent)
            Files.writeString(localFile, GsonBuilder().setPrettyPrinting().create().toJson(o))
        } catch (e: Exception) {
            Cheonsang.LOG.warn("Cheonsang: failed to save log_local.json", e)
        }
    }

    /** 수첩·`/clog list` 보기 전에 (이 컴퓨터 저장이면 읽어 둠) */
    fun ensureLoaded() { if (!serverMode()) loadLocal() }

    // ── 발전과제 (도감에서 계산) ──
    /** parts=조건 이름(data/cheonsang/advancement, _tools/make_advancements.py 와 같음, 하나짜리는 "done"), doneParts=지금 도감으로 달성한 조건 */
    class Achievement(val id: String, val parts: List<String>, val doneParts: () -> List<String>) {
        val goal get() = parts.size
        fun count() = doneParts().size
        fun done() = count() >= goal
        val name: String get() = net.minecraft.client.resources.language.I18n.get("advancements.cheonsang.$id.title")
    }

    private fun countPrefix(p: String) = entries.keys.count { it.startsWith(p) }

    /** 별자리 88개 — 별자리마다 가장 밝은 별 HIP (뱀자리 머리·꼬리는 하나로) */
    private var conBrightestCache: Map<String, Int>? = null
    private fun conBrightest(): Map<String, Int> {
        conBrightestCache?.let { return it }
        val cat = CheonsangSky.catalog ?: return emptyMap()
        val out = LinkedHashMap<String, Int>()
        for (i in 0 until cat.realCount) {
            val c = cat.con[i].takeIf { it >= 0 }?.let { SkyData.constellations.getOrNull(it) } ?: continue
            if (c.id !in out && cat.hip[i] > 0) out[c.id] = cat.hip[i]
        }
        return out.also { conBrightestCache = it }
    }
    fun constellationCount() = conBrightest().values.count { "star:$it" in entries }

    /** 주요 은하 10개 */
    private val GALAXIES = listOf("M31", "M33", "LMC", "SMC", "M51", "M81", "M82", "M101", "M104", "C77")
    /** 위성 도감 키(한국어 이름) → 조건 이름 */
    private val JOVIAN = linkedMapOf("이오" to "io", "유로파" to "europa", "가니메데" to "ganymede", "칼리스토" to "callisto")
    private val SATURNIAN = linkedMapOf("타이탄" to "titan", "레아" to "rhea", "이아페투스" to "iapetus", "디오네" to "dione",
        "테티스" to "tethys", "엔셀라두스" to "enceladus", "미마스" to "mimas")

    private fun one(id: String, ok: () -> Boolean) = Achievement(id, listOf("done")) { if (ok()) listOf("done") else emptyList() }
    /** 조건 이름 → 도감 키 */
    private fun many(id: String, parts: Map<String, String>) = Achievement(id, parts.keys.toList()) { parts.filter { it.value in entries }.keys.toList() }

    /** 마크 발전과제 "천상" 탭과 같은 id(data/cheonsang/advancement). 도감 등록 메시지는 없고 달성은 바닐라 토스트로, 여러 개 찾는 항목은 대상마다 조건(진행도 분수) */
    val ACHIEVEMENTS: List<Achievement> by lazy { listOf(
        one("moon") { "moon" in entries },
        one("lunar") { countPrefix("event:lunar") > 0 },
        one("solar") { countPrefix("event:solar") > 0 },
        many("planets", Planet.entries.associate { it.name to "planet:${it.name}" }),
        one("mercury") { "planet:MERCURY" in entries },
        many("jovian", JOVIAN.entries.associate { (ko, c) -> c to "jovian:$ko" }),
        many("saturnian", SATURNIAN.entries.associate { (ko, c) -> c to "saturnian:$ko" }),
        many("asteroids", mapOf("Ceres" to "asteroid:Ceres", "Vesta" to "asteroid:Vesta")),
        Achievement("constellations", SkyData.constellations.map { it.id }.distinct()) { conBrightest().filter { "star:${it.value}" in entries }.keys.toList() },
        many("messier", (1..110).associate { "M$it" to "dso:M$it" }),
        many("caldwell", (1..109).associate { "C$it" to "dso:C$it" }),
        many("galaxies", GALAXIES.associateWith { "dso:$it" }),
        one("meteor") { "event:meteor_seen" in entries },
        one("shower") { countPrefix("event:meteor:") > 0 },
        one("algol") { "event:algol_min" in entries },
        one("aurora") { "event:aurora" in entries },
        one("zodiacal") { "event:zodiacal" in entries },
        many("south", linkedMapOf("acrux" to "star:60718", "LMC" to "dso:LMC", "SMC" to "dso:SMC")),
        one("angle") { "event:angle" in entries },
        // 천상열차분야지도: 28수 · 삼원 · 북두 · 노인성
        many("joseon28", SkyData.koreanMeta.mansions.withIndex().associate { (i, hip) -> "m%02d".format(i + 1) to "star:$hip" }),
        Achievement("joseon3", listOf("jami", "taemi", "cheonsi")) {
            SkyData.koreanMeta.enclosures.filter { (_, set) -> set.any { "star:$it" in entries } }.keys.toList()
        },
        many("joseonladle", SkyData.koreanMeta.ladle.withIndex().associate { (i, hip) -> "s${i + 1}" to "star:$hip" }),
        one("joseonnoin") { "star:${SkyData.koreanMeta.noin}" in entries },
    ) }

    fun achieved(): Set<String> = ACHIEVEMENTS.filter { it.done() }.map { it.id }.toSet()

    fun achievementLines(): List<String> = ACHIEVEMENTS.map { a ->
        val n = a.count().coerceAtMost(a.goal)
        (if (a.done()) "■ " else "□ ") + a.name + if (a.goal > 1) " ($n/${a.goal})" else ""
    }

    fun entryLines(): List<String> = entries.values.sortedByDescending { it.jd }.map { e -> "${dateText(e.jd)} · ${e.name} · ${how(e.zoom)}" }

    private fun summary(): List<String> {
        ensureLoaded()
        val done = ACHIEVEMENTS.count { it.done() }
        return listOf(L.t("cheonsang.logbook.12", entries.size, done, ACHIEVEMENTS.size) + if (serverMode()) "" else L.t("cheonsang.logbook.11")) +
            entryLines().take(10) + if (entries.size > 10) listOf(L.t("cheonsang.logbook.13")) else emptyList()
    }
}
