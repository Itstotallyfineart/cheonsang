package kr.inf.cheonsang

import kr.inf.cheonsang.astro.Calendar
import kr.inf.cheonsang.astro.Events
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent

/**
 * 다음 천문 현상 (일식·월식·보름·삭·유성우·행성 근접) — `/ctonight next` (내 화면) 에서 씀.
 * 그 장소(위도·경도) 기준, 시각은 현지 평균시
 */
object NextEvents {
    val KINDS = listOf("solar", "lunar", "full", "new", "meteor", "conj")
    private val NAMES get() = mapOf("solar" to L.t("cheonsang.nextevents.6"), "lunar" to L.t("cheonsang.nextevents.5"), "full" to L.t("cheonsang.nextevents.4"), "new" to L.t("cheonsang.nextevents.3"), "meteor" to L.t("cheonsang.nextevents.2"), "conj" to L.t("cheonsang.nextevents.1"))

    private val showers: List<Events.Shower> by lazy {
        try {
            val txt = NextEvents::class.java.getResourceAsStream("/assets/cheonsang/sky/meteors.json")!!.readBytes().toString(Charsets.UTF_8)
            com.google.gson.JsonParser.parseString(txt).asJsonObject.getAsJsonArray("showers").map {
                val o = it.asJsonObject
                Events.Shower(o["ko"].asString, o["lon"].asDouble, o["ra"].asDouble, o["dec"].asDouble, o["zhr"].asDouble, o["en1"]?.asString ?: o["ko"].asString)
            }
        } catch (_: Exception) { emptyList() }
    }

    /** 위도 범위 → "25°N~89°N" — 이어진 구간마다 */
    private fun bandText(lats: List<Int>): String {
        val runs = ArrayList<IntRange>()
        for (l in lats) if (runs.isNotEmpty() && runs.last().last == l - 1) runs[runs.size - 1] = runs.last().first..l else runs += l..l
        fun lt(l: Int) = when { l == 0 -> "0°"; l > 0 -> "${l}°N"; else -> "${-l}°S" }
        return runs.joinToString(" · ") { r -> if (r.first == r.last) lt(r.first) else "${lt(r.first)}~${lt(r.last)}" }
    }

    /**
     * 현상마다 한 줄. jumpLink = true 면 OP 용 [이동] (`/cwhen go`, worldLon 은 안 씀)
     * 일식·월식은 내 경도에서 보이는 위도 띠 + 누구나 [여기로 옮기기] (`/cplace 위도 경도`, 내 하늘만)
     */
    fun lines(jd0: Double, lat: Double, lon: Double, kind: String?, jumpLink: Boolean, latText: String, worldLon: Double = lon): List<Component> =
        (kind?.let { listOf(it) } ?: KINDS).map { k ->
            val band = when (k) { "solar" -> Events.solarEclipseBand(jd0, lon); "lunar" -> Events.lunarEclipseBand(jd0, lon); else -> null }
            val here = band != null && Math.round(lat).toInt() in band.lats
            val f = if (band != null) band.found.copy(text = band.found.text + L.t("cheonsang.m.visible_from") + bandText(band.lats) + " (" + SkyServer.lonText(lon) + ")")
            else when (k) {
                "solar", "lunar" -> null
                "full" -> Events.fullMoon(jd0, lat, lon)
                "new" -> Events.newMoon(jd0, lat, lon)
                "meteor" -> Events.meteor(jd0, lat, lon, showers)
                else -> Events.conjunction(jd0, lat, lon)
            }
            val msg = Component.literal(f?.text ?: L.t("cheonsang.nextevents.7", NAMES[k], latText))
            if (f != null && jumpLink) {
                // [이동] = /cwhen go (클라이언트가 내 경도 기준으로 /cele set)
                val cmd = "/cwhen go %.5f".format(java.util.Locale.ROOT, f.view)
                msg.append(Component.literal(L.t("cheonsang.m.go")).withStyle { st ->
                    st.withColor(ChatFormatting.AQUA).withClickEvent(ClickEvent.RunCommand(cmd)).withHoverEvent(HoverEvent.ShowText(Component.literal(cmd)))
                })
            }
            if (band != null && !here) {
                // 소수점은 언제나 점 (쉼표 소수점 언어에서 /cplace 가 거절)
                val cmd = "/cplace %d %.1f".format(java.util.Locale.ROOT, band.best, lon)
                msg.append(Component.literal(L.t("cheonsang.m.move_here", (if (band.best >= 0) "${band.best}°N" else "${-band.best}°S"))).withStyle { st ->
                    st.withColor(ChatFormatting.GREEN).withClickEvent(ClickEvent.RunCommand(cmd)).withHoverEvent(HoverEvent.ShowText(Component.literal(cmd)))
                })
            }
            msg
        }
}
