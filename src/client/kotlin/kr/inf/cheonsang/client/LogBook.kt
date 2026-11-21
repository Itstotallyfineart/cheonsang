package kr.inf.cheonsang.client

import kr.inf.cheonsang.L
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import kr.inf.cheonsang.Cheonsang
import kr.inf.cheonsang.LogServer
import kr.inf.cheonsang.astro.Calendar
import kr.inf.cheonsang.astro.Planet
import kr.inf.cheonsang.astro.Vec3
import kr.inf.cheonsang.astro.norm180
import kr.inf.cheonsang.client.sky.CheonsangSky
import kr.inf.cheonsang.client.sky.SkyData
import kr.inf.cheonsang.client.sky.VariableStars
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import java.nio.file.Files

/**
 * 1.4.0 관측 기록 (클라이언트). 2.0.2-dev1: 도감 화면·발전과제·/clog 삭제, 기록만 내부에 남김.
 * - 등록: 망원경 좌클릭(·Alt)으로 고른 천체가 [Visibility.visible] 일 때만, 처음 한 번 (날짜·시각·배율·맨눈/망원경)
 * - 현상: 일식·월식·유성우 극대 밤은 그 순간 하늘이 트인 자리에서 보이면 자동 등록
 * - 저장: 서버에 천상이 있으면 서버(사람마다, [LogServer]), 없으면 이 컴퓨터 (config/cheonsang/log_local.json)
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


    private fun serverMode() = ClientPlayNetworking.canSend(LogServer.ADD)

    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(LogServer.ALL) { all, _ -> receive(all.lines) }
        ClientPlayConnectionEvents.JOIN.register { _, _, _ -> reset() }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> reset() }
        // 2.0.2-dev3: 관측 수첩 화면·키 삭제 (기록은 계속)
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (mc.level != null && mc.player != null && (mc.level!!.gameTime % 40L) == 0L) Safety.run(Safety.LOG, Unit) { checkEvents() }
        }
    }

    private fun reset() {
        entries.clear(); sent.clear(); baseline = false; localLoaded = false
    }

    // ── 등록 ──

    /** 망원경으로 고른 천체 — 보일 때만, 처음만 */
    internal fun observe(t: Target) {
        val key = logKey(t) ?: return
        if (!Visibility.visible(t)) return
        // 1.5.3 알골 식: 알골(HIP 14576)이 3등보다 어두울 때 고름
        if (key == "star:14576") VariableStars.byHip[14576]?.let { v ->
            if (v.mag(CheonsangSky.clock.jd((Minecraft.getInstance().level?.defaultClockTime ?: 0L).toDouble())) > 3.0) tryAdd("event:algol_min", L.t("cheonsang.logbook.1"))
        }
        if (key in entries || key in sent) return
        add(key, t.short)
    }

    /** 1.5.3: 현상 등록 */
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
            entries[key] = Entry(key, name, Place.baseClock.jd((Minecraft.getInstance().level?.defaultClockTime ?: 0L).toDouble()), zoom)
            saveLocal()
            sent.remove(key)
        }
    }

    private fun receive(lines: List<String>) {
        val old = entries.keys.toSet()
        entries.clear()
        for (l in lines) {
            val p = l.split("\t")
            if (p.size < 4) continue
            entries[p[0]] = Entry(p[0], p[3], p[1].toDoubleOrNull() ?: 0.0, p[2].toDoubleOrNull() ?: 1.0)
        }
        val sorted = entries.values.sortedBy { it.jd }
        entries.clear(); for (e in sorted) entries[e.key] = e
        for (k in entries.keys) if (k !in old) sent.remove(k)
        baseline = true
    }

    fun how(zoom: Double) = if (zoom <= 1.01) L.t("cheonsang.logbook.4") else L.t("cheonsang.logbook.3").format(zoom)

    // ── 현상 자동 등록 (2초마다) ──
    private fun checkEvents() {
        if (!CheonsangSky.isActive() || !Visibility.skyOpen()) return
        if (!serverMode()) loadLocal()
        val today = dateText(Place.baseClock.jd((Minecraft.getInstance().level?.defaultClockTime ?: 0L).toDouble()))
        // 일식: 해가 지평선 위에서 5% 넘게 가려짐
        if (CheonsangSky.eclipseObsc > 0.05f && CheonsangSky.sunAlt > -0.5) tryAdd("event:solar", L.t("cheonsang.logbook.5", today))
        // 월식: 본영이 달을 덮고 달이 지평선 위, 해는 지평선 아래
        if (CheonsangSky.lunarUmbra > 0.02f && Visibility.altitude(CheonsangSky.moonWorld) > -0.5 && CheonsangSky.sunAlt < -0.5) tryAdd("event:lunar", L.t("cheonsang.logbook.6", today))
        // 1.5.3 별똥별: 최근 3초 안에 화면 가까이 별똥별이 그려짐
        if (System.nanoTime() - CheonsangSky.meteorSeenNanos < 3_000_000_000L) tryAdd("event:meteor_seen", L.t("cheonsang.logbook.7", today))
        // 1.5.3 오로라·황도광: 그려지는 조건과 같게
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

    /** 1.9.0-dev3: 도감 시각은 월드 세계시 (서버 저장과 같게) → 월드 경도 현지 시각 = 마크 시계 = 내 관측 위치 현지 시각 */
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

    /** 기록 보기 전에 (이 컴퓨터 저장이면 읽어 둠) */
    fun ensureLoaded() { if (!serverMode()) loadLocal() }
}
