package kr.inf.cheonsang.client

import kr.inf.cheonsang.L
import com.mojang.brigadier.arguments.StringArgumentType
import kr.inf.cheonsang.Cheonsang
import kr.inf.cheonsang.astro.Planet
import kr.inf.cheonsang.astro.Vec3
import kr.inf.cheonsang.astro.asinD
import kr.inf.cheonsang.astro.atan2D
import kr.inf.cheonsang.astro.norm360
import kr.inf.cheonsang.client.sky.CheonsangSky
import kr.inf.cheonsang.client.sky.SkyData
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * `/cfind <이름>` (클라이언트 명령, 0.1.3-b · 0.1.8 이름 변경): 별·행성·달·해·심원천체(M/C 번호)·별자리 위치 한 줄 + 방향 화살표.
 * 1.8.3: 위성·유성우 복사점·달 지명 · 영어 이름 · 별 부호(그리스 문자·플램스티드, 한글/영어/약자/소유격) · HD · 반성(B·C) 도.
 * 시점은 돌리지 않는다. 화살표는 60초 동안, 천체가 화면 안에 들어오면 화살표 대신 테두리 표시.
 * 0.1.3-b~0.1.7 은 `/cele find` 였으나, 서버 `/cele` 와 이름이 겹치면 Fabric 이 클라이언트 하위 명령을 자동완성에서 버리고
 * 나머지 하위 명령은 가로채 서버로 넘기다 튕기는 문제가 있어 따로 뺌. `/cele` 는 서버 명령 그대로.
 */
object FindGuide {
    private const val SHOW_MS = 60_000L
    private const val COLOR = 0xFFFFD050.toInt()
    private const val FOUND_RADIUS = 16.0

    /** 찾을 수 있는 것 하나. names = 입력과 맞춰 볼 이름들 (첫 번째가 자동완성에 뜨는 이름) */
    private class Target(val names: List<String>, val display: String, val dir: () -> Vec3?, val info: () -> String?) {
        /** 1.8.4: true 추적 때 가운데 다시 고르지 않고 이 대상을 그대로 (위성) */
        var lock: (() -> kr.inf.cheonsang.client.Target)? = null
        var lockNote: (() -> String?)? = null
        /** 1.10.0-dev2 `/cwhen`: 무엇인지 (기본 = 고정 천체) · 밝기 */
        var kind = CWhen.Kind.FIXED
        var planet: Planet? = null
        var asteroid: kr.inf.cheonsang.client.sky.Asteroids.Body? = null
        var feature: MoonNames.Feature? = null
        var showerId: String? = null
        var mag: (() -> Double?)? = null
    }

    private var current: Target? = null
    private var until = 0L

    private fun jdNow() = CheonsangSky.clock.jd((Minecraft.getInstance().level?.defaultClockTime ?: 0L).toDouble())

    // ── 1.8.3: 모든 이름 형태 (한글·영어·약자·소유격·그리스 문자·플램스티드·HIP·HD·반성) ──
    private val MOON_EN = linkedMapOf("타이탄" to "Titan", "레아" to "Rhea", "이아페투스" to "Iapetus", "테티스" to "Tethys", "디오네" to "Dione",
        "엔셀라두스" to "Enceladus", "미마스" to "Mimas", "이오" to "Io", "유로파" to "Europa", "가니메데" to "Ganymede", "칼리스토" to "Callisto")
    private val MOON_NUM = mapOf("타이탄" to "Ⅵ", "레아" to "Ⅴ", "이아페투스" to "Ⅷ", "테티스" to "Ⅲ", "디오네" to "Ⅳ", "엔셀라두스" to "Ⅱ", "미마스" to "Ⅰ", "이오" to "Ⅰ", "유로파" to "Ⅱ", "가니메데" to "Ⅲ", "칼리스토" to "Ⅳ")
    private val JOVIAN_KO = setOf("이오", "유로파", "가니메데", "칼리스토")
    private val ROMAN = mapOf("Ⅰ" to "I", "Ⅱ" to "II", "Ⅲ" to "III", "Ⅳ" to "IV", "Ⅴ" to "V", "Ⅵ" to "VI", "Ⅶ" to "VII", "Ⅷ" to "VIII")
    private val ROMAN_NUM = mapOf("Ⅰ" to 1, "Ⅱ" to 2, "Ⅲ" to 3, "Ⅳ" to 4, "Ⅴ" to 5, "Ⅵ" to 6, "Ⅶ" to 7, "Ⅷ" to 8)
    private const val GREEK_SYM = "αβγδεζηθικλμνξοπρστυφχψω"
    private val GREEK_EN = listOf("alpha", "beta", "gamma", "delta", "epsilon", "zeta", "eta", "theta", "iota", "kappa", "lambda", "mu",
        "nu", "xi", "omicron", "pi", "rho", "sigma", "tau", "upsilon", "phi", "chi", "psi", "omega")
    private val GREEK_KO = listOf("알파", "베타", "감마", "델타", "엡실론", "제타", "에타", "세타", "이오타", "카파", "람다", "뮤",
        "뉴", "크사이", "오미크론", "파이", "로", "시그마", "타우", "입실론", "피", "카이", "프사이", "오메가")
    private val GREEK_KO_ALT = mapOf("앱실론" to 5, "요타" to 9, "크시" to 14, "윕실론" to 20, "웁실론" to 20, "키" to 22, "프시" to 23)
    /** IAU 약자 → 라틴어 소유격 (별 부호 "Alpha Lyrae" 용) */
    private val GENITIVE = mapOf(
        "And" to "Andromedae", "Ant" to "Antliae", "Aps" to "Apodis", "Aqr" to "Aquarii", "Aql" to "Aquilae", "Ara" to "Arae", "Ari" to "Arietis",
        "Aur" to "Aurigae", "Boo" to "Bootis", "Cae" to "Caeli", "Cam" to "Camelopardalis", "Cnc" to "Cancri", "CVn" to "Canum Venaticorum",
        "CMa" to "Canis Majoris", "CMi" to "Canis Minoris", "Cap" to "Capricorni", "Car" to "Carinae", "Cas" to "Cassiopeiae", "Cen" to "Centauri",
        "Cep" to "Cephei", "Cet" to "Ceti", "Cha" to "Chamaeleontis", "Cir" to "Circini", "Col" to "Columbae", "Com" to "Comae Berenices",
        "CrA" to "Coronae Australis", "CrB" to "Coronae Borealis", "Crv" to "Corvi", "Crt" to "Crateris", "Cru" to "Crucis", "Cyg" to "Cygni",
        "Del" to "Delphini", "Dor" to "Doradus", "Dra" to "Draconis", "Equ" to "Equulei", "Eri" to "Eridani", "For" to "Fornacis", "Gem" to "Geminorum",
        "Gru" to "Gruis", "Her" to "Herculis", "Hor" to "Horologii", "Hya" to "Hydrae", "Hyi" to "Hydri", "Ind" to "Indi", "Lac" to "Lacertae",
        "Leo" to "Leonis", "LMi" to "Leonis Minoris", "Lep" to "Leporis", "Lib" to "Librae", "Lup" to "Lupi", "Lyn" to "Lyncis", "Lyr" to "Lyrae",
        "Men" to "Mensae", "Mic" to "Microscopii", "Mon" to "Monocerotis", "Mus" to "Muscae", "Nor" to "Normae", "Oct" to "Octantis", "Oph" to "Ophiuchi",
        "Ori" to "Orionis", "Pav" to "Pavonis", "Peg" to "Pegasi", "Per" to "Persei", "Phe" to "Phoenicis", "Pic" to "Pictoris", "Psc" to "Piscium",
        "PsA" to "Piscis Austrini", "Pup" to "Puppis", "Pyx" to "Pyxidis", "Ret" to "Reticuli", "Sge" to "Sagittae", "Sgr" to "Sagittarii",
        "Sco" to "Scorpii", "Scl" to "Sculptoris", "Sct" to "Scuti", "Ser" to "Serpentis", "Sex" to "Sextantis", "Tau" to "Tauri", "Tel" to "Telescopii",
        "Tri" to "Trianguli", "TrA" to "Trianguli Australis", "Tuc" to "Tucanae", "UMa" to "Ursae Majoris", "UMi" to "Ursae Minoris", "Vel" to "Velorum",
        "Vir" to "Virginis", "Vol" to "Volantis", "Vul" to "Vulpeculae",
    )

    private fun planetEn(pl: Planet) = pl.name.lowercase().replaceFirstChar { it.uppercase() }

    private val targets: List<Target> by lazy {
        val list = ArrayList<Target>()
        list += Target(listOf("해", "태양", "Sun", "Sol"), L.t("cheonsang.m.sun"), { CheonsangSky.sunWorld }) { null }.also { it.kind = CWhen.Kind.SUN }
        list += Target(listOf("달", "Moon", "Luna"), L.t("cheonsang.m.moon"), { CheonsangSky.moonWorld }) { L.t("cheonsang.m.phase", (CheonsangSky.moonIllum * 100).roundToInt()) }.also { it.kind = CWhen.Kind.MOON }
        for (pl in Planet.entries) {
            fun pos() = CheonsangSky.planets.firstOrNull { it.planet == pl }
            list += Target(listOf(pl.ko, planetEn(pl)), pl.label, { pos()?.let { CheonsangSky.skyMat3 * it.dir } }) { pos()?.let { L.t("cheonsang.findguide.1").format(it.mag) } }
                .also {
                    it.kind = CWhen.Kind.PLANET; it.planet = pl; it.mag = { pos()?.mag }
                    // 1.10.0-dev2: true 추적은 가운데를 다시 고르지 않고 이 행성 그대로 (어두운 해왕성이 한계 밖이라 근처 C51 이 잡히던 것, 11-14 지율님)
                    it.lock = { TelescopeHud.planetTarget(pos() ?: error("no planet")) }
                }
        }
        // 1.9.0-dev4 천상열차분야지도 별자리 (한글·음역) — 이름 위치로 안내, 서양 별자리 이름과 겹치면 서양이 먼저
        for (k in SkyData.korean) {
            if (k.ko.isEmpty()) continue
            val dirJ = Vec3.fromSpherical(k.labelRa, k.labelDec)
            list += Target(listOf(k.ko, k.en), L.t("cheonsang.korean.target", L.name(k.ko, k.en)), { CheonsangSky.skyMat3 * dirJ }) { L.t("cheonsang.korean.joseon") }.also { it.kind = CWhen.Kind.CONST }
        }
        // 1.9.0-dev2 이름 없는 유명 별 (맨눈 목록에 이름이 있는 별은 그쪽으로)
        for (f in SkyData.famous) {
            if (f.hip > 0 && f.hip in SkyData.starNames) continue
            list += Target(listOf(f.ko, f.en) + f.alt, f.label, {
                (if (f.hip > 0) starIndex(f.hip)?.let { starDir(it) } else null) ?: CheonsangSky.skyMat3 * f.dirJ
            }) { L.t("cheonsang.findguide.1").format(f.fixMag ?: f.mag) + " · " + f.note }.also { it.mag = { f.fixMag ?: f.mag } }
        }
        // 1.8.3 위성: 이름 · 영어 · "토성 Ⅵ"·"토성 6"·"Saturn VI"
        for ((ko, en) in MOON_EN) {
            fun now() = CheonsangSky.moonsNow().firstOrNull { it.ko == ko }
            val pl = if (ko in JOVIAN_KO) Planet.JUPITER else Planet.SATURN
            val num = MOON_NUM.getValue(ko)
            val names = listOf(ko, en, "${pl.ko} $num", "${pl.ko} ${ROMAN_NUM[num]}", "${planetEn(pl)} ${ROMAN[num]}", "${planetEn(pl)} ${ROMAN_NUM[num]}")
            list += Target(names, "${L.moon(ko)} (${pl.label} $num)", { now()?.let { CheonsangSky.skyMat3 * it.dirJ } }) {
                now()?.let { m -> L.t("cheonsang.findguide.3").format(m.mag, pl.label) + if (m.hidden) L.t("cheonsang.findguide.2") else "" }
            }.also { t ->
                t.lock = { val m = now(); TelescopeHud.moonTarget(ko, num, m?.mag ?: 0.0, m?.let { CheonsangSky.skyMat3 * it.dirJ } ?: Vec3(0.0, 1.0, 0.0), pl == Planet.SATURN) }
                t.lockNote = { if (now()?.hidden == true) L.t("cheonsang.findguide.4") else null }
                t.kind = CWhen.Kind.SATELLITE; t.planet = pl; t.mag = { now()?.mag }
            }
        }
        // 1.2.5 소행성
        for (b in kr.inf.cheonsang.client.sky.Asteroids.bodies) {
            fun pos() = CheonsangSky.asteroids.firstOrNull { it.body === b }
            list += Target(listOf(b.ko, b.en, "${b.num} ${b.en}", "${b.num} ${b.ko}"), if (L.ko) "${b.ko} (${b.num} ${b.en})" else "${b.num} ${b.en}", { pos()?.let { CheonsangSky.skyMat3 * it.dir } }) {
                pos()?.let { L.t("cheonsang.findguide.1").format(it.mag) } ?: L.t("cheonsang.findguide.5")
            }.also { it.kind = CWhen.Kind.ASTEROID; it.asteroid = b; it.mag = { pos()?.mag } }
        }
        // 1.2.5 변광성 중 이름 목록에 없는 것 (미라)
        for (v in kr.inf.cheonsang.client.sky.VariableStars.list) if (v.hip !in SkyData.starNames) {
            fun idx() = CheonsangSky.catalog?.let { c -> (0 until c.realCount).firstOrNull { c.hip[it] == v.hip } }
            list += Target(listOf(v.ko), L.t("cheonsang.findguide.6", v.label), { idx()?.let { starDir(it) } }) {
                L.t("cheonsang.findguide.7").format(v.mag(jdNow()))
            }.also { it.mag = { v.mag(jdNow()) } }
        }
        for ((hip, ko) in SkyData.starNames) {
            // "베가(직녀성)" → 베가(직녀성) · 베가 · 직녀성 · Vega 로도 찾음
            list += Target(starAliases(hip, ko), ko, { starIndex(hip)?.let { starDir(it) } }) {
                val v = kr.inf.cheonsang.client.sky.VariableStars.byHip[hip]
                if (v != null) L.t("cheonsang.findguide.7").format(v.mag(jdNow()))
                else starIndex(hip)?.let { L.t("cheonsang.findguide.1").format(CheonsangSky.catalog!!.mag[it]) }
            }.also { it.mag = { kr.inf.cheonsang.client.sky.VariableStars.byHip[hip]?.mag(jdNow()) ?: starIndex(hip)?.let { i -> CheonsangSky.catalog!!.mag[i].toDouble() } } }
        }
        for (o in SkyData.dso) {
            val name = L.name(o.ko, o.en)
            // 1.1.3: NGC·IC·Mel·Cr 번호로도 (앞 0 빼고, "NGC104"·"NGC 104") · 1.8.3: "Messier 31"·"Caldwell 14"
            val cat = Regex("^(NGC|IC|Mel|Cr)0*(\\d+)").find(o.ngc)?.let { listOf("${it.groupValues[1]}${it.groupValues[2]}", "${it.groupValues[1]} ${it.groupValues[2]}") }.orEmpty()
            val long = Regex("^([MC])(\\d+)$").find(o.id)?.let { listOf((if (it.groupValues[1] == "M") "Messier " else "Caldwell ") + it.groupValues[2]) }.orEmpty()
            list += Target(listOfNotNull(o.id, o.ko.ifEmpty { null }, o.en.ifEmpty { null }) + cat + long, if (name.isEmpty()) o.id else "${o.id} $name",
                { CheonsangSky.skyMat3 * Vec3.fromSpherical(o.ra, o.dec) }) { if (o.mag < 90) L.t("cheonsang.findguide.1").format(o.mag) else null }
                .also { it.kind = CWhen.Kind.DEEP; it.mag = { o.mag.takeIf { m -> m < 90 } }; it.lock = { TelescopeHud.dsoTarget(o) } }
        }
        for (c in SkyData.constellations.distinctBy { it.id }) {
            val ko = c.ko.ifEmpty { null }
            list += Target(listOfNotNull(ko, c.en, c.id, GENITIVE[c.id], ko?.removeSuffix("자리")?.takeIf { it != ko && it.isNotEmpty() }), L.name(c.ko, c.en),
                { CheonsangSky.skyMat3 * Vec3.fromSpherical(c.labelRa, c.labelDec) }) { null }.also { it.kind = CWhen.Kind.CONST }
        }
        // 1.8.3 유성우 복사점 (별자리 이름과 겹치는 "거문고자리" 등은 별자리가 먼저 걸림)
        for (s in SkyData.showers) {
            list += Target(listOf(L.t("cheonsang.findguide.10", s.ko), s.ko, L.t("cheonsang.findguide.9", s.ko), s.id), L.t("cheonsang.findguide.8", s.label),
                { CheonsangSky.skyMat3 * Vec3.fromSpherical(s.ra, s.dec) }) { "ZHR %.0f".format(s.zhr) }.also { it.kind = CWhen.Kind.SHOWER; it.showerId = s.id }
        }
        // 1.8.3 달 지명 — 달 뒷면이면 달 가운데로 안내. 2.0.2-dev1: 유명 지명만
        for (f in MoonNames.famous) {
            list += Target(listOfNotNull(f.ko.ifEmpty { null }, f.name), f.title, { MoonNames.world(f.lat, f.lon) ?: CheonsangSky.moonWorld }) {
                L.t("cheonsang.findguide.12", f.typeLabel) + if (MoonNames.world(f.lat, f.lon) == null) L.t("cheonsang.findguide.11") else ""
            }.also { it.kind = CWhen.Kind.FEATURE; it.feature = f }
        }
        list
    }

    private fun starAliases(hip: Int, ko: String): List<String> =
        (listOf(ko) + Regex("""^(.*?)\((.*)\)$""").find(ko)?.groupValues?.drop(1).orEmpty() + listOfNotNull(SkyData.starNamesEn[hip]))
            .map { it.trim() }.filter { it.isNotBlank() }.distinct()

    private val hipIndex: Map<Int, Int> by lazy {
        val cat = CheonsangSky.catalog ?: return@lazy emptyMap()
        val wanted = SkyData.starNames.keys
        (0 until cat.size).filter { cat.hip[it] in wanted }.associateBy { cat.hip[it] }
    }

    private fun starIndex(hip: Int) = if (CheonsangSky.catalog == null) null else hipIndex[hip]

    private fun starDir(i: Int): Vec3 {
        val cat = CheonsangSky.catalog!!
        return CheonsangSky.skyMat3 * Vec3(cat.dir[i * 3].toDouble(), cat.dir[i * 3 + 1].toDouble(), cat.dir[i * 3 + 2].toDouble())
    }

    private const val SUPERS = "⁰¹²³⁴⁵⁶⁷⁸⁹"

    /** 대소문자·띄어쓰기·점·하이픈 무시, 위첨자 → 숫자, 로마 숫자 기호 → 글자, ö → o */
    private fun key(s: String): String {
        val b = StringBuilder()
        for (ch in s.lowercase()) {
            val r = ROMAN[ch.toString()] ?: ROMAN[ch.uppercaseChar().toString()]
            when {
                ch == ' ' || ch == '.' || ch == '-' || ch == '_' || ch == '\'' -> {}
                SUPERS.indexOf(ch) >= 0 -> b.append(SUPERS.indexOf(ch))
                r != null -> b.append(r.lowercase())
                ch == 'ö' -> b.append('o')
                else -> b.append(ch)
            }
        }
        return b.toString()
    }

    /** 이름으로 지금 월드 방향 (1.0.0a /cbench) */
    internal fun dirOf(name: String) = lookup(name)?.dir?.invoke()

    /** 정확히 같은 이름 → 번호(HIP·HD) → 별 부호 → 반성(B·C…) → 앞부분이 같은 이름 → 이름 안에 들어 있음 순서로 */
    private fun lookup(query: String): Target? {
        val q = key(query)
        if (q.isEmpty()) return null
        return targets.firstOrNull { t -> t.names.any { key(it) == q } }
            ?: hipTarget(q)
            ?: hdTarget(q)
            ?: designationTarget(q)
            ?: companionTarget(q)
            ?: targets.firstOrNull { t -> t.names.any { key(it).startsWith(q) } }
            ?: targets.firstOrNull { t -> t.names.any { key(it).contains(q) } }
    }

    /** 카탈로그 별 하나 → 찾기 대상 (이름 · 별자리 부호 · HIP) */
    private fun starTarget(i: Int): Target {
        val cat = CheonsangSky.catalog!!
        val hip = cat.hip[i]
        val name = if (hip > 0) SkyData.starLabel(hip) else null
        val con = cat.con[i].takeIf { it >= 0 }?.let { SkyData.constellations.getOrNull(it) }
        val desig = con?.let { cat.designation(i, L.name(it.ko, it.en)) }
        val parts = listOfNotNull(name, desig, if (hip > 0) "HIP $hip" else null)
        val label = if (parts.isEmpty()) L.t("cheonsang.findguide.13") else parts[0] + if (parts.size > 1) " (" + parts.drop(1).joinToString(" · ") + ")" else ""
        return Target(listOf(label), label, { starDir(i) }) { L.t("cheonsang.findguide.1").format(cat.mag[i]) }.also { it.mag = { cat.mag[i].toDouble() } }
    }

    /** 1.6.0: "HIP 21421"·"hip21421"·"21421" → 카탈로그 전체(이름 없는 별 포함)에서 HIP 번호로 */
    private fun hipTarget(q: String): Target? {
        val n = Regex("^(?:hip)?(\\d{1,6})$").find(q)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val cat = CheonsangSky.catalog ?: return null
        val i = (0 until cat.realCount).firstOrNull { cat.hip[it] == n } ?: return null
        return starTarget(i)
    }

    /** 1.8.3: "HD 48915" */
    private fun hdTarget(q: String): Target? {
        val n = Regex("^hd(\\d{1,6})$").find(q)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val cat = CheonsangSky.catalog ?: return null
        val hd = SkyData.starInfo?.hd ?: return null
        val i = (0 until minOf(cat.realCount, hd.size)).firstOrNull { hd[it] == n } ?: return null
        return starTarget(i)
    }

    /** 별자리 이름 모든 형태 (key 정규화) → 별자리 id. 긴 것부터 */
    private val conTokens: List<Pair<String, String>> by lazy {
        val out = ArrayList<Pair<String, String>>()
        for (c in SkyData.constellations) {
            val forms = listOfNotNull(c.ko.ifEmpty { null }, c.ko.removeSuffix("자리").ifEmpty { null }, c.en, c.id, GENITIVE[c.id])
            for (f in forms) out += key(f) to c.id
        }
        out.distinct().sortedByDescending { it.first.length }
    }

    private val greekTokens: List<Pair<String, Int>> by lazy {
        val out = ArrayList<Pair<String, Int>>()
        for (g in 0 until 24) { out += GREEK_SYM[g].toString() to g + 1; out += GREEK_EN[g] to g + 1; out += GREEK_KO[g] to g + 1 }
        for ((k, g) in GREEK_KO_ALT) out += k to g
        out.sortedByDescending { it.first.length }
    }

    /** "α"·"alpha"·"알파"·"ο2" → (문자 1~24, 위첨자 0~9) / "58" → 플램스티드 (0, -58) */
    private fun parseLetter(s: String): Pair<Int, Int>? {
        if (s.isEmpty()) return null
        if (s.all { it.isDigit() }) return s.toIntOrNull()?.takeIf { it > 0 }?.let { 0 to -it }
        for ((tok, g) in greekTokens) {
            if (!s.startsWith(tok)) continue
            val rest = s.substring(tok.length)
            if (rest.isEmpty()) return g to 0
            if (rest.length == 1 && rest[0].isDigit()) return g to (rest[0] - '0')
        }
        return null
    }

    /** 1.8.3 별 부호: "거문고자리 α"·"α 거문고자리"·"알파 거문고"·"α Lyr"·"Alpha Lyrae"·"Lyr 3"·"3 Lyrae"·"백조자리 61" */
    private fun designationTarget(q: String): Target? {
        val cat = CheonsangSky.catalog ?: return null
        for ((tok, id) in conTokens) {
            val rest = when {
                q.startsWith(tok) -> q.substring(tok.length)
                q.endsWith(tok) -> q.substring(0, q.length - tok.length)
                else -> continue
            }
            val (g, n) = parseLetter(rest) ?: continue
            val conIdx = SkyData.constellations.indices.filter { SkyData.constellations[it].id == id }.toSet()
            // 밝은 순 정렬이라 첫 번째가 가장 밝은 것 (ο¹·ο² 를 번호 없이 치면 밝은 쪽)
            val i = (0 until cat.realCount).firstOrNull { k ->
                cat.con[k] in conIdx && if (g > 0) cat.greek[k].toInt() == g && (n == 0 || cat.sup[k].toInt() == n) else cat.flam[k].toInt() == -n
            } ?: continue
            return starTarget(i)
        }
        return null
    }

    /** 1.8.3 반성: "시리우스 B"·"알마크 C" — 주성 120″ 안 다른 별을 밝은 순으로 B, C, … */
    private fun companionTarget(q: String): Target? {
        if (q.length < 2) return null
        val letter = q.last()
        if (letter !in 'b'..'f') return null
        val cat = CheonsangSky.catalog ?: return null
        val primary = primaryIndex(q.dropLast(1)) ?: return null
        val px = cat.dir[primary * 3]; val py = cat.dir[primary * 3 + 1]; val pz = cat.dir[primary * 3 + 2]
        val cosMax = cos(Math.toRadians(120.0 / 3600.0))
        val near = (0 until cat.realCount).filter { k ->
            k != primary && px * cat.dir[k * 3] + py * cat.dir[k * 3 + 1] + pz * cat.dir[k * 3 + 2] > cosMax
        }
        val i = near.getOrNull(letter - 'b') ?: return null
        val t = starTarget(i)
        val name = primaryName(primary) + " " + letter.uppercaseChar()
        return Target(listOf(name), "$name (${t.display})", t.dir, t.info).also { it.mag = t.mag }
    }

    /** 반성 찾기용: 이름·HIP·HD·별 부호로 주성 카탈로그 번호 */
    private fun primaryIndex(base: String): Int? {
        if (base.isEmpty()) return null
        for ((hip, ko) in SkyData.starNames) if (starAliases(hip, ko).any { key(it) == base }) return starIndex(hip)
        val cat = CheonsangSky.catalog ?: return null
        Regex("^(?:hip)?(\\d{1,6})$").find(base)?.groupValues?.get(1)?.toIntOrNull()?.let { n -> return (0 until cat.realCount).firstOrNull { cat.hip[it] == n } }
        val t = hdTarget(base) ?: designationTarget(base) ?: return null
        val d = t.dir() ?: return null
        val m = CheonsangSky.skyMat3.m
        val jx = m[0] * d.x + m[3] * d.y + m[6] * d.z; val jy = m[1] * d.x + m[4] * d.y + m[7] * d.z; val jz = m[2] * d.x + m[5] * d.y + m[8] * d.z
        return (0 until cat.realCount).maxByOrNull { k -> jx * cat.dir[k * 3] + jy * cat.dir[k * 3 + 1] + jz * cat.dir[k * 3 + 2] }
    }

    private fun primaryName(i: Int): String {
        val cat = CheonsangSky.catalog!!
        val hip = cat.hip[i]
        if (hip > 0) SkyData.starLabel(hip)?.let { return it }
        val con = cat.con[i].takeIf { it >= 0 }?.let { SkyData.constellations.getOrNull(it) }
        return con?.let { cat.designation(i, L.name(it.ko, it.en)) } ?: if (hip > 0) "HIP $hip" else L.t("cheonsang.findguide.14")
    }

    internal val COMPASS get() = arrayOf(L.t("cheonsang.findguide.30"), L.t("cheonsang.findguide.29"), L.t("cheonsang.findguide.28"), L.t("cheonsang.findguide.27"), L.t("cheonsang.findguide.26"), L.t("cheonsang.findguide.25"), L.t("cheonsang.findguide.24"), L.t("cheonsang.findguide.23"), L.t("cheonsang.findguide.22"), L.t("cheonsang.findguide.21"), L.t("cheonsang.findguide.20"), L.t("cheonsang.findguide.19"), L.t("cheonsang.findguide.18"), L.t("cheonsang.findguide.17"), L.t("cheonsang.findguide.16"), L.t("cheonsang.findguide.15"))

    // ── 1.10.0-dev2 `/cwhen <천체>`: /cfind 와 같은 찾기 ──
    /** 찾은 것 하나 (메인 스레드에서 만든 값 — 계산 스레드로 넘김) */
    internal class Found(val name: String, val display: String, val kind: CWhen.Kind, val dirJ: Vec3?, val mag: Double?, val planet: Planet?,
                         val asteroid: kr.inf.cheonsang.client.sky.Asteroids.Body?, val feature: MoonNames.Feature?, val showerId: String?)

    /** 이름 → (찾은 것, 다른 후보 이름 3개까지). 세계 방향 → J2000 은 skyMat3 의 전치 */
    internal fun whenLookup(query: String): Pair<Found?, List<String>> {
        val t = lookup(query) ?: return null to emptyList()
        val q = key(query)
        val others = (targets.filter { o -> o.names.any { key(it) == q } } + targets.filter { o -> o.names.any { key(it).startsWith(q) } } +
            targets.filter { o -> o.names.any { key(it).contains(q) } }).filter { it !== t && it.display != t.display }.distinctBy { it.display }.take(3).map { it.names[0] }
        val w = t.dir()?.normalized()
        val m = CheonsangSky.skyMat3.m
        val dirJ = w?.let { d -> Vec3(m[0] * d.x + m[3] * d.y + m[6] * d.z, m[1] * d.x + m[4] * d.y + m[7] * d.z, m[2] * d.x + m[5] * d.y + m[8] * d.z).normalized() }
        return Found(t.names[0], t.display, t.kind, dirJ, t.mag?.invoke(), t.planet, t.asteroid, t.feature, t.showerId) to others
    }

    /** 자동완성 이름 (/cfind 와 같은 순서) */
    internal fun whenSuggest(text: String): List<String> {
        val q = key(text)
        val all = targets.flatMap { it.names }.distinct()
        return (all.filter { key(it).startsWith(q) } + all.filter { q.isNotEmpty() && !key(it).startsWith(q) && key(it).contains(q) }).take(60)
    }

    /** 못 찾았을 때 비슷한 이름 3개 (앞 두 글자 → 앞 한 글자가 들어 있는 이름) */
    internal fun similar(text: String): List<String> {
        val q = key(text)
        if (q.isEmpty()) return emptyList()
        val all = targets.flatMap { it.names }.distinct()
        for (n in listOf(minOf(2, q.length), 1)) { val p = q.substring(0, n); val r = all.filter { key(it).contains(p) }.take(3); if (r.isNotEmpty()) return r }
        return emptyList()
    }

    internal fun nameKey(s: String) = key(s)

    fun register() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                ClientCommands.literal("cfind").then(
                    ClientCommands.argument("name", StringArgumentType.greedyString())
                        .suggests { _, b ->
                            // 1.10.1-dev7: 이름 뒤 공백이면 true·false 를 끝 단어 자리에 (바닐라 on/off 처럼)
                            val rem = b.remaining
                            val sp = rem.lastIndexOf(' ')
                            if (sp > 0) {
                                val word = rem.substring(sp + 1).lowercase()
                                val tf = listOf("true", "false").filter { it.startsWith(word) }
                                if (tf.isNotEmpty() && lookup(rem.substring(0, sp).trim()) != null) {
                                    val ob = b.createOffset(b.start + sp + 1)
                                    tf.forEach { ob.suggest(it) }
                                    return@suggests ob.buildFuture()
                                }
                            }
                            val q = key(b.remaining)
                            // 1.8.3: 첫 이름만이 아니라 모든 이름 (앞부분이 같은 것 먼저)
                            val all = targets.flatMap { it.names }.distinct()
                            (all.filter { key(it).startsWith(q) } + all.filter { q.isNotEmpty() && !key(it).startsWith(q) && key(it).contains(q) })
                                .take(60).forEach { b.suggest(it) }
                            b.buildFuture()
                        }
                        .executes { c ->
                            // 1.5.4: 끝 단어 true/false = 바로 추적 (true 면 그 천체로 시점 + 망원경을 들고 있으면 켜고 추적 고정)
                            val raw = StringArgumentType.getString(c, "name").trim()
                            val last = raw.substringAfterLast(' ').lowercase()
                            val auto = last == "true"
                            val name = if ((last == "true" || last == "false") && raw.contains(' ')) raw.substringBeforeLast(' ').trim() else raw
                            val t = lookup(name)
                            val dir = t?.dir?.invoke()
                            when {
                                !CheonsangSky.isActive() -> c.source.sendError(Component.literal(L.t("cheonsang.findguide.31")))
                                t == null || dir == null -> c.source.sendError(Component.literal(L.t("cheonsang.findguide.32", name)))
                                else -> {
                                    val d = dir.normalized()
                                    val alt = asinD(d.y)
                                    // 월드: 동 = +X, 북 = -Z
                                    val az = norm360(atan2D(d.x, -d.z))
                                    val parts = mutableListOf(t.display, L.t("cheonsang.findguide.33").format(alt, az, COMPASS[((az + 11.25) / 22.5).toInt() % 16]))
                                    t.info()?.let { parts += it }
                                    c.source.sendFeedback(Component.literal(parts.joinToString(" | ")))
                                    current = t
                                    until = System.currentTimeMillis() + SHOW_MS
                                    if (auto) {
                                        if (alt < -0.5) c.source.sendError(Component.literal(L.t("cheonsang.findguide.34")))
                                        else {
                                            val on = TelescopeView.ensureOn()
                                            TelescopeView.lookAt(Visibility.refractWorld(d))
                                            if (on) { val lk = t.lock; val nt = t.lockNote; if (lk != null) TelescopeHud.lockSoon(lk) { nt?.invoke() } else TelescopeHud.lockCenterSoon() }
                                            current = null
                                        }
                                    }
                                    // 1.10.1-dev8: 지금 관측 조건이 아니면 가장 좋은 밤 + [위치]·[이동](OP)·[다음] (/cwhen <천체> 와 같은 계산)
                                    try { CWhen.afterFind(c.source, name) } catch (e: Exception) { Cheonsang.LOG.warn("Cheonsang: /cfind when", e) }
                                }
                            }
                            1
                        },
                ),
            )
        }
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register { _ -> Safety.run(Safety.FIND, Unit) { TelescopeHud.tickLock() } }
        HudElementRegistry.addLast(Cheonsang.id("find_guide")) { g, _ -> if (!TelescopeHud.hideAll) Safety.run(Safety.FIND, Unit) { draw(g) } }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> current = null }
    }


    private fun draw(g: GuiGraphicsExtractor) {
        val t = current ?: return
        if (System.currentTimeMillis() > until || !CheonsangSky.isActive()) { current = null; return }
        val dir = t.dir()?.normalized() ?: return
        val mc = Minecraft.getInstance()
        val pr = TelescopeHud.Projector(mc)
        val p = pr.project(dir)
        val inView = p != null && p[0] in 8.0..pr.w - 8 && p[1] in 8.0..pr.h - 8 && (!TelescopeView.active || pr.fromCenter(p) < pr.radius * 0.8)
        // 조준선 가운데(반경 16 GUI 픽셀)에 들어오면 찾은 것으로 보고 표시 끝 (0.1.9)
        if (p != null && pr.fromCenter(p) <= FOUND_RADIUS) { current = null; return }
        if (inView) {
            // 테두리 (모서리 4개)
            val x = p!![0].roundToInt(); val y = p[1].roundToInt(); val r = 7; val k = 3
            g.fill(x - r, y - r, x - r + k, y - r + 1, COLOR); g.fill(x - r, y - r, x - r + 1, y - r + k, COLOR)
            g.fill(x + r - k + 1, y - r, x + r + 1, y - r + 1, COLOR); g.fill(x + r, y - r, x + r + 1, y - r + k, COLOR)
            g.fill(x - r, y + r, x - r + k, y + r + 1, COLOR); g.fill(x - r, y + r - k + 1, x - r + 1, y + r + 1, COLOR)
            g.fill(x + r - k + 1, y + r, x + r + 1, y + r + 1, COLOR); g.fill(x + r, y + r - k + 1, x + r + 1, y + r + 1, COLOR)
            return
        }
        // 화살표: 카메라 기준 오른쪽·위 성분으로 돌아야 할 방향 (뒤쪽이어도 성분 방향 그대로)
        val right = -(dir.x * pr.l.x() + dir.y * pr.l.y() + dir.z * pr.l.z())
        val up = dir.x * pr.u.x() + dir.y * pr.u.y() + dir.z * pr.u.z()
        val angle = if (abs(right) < 1e-6 && abs(up) < 1e-6) Math.PI / 2 else atan2(-up, right)
        val dist = 34.0
        val cx = pr.w / 2 + cos(angle) * dist; val cy = pr.h / 2 + sin(angle) * dist
        g.pose().pushMatrix()
        g.pose().translate(cx.toFloat(), cy.toFloat())
        g.pose().rotate(angle.toFloat())
        g.fill(-6, 0, 2, 1, COLOR)
        for (i in 0..4) g.fill(2 + i, -(4 - i), 3 + i, 5 - i, COLOR)
        g.pose().popMatrix()
    }
}
