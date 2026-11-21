package kr.inf.cheonsang.client

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import kr.inf.cheonsang.Cheonsang
import kr.inf.cheonsang.L
import kr.inf.cheonsang.SkyServer
import kr.inf.cheonsang.astro.Calendar
import kr.inf.cheonsang.astro.Events
import kr.inf.cheonsang.astro.Frames
import kr.inf.cheonsang.astro.Moon
import kr.inf.cheonsang.astro.Planet
import kr.inf.cheonsang.astro.Planets
import kr.inf.cheonsang.astro.Sun
import kr.inf.cheonsang.astro.Vec3
import kr.inf.cheonsang.astro.WhenCalc
import kr.inf.cheonsang.astro.norm180
import kr.inf.cheonsang.astro.toSpherical
import kr.inf.cheonsang.client.sky.CheonsangSky
import kr.inf.cheonsang.client.sky.SkyData
import kr.inf.cheonsang.client.sky.Shower
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.MutableComponent
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 1.10.0-dev2 `/cwhen` (설계서 cwhen_설계_1.10.0, 11-14 10:16 지율님 확정) — 클라이언트 명령, OP 필요 없음.
 * - `/cwhen` = 오늘 밤 (`/ctonight` 그대로) · `/cwhen <천체>` · `solar total|annular|partial` · `lunar total|partial|penumbral`
 *   · `meteor [이름]` · `full` · `new` · `conj` · `next`. 명령 낱말은 영어만, 천체 이름은 /cfind 와 같은 찾기 (한국어·영어)
 * - 일식·월식·유성우는 지구 어디든 가장 좋은 곳 → [이동] = `/cplace 위도 경도` (내 관측 위치만, 시각은 그대로)
 * - 시각 = 그곳 지방평균시 (= 그곳으로 옮긴 뒤 마크 시계). 알림 없음
 * - 계산은 별도 스레드 하나, 새로 치면 앞 계산 취소. 숨은 쪽수 `@<율리우스일>` 은 [다음] 이 붙임
 */
object CWhen {
    enum class Kind { FIXED, DEEP, CONST, SHOWER, SUN, MOON, PLANET, ASTEROID, SATELLITE, FEATURE }

    private val WORDS = listOf("solar", "lunar", "meteor", "full", "new", "conj", "next")
    private val SOLAR_TYPES = listOf("total", "annular", "partial")
    private val LUNAR_TYPES = listOf("total", "partial", "penumbral")

    private val pool = Executors.newSingleThreadExecutor { r -> Thread(r, "Cheonsang cwhen").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 } }
    private val gen = AtomicInteger()

    /** `/ctonight` 처럼 자동완성 목록(바닐라 명령 나무로 옮기는 중)에만 안 보이게 — 실행은 됨 */
    internal fun copyingTree(): Boolean = try {
        StackWalker.getInstance().walk { s -> s.anyMatch { it.methodName == "copyChildren" && it.className.endsWith("ClientCommandInternals") } }
    } catch (_: Exception) { false }

    fun register() {
        ClientCommandRegistrationCallback.EVENT.register { d, _ ->
            val root = ClientCommands.literal("cwhen")
                .executes { c -> Safety.run(Safety.TONIGHT, Unit) { Tonight.report().forEach { line -> c.source.sendFeedback(Component.literal(line)) } }; 1 }
            // 2.0.3-dev1: `/cwhen <천체> <밝은 비율%>` — 천체 이름 뒤에 숫자가 오면 그 위상이 되는 다음 순간 (값 범위는 입력 중에 표시)
            for (b in PHASE_BODIES) for (key in b.keys) root.then(ClientCommands.literal(key)
                .executes { c -> Safety.run(Safety.TONIGHT, Unit) { run(c.source, key) }; 1 }
                .then(ClientCommands.argument("pct", PhaseArg(b.min)).executes { c -> Safety.run(Safety.TONIGHT, Unit) { phase(c.source, b, PhaseArg.get(c, "pct")) }; 1 }))
            d.register(root
                .then(ClientCommands.argument("what", StringArgumentType.greedyString())
                    .suggests { _, b -> suggest(b) }
                    .executes { c -> Safety.run(Safety.TONIGHT, Unit) { run(c.source, StringArgumentType.getString(c, "what")) }; 1 }))
        }
    }

    // ── 2.0.3-dev1 위상 % ──
    /** min = 가능한 가장 작은 밝은 비율 % (외행성: 지구가 구 위치일 때 최대 위상각, 근일점 거리로 — (1+cos i)/2) */
    private class PhaseBody(val keys: List<String>, val planet: Planet?, val min: Double) {
        val label: String get() = planet?.let { if (L.ko) it.ko else it.name.lowercase().replaceFirstChar { c -> c.uppercase() } } ?: L.t("cheonsang.m.moon")
        fun illum(jd: Double): Double = (if (planet == null) Moon.phase(Moon.position(jd), Sun.position(jd)).illuminated else Planets.position(planet, jd).illuminated) * 100
        /** (고도, 방위) — 달은 그날 적도, 행성은 J2000 */
        fun altAz(jd: Double, lat: Double, lon: Double): Pair<Double, Double> =
            if (planet == null) Events.altAz(jd, lat, lon, Moon.position(jd).dir, false) else Events.altAz(jd, lat, lon, Planets.position(planet, jd).dir, true)
        /** 보기 좋은 때를 찾는 범위 (일) · 해 고도 문턱 */
        val window: Double get() = if (planet == null) 1.0 else 5.0
        val sunMax: Double get() = if (planet == null) -6.0 else -12.0
        /** 찾는 기간 (일) — 회합 주기 한 번 남짓 · 표본 간격 (일) */
        val span: Double get() = when (planet) { null -> 32.0; Planet.MERCURY -> 130.0; Planet.VENUS -> 600.0; Planet.MARS -> 800.0; else -> 420.0 }
        val step: Double get() = if (planet == null) 0.25 else 1.0
    }
    private val PHASE_BODIES = listOf(
        PhaseBody(listOf("moon", "달"), null, 0.0), PhaseBody(listOf("mercury", "수성"), Planet.MERCURY, 0.0), PhaseBody(listOf("venus", "금성"), Planet.VENUS, 0.0),
        PhaseBody(listOf("mars", "화성"), Planet.MARS, 84.5), PhaseBody(listOf("jupiter", "목성"), Planet.JUPITER, 98.9), PhaseBody(listOf("saturn", "토성"), Planet.SATURN, 99.6),
        PhaseBody(listOf("uranus", "천왕성"), Planet.URANUS, 99.9), PhaseBody(listOf("neptune", "해왕성"), Planet.NEPTUNE, 99.9))

    private fun phase(src: FabricClientCommandSource, b: PhaseBody, v: PhaseArg.Value) {
        val level = Minecraft.getInstance().level ?: run { src.sendError(Component.literal(L.t("cheonsang.tonight.1"))); return }
        if (!CheonsangSky.isActive()) { src.sendError(Component.literal(L.t("cheonsang.findguide.31"))); return }
        val h = Here(CheonsangSky.clock.jd(level.defaultClockTime.toDouble()), CheonsangSky.latitude, CheonsangSky.longitude, CheonsangSky.nakedLimit.toDouble(), canSetDate())
        calc(src) { alive -> phaseLines(h, b, v, alive) }
    }

    private fun phaseLines(h: Here, b: PhaseBody, v: PhaseArg.Value, alive: () -> Boolean): List<Component> {
        val n = (b.span / b.step).toInt()
        val xs = DoubleArray(n + 1) { h.jd0 + it * b.step }
        val ys = DoubleArray(n + 1) { if (!alive()) throw WhenCalc.Cancel(); b.illum(xs[it]) - v.pct }
        // 2.0.3-dev3: 그 값을 지나는 순간을 방향별로 모두 모음 (외행성은 충·합 둘 다 100% 근처라 첫 순간이 해 뒤일 수 있음)
        val crossings = HashMap<Int, MutableList<Double>>()
        for (i in 0 until n) {
            val d = if (ys[i] < 0 && ys[i + 1] >= 0) 1 else if (ys[i] > 0 && ys[i + 1] <= 0) -1 else 0
            if (d == 0) continue
            var lo = xs[i]; var hi = xs[i + 1]; val fl = ys[i]
            repeat(40) { val m = (lo + hi) / 2; val fm = b.illum(m) - v.pct; if ((fm < 0) == (fl < 0)) lo = m else hi = m }
            crossings.getOrPut(d) { mutableListOf() } += (lo + hi) / 2
        }
        val dirs = if (v.dir == 0) listOf(1, -1) else listOf(v.dir)
        val out = mutableListOf<Component>()
        val pctText = if (v.pct == Math.floor(v.pct)) "%.0f".format(v.pct) else "%.1f".format(v.pct)
        for (d in dirs) {
            val list = crossings[d] ?: continue
            val tag = L.t(if (d > 0) "cheonsang.cwhen.phase_up" else "cheonsang.cwhen.phase_down")
            out += pickLine(h, b, list, L.t("cheonsang.cwhen.phase_line", b.label, pctText, tag), alive)
        }
        if (out.isEmpty()) {
            // 그 값을 지나지 않음 → 그 값에 가장 가까워지는 순간들(극대·극소)을 가까운 순서로
            val ext = mutableListOf<Double>()
            for (i in 1 until n) {
                val a = kotlin.math.abs(ys[i]); if (a > kotlin.math.abs(ys[i - 1]) || a > kotlin.math.abs(ys[i + 1])) continue
                var lo = xs[i - 1]; var hi = xs[i + 1]
                repeat(40) {
                    val m1 = lo + (hi - lo) * 0.382; val m2 = lo + (hi - lo) * 0.618
                    if (kotlin.math.abs(b.illum(m1) - v.pct) < kotlin.math.abs(b.illum(m2) - v.pct)) hi = m2 else lo = m1
                }
                ext += (lo + hi) / 2
            }
            if (ext.isEmpty()) ext += xs[ys.indices.minByOrNull { kotlin.math.abs(ys[it]) }!!]
            val bestGap = ext.minOf { kotlin.math.abs(b.illum(it) - v.pct) }
            val near = ext.filter { kotlin.math.abs(b.illum(it) - v.pct) < bestGap + 0.05 }  // 거의 같은 값(충·합)은 모두 후보
            val nv = b.illum(near.first())
            out += pickLine(h, b, near, L.t(if (nv > v.pct) "cheonsang.cwhen.phase_min" else "cheonsang.cwhen.phase_max", b.label, "%.2f".format(nv)), alive)
        }
        return out
    }

    /** 후보 순간들 중 어두울 때 보이는 첫 순간으로 한 줄. 하나도 안 보이면 첫 후보 + "어두울 때 안 보임" */
    private fun pickLine(h: Here, b: PhaseBody, candidates: List<Double>, head: String, alive: () -> Boolean): Component {
        for (jd in candidates.sorted()) {
            if (!alive()) throw WhenCalc.Cancel()
            val v = bestView(h, b, jd) ?: continue
            val l = Component.literal(head + " " + L.t("cheonsang.cwhen.phase_view", lt(v.first, h.lon), compass(v.third), v.second.roundToInt(), "%.0f".format(b.illum(v.first))))
            moveButton(l, h, v.first); return l
        }
        val jd0 = candidates.minOrNull()!!
        val l = Component.literal(head + " " + lt(jd0, h.lon) + " " + L.t("cheonsang.cwhen.phase_dark"))
        moveButton(l, h, jd0); return l
    }

    /**
     * 2.0.3-dev2: 위상 순간 앞뒤(달 ±1일 · 행성 ±5일)에서 해가 문턱 아래이고 천체가 가장 높은 때 (시각, 고도, 방위).
     * 그 사이 어두울 때 고도 3° 위로 안 뜨면 null
     */
    private fun bestView(h: Here, b: PhaseBody, jd0: Double): Triple<Double, Double, Double>? {
        val step = if (b.planet == null) 10.0 / 1440 else 30.0 / 1440
        val k = if (b.planet == null) 4.0 else 0.5  // 위상 순간에서 멀수록 조금 깎음 (같은 높이면 가까운 밤)
        var best: Triple<Double, Double, Double>? = null; var bestScore = -1e9
        var t = jd0 - b.window
        while (t <= jd0 + b.window) {
            if (Events.altOfDate(t, h.lat, h.lon, Sun.position(t).dir) < b.sunMax) {
                val (alt, az) = b.altAz(t, h.lat, h.lon)
                val score = alt - kotlin.math.abs(t - jd0) * k
                if (alt > 3.0 && score > bestScore) { best = Triple(t, alt, az); bestScore = score }
            }
            t += step
        }
        return best
    }

    // ── 자동완성 ──
    private fun suggest(b: SuggestionsBuilder): java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> {
        val text = b.remaining
        val sp = text.indexOf(' ')
        if (sp < 0) {
            val q = text.lowercase()
            WORDS.filter { it.startsWith(q) }.forEach { b.suggest(it) }
            try { FindGuide.whenSuggest(text).forEach { b.suggest(it) } } catch (_: Exception) {}
            return b.buildFuture()
        }
        val first = text.substring(0, sp).lowercase()
        val rest = text.substring(sp + 1)
        val sub = b.createOffset(b.start + sp + 1)
        val opts = when (first) {
            "solar" -> SOLAR_TYPES
            "lunar" -> LUNAR_TYPES
            "meteor" -> SkyData.showers.map { it.id } + SkyData.showers.map { it.en }
            else -> null
        }
        if (opts != null) {
            if (rest.contains(' ')) return sub.buildFuture()
            opts.filter { it.lowercase().startsWith(rest.lowercase()) }.forEach { sub.suggest(it) }
            return sub.buildFuture()
        }
        try { FindGuide.whenSuggest(text).forEach { b.suggest(it) } } catch (_: Exception) {}
        return b.buildFuture()
    }

    // ── 실행 ──
    /** 그 순간 내 하늘 (메인 스레드에서 읽음) */
    private class Here(val jd0: Double, val lat: Double, val lon: Double, val naked: Double, val date: Boolean)

    private fun run(src: FabricClientCommandSource, raw: String) {
        val level = Minecraft.getInstance().level ?: run { src.sendError(Component.literal(L.t("cheonsang.tonight.1"))); return }
        if (!CheonsangSky.isActive()) { src.sendError(Component.literal(L.t("cheonsang.findguide.31"))); return }
        val words = raw.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.toMutableList()
        var page: Double? = null
        if (words.size > 1 && words.last().startsWith("@")) page = words.removeAt(words.size - 1).drop(1).toDoubleOrNull()
        if (words.isEmpty()) { Tonight.report().forEach { src.sendFeedback(Component.literal(it)) }; return }
        val here = Here(CheonsangSky.clock.jd(level.defaultClockTime.toDouble()), CheonsangSky.latitude, CheonsangSky.longitude, CheonsangSky.nakedLimit.toDouble(), canSetDate())
        val first = words[0].lowercase()
        val arg = words.drop(1).joinToString(" ")
        if (first == "go") { goTo(src, words); return }
        when (first) {
            "solar", "lunar" -> {
                val types = if (first == "solar") SOLAR_TYPES else LUNAR_TYPES
                val t = words.getOrNull(1)?.lowercase()
                if (t == null || t !in types || words.size > 2) { src.sendFeedback(chooseLine(first, types)); return }
                calc(src) { alive -> if (first == "solar") solar(here, t, page, alive) else lunar(here, t, page, alive) }
            }
            "meteor" -> {
                val sh = if (arg.isEmpty()) null else findShower(arg) ?: run {
                    src.sendError(Component.literal(L.t("cheonsang.findguide.32", arg) + L.t("cheonsang.cwhen.showers", SkyData.showers.joinToString(" ") { it.id })))
                    return
                }
                val list = SkyData.showers.map { Events.Shower(it.ko, it.peakLon, it.ra, it.dec, it.zhr, it.en1) to it }
                calc(src, quiet = true) { alive -> meteor(here, list, sh, arg, page, alive) }
            }
            "full", "new", "conj" -> calc(src, quiet = true) { alive -> simple(here, first, page, alive) }
            "next" -> {
                val list = SkyData.showers.map { Events.Shower(it.ko, it.peakLon, it.ra, it.dec, it.zhr, it.en1) to it }
                calc(src) { alive -> next(here, list, page, alive) }
            }
            else -> {
                val name = words.joinToString(" ")
                val (f, others) = FindGuide.whenLookup(name)
                if (f == null) {
                    val sim = FindGuide.similar(name)
                    val msg = Component.literal(L.t("cheonsang.findguide.32", name))
                    if (sim.isNotEmpty()) { msg.append(L.t("cheonsang.cwhen.similar")); appendNames(msg, sim) }
                    src.sendFeedback(msg.withStyle(ChatFormatting.RED))
                    return
                }
                calc(src) { alive -> objectLines(here, f, others, page, alive) }
            }
        }
    }

    /**
     * 1.10.1-dev8: /cfind 뒤에 붙임 (11-14 17:44 지율님 "cwhen 이랑 cfind 합쳐서") — 지금 관측 조건(지평선 위 + 해가 그 천체 기준 문턱 아래)이
     * 아니면 /cwhen <천체> 의 "가장 좋은 밤" 줄과 [위치]·[이동]·[다음] 을 이어서 보냄. 머리줄은 /cfind 가 이미 보냈으므로 뺌. 해는 안 함
     */
    internal fun afterFind(src: FabricClientCommandSource, name: String) {
        val level = Minecraft.getInstance().level ?: return
        val (f, _) = FindGuide.whenLookup(name)
        if (f == null || f.kind == Kind.SUN) return
        val here = Here(CheonsangSky.clock.jd(level.defaultClockTime.toDouble()), CheonsangSky.latitude, CheonsangSky.longitude, CheonsangSky.nakedLimit.toDouble(), canSetDate())
        val pos = posFn(f) ?: return
        val cls = clsFor(f, here)
        val alt = Events.altOfDate(here.jd0, here.lat, here.lon, pos(here.jd0))
        val sun = Events.altOfDate(here.jd0, here.lat, here.lon, Sun.position(here.jd0).dir)
        if (alt >= 0.0 && sun <= cls.sunThr) return
        calc(src, quiet = true) { alive -> objectLines(here, f, emptyList(), null, alive).drop(1) }
    }

    private fun calc(src: FabricClientCommandSource, quiet: Boolean = false, block: (() -> Boolean) -> List<Component>) {
        val id = gen.incrementAndGet()
        val alive = { gen.get() == id }
        if (!quiet) src.sendFeedback(Component.literal(L.t("cheonsang.cwhen.calc")).withStyle(ChatFormatting.GRAY))
        pool.execute {
            val out = try { block(alive) } catch (_: WhenCalc.Cancel) { return@execute } catch (e: Exception) {
                Cheonsang.LOG.warn("Cheonsang: /cwhen failed", e)
                listOf(Component.literal(L.t("cheonsang.cwhen.error")).withStyle(ChatFormatting.RED))
            }
            if (alive()) Minecraft.getInstance().execute { if (alive()) out.forEach { src.sendFeedback(it) } }
        }
    }

    // ── 글자 조각 ──
    private fun lt(jd: Double, lon: Double) = Calendar.toLocal(jd, lon / 15.0).toString()
    private fun hm(jd: Double, lon: Double) = lt(jd, lon).substring(11)
    private fun place(lat: Double, lon: Double) = SkyServer.placeText(lat, lon)
    private fun compass(az: Double) = FindGuide.COMPASS[((az + 11.25) / 22.5).toInt() % 16]
    private fun pct(x: Double) = (x * 100).roundToInt()

    private fun chooseLine(word: String, types: List<String>): MutableComponent {
        val c = Component.literal(L.t("cheonsang.cwhen.choose"))
        types.forEachIndexed { i, t ->
            if (i > 0) c.append(" · ")
            val cmd = "/cwhen $word $t"
            c.append(Component.literal(t).withStyle { st -> st.withColor(ChatFormatting.AQUA).withClickEvent(ClickEvent.SuggestCommand(cmd)).withHoverEvent(HoverEvent.ShowText(Component.literal(cmd))) })
        }
        return c
    }

    private fun appendNames(msg: MutableComponent, names: List<String>) {
        names.forEachIndexed { i, n ->
            if (i > 0) msg.append(" · ")
            val cmd = "/cwhen $n"
            msg.append(Component.literal(n).withStyle { st -> st.withColor(ChatFormatting.AQUA).withClickEvent(ClickEvent.RunCommand(cmd)).withHoverEvent(HoverEvent.ShowText(Component.literal(cmd))) })
        }
    }

    /** [이동] = `/cplace 위도 경도` (소수 1자리, 점) — 이미 그 근처(0.5° 안)면 없음 */
    private fun goButton(msg: MutableComponent, h: Here, lat: Double, lon: Double) {
        if (abs(lat - h.lat) < 0.5 && abs(norm180(lon - h.lon)) < 0.5) return
        val cmd = "/cplace %.1f %.1f".format(Locale.ROOT, lat, norm180(lon))
        msg.append(Component.literal(L.t("cheonsang.cwhen.go")).withStyle { st ->
            st.withColor(ChatFormatting.GREEN).withClickEvent(ClickEvent.RunCommand(cmd)).withHoverEvent(HoverEvent.ShowText(Component.literal(cmd)))
        })
    }

    /**
     * 1.10.1-dev3 [이동] (OP, 11-14 13:06 지율님) = 그 순간·그 장소로 한 번에: `/cwhen go <jd> [위도 경도]`.
     * 장소가 있으면 내 /cplace 해제 + `/cele set` 에 위도·경도까지, 시각은 그 장소 경도의 현지시. 없으면 날짜만, 시각은 내 경도 현지시
     * (내 하늘 = 월드 시각 + (월드 경도 − 내 경도)/15 시간 이라 — dev2 [날짜] 가 어긋나던 것)
     */
    private fun moveButton(msg: MutableComponent, h: Here, jd: Double, lat: Double? = null, lon: Double? = null) {
        if (!h.date) return
        val far = lat != null && lon != null && (abs(lat - h.lat) >= 0.5 || abs(norm180(lon - h.lon)) >= 0.5)
        val cmd = if (far) "/cwhen go %.5f %.1f %.1f".format(Locale.ROOT, jd, lat, norm180(lon!!)) else "/cwhen go %.5f".format(Locale.ROOT, jd)
        msg.append(Component.literal(L.t("cheonsang.cwhen.move")).withStyle { st ->
            st.withColor(ChatFormatting.GOLD).withClickEvent(ClickEvent.RunCommand(cmd)).withHoverEvent(HoverEvent.ShowText(Component.literal(cmd)))
        })
    }

    private fun goTo(src: FabricClientCommandSource, words: List<String>) {
        val jd = words.getOrNull(1)?.toDoubleOrNull()
        val lat = words.getOrNull(2)?.toDoubleOrNull()
        val lon = words.getOrNull(3)?.toDoubleOrNull()
        val conn = Minecraft.getInstance().connection
        if (jd == null || conn == null || (words.size > 2 && (lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0))) {
            src.sendError(Component.literal("/cwhen go <jd> [lat lon]")); return
        }
        if (!canSetDate()) { src.sendError(Component.literal(L.t("cheonsang.cwhen.move_op"))); return }
        val zoneLon = if (lat != null && lon != null) lon else CheonsangSky.longitude
        val v = Calendar.toLocal(jd, zoneLon / 15.0)
        val cmd = StringBuilder("cele set %d %d %d %d %d".format(Locale.ROOT, v.year, v.month, v.day, v.hour, v.minute))
        if (lat != null && lon != null) {
            Place.reset()
            cmd.append(" %.1f %.1f".format(Locale.ROOT, lat, lon))
        }
        conn.sendCommand(cmd.toString())
    }

    /** 날짜를 옮길 수 있나: 리얼타임 아님 + 서버가 `/cele set` 을 보내줌(= 권한 있음) */
    fun canSetDate(): Boolean = CheonsangSky.realtimeCity == null &&
        Minecraft.getInstance().connection?.commands?.root?.getChild("cele")?.getChild("set") != null

    private fun nextButton(msg: MutableComponent, args: String, jd: Double) {
        val cmd = "/cwhen $args @%.4f".format(Locale.ROOT, jd)
        msg.append(Component.literal(L.t("cheonsang.cwhen.next")).withStyle { st ->
            st.withColor(ChatFormatting.AQUA).withClickEvent(ClickEvent.RunCommand(cmd)).withHoverEvent(HoverEvent.ShowText(Component.literal(cmd)))
        })
    }

    /** 2-1 미래 판정: 쪽수가 있으면 그 뒤, 없으면 결과 위치 현지 시각이 지금 마크 시계보다 뒤 */
    private fun acceptor(h: Here, page: Double?): (Double, Double) -> Boolean =
        if (page != null) { t, _ -> t > page } else { t, lon -> t + lon / 360.0 > h.jd0 + h.lon / 360.0 }

    private fun from(h: Here, page: Double?) = page ?: (h.jd0 - 0.5)

    // ── 일식 (4절) ──
    private fun solarName(kind: Int) = L.t("cheonsang.cwhen.solar.$kind")

    private fun solar(h: Here, want: String, page: Double?, alive: () -> Boolean): List<Component> {
        val r = WhenCalc.solar(from(h, page), want, h.lat, h.lon, alive, acceptor(h, page))
            ?: return listOf(Component.literal(L.t("cheonsang.cwhen.none", "solar $want", WhenCalc.YEARS.toInt())))
        return solarLines(h, r, "solar $want", full = true)
    }

    /** 1.10.1-dev5 (11-14 14:02 지율님 "곁정보 걷어내자"): 이름·시각·장소·숫자·버튼만 한 줄 */
    private fun solarLines(h: Here, r: WhenCalc.Solar, tag: String, full: Boolean): List<Component> {
        val l1 = StringBuilder(L.t("cheonsang.cwhen.head", tag, solarName(r.kind), lt(r.jd, r.lon), place(r.lat, r.lon)))
        if (r.durSec > 0) { val s = r.durSec.roundToInt(); l1.append(L.t("cheonsang.cwhen.dur", s / 60, s % 60)) }
        if (r.kind == 0) l1.append(L.t("cheonsang.cwhen.covered", pct(r.obscuration)))
        if (full && r.widthKm > 0) l1.append(" · ").append(L.t("cheonsang.cwhen.width", r.widthKm.roundToInt()))
        val c = Component.literal(l1.toString())
        if (!r.hereKind) goButton(c, h, r.lat, r.lon)
        if (r.hereKind) moveButton(c, h, r.jd) else moveButton(c, h, r.jd, r.lat, r.lon)
        if (full) nextButton(c, tag, r.jd + 1.0)
        return listOf(c)
    }

    // ── 월식 (5절) ──
    private fun lunar(h: Here, want: String, page: Double?, alive: () -> Boolean): List<Component> {
        val r = WhenCalc.lunar(from(h, page), want, h.lat, h.lon, alive, acceptor(h, page))
            ?: return listOf(Component.literal(L.t("cheonsang.cwhen.none", "lunar $want", WhenCalc.YEARS.toInt())))
        return lunarLines(h, r, "lunar $want", full = true)
    }

    private fun lunarLines(h: Here, r: WhenCalc.Lunar, tag: String, full: Boolean): List<Component> {
        val seen = r.hereAlt > 0
        val la = if (seen) h.lat else r.lat; val lo = if (seen) h.lon else r.lon
        val cover = when (r.kind) { 2 -> ""; 1 -> L.t("cheonsang.cwhen.umbra", pct(r.umbraMag)); else -> L.t("cheonsang.cwhen.penumbra", pct(r.penMag)) }
        val l1 = Component.literal(L.t("cheonsang.cwhen.head", tag, L.t("cheonsang.cwhen.lunar.${r.kind}"), lt(r.jd, lo), place(la, lo)) + cover)
        fun buttons(c: MutableComponent) {
            if (!seen) goButton(c, h, r.lat, r.lon)
            if (seen) moveButton(c, h, r.jd) else moveButton(c, h, r.jd, r.lat, r.lon)
        }
        if (!full) { buttons(l1); return listOf(l1) }
        val c = r.contacts
        fun t(i: Int) = if (c[i].isNaN()) null else hm(c[i], lo)
        val phases = listOfNotNull(
            t(0)?.let { L.t("cheonsang.cwhen.p1", it) }, t(1)?.let { L.t("cheonsang.cwhen.u1", it) },
            if (t(2) != null && t(3) != null) L.t("cheonsang.cwhen.u2", t(2), t(3)) else null,
            L.t("cheonsang.cwhen.max", hm(r.jd, lo)),
            t(4)?.let { L.t("cheonsang.cwhen.u4", it) }, t(5)?.let { L.t("cheonsang.cwhen.p4", it) })
        val l2 = Component.literal("  " + phases.joinToString(" · "))
        buttons(l2)
        nextButton(l2, tag, r.jd + 1.0)
        return listOf(l1, l2)
    }

    // ── 유성우 (6절) ──
    private fun findShower(name: String): Shower? {
        val q = FindGuide.nameKey(name)
        return SkyData.showers.firstOrNull { s ->
            listOf(s.id, s.ko, s.en, s.en1, s.ko + "유성우", s.ko.removeSuffix("자리"), s.en1 + " meteor shower").any { FindGuide.nameKey(it) == q }
        } ?: SkyData.showers.firstOrNull { s -> listOf(s.ko, s.en, s.en1).any { FindGuide.nameKey(it).startsWith(q) } }
    }

    private fun showerName(s: Shower) = L.t("cheonsang.cwhen.shower", s.label)

    private fun meteor(h: Here, list: List<Pair<Events.Shower, Shower>>, only: Shower?, arg: String, page: Double?, alive: () -> Boolean): List<Component> {
        val start = page ?: (h.jd0 - 0.5)
        val cands = list.filter { only == null || it.second === only }.mapNotNull { (es, s) -> WhenCalc.showerPeak(start, es.lon)?.let { it to s } }
        val (peak, s) = cands.minByOrNull { it.first } ?: return listOf(Component.literal(L.t("cheonsang.cwhen.none", "meteor", 1)))
        if (!alive()) throw WhenCalc.Cancel()
        return meteorLines(h, peak, s, if (arg.isEmpty()) "meteor" else "meteor $arg", full = true)
    }

    private fun meteorLines(h: Here, peak: Double, s: Shower, tag: String, full: Boolean): List<Component> {
        val mine = WhenCalc.meteorHere(peak, h.lat, h.lon, s.ra, s.dec, s.zhr)
        val good = mine != null && mine.radAlt >= 20
        val spot = if (good) mine else WhenCalc.meteorBest(peak, s.ra, s.dec, s.zhr)
        val lo = spot?.lon ?: h.lon; val la = spot?.lat ?: h.lat
        val text = StringBuilder(L.t("cheonsang.cwhen.meteor_head", tag, showerName(s), lt(peak, lo), place(la, lo)))
        if (full && spot != null) text.append(L.t("cheonsang.cwhen.meteor_spot", lt(spot.jd, lo), spot.rate.roundToInt()))
        val c = Component.literal(text.toString())
        if (!good && spot != null) goButton(c, h, spot.lat, spot.lon)
        if (!good && spot != null) moveButton(c, h, spot.jd, spot.lat, spot.lon) else moveButton(c, h, spot?.jd ?: peak)
        if (full) nextButton(c, tag, peak + 1.0)
        return listOf(c)
    }

    // ── 보름·삭·행성 근접 (6절) — 내 관측 위치 기준 ──
    private fun simple(h: Here, k: String, page: Double?, alive: () -> Boolean): List<Component> {
        val from = page ?: h.jd0
        val f = when (k) {
            "full" -> Events.fullMoon(from, h.lat, h.lon)
            "new" -> Events.newMoon(from, h.lat, h.lon)
            else -> Events.conjunction(from, h.lat, h.lon)
        } ?: return listOf(Component.literal(L.t("cheonsang.cwhen.none", k, 30)))
        if (!alive()) throw WhenCalc.Cancel()
        val out = mutableListOf<Component>()
        val l1 = Component.literal("[$k] " + f.text)
        moveButton(l1, h, f.view)
        if (k != "conj") { nextButton(l1, k, f.jd + 1.0); return listOf(l1) }
        out += l1
        conjNote(h, f)?.let { out += it }
        nextButton(out.last() as MutableComponent, k, f.jd + 3.0)
        return out
    }

    /** 행성 근접: 내 위치에서 어두울 때 낮으면 (10° 아래) 위도 추천 */
    private fun conjNote(h: Here, f: Events.Found): MutableComponent? {
        val pos = Planet.entries.map { Planets.position(it, f.jd) }
        var best: Vec3? = null; var bs = 999.0
        for (i in pos.indices) for (j in i + 1 until pos.size) { val s = pos[i].dir.angleTo(pos[j].dir); if (s < bs) { bs = s; best = (pos[i].dir + pos[j].dir).normalized() } }
        val mid = best ?: return null
        val alt = Events.altJ2000(f.view, h.lat, h.lon, mid)
        if (alt >= 10) return Component.literal(L.t("cheonsang.cwhen.conj_alt", hm(f.view, h.lon), alt.roundToInt()))
        val dec = mid.toSpherical().second
        val rl = WhenCalc.recommendLat(h.lat, dec, 8.0)
        val v = Events.bestNight(Events.localDay0(f.jd, h.lon), rl, h.lon) { Events.altJ2000(it, rl, h.lon, mid) }
        val alt2 = Events.altJ2000(v, rl, h.lon, mid)
        val c = Component.literal(L.t("cheonsang.cwhen.conj_alt", hm(v, h.lon), alt2.roundToInt()))   // dev5: 추천 위도는 [위치] 버튼으로만
        goButton(c, h, rl, h.lon)
        moveButton(c, h, v, rl, h.lon)
        return c
    }

    // ── 다음 현상 전부 ──
    private fun next(h: Here, list: List<Pair<Events.Shower, Shower>>, page: Double?, alive: () -> Boolean): List<Component> {
        val out = mutableListOf<Component>()
        val fr = from(h, page); val acc = acceptor(h, page)
        WhenCalc.solar(fr, "any", h.lat, h.lon, alive, acc)?.let { out += solarLines(h, it, "solar", full = false) }
            ?: run { out += Component.literal(L.t("cheonsang.cwhen.none", "solar", WhenCalc.YEARS.toInt())) }
        WhenCalc.lunar(fr, "any", h.lat, h.lon, alive, acc)?.let { out += lunarLines(h, it, "lunar", full = false) }
            ?: run { out += Component.literal(L.t("cheonsang.cwhen.none", "lunar", WhenCalc.YEARS.toInt())) }
        val from0 = page ?: h.jd0
        Events.fullMoon(from0, h.lat, h.lon).let { f -> out += Component.literal("[full] " + f.text).also { moveButton(it, h, f.view) } }
        Events.newMoon(from0, h.lat, h.lon).let { f -> out += Component.literal("[new] " + f.text).also { moveButton(it, h, f.view) } }
        if (!alive()) throw WhenCalc.Cancel()
        list.mapNotNull { (es, s) -> WhenCalc.showerPeak(fr, es.lon)?.let { it to s } }.minByOrNull { it.first }?.let { (pk, s) -> out += meteorLines(h, pk, s, "meteor", full = false) }
        val cj = Events.conjunction(from0, h.lat, h.lon)
        out += Component.literal("[conj] " + (cj?.text ?: L.t("cheonsang.cwhen.none", "conj", 30))).also { if (cj != null) moveButton(it, h, cj.view) }
        return out
    }

    // ── 천체 (2·3절) ──
    private class Cls(val sunThr: Double, val minAlt: Double, val moonCut: Double, val days: Int)

    private fun clsFor(f: FindGuide.Found, h: Here): Cls = when (f.kind) {
        Kind.DEEP -> Cls(-18.0, 25.0, 0.8, 366)
        Kind.PLANET, Kind.SATELLITE -> if (f.planet == Planet.MERCURY || f.planet == Planet.VENUS) Cls(-3.0, 5.0, 0.0, 1096) else Cls(-6.0, 8.0, 0.0, 1096)
        Kind.ASTEROID -> Cls(-6.0, 8.0, 0.0, 1096)
        Kind.MOON -> Cls(-6.0, 15.0, 0.0, 60)
        Kind.FEATURE -> Cls(-6.0, 15.0, 0.0, 120)
        else -> if (f.mag == null || f.mag <= h.naked) Cls(-12.0, 15.0, 0.2, 366) else Cls(-15.0, 20.0, 0.5, 366)
    }

    private fun objectLines(h: Here, f: FindGuide.Found, others: List<String>, page: Double?, alive: () -> Boolean): List<Component> {
        val out = mutableListOf<Component>()
        val head = StringBuilder(L.t("cheonsang.cwhen.obj_head", f.display))
        f.mag?.let { head.append(L.t("cheonsang.cwhen.mag").format(it)) }
        out += Component.literal(head.toString())

        if (f.kind == Kind.SUN) {
            // 해: 오늘 남중
            val day0 = Events.localDay0(h.jd0, h.lon)
            var bt = day0 + 0.5; var ba = -90.0
            var x = day0 + 0.25
            while (x <= day0 + 0.75) { val a = Events.altOfDate(x, h.lat, h.lon, Sun.position(x).dir); if (a > ba) { ba = a; bt = x }; x += 2.0 / 1440 }
            out += Component.literal(L.t("cheonsang.cwhen.sun", hm(bt, h.lon), ba.roundToInt()))
            return out
        }

        val cls = clsFor(f, h)
        val pos: (Double) -> Vec3 = posFn(f) ?: run { out += Component.literal(L.t("cheonsang.cwhen.no_pos")); return out }
        val feat = f.feature
        val extra: ((Double) -> Double)? = if (feat == null) null else { jd ->
            if (abs(feat.lon) > 90) 0.0 else { val a = WhenCalc.featureSunAlt(jd, feat.lat, feat.lon); if (a in 2.0..15.0) 1.0 else 0.0 }
        }
        val spec = WhenCalc.Spec(pos, cls.sunThr, cls.minAlt, cls.moonCut, extra)
        val start = page ?: h.jd0
        var r = WhenCalc.objectSearch(spec, start, cls.days, h.lat, h.lon, alive)
        var recLat: Double? = null
        // 3-4 위도 추천 (달·달 지형 제외): 점수가 낮으면 적위 쪽으로, 안 되면 5° 씩 적도 쪽으로
        if (r.maxScore < 0.35 && f.kind != Kind.MOON && f.kind != Kind.FEATURE) {
            var rl = WhenCalc.recommendLat(h.lat, decFor(pos, start, cls.days), cls.minAlt)
            for (i in 0 until 12) {
                if (abs(rl - h.lat) < 1.0) break
                val r2 = WhenCalc.objectSearch(spec, start, cls.days, rl, h.lon, alive)
                if (r2.maxScore >= 0.35) {
                    r = r2
                    recLat = rl
                    break
                }
                if (rl == 0.0) break
                rl = if (abs(rl) <= 5) 0.0 else rl - 5.0 * Math.signum(rl)
            }
        }
        val e = r.earliest; val b = r.best
        if (e == null || b == null) {
            out += Component.literal(L.t("cheonsang.cwhen.no_night", cls.days))
        } else {
            // dev5: 가장 좋은 밤 한 줄 (시각·고도·방위, 달·달 지형은 위상) — 추천 위도면 그 위도 [위치]
            val phase = if (f.kind == Kind.MOON || f.kind == Kind.FEATURE) L.t("cheonsang.cwhen.moon_phase", pct(b.illum)) else ""
            val lb = Component.literal(L.t("cheonsang.cwhen.best", lt(b.jd, h.lon), b.alt.roundToInt(), compass(b.az), phase))
            recLat?.let { goButton(lb, h, it, h.lon) }
            moveButton(lb, h, b.jd, recLat, recLat?.let { h.lon })
            nextButton(lb, f.name, maxOf(e.jd, b.jd) + 1.0)
            out += lb
        }
        if (others.isNotEmpty()) { val c = Component.literal(L.t("cheonsang.cwhen.others")); appendNames(c, others); out += c }
        return out
    }

    /** 위도 추천용 적위: 해에서 가장 멀리 떨어진 날의 적위 (고정 천체는 그대로) */
    private fun decFor(pos: (Double) -> Vec3, start: Double, days: Int): Double {
        var best = start; var bs = -1.0
        var t = start
        while (t < start + days) { val s = pos(t).angleTo(Sun.position(t).dir); if (s > bs) { bs = s; best = t }; t += 5.0 }
        return pos(best).toSpherical().second
    }

    /** 그 순간 날짜 적도 단위벡터 — 세차·행성 위치는 잠깐씩 붙잡아 둠 */
    private fun posFn(f: FindGuide.Found): ((Double) -> Vec3)? {
        fun <T> cached(step: Double, calc: (Double) -> T): (Double) -> T {
            var key = Double.NaN; var v: T? = null
            return { jd -> val k = kotlin.math.floor(jd / step); if (k != key || v == null) { key = k; v = calc(jd) }; v!! }
        }
        val prec = cached(5.0) { Frames.precession(it) }
        return when (f.kind) {
            Kind.SUN -> { jd: Double -> Sun.position(jd).dir }
            Kind.MOON, Kind.FEATURE -> { jd: Double -> Moon.position(jd).dir }
            Kind.PLANET, Kind.SATELLITE -> { val p = f.planet ?: return null; val c = cached(0.25) { Planets.position(p, it).dir }; val fn: (Double) -> Vec3 = { jd -> prec(jd) * c(jd) }; fn }
            Kind.ASTEROID -> {
                val b = f.asteroid ?: return null
                val c = cached(0.5) { kr.inf.cheonsang.client.sky.Asteroids.position(b, it)?.dir ?: Vec3(1.0, 0.0, 0.0) }
                val fn: (Double) -> Vec3 = { jd -> prec(jd) * c(jd) }
                fn
            }
            else -> { val d = f.dirJ ?: return null; val fn: (Double) -> Vec3 = { jd -> prec(jd) * d }; fn }
        }
    }
}
