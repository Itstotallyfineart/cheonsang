package kr.inf.cheonsang.client

import kr.inf.cheonsang.L
import kr.inf.cheonsang.astro.Frames
import kr.inf.cheonsang.astro.Moon
import kr.inf.cheonsang.astro.Planet
import kr.inf.cheonsang.astro.PlanetPos
import kr.inf.cheonsang.astro.RiseSet
import kr.inf.cheonsang.astro.SkyDay
import kr.inf.cheonsang.astro.Sun
import kr.inf.cheonsang.astro.Vec3
import kr.inf.cheonsang.astro.norm360
import kr.inf.cheonsang.astro.toSpherical
import kr.inf.cheonsang.client.sky.CheonsangSky
import kr.inf.cheonsang.client.sky.Dso
import kr.inf.cheonsang.client.sky.SkyData
import kr.inf.cheonsang.client.sky.StarCatalog
import net.minecraft.client.Minecraft
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * 1.1.0 좌클릭 선택 + 정보 카드.
 * 고른 천체(Target)는 방향·카드 내용을 매 프레임 다시 계산 (방위·고도가 실시간으로 바뀜). 출·남중·몰은 3초마다.
 */
internal class Target(
    /** 같은 천체인지 가리는 이름표 */
    val key: String,
    /** 조준 중 이름 (조준선 아래) */
    val short: String,
    /** 좌클릭 한 줄 (액션바) */
    val line: String,
    /** 지금 월드 방향 */
    val dir: () -> Vec3?,
    /** 원판 반지름 (도) — 선택 원 크기 */
    val radiusDeg: () -> Double,
    /** 카드: 첫 줄 = 제목 */
    val card: () -> List<String>,
)

internal object InfoCard {
    private fun now(): Long = Minecraft.getInstance().level?.defaultClockTime ?: 0L
    private fun jdNow() = CheonsangSky.clock.jd(now().toDouble())
    fun day() = SkyDay(CheonsangSky.clock, CheonsangSky.latitude, now())

    // ── 출·남중·몰: 3초마다 ──
    private var rsKey = ""
    private var rsAt = 0L
    private var rsText = ""
    /** 1.3.0 TIME-001: 하늘 시각·위치가 바뀌면 출몰 캐시 버림 */
    fun invalidate() { rsKey = ""; rsAt = 0L }

    private fun riseSet(key: String, calc: () -> String): String {
        val t = System.nanoTime()
        if (key != rsKey || t - rsAt > 3_000_000_000L) { rsText = try { calc() } catch (_: Exception) { "" }; rsKey = key; rsAt = t }
        return rsText
    }

    /** 1.1.3: 분 → 시각 글자. 실시간 모드면 그 도시 시계 시각, 아니면 천상 시계(해 기준) */
    fun timeFmt(day: SkyDay): (Double) -> String {
        val c = CheonsangSky.realtimeCity ?: return { RiseSet.hm(it) }
        val zone = java.time.ZoneId.of(c.zone)
        val f = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
        return { min -> f.format(java.time.Instant.ofEpochMilli(((day.jd(min) - 2440587.5) * 86_400_000.0).toLong()).atZone(zone)) }
    }

    private fun rsText(f: (SkyDay) -> RiseSet.Day, kind: String): String {
        val d = day()
        return RiseSet.text(f(d), kind, timeFmt(d)) + (CheonsangSky.realtimeCity?.let { L.t("cheonsang.infocard.1", it.label) } ?: "")
    }

    /** Stellarium 식 겉보기 고도: 대기 굴절 더함 (Saemundsson, 기압 1010hPa·10°C) */
    private fun refracted(h: Double): Double =
        if (h < -2.0) h else h + 1.02 / kotlin.math.tan(Math.toRadians(h + 10.3 / (h + 5.11))) / 60.0

    /** 방위·고도(굴절 포함) · 적경·적위(오늘 날짜 기준) 두 줄 — Stellarium 과 같은 기준 */
    private fun position(world: Vec3?): List<String> {
        if (world == null) return emptyList()
        val (alt0, az) = Frames.altAz(world)
        val alt = refracted(alt0)
        val m = CheonsangSky.skyMat3.m
        val j = Vec3(
            m[0] * world.x + m[3] * world.y + m[6] * world.z,
            m[1] * world.x + m[4] * world.y + m[7] * world.z,
            m[2] * world.x + m[5] * world.y + m[8] * world.z,
        ).normalized()
        val (ra, dec) = (Frames.precession(jdNow()) * j).normalized().toSpherical()
        return listOf(L.t("cheonsang.infocard.3").format(az, compass(az), alt), L.t("cheonsang.infocard.2", raText(norm360(ra)), decText(dec)))
    }

    private fun compass(az: Double) = listOf(L.t("cheonsang.infocard.11"), L.t("cheonsang.infocard.10"), L.t("cheonsang.infocard.9"), L.t("cheonsang.infocard.8"), L.t("cheonsang.infocard.7"), L.t("cheonsang.infocard.6"), L.t("cheonsang.infocard.5"), L.t("cheonsang.infocard.4"))[((az + 22.5) / 45).toInt() % 8]

    fun raText(deg: Double): String {
        val s = (deg / 15 * 3600).roundToLong() % 86400
        return "%dh %02dm %02ds".format(s / 3600, s / 60 % 60, s % 60)
    }

    fun decText(deg: Double): String {
        val s = (abs(deg) * 3600).roundToLong()
        return "%s%d° %02d′ %02d″".format(if (deg < 0) "-" else "+", s / 3600, s / 60 % 60, s % 60)
    }

    /** 광년: 1,340광년 · 3.6만 광년 · 254만 광년 */
    fun lightYears(ly: Double): String = when {
        ly <= 0 -> L.t("cheonsang.m.dist_unknown")
        !L.ko -> if (ly >= 1e6) "%.1f million ly".format(ly / 1e6).replace(".0 million", " million") else "%,d ly".format(ly.roundToInt())
        ly >= 10000 -> { val man = ly / 10000; (if (man >= 100) "%.0f".format(man) else "%.1f".format(man).removeSuffix(".0")) + "만 광년" }
        else -> "%,d광년".format(ly.roundToInt())
    }

    private val TYPE_KO get() = mapOf("galaxy" to L.t("cheonsang.infocard.15"), "open" to L.t("cheonsang.infocard.14"), "globular" to L.t("cheonsang.infocard.13"), "nebula" to L.t("cheonsang.infocard.12"),
        "planetary" to L.t("cheonsang.infocard.19"), "dark" to L.t("cheonsang.infocard.18"), "star" to L.t("cheonsang.infocard.17"), "other" to L.t("cheonsang.infocard.16"))

    /** 분광형 → (색 + 광도) 한국어. "M1-2Ia-Iab" → "붉은 초거성" */
    fun spectKo(sp: String): String? {
        val s = sp.trim()
        if (s.isEmpty()) return null
        if (s.startsWith("D")) return L.t("cheonsang.infocard.20")
        val color = when (s[0]) { 'O' -> L.t("cheonsang.infocard.28"); 'B' -> L.t("cheonsang.infocard.27"); 'A' -> L.t("cheonsang.infocard.26"); 'F' -> L.t("cheonsang.infocard.25"); 'G' -> L.t("cheonsang.infocard.24"); 'K' -> L.t("cheonsang.infocard.23"); 'M' -> L.t("cheonsang.infocard.22"); 'C', 'N', 'R', 'S' -> L.t("cheonsang.infocard.21"); else -> return null }
        val lc = Regex("(VI|IV|V|III|II|I)").find(s.substring(1))?.value
        val kind = when (lc) { "I" -> L.t("cheonsang.infocard.34"); "II" -> L.t("cheonsang.infocard.33"); "III" -> L.t("cheonsang.infocard.32"); "IV" -> L.t("cheonsang.infocard.31"); "V" -> L.t("cheonsang.infocard.30"); "VI" -> L.t("cheonsang.infocard.29"); else -> L.t("cheonsang.infocard.17") }
        return "$color $kind"
    }

    // ── 천체 종류별 ──

    fun sun(): Target = Target("sun", L.t("cheonsang.infocard.35"), L.t("cheonsang.infocard.35"), { CheonsangSky.sunWorld }, { 0.27 }) {
        val s = Sun.position(jdNow())
        listOf(L.t("cheonsang.infocard.35"), L.t("cheonsang.infocard.36").format(Sun.angularRadius(s.distAu) * 120, s.distAu, s.distAu * 8.3167)) +
            position(CheonsangSky.sunWorld) + riseSet("sun") { rsText({ it.sun() }, "sun") }
    }

    fun moon(): Target = Target("moon", L.t("cheonsang.infocard.38"), L.t("cheonsang.infocard.37", (CheonsangSky.moonIllum * 100).roundToInt()),
        { CheonsangSky.moonWorld }, { CheonsangSky.moonRadius }) {
        val jd = jdNow()
        val m = Moon.position(jd); val ph = Moon.phase(m, Sun.position(jd))
        // 관측자 기준 (1.3.0: 하늘이 이미 관측자 기준 방향·거리로 그림)
        val topo = CheonsangSky.moonWorld.normalized() * CheonsangSky.moonTopoKm
        listOf(
            L.t("cheonsang.infocard.38"),
            L.t("cheonsang.infocard.39").format(ph.illuminated * 100, ph.elongation / 360 * 29.530588, Moon.angularRadius(m.distKm) * 120),
            L.t("cheonsang.infocard.40").format(topo.length().roundToInt()),
        ) + position(topo.normalized()) + riseSet("moon") { rsText({ it.moon() }, "moon") }
    }

    fun planet(p: PlanetPos): Target {
        val pl = p.planet
        val extra = if (pl == Planet.VENUS || pl == Planet.MERCURY) L.t("cheonsang.infocard.41", (p.illuminated * 100).roundToInt()) else ""
        fun pos() = CheonsangSky.planets.firstOrNull { it.planet == pl }
        return Target("planet:${pl.name}", pl.label, L.t("cheonsang.infocard.42", pl.label).format(p.mag, p.diameterArcsec) + extra,
            { pos()?.let { CheonsangSky.skyMat3 * it.dir } }, { (pos()?.diameterArcsec ?: 0.0) / 7200.0 }) {
            val q = pos() ?: p
            val w = CheonsangSky.skyMat3 * q.dir
            listOf(
                pl.label,
                L.t("cheonsang.infocard.43").format(q.mag, q.diameterArcsec, q.illuminated * 100),
                L.t("cheonsang.infocard.44").format(q.distAu, q.distAu * 8.3167, q.sunDistAu),
                L.t("cheonsang.infocard.45").format(w.normalized().angleTo(CheonsangSky.sunWorld.normalized())),
            ) + position(w) + riseSet("planet:${pl.name}") { rsText({ it.planet(pl) }, "") }
        }
    }

    /** 1.8.4: 지금 위성 (그렸는지·가려졌는지와 무관) — 추적이 저배율·가려짐에서도 이어지게 */
    private fun moonNow(name: String) = CheonsangSky.moonsNow().firstOrNull { it.ko == name }
    private fun hiddenLine(name: String, planet: String) = if (moonNow(name)?.hidden == true) listOf(L.t("cheonsang.infocard.46", planet)) else emptyList()

    fun jovianMoon(name: String, num: String, mag: Double, dir: Vec3): Target =
        Target("jovian:$name", L.moon(name), L.t("cheonsang.infocard.48", L.moon(name), num) + L.t("cheonsang.infocard.47").format(mag), { moonNow(name)?.let { CheonsangSky.skyMat3 * it.dirJ } }, { 0.0 }) {
            val w = moonNow(name)?.let { CheonsangSky.skyMat3 * it.dirJ } ?: dir
            listOf(L.t("cheonsang.infocard.51", L.moon(name), num), L.t("cheonsang.infocard.50").format(mag)) + hiddenLine(name, L.t("cheonsang.infocard.49")) + position(w) +
                riseSet("planet:JUPITER") { rsText({ it.planet(Planet.JUPITER) }, "") }
        }

    /** 1.2.4 토성 위성 (타이탄·레아) */
    fun saturnMoon(name: String, num: String, mag: Double, dir: Vec3): Target {
        fun now() = moonNow(name)?.let { CheonsangSky.skyMat3 * it.dirJ }
        return Target("saturnian:$name", L.moon(name), L.t("cheonsang.infocard.52", L.moon(name), num) + L.t("cheonsang.infocard.47").format(mag), { now() }, { 0.0 }) {
            val w = now() ?: dir
            listOf(L.t("cheonsang.infocard.55", L.moon(name), num), L.t("cheonsang.infocard.54").format(mag)) + hiddenLine(name, L.t("cheonsang.infocard.53")) + position(w) +
                riseSet("planet:SATURN") { rsText({ it.planet(Planet.SATURN) }, "") }
        }
    }

    fun dso(o: Dso): Target {
        val name = L.name(o.ko, o.en)
        val short = if (name.isEmpty()) o.id else "${o.id} $name"
        val dirJ = Vec3.fromSpherical(o.ra, o.dec)
        return Target("dso:${o.id}", short, if (o.mag >= 90) short else "$short | " + L.t("cheonsang.infocard.47").format(o.mag),
            { CheonsangSky.skyMat3 * dirJ }, { o.major / 120.0 }) {
            val ngc = Regex("^(NGC|IC|Mel|Cr)0*(\\d+)").find(o.ngc)?.let { "${it.groupValues[1]} ${it.groupValues[2]}" }
            val size = if (o.minor > 0 && abs(o.minor - o.major) > 0.05) "%.1f′×%.1f′".format(o.major, o.minor) else "%.1f′".format(o.major)
            listOf(
                short,
                listOfNotNull((TYPE_KO[o.type] ?: o.type) + (SkyData.dsoMorph[o.id]?.let { " $it" } ?: ""), ngc, if (o.mag < 90) L.t("cheonsang.infocard.57").format(o.mag) else null, L.t("cheonsang.infocard.56", size)).joinToString(" · "),
                L.t("cheonsang.infocard.58") + lightYears(SkyData.dsoDist[o.id] ?: 0.0).removePrefix(L.t("cheonsang.infocard.58")),
            ) + position(CheonsangSky.skyMat3 * dirJ) + riseSet("dso:${o.id}") { rsText({ it.fixed(dirJ) }, "") }
        }
    }

    /** 1.9.0-dev2 유명 별 거리: 100광년 안은 소수 한 자리 */
    private fun famousDist(f: kr.inf.cheonsang.client.sky.Famous) =
        if (f.ly < 100) (if (L.ko) "%.1f광년" else "%.1f ly").format(f.ly) else lightYears(f.ly)

    private fun famousLine(f: kr.inf.cheonsang.client.sky.Famous) = listOf(f.note)

    /** 1.9.0-dev2 이름 없는 유명 별 (깊은 별·목록 밖) — /cfind 와 망원경 고르기 */
    fun famous(f: kr.inf.cheonsang.client.sky.Famous, mag: Double = f.fixMag ?: f.mag): Target {
        val dirJ = f.dirJ
        val key = "famous:${f.id}"
        return Target(key, f.label, "${f.label} | " + L.t("cheonsang.infocard.47").format(mag), { CheonsangSky.skyMat3 * dirJ }, { 0.0 }) {
            listOf(f.label, L.t("cheonsang.infocard.61").format(mag) + " · " + L.t("cheonsang.infocard.58") + famousDist(f).removePrefix(L.t("cheonsang.infocard.58"))) +
                famousLine(f) + position(CheonsangSky.skyMat3 * dirJ) + riseSet(key) { rsText({ it.fixed(dirJ) }, "") }
        }
    }

    fun star(cat: StarCatalog, i: Int, short0: String, line0: String): Target {
        val dirJ = Vec3(cat.dir[i * 3].toDouble(), cat.dir[i * 3 + 1].toDouble(), cat.dir[i * 3 + 2].toDouble())
        // 1.9.0-dev2: 이름 없는 유명 별이면 그 이름 + 한 줄 설명
        val fam = cat.hip[i].takeIf { it > 0 }?.let { SkyData.famousByHip[it] } ?: SkyData.famousNear(dirJ.x, dirJ.y, dirJ.z)
        val short = if (fam != null && SkyData.starLabel(cat.hip[i]) == null) fam.label else short0
        val line = if (fam != null && short != short0) "$short | " + L.t("cheonsang.infocard.47").format(cat.mag[i]) else line0
        return Target("star:$i", short, line, { CheonsangSky.skyMat3 * dirJ }, { 0.0 }) {
            val info = SkyData.starInfo?.takeIf { i < it.distLy.size }
            val ids = listOfNotNull(
                cat.hip[i].takeIf { it > 0 }?.let { "HIP $it" },
                info?.hd?.get(i)?.takeIf { it > 0 }?.let { "HD $it" },
                info?.spect?.get(i)?.takeIf { it.isNotBlank() }?.let { sp -> L.t("cheonsang.infocard.59", sp) + (spectKo(sp)?.let { " ($it)" } ?: "") },
            )
            val am = info?.absMag?.get(i)?.takeIf { !it.isNaN() }
            listOfNotNull(
                short,
                ids.joinToString(" · ").ifEmpty { null },
                listOfNotNull(L.t("cheonsang.infocard.61").format(cat.mag[i]), am?.let { L.t("cheonsang.infocard.60").format(it) },
                    L.t("cheonsang.infocard.58") + lightYears((info?.distLy?.get(i) ?: 0f).toDouble()).removePrefix(L.t("cheonsang.infocard.58"))).joinToString(" · "),
            ) + (fam?.let { famousLine(it) } ?: emptyList()) + variableLine(cat.hip[i]) + position(CheonsangSky.skyMat3 * dirJ) + riseSet("star:$i") { rsText({ it.fixed(dirJ) }, "") }
        }
    }

    /**
     * 1.8.1 깊은 별 (9등 넘는 Tycho-2·Gaia DR3) 카드 — 번호 없이 등급·색·위치·출몰.
     * 별자리 = 가장 가까운 맨눈 별의 별자리 (경계 근처는 틀릴 수 있음, TBD)
     */
    fun deep(cat: StarCatalog, dirJ: Vec3, mag: Float, bv: Float): Target {
        // 깊은 별은 Tycho-2(2000)·Gaia(2016) 위치라 빨리 움직이는 별이 조금 어긋남 → 3′ 안 + 등급 비슷한 것
        SkyData.famousNear(dirJ.x, dirJ.y, dirJ.z, 3.0, mag.toDouble())?.let { return famous(it, mag.toDouble()) }
        var best = -2.0; var con: kr.inf.cheonsang.client.sky.Constellation? = null
        for (i in 0 until cat.realCount) {
            val c = cat.con[i]; if (c < 0) continue
            val d = cat.dir[i * 3] * dirJ.x + cat.dir[i * 3 + 1] * dirJ.y + cat.dir[i * 3 + 2] * dirJ.z
            if (d > best) { best = d; con = SkyData.constellations.getOrNull(c) }
        }
        val short = con?.let { L.t("cheonsang.infocard.63", L.name(it.ko, it.en)) } ?: L.t("cheonsang.infocard.62")
        val ra = (Math.toDegrees(kotlin.math.atan2(dirJ.y, dirJ.x)) + 360) % 360; val de = Math.toDegrees(kotlin.math.asin(dirJ.z.coerceIn(-1.0, 1.0)))
        val key = "deep:${(ra * 1e4).roundToInt()}:${(de * 1e4).roundToInt()}"
        val cls = when { bv < -0.02f -> "B"; bv < 0.30f -> "A"; bv < 0.58f -> "F"; bv < 0.81f -> "G"; bv < 1.40f -> "K"; else -> "M" }
        return Target(key, short, "$short | " + L.t("cheonsang.infocard.47").format(mag), { CheonsangSky.skyMat3 * dirJ }, { 0.0 }) {
            listOf(
                short,
                "Tycho-2 · Gaia DR3",
                L.t("cheonsang.infocard.65").format(mag, bv, cls, spectKo(cls)?.removeSuffix(L.t("cheonsang.infocard.64")) ?: ""),
            ) + position(CheonsangSky.skyMat3 * dirJ) + riseSet(key) { rsText({ it.fixed(dirJ) }, "") }
        }
    }

    /** 1.2.5 변광성 줄: 지금 등급 · 범위 · 주기 */
    private fun variableLine(hip: Int): List<String> {
        val v = kr.inf.cheonsang.client.sky.VariableStars.byHip[hip] ?: return emptyList()
        val p = if (v.period < 10) L.t("cheonsang.infocard.67").format(v.period) else L.t("cheonsang.infocard.66").format(v.period)
        return listOf(L.t("cheonsang.infocard.68", v.kind, v.range, p).format(v.mag(jdNow())))
    }

    /** 1.2.5 소행성 */
    fun asteroid(a: kr.inf.cheonsang.client.sky.AsteroidPos): Target {
        val b = a.body
        val title = if (L.ko) "${b.ko} (${b.num} ${b.en})" else "${b.num} ${b.en}"
        fun pos() = CheonsangSky.asteroids.firstOrNull { it.body === b }
        return Target("asteroid:${b.en}", L.name(b.ko, b.en), "$title | " + L.t("cheonsang.infocard.47").format(a.mag), { pos()?.let { CheonsangSky.skyMat3 * it.dir } }, { 0.0 }) {
            val q = pos() ?: a
            val w = CheonsangSky.skyMat3 * q.dir
            listOf(
                title,
                L.t("cheonsang.infocard.69").format(q.mag, q.phaseAngle),
                L.t("cheonsang.infocard.44").format(q.distAu, q.distAu * 8.3167, q.sunDistAu),
                L.t("cheonsang.infocard.45").format(w.normalized().angleTo(CheonsangSky.sunWorld.normalized())),
            ) + position(w) + riseSet("asteroid:${b.en}") { rsText({ it.fixed(q.dir) }, "") }
        }
    }
}
