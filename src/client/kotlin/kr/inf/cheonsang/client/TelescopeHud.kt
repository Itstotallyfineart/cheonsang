package kr.inf.cheonsang.client

import kr.inf.cheonsang.L
import kr.inf.cheonsang.Cheonsang
import kr.inf.cheonsang.astro.Frames
import kr.inf.cheonsang.astro.Planet
import kr.inf.cheonsang.astro.Vec3
import kr.inf.cheonsang.client.sky.CheonsangSky
import kr.inf.cheonsang.client.sky.SkyData
import kr.inf.cheonsang.client.sky.StarCatalog
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * 망원경 화면(HUD): 둥근 시야·조준선·별자리 선/이름·행성/달 이름을 그리는 바닐라 조준점 대체 요소(망원경 켰을 때만).
 * 좌클릭으로 천체 선택(정보 카드), Shift+좌클릭으로 해제. 격자·경계선은 /cfilter grid·altaz·bounds.
 */
object TelescopeHud {
    private const val LINE_RGB = 0x9FC4FF
    private const val LINE_ALPHA = 0xA0
    private const val NAME_RGB = 0x9FD0FF
    /** 행성·달 이름 (별자리와 구분) */
    private const val BODY_RGB = 0xFFB060
    private const val RETICLE_COLOR = 0x80FFFFFF.toInt()
    /** 광학 정보 글씨 (옅은 회색) */
    private const val OPTICS_COLOR = 0xC0B8C0C8.toInt()
    private const val TARGET_COLOR = 0xFFE0E0E0.toInt()

    /** 좌클릭으로 고를 때 화면 가운데에서 허용 반경 (GUI 픽셀) */
    private const val PICK_RADIUS = 24.0
    /** 조준 중 이름을 계속 띄울 때 반경 (GUI 픽셀) */
    private const val AIM_RADIUS = 10.0
    /** 조준선 가운데 빈칸 반지름 · 선 길이 (GUI 픽셀) */
    private const val RETICLE_GAP = 3
    private const val RETICLE_LEN = 8
    /** 별자리 선이 별 앞에서 끊기는 길이 (GUI 픽셀). 배율이 커질수록 간격도 늘어남 */
    private fun starGap(): Double {
        val z = if (TelescopeView.active) TelescopeView.zoom else 1.0
        return (0.4 + 3.2 * kotlin.math.ln(z) / kotlin.math.ln(25.0)).coerceIn(0.4, 3.6)
    }
    /** 한 번에 보이는 별자리 수, 나타나고 사라지는 시간(초) */
    private const val MAX_CONSTELLATIONS = 3
    /** 천상열차 별자리는 작고 많아서 더 */
    private const val MAX_KOREAN = 8
    private const val FADE_SEC = 0.3f

    /** 둥근 시야 테두리: 지름 = 화면 가로, 가장자리로 갈수록 서서히 어두워짐 (유리 무늬 없음) */
    private val VIGNETTE = Cheonsang.id("textures/gui/telescope_vignette.png")

    /** 좌클릭으로 고른 천체 (빈 하늘 좌클릭 = 해제) */
    private var selected: Target? = null
    /** Alt+좌클릭으로 추적 고정한 천체 (TelescopeView 가 화면 가운데에 붙잡음) */
    internal var locked: Target? = null
    /** 각거리: 선택한 천체에서 Ctrl+좌클릭으로 고른 두 번째 천체 */
    private var measure: Target? = null
    private const val MEASURE_COLOR = 0xD070D0FF.toInt()
    private const val SELECT_COLOR = 0xD0FFD070.toInt()
    private const val CARD_BG = 0xB0000000.toInt()
    private const val CARD_TITLE = 0xFFFFE0A0.toInt()
    private const val CARD_TEXT = 0xFFD8D8D8.toInt()

    /** 이름표별 진하기 0~1 (별자리 id, 행성·달) */
    private val fade = HashMap<String, Float>()
    private var lastNanos = 0L

    fun register() {
        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR) { vanilla ->
            HudElement { g, dt ->
                if (TelescopeView.active && Safety.ok(Safety.TELESCOPE)) Safety.run(Safety.TELESCOPE, Unit) { draw(g) } else { fade.clear(); vanilla.extractRenderState(g, dt) }
            }
        }
    }

    /** 화면 좌표 계산기 (이번 프레임 카메라 기준, GUI 좌표) */
    internal class Projector(mc: Minecraft) {
        val cam = mc.gameRenderer.mainCamera()
        val f = cam.forwardVector(); val u = cam.upVector(); val l = cam.leftVector()
        val w = mc.window.guiScaledWidth.toDouble()
        val h = mc.window.guiScaledHeight.toDouble()
        val t = tan(Math.toRadians(cam.getFov().toDouble() / 2))
        /** 둥근 시야 반지름 — 원 전체가 보이게 지름 = 화면 세로 */
        val radius = min(w, h) / 2

        /** 월드 방향 → 화면 (x, y), 카메라 뒤면 null */
        fun project(d: Vec3): DoubleArray? {
            val z = d.x * f.x() + d.y * f.y() + d.z * f.z()
            if (z <= 0.05) return null
            val right = -(d.x * l.x() + d.y * l.y() + d.z * l.z()) / z
            val up = (d.x * u.x() + d.y * u.y() + d.z * u.z()) / z
            return doubleArrayOf(w / 2 + right / t * h / 2, h / 2 - up / t * h / 2)
        }

        fun fromCenter(p: DoubleArray) = hypot(p[0] - w / 2, p[1] - h / 2)

        /** 화면 반경(픽셀) → 하늘 각도(도) */
        fun pixelsToDeg(px: Double) = Math.toDegrees(atan(px / (h / 2) * t))

        /** 하늘 각도(도) → 화면 반경(픽셀) */
        fun degToPixels(deg: Double) = tan(Math.toRadians(deg)) / t * h / 2

        /** 화면 가운데 방향 (월드) */
        val center get() = Vec3(f.x().toDouble(), f.y().toDouble(), f.z().toDouble()).normalized()
    }

    /** 그리는 별처럼 굴절만큼 올림 (지평선 근처 별자리 선이 별과 맞게) */
    private fun sky(ra: Double, dec: Double) = Visibility.refractWorld(CheonsangSky.skyMat3 * Vec3.fromSpherical(ra, dec))

    // ── 별자리 선 투영 캐시 ──
    /** 별자리마다 J2000 선 점들을 감싸는 원 (가운데 방향, 반지름 cos) — 처음 한 번 */
    private class ConCap(val c: kr.inf.cheonsang.client.sky.Constellation, val center: Vec3, val radiusDeg: Double)
    private val capsCache = java.util.IdentityHashMap<List<kr.inf.cheonsang.client.sky.Constellation>, List<ConCap>>()
    /** 그리는 별자리 체계마다 한 번 */
    private val conCaps: List<ConCap> get() = SkyData.drawnConstellations.let { list -> capsCache.getOrPut(list) { makeCaps(list) } }
    private fun makeCaps(list: List<kr.inf.cheonsang.client.sky.Constellation>): List<ConCap> =
        list.map { c ->
            val pts = c.lines.flatMap { line -> List(line.size / 2) { k -> Vec3.fromSpherical(line[k * 2], line[k * 2 + 1]) } }
            if (pts.isEmpty()) return@map ConCap(c, Vec3(1.0, 0.0, 0.0), 180.0)
            var sx = 0.0; var sy = 0.0; var sz = 0.0
            for (v in pts) { sx += v.x; sy += v.y; sz += v.z }
            val ctr = Vec3(sx, sy, sz).let { if (it.length() < 1e-9) pts[0] else it.normalized() }
            ConCap(c, ctr, pts.maxOf { ctr.angleTo(it) })
        }
    private val conProjected = HashMap<String, List<List<DoubleArray?>>>()
    private var conShown: Set<String> = emptySet()
    private val conScore = ArrayList<Pair<String, Double>>()
    /** 지난 계산 때의 카메라·하늘 (앞·위·왼쪽, 시야 tan, 화면 크기, 하늘 행렬 9개) */
    private val conKey = DoubleArray(9 + 3 + 9 + 1)
    private val conKeyNow = DoubleArray(9 + 3 + 9 + 1)

    private fun constellationsChanged(pr: Projector): Boolean {
        val k = conKeyNow
        k[0] = pr.f.x().toDouble(); k[1] = pr.f.y().toDouble(); k[2] = pr.f.z().toDouble()
        k[3] = pr.u.x().toDouble(); k[4] = pr.u.y().toDouble(); k[5] = pr.u.z().toDouble()
        k[6] = pr.l.x().toDouble(); k[7] = pr.l.y().toDouble(); k[8] = pr.l.z().toDouble()
        k[9] = pr.t; k[10] = pr.w; k[11] = pr.h
        val m = CheonsangSky.skyMat3.m
        for (i in 0 until 9) k[12 + i] = m[i]
        k[21] = if (TelescopeFilter.korean) 1.0 else 0.0
        if (k.contentEquals(conKey)) return false
        k.copyInto(conKey)
        return true
    }

    private fun projectConstellations(pr: Projector) {
        conProjected.clear(); conScore.clear()
        // 화면 가운데 방향 (J2000) — 시야 대각 반각 + 별자리 반지름 밖이면 건너뜀
        val c = pr.center; val m = CheonsangSky.skyMat3.m
        val fj = Vec3(m[0] * c.x + m[3] * c.y + m[6] * c.z, m[1] * c.x + m[4] * c.y + m[7] * c.z, m[2] * c.x + m[5] * c.y + m[8] * c.z)
        val halfDiag = Math.toDegrees(atan(pr.t * hypot(1.0, pr.w / pr.h)))
        for (cap in conCaps) {
            if (fj.angleTo(cap.center) > halfDiag + cap.radiusDeg + 2.0) continue
            val segs = cap.c.lines.map { line -> List(line.size / 2) { k -> pr.project(sky(line[k * 2], line[k * 2 + 1])) } }
            var best = Double.MAX_VALUE
            for (pts in segs) for (k in 1 until pts.size) {
                val a = pts[k - 1] ?: continue; val b = pts[k] ?: continue
                best = min(best, distToSegment(pr.w / 2, pr.h / 2, a, b))
            }
            if (best < pr.radius) { conScore += cap.c.id to best; conProjected[cap.c.id] = segs }
        }
        conShown = conScore.sortedBy { it.second }.take(if (TelescopeFilter.korean) MAX_KOREAN else MAX_CONSTELLATIONS).map { it.first }.toSet()
    }

    private fun draw(g: GuiGraphicsExtractor) {
        val mc = Minecraft.getInstance()
        val pr = Projector(mc)
        val now = System.nanoTime()
        val step = if (lastNanos == 0L) 0f else ((now - lastNanos) / 1e9f / FADE_SEC).coerceIn(0f, 1f)
        lastNanos = now
        val scale = mc.window.guiScale.toFloat()

        // 별자리: 시야가 안 바뀌었으면 지난 프레임 결과 재사용, 바뀌면 근처 별자리만 다시 투영
        if (constellationsChanged(pr)) projectConstellations(pr)
        val projected = conProjected
        val shown = conShown
        for (c in SkyData.drawnConstellations) fadeTo(c.id, c.id in shown, step)

        // 격자·경계선은 별자리 선 아래에 그림
        if (TelescopeFilter.grid || TelescopeFilter.altaz || TelescopeFilter.bounds) drawGrids(g, mc, pr, scale)

        val gapNow = starGap()
        if (TelescopeFilter.lines) {
            g.pose().pushMatrix()
            g.pose().scale(1f / scale, 1f / scale)
            for (c in SkyData.drawnConstellations) {
                val a = fade[c.id] ?: continue
                val segs = projected[c.id] ?: continue
                val color = ((LINE_ALPHA * a).toInt() shl 24) or LINE_RGB
                for (pts in segs) for (k in 1 until pts.size) {
                    val p = pts[k - 1] ?: continue; val q = pts[k] ?: continue
                    starToStar(g, p, q, scale.toDouble(), color, mc.window.width.toDouble(), mc.window.height.toDouble(), gapNow, gapNow)
                }
            }
            g.pose().popMatrix()
        }

        // 이름표는 하늘에 고정: 별자리 이름은 기준점, 행성·달 이름은 바로 아래
        val aim = find(pr, AIM_RADIUS)
        val aimY = (pr.h * 0.62)
        val lim = Visibility.pointLimit(pr.center) + 0.5  // 가시성 모델 (하늘 밝기는 천정 고정)
        fun body(key: String, name: String, dir: Vec3, radiusDeg: Double, visible: Boolean) {
            val p = pr.project(Visibility.refractWorld(dir))
            fadeTo(key, visible && TelescopeFilter.bodies && p != null && pr.fromCenter(p) < pr.radius - 6, step)
            val a = fade[key] ?: return
            if (p == null) return
            text(g, mc, name, p[0], p[1] + pr.degToPixels(radiusDeg) + 4, BODY_RGB, a)
        }
        body("moon", L.t("cheonsang.telescopehud.1"), CheonsangSky.moonWorld, CheonsangSky.moonRadius, true)
        for (p in CheonsangSky.planets) body(p.planet.name, p.planet.label, CheonsangSky.skyMat3 * p.dir, p.diameterArcsec / 7200.0, p.mag <= lim)
        for (a in CheonsangSky.asteroids) body("ast:" + a.body.en, L.name(a.body.ko, a.body.en), CheonsangSky.skyMat3 * a.dir, 0.0, a.mag <= lim)
        if (TelescopeFilter.names) for (c in SkyData.drawnConstellations) {
            val a = fade[c.id] ?: continue
            val lp = pr.project(sky(c.labelRa, c.labelDec)) ?: continue
            text(g, mc, L.name(c.ko, c.en), lp[0], lp[1], NAME_RGB, a)
        }

        // 둥근 시야 가장자리를 서서히 어둡게 (화면이 원보다 좁으면 모서리만 어두워짐)
        val d = (pr.radius * 2).roundToInt()
        val x0 = ((pr.w - d) / 2).roundToInt(); val y0 = ((pr.h - d) / 2).roundToInt()
        val gw = pr.w.roundToInt(); val gh = pr.h.roundToInt()
        if (TelescopeView.magnifying) {
            g.blit(RenderPipelines.GUI_TEXTURED, VIGNETTE, x0, y0, 0f, 0f, d, d, d, d)
            if (y0 > 0) { g.fill(0, 0, gw, y0, BLACK); g.fill(0, y0 + d, gw, gh, BLACK) }
            if (x0 > 0) { g.fill(0, 0, x0, gh, BLACK); g.fill(x0 + d, 0, gw, gh, BLACK) }
        }

        // 구경·초점거리·접안·배율 (왼쪽 아래, 작게)
        // 핫바(가운데 182 + 왼손 칸 29)와 겹치지 않게. 왼쪽 빈 폭에 맞춰 줄이고 (최소 0.5배), 그래도 넘으면 체력·방어 칸 위로 올림
        run {
            val line = TelescopeView.opticsLine()
            val room = gw / 2f - 91f - 29f - 4f - 6f
            val tw = mc.font.width(line).toFloat()
            var k = 0.75f
            var bottom = gh - 6f
            if (tw * k > room) {
                if (tw * 0.5f <= room) k = room / tw
                else bottom = gh - 64f
            }
            g.pose().pushMatrix()
            g.pose().scale(k, k)
            g.text(mc.font, line, (6 / k).roundToInt(), ((bottom - mc.font.lineHeight * k) / k).roundToInt(), OPTICS_COLOR, true)
            g.pose().popMatrix()
        }

        // 조준선: 실제 1픽셀, 가운데 비움, 상하좌우 대칭
        g.pose().pushMatrix()
        g.pose().scale(1f / scale, 1f / scale)
        val s = scale.toInt()
        val cx = mc.window.width / 2; val cy = mc.window.height / 2
        val gap = RETICLE_GAP * s; val len = RETICLE_LEN * s
        g.fill(cx - gap - len, cy, cx - gap, cy + 1, RETICLE_COLOR); g.fill(cx + 1 + gap, cy, cx + 1 + gap + len, cy + 1, RETICLE_COLOR)
        g.fill(cx, cy - gap - len, cx + 1, cy - gap, RETICLE_COLOR); g.fill(cx, cy + 1 + gap, cx + 1, cy + 1 + gap + len, RETICLE_COLOR)
        g.pose().popMatrix()

        // 조준 중인 천체 이름 (조준선 아래)
        if (TelescopeFilter.aim) aim?.let { g.centeredText(mc.font, it.short, gw / 2, aimY.roundToInt(), TARGET_COLOR) }

        // 선택 원 + 정보 카드. 깊게 확대해 원이 화면보다 커지면 그리지 않음
        selected?.let { t ->
            t.dir()?.let { w -> pr.project(drawnDir(t, w))?.let { p ->
                val r = max(pr.degToPixels(t.radiusDeg()) + 3, 6.0)
                if (r < hypot(pr.w, pr.h)) {
                    g.pose().pushMatrix(); g.pose().scale(1f / scale, 1f / scale)
                    ring(g, p[0] * scale, p[1] * scale, r * scale, SELECT_COLOR, mc.window.width.toDouble(), mc.window.height.toDouble())
                    g.pose().popMatrix()
                }
            } }
            measure?.let { m -> drawMeasure(g, mc, pr, scale, t, m) }
            if (TelescopeFilter.card) drawCard(g, mc, cardLines(t).let { l -> if (locked === t && l.isNotEmpty()) listOf(l[0] + L.t("cheonsang.m.tracking")) + l.drop(1) else l } +
                (measure?.let { m -> angleText(t, m)?.let { listOf(L.t("cheonsang.telescopehud.2", m.short, it)) } } ?: emptyList()))
        }
    }

    /** 각거리 글자: "12.35° (12° 20′ 42″)" — 두 천체 지금 방향 사이 */
    private fun angleText(a: Target, b: Target): String? {
        val wa = a.dir()?.normalized() ?: return null
        val wb = b.dir()?.normalized() ?: return null
        val deg = wa.angleTo(wb)
        val s = Math.round(deg * 3600)
        return "%.2f° (%d° %02d′ %02d″)".format(deg, s / 3600, s / 60 % 60, s % 60)
    }

    /** 두 번째 천체 원 + 두 천체 잇는 선 (화면 안 부분만) */
    private fun drawMeasure(g: GuiGraphicsExtractor, mc: Minecraft, pr: Projector, scale: Float, a: Target, b: Target) {
        val pa = a.dir()?.let { pr.project(drawnDir(a, it)) }
        val pb = b.dir()?.let { pr.project(drawnDir(b, it)) } ?: return
        g.pose().pushMatrix(); g.pose().scale(1f / scale, 1f / scale)
        val sw = mc.window.width.toDouble(); val sh = mc.window.height.toDouble()
        val r = max(pr.degToPixels(b.radiusDeg()) + 3, 6.0)
        if (r < hypot(pr.w, pr.h)) ring(g, pb[0] * scale, pb[1] * scale, r * scale, MEASURE_COLOR, sw, sh)
        if (pa != null) fastLine(g, pa[0] * scale, pa[1] * scale, pb[0] * scale, pb[1] * scale, (0x60 shl 24) or (MEASURE_COLOR and 0xFFFFFF), sw, sh)
        g.pose().popMatrix()
    }

    /** 화면에 그려진 방향 — 별·행성·해·달은 굴절만큼 올려 그림, 심원천체 빛무리는 굴절 없음 */
    internal fun drawnDir(t: Target, w: Vec3): Vec3 = if (t.key.startsWith("dso:")) w else Visibility.refractWorld(w)

    private fun ring(g: GuiGraphicsExtractor, cx: Double, cy: Double, r: Double, color: Int, sw: Double, sh: Double) {
        val n = 48
        for (k in 0 until n) {
            val a0 = Math.PI * 2 * k / n; val a1 = Math.PI * 2 * (k + 1) / n
            fastLine(g, cx + r * kotlin.math.cos(a0), cy + r * kotlin.math.sin(a0), cx + r * kotlin.math.cos(a1), cy + r * kotlin.math.sin(a1), color, sw, sh)
        }
    }

    /** 선분 하나 = 화면 안으로 자른 뒤 기울인 얇은 사각형 1개 (실제 픽셀 좌표, 굵기 2) */
    private fun fastLine(g: GuiGraphicsExtractor, ax: Double, ay: Double, bx: Double, by: Double, color: Int, sw: Double, sh: Double) {
        var t0 = 0.0; var t1 = 1.0
        val dx = bx - ax; val dy = by - ay
        for ((p, q) in arrayOf(-dx to ax, dx to sw - ax, -dy to ay, dy to sh - ay)) {
            if (p == 0.0) { if (q < 0) return; continue }
            val r = q / p
            if (p < 0) { if (r > t1) return; t0 = max(t0, r) } else { if (r < t0) return; t1 = min(t1, r) }
        }
        val x0 = ax + dx * t0; val y0 = ay + dy * t0
        val len = hypot(dx, dy) * (t1 - t0)
        if (len < 0.5) return
        g.pose().pushMatrix()
        g.pose().translate(x0.toFloat(), y0.toFloat())
        g.pose().rotate(kotlin.math.atan2(dy, dx).toFloat())
        g.fill(-1, -1, kotlin.math.ceil(len).toInt() + 1, 1, color)  // 양 끝 1px 늘려 꺾이는 이음매 틈 덮기
        g.pose().popMatrix()
    }

    /** 카드 천문 계산은 [CARD_NANOS] 마다(초당 약 7번), 그리기는 매 프레임 같은 글자로. 천체가 바뀌거나 시간이 점프하면 바로 다시 계산 */
    private const val CARD_NANOS = 150_000_000L
    private var cardFor: Target? = null
    private var cardAt = 0L
    private var cardJd = 0.0
    private var cardCache: List<String> = emptyList()
    private fun cardLines(t: Target): List<String> {
        val now = System.nanoTime()
        val jd = CheonsangSky.clock.jd((Minecraft.getInstance().level?.defaultClockTime ?: 0L).toDouble())
        if (t !== cardFor || now - cardAt > CARD_NANOS || kotlin.math.abs(jd - cardJd) > 0.01) {
            cardCache = t.card(); cardFor = t; cardAt = now; cardJd = jd
        }
        return cardCache
    }

    /** 카드 줄 고르기 — 방위·고도 / 적경·적위 / 출몰 줄은 설정으로 숨김 */
    private fun keepCardLine(s: String): Boolean = when {
        s.startsWith(L.t("cheonsang.telescopehud.3")) -> TelescopeFilter.cardAltAz
        s.startsWith(L.t("cheonsang.telescopehud.4")) -> TelescopeFilter.cardRaDec
        RISE_SET.any { s.startsWith(it) } || s.contains(L.t("cheonsang.telescopehud.5")) -> TelescopeFilter.cardRiseSet
        else -> true
    }
    private val RISE_SET get() = listOf(L.t("cheonsang.telescopehud.14"), L.t("cheonsang.telescopehud.13"), L.t("cheonsang.telescopehud.12"), L.t("cheonsang.telescopehud.11"), L.t("cheonsang.telescopehud.10"), L.t("cheonsang.telescopehud.9"), L.t("cheonsang.telescopehud.8"), L.t("cheonsang.telescopehud.7"), L.t("cheonsang.telescopehud.6"))

    /** 카드 기본 크기 (설정 화면 글씨 크기와 곱함) */
    private const val CARD_SCALE = 0.85f

    /** 위치(네 귀퉁이)·투명도·글씨 배율 (설정 화면) */
    private fun drawCard(g: GuiGraphicsExtractor, mc: Minecraft, lines0: List<String>) {
        // " · " 로 이은 항목을 한 줄씩 (세로로 길고 폭 좁게)
        val lines = lines0.filterIndexed { k, s -> k == 0 || keepCardLine(s) }.flatMapIndexed { k, s -> if (k == 0) listOf(s) else s.split(" · ") }
        if (lines.isEmpty()) return
        val font = mc.font
        val sc = TelescopeFilter.uiScale * CARD_SCALE
        val lh = font.lineHeight + 2
        val w = lines.maxOf { font.width(it) } + 12
        val h = lines.size * lh + 8
        val sw = (g.guiWidth() / sc).toInt(); val sh = (g.guiHeight() / sc).toInt()
        val corner = TelescopeFilter.cardCorner
        val x0 = if (corner == 1 || corner == 3) sw - 6 - w else 6
        val y0 = if (corner >= 2) sh - 6 - h else 6
        val bg = ((TelescopeFilter.cardOpacity * 255).toInt().coerceIn(0, 255) shl 24) or (CARD_BG and 0xFFFFFF)
        g.pose().pushMatrix()
        g.pose().scale(sc, sc)
        g.fill(x0, y0, x0 + w, y0 + h, bg)
        lines.forEachIndexed { k, s -> g.text(font, s, x0 + 6, y0 + 5 + k * lh, if (k == 0) CARD_TITLE else CARD_TEXT, false) }
        g.pose().popMatrix()
    }

    // ── 격자 (점은 처음 한 번만 만들고, 시야 밖 점은 투영 안 함) ──
    private const val GRID_RGB = 0x6080C0; private const val EQUATOR_RGB = 0x90B0FF; private const val ECLIPTIC_RGB = 0xE0C060
    private const val ALTAZ_RGB = 0x60B080; private const val HORIZON_RGB = 0x90E0A0; private const val BOUNDS_RGB = 0xC08870
    private const val OBLIQUITY_J2000 = 23.4392911

    private fun altAzWorld(alt: Double, az: Double): Vec3 {
        val ca = kotlin.math.cos(Math.toRadians(alt))
        return Vec3(ca * kotlin.math.sin(Math.toRadians(az)), kotlin.math.sin(Math.toRadians(alt)), -ca * kotlin.math.cos(Math.toRadians(az)))
    }

    /** 선 묶음: 선마다 단위벡터 x,y,z 를 이어 붙인 배열 + 색 */
    private class LineSet(val polys: List<DoubleArray>, val colors: IntArray, val keep: List<BooleanArray>? = null)

    private fun xyz(vs: List<Vec3>) = DoubleArray(vs.size * 3).also { a -> vs.forEachIndexed { i, v -> a[i * 3] = v.x; a[i * 3 + 1] = v.y; a[i * 3 + 2] = v.z } }

    /** 적도 격자(J2000): 시간선 24개, 적위선 17개(0° = 천구 적도), 황도 */
    private val eqGrid: LineSet by lazy {
        val polys = ArrayList<DoubleArray>(); val cols = ArrayList<Int>()
        for (h in 0 until 24) { polys += xyz((-88..88 step 2).map { Vec3.fromSpherical(h * 15.0, it.toDouble()) }); cols += (0x50 shl 24) or GRID_RGB }
        for (d in -80..80 step 10) {
            polys += xyz((0..360 step 2).map { Vec3.fromSpherical(it.toDouble(), d.toDouble()) })
            cols += if (d == 0) (0xA0 shl 24) or EQUATOR_RGB else (0x50 shl 24) or GRID_RGB
        }
        polys += xyz((0..360 step 2).map { Frames.eclipticToEquatorial(it.toDouble(), 0.0, OBLIQUITY_J2000) }); cols += (0xA0 shl 24) or ECLIPTIC_RGB
        LineSet(polys, cols.toIntArray())
    }

    /** 지평 격자(월드): 방위선 24개, 고도선 9개(0° = 지평선) */
    private val altazGrid: LineSet by lazy {
        val polys = ArrayList<DoubleArray>(); val cols = ArrayList<Int>()
        for (az in 0 until 360 step 15) { polys += xyz((0..88 step 2).map { altAzWorld(it.toDouble(), az.toDouble()) }); cols += (0x50 shl 24) or ALTAZ_RGB }
        for (alt in 0..80 step 10) {
            polys += xyz((0..360 step 2).map { altAzWorld(alt.toDouble(), it.toDouble()) })
            cols += if (alt == 0) (0xB0 shl 24) or HORIZON_RGB else (0x50 shl 24) or ALTAZ_RGB
        }
        LineSet(polys, cols.toIntArray())
    }

    private const val MOONPATH_RGB = 0xC8C8D8
    private var moonPathDay = Double.NaN
    private var moonPathCache: Pair<LineSet, Vec3>? = null

    /**
     * 백도: 황도에 5.145° 기운 큰원. 승교점 = 그날 달의 평균 승교점(Meeus 47.7, 18.6년에 한 바퀴 서쪽으로).
     * 하루에 한 번만 다시 만듦. 반환 = (선, 승교점 J2000 방향)
     */
    private fun moonPath(): Pair<LineSet, Vec3> {
        val level = Minecraft.getInstance().level
        val jd = CheonsangSky.clock.jd((level?.defaultClockTime ?: 0L).toDouble())
        val cached = moonPathCache
        if (cached != null && kotlin.math.abs(jd - moonPathDay) < 1.0) return cached
        val t = kr.inf.cheonsang.astro.centuries(jd)
        val node = Math.toRadians(125.0445479 - 1934.1362891 * t + 0.0020754 * t * t)
        val inc = Math.toRadians(5.145)
        val eps = Math.toRadians(Frames.obliquity(jd))
        val pm = Frames.precession(jd).m
        fun toJ2000(u: Double): Vec3 {
            val cu = kotlin.math.cos(u); val su = kotlin.math.sin(u)
            val ex = kotlin.math.cos(node) * cu - kotlin.math.sin(node) * su * kotlin.math.cos(inc)
            val ey = kotlin.math.sin(node) * cu + kotlin.math.cos(node) * su * kotlin.math.cos(inc)
            val ez = su * kotlin.math.sin(inc)
            val qx = ex; val qy = ey * kotlin.math.cos(eps) - ez * kotlin.math.sin(eps); val qz = ey * kotlin.math.sin(eps) + ez * kotlin.math.cos(eps)
            // 그날 적도 → J2000: 세차 행렬의 전치
            return Vec3(pm[0] * qx + pm[3] * qy + pm[6] * qz, pm[1] * qx + pm[4] * qy + pm[7] * qz, pm[2] * qx + pm[5] * qy + pm[8] * qz)
        }
        val set = LineSet(listOf(xyz((0..360 step 2).map { toJ2000(Math.toRadians(it.toDouble())) })), intArrayOf((0xA0 shl 24) or MOONPATH_RGB))
        return (set to toJ2000(0.0)).also { moonPathCache = it; moonPathDay = jd }
    }

    /** 별자리 경계선 (J2000) */
    private val boundLines: LineSet by lazy {
        val polys = SkyData.bounds.map { p -> xyz(List(p.size / 2) { Vec3.fromSpherical(p[it * 2], p[it * 2 + 1]) }) }
        // 꺾이는 점 (앞뒤 방향이 3° 넘게 바뀜) — 건너뛰기에서 항상 남김
        val keep = polys.map { a ->
            val n = a.size / 3
            BooleanArray(n) { i ->
                if (i == 0 || i == n - 1) true else {
                    val d1 = Vec3(a[i * 3] - a[i * 3 - 3], a[i * 3 + 1] - a[i * 3 - 2], a[i * 3 + 2] - a[i * 3 - 1])
                    val d2 = Vec3(a[i * 3 + 3] - a[i * 3], a[i * 3 + 4] - a[i * 3 + 1], a[i * 3 + 5] - a[i * 3 + 2])
                    d1.length() > 0 && d2.length() > 0 && d1.normalized().angleTo(d2.normalized()) > 3.0
                }
            }
        }
        LineSet(polys, IntArray(polys.size) { (0x70 shl 24) or BOUNDS_RGB }, keep)
    }

    /**
     * 선 묶음 그리기: mat = J2000 → 월드 (null 이면 이미 월드). 선분 양 끝 중 하나라도 시야(대각 반각 + 4°) 안이면 그려서 화면 끝까지 이어지게 함(밖은 fastLine 이 자름).
     * decimate: 경계선용 — 배율에 따라 점을 듬성듬성 쓰고(×1 4점마다, ×7 2점마다, ×25↑ 전부) 꺾이는 모서리는 항상 씀.
     */
    private fun drawLineSet(g: GuiGraphicsExtractor, mc: Minecraft, pr: Projector, scale: Float, set: LineSet, mat: kr.inf.cheonsang.astro.Mat3?, decimate: Boolean = false) {
        val m = mat?.m
        val fx = pr.f.x().toDouble(); val fy = pr.f.y().toDouble(); val fz = pr.f.z().toDouble()
        val ux = pr.u.x().toDouble(); val uy = pr.u.y().toDouble(); val uz = pr.u.z().toDouble()
        val lx = pr.l.x().toDouble(); val ly = pr.l.y().toDouble(); val lz = pr.l.z().toDouble()
        val halfDiag = Math.toDegrees(kotlin.math.atan(pr.t * hypot(1.0, pr.w / pr.h)))
        val cosCull = kotlin.math.cos(Math.toRadians(min(halfDiag + 4.0, 89.0)))
        val s = scale.toDouble(); val sw = mc.window.width.toDouble(); val sh = mc.window.height.toDouble()
        val k = pr.h / 2 / pr.t
        val step = if (!decimate) 1 else {
            val z = if (TelescopeView.active) TelescopeView.LEVELS[TelescopeView.level] else 1.0
            if (z < 3) 4 else if (z < 15) 2 else 1
        }
        for ((i, a) in set.polys.withIndex()) {
            val color = set.colors[i]
            val keep = set.keep?.get(i)
            val n = a.size / 3
            var prevFront = false; var prevIn = false; var px = 0.0; var py = 0.0
            for (idx in 0 until n) {
                if (step > 1 && idx % step != 0 && idx != n - 1 && (keep == null || !keep[idx])) continue
                val j = idx * 3
                var x = a[j]; var y = a[j + 1]; var z = a[j + 2]
                if (m != null) {
                    val wx = m[0] * x + m[1] * y + m[2] * z; val wy = m[3] * x + m[4] * y + m[5] * z; val wz = m[6] * x + m[7] * y + m[8] * z
                    x = wx; y = wy; z = wz
                }
                val depth = x * fx + y * fy + z * fz
                val front = depth > 0.05
                val inside = depth >= cosCull
                if (front) {
                    val qx = (pr.w / 2 - (x * lx + y * ly + z * lz) / depth * k) * s
                    val qy = (pr.h / 2 - (x * ux + y * uy + z * uz) / depth * k) * s
                    if (prevFront && (inside || prevIn)) fastLine(g, px, py, qx, qy, color, sw, sh)
                    px = qx; py = qy
                }
                prevFront = front; prevIn = inside
            }
        }
    }

    private fun drawGrids(g: GuiGraphicsExtractor, mc: Minecraft, pr: Projector, scale: Float) {
        g.pose().pushMatrix(); g.pose().scale(1f / scale, 1f / scale)
        val labels = ArrayList<Triple<String, Vec3, Int>>()
        if (TelescopeFilter.grid) {
            drawLineSet(g, mc, pr, scale, eqGrid, CheonsangSky.skyMat3)
            // 백도 + 승교점·강교점
            val mp = moonPath()
            drawLineSet(g, mc, pr, scale, mp.first, CheonsangSky.skyMat3)
            labels += Triple(L.t("cheonsang.telescopehud.15"), CheonsangSky.skyMat3 * mp.second, MOONPATH_RGB)
            labels += Triple(L.t("cheonsang.telescopehud.16"), CheonsangSky.skyMat3 * (mp.second * -1.0), MOONPATH_RGB)
            for (h in 0 until 24) labels += Triple("${h}h", sky(h * 15.0, 0.0), EQUATOR_RGB)
            // 적위 숫자: 화면 가운데에 가장 가까운 시간선 위
            val m = CheonsangSky.skyMat3.m; val c = pr.center
            val cra = Math.toDegrees(kotlin.math.atan2(m[1] * c.x + m[4] * c.y + m[7] * c.z, m[0] * c.x + m[3] * c.y + m[6] * c.z))
            val ra0 = Math.round(cra / 15.0) * 15.0
            for (d in -80..80 step 10) if (d != 0) labels += Triple("%+d°".format(d), sky(ra0, d.toDouble()), GRID_RGB)
        }
        if (TelescopeFilter.altaz) {
            drawLineSet(g, mc, pr, scale, altazGrid, null)
            listOf(L.t("cheonsang.telescopehud.24"), L.t("cheonsang.telescopehud.23"), L.t("cheonsang.telescopehud.22"), L.t("cheonsang.telescopehud.21"), L.t("cheonsang.telescopehud.20"), L.t("cheonsang.telescopehud.19"), L.t("cheonsang.telescopehud.18"), L.t("cheonsang.telescopehud.17")).forEachIndexed { k, n -> labels += Triple(n, altAzWorld(1.5, k * 45.0), HORIZON_RGB) }
            val (_, caz) = Frames.altAz(pr.center)
            val az0 = Math.round(caz / 15.0) * 15.0
            for (alt in 10..80 step 10) labels += Triple("$alt°", altAzWorld(alt.toDouble(), az0), ALTAZ_RGB)
        }
        if (TelescopeFilter.bounds) drawLineSet(g, mc, pr, scale, boundLines, CheonsangSky.skyMat3, decimate = true)
        g.pose().popMatrix()
        for ((label, w, rgb) in labels) {
            val p = pr.project(w) ?: continue
            if (p[0] < 0 || p[1] < 0 || p[0] > pr.w || p[1] > pr.h) continue
            text(g, mc, label, p[0], p[1] - 4, rgb, 0.85f)
        }
    }

    private const val BLACK = 0xFF000000.toInt()

    /** 갈릴레이 위성 번호·평균 밝기 (충 무렵) */
    private val JOVIAN = mapOf("이오" to ("Ⅰ" to 5.0), "유로파" to ("Ⅱ" to 5.3), "가니메데" to ("Ⅲ" to 4.6), "칼리스토" to ("Ⅳ" to 5.7))

    private fun fadeTo(key: String, on: Boolean, step: Float) {
        val a = ((fade[key] ?: 0f) + if (on) step else -step).coerceIn(0f, 1f)
        if (a <= 0f) fade.remove(key) else fade[key] = a
    }

    private fun text(g: GuiGraphicsExtractor, mc: Minecraft, s: String, x: Double, y: Double, rgb: Int, a: Float) {
        val alpha = (255 * a).toInt()
        if (alpha < 10) return
        val sc = TelescopeFilter.uiScale
        if (sc == 1f) { g.centeredText(mc.font, s, x.roundToInt(), y.roundToInt(), (alpha shl 24) or rgb); return }
        // 글씨 배율 — 기준점(x, y)은 그대로
        g.pose().pushMatrix()
        g.pose().translate(x.toFloat(), y.toFloat())
        g.pose().scale(sc, sc)
        g.centeredText(mc.font, s, 0, 0, (alpha shl 24) or rgb)
        g.pose().popMatrix()
    }

    private fun distToSegment(px: Double, py: Double, a: DoubleArray, b: DoubleArray): Double {
        val dx = b[0] - a[0]; val dy = b[1] - a[1]
        val len2 = dx * dx + dy * dy
        val k = if (len2 == 0.0) 0.0 else (((px - a[0]) * dx + (py - a[1]) * dy) / len2).coerceIn(0.0, 1.0)
        return hypot(px - (a[0] + dx * k), py - (a[1] + dy * k))
    }

    /** 별 ─ 별: 실선, 양쪽 별 앞에서 끊음. a·b 는 GUI 좌표, 그리기는 실제 픽셀 (s = GUI 배율) */
    private fun starToStar(g: GuiGraphicsExtractor, a: DoubleArray, b: DoubleArray, s: Double, color: Int, sw: Double, sh: Double, gapA: Double, gapB: Double) {
        var x0 = a[0] * s; var y0 = a[1] * s; var x1 = b[0] * s; var y1 = b[1] * s
        val len = hypot(x1 - x0, y1 - y0)
        val ga = gapA * s; val gb = gapB * s
        // 길이 상한 없음 — 화면 밖은 아래에서 잘라냄
        if (len <= ga + gb || !len.isFinite()) return
        val ux = (x1 - x0) / len; val uy = (y1 - y0) / len
        x0 += ux * ga; y0 += uy * ga; x1 -= ux * gb; y1 -= uy * gb
        // 화면 밖 부분은 잘라냄 (Liang–Barsky)
        var t0 = 0.0; var t1 = 1.0
        val dx = x1 - x0; val dy = y1 - y0
        for ((p, q) in arrayOf(-dx to x0, dx to sw - x0, -dy to y0, dy to sh - y0)) {
            if (p == 0.0) { if (q < 0) return; continue }
            val r = q / p
            if (p < 0) { if (r > t1) return; t0 = max(t0, r) } else { if (r < t0) return; t1 = min(t1, r) }
        }
        line(g, x0 + dx * t0, y0 + dy * t0, x0 + dx * t1, y0 + dy * t1, color)
    }

    /** 선분이 사각형(x0, y0, x1, y1)을 지나는지 (Liang–Barsky) */
    private fun segHitsRect(a: DoubleArray, b: DoubleArray, r: DoubleArray): Boolean {
        var t0 = 0.0; var t1 = 1.0
        val dx = b[0] - a[0]; val dy = b[1] - a[1]
        for ((p, q) in arrayOf(-dx to a[0] - r[0], dx to r[2] - a[0], -dy to a[1] - r[1], dy to r[3] - a[1])) {
            if (p == 0.0) { if (q < 0) return false; continue }
            val t = q / p
            if (p < 0) { if (t > t1) return false; t0 = max(t0, t) } else { if (t < t0) return false; t1 = min(t1, t) }
        }
        return true
    }

    /** 2픽셀 선. 같은 줄에 이어지는 픽셀은 사각형 하나로 묶어 그림 (반투명이 겹쳐 진해지지 않게) */
    private fun line(g: GuiGraphicsExtractor, x0: Double, y0: Double, x1: Double, y1: Double, color: Int) {
        val dx = x1 - x0; val dy = y1 - y0
        val horizontal = abs(dx) >= abs(dy)
        val n = (if (horizontal) abs(dx) else abs(dy)).roundToInt()
        if (n == 0) return
        fun major(k: Int) = floor(if (horizontal) x0 + dx * k / n else y0 + dy * k / n).toInt()
        fun minor(k: Int) = floor(if (horizontal) y0 + dy * k / n else x0 + dx * k / n).toInt()
        fun run(m0: Int, m1: Int, c: Int) {
            val lo = min(m0, m1); val hi = max(m0, m1) + 1
            if (horizontal) g.fill(lo, c, hi, c + 2, color) else g.fill(c, lo, c + 2, hi, color)
        }
        var start = major(0); var prev = start; var row = minor(0)
        for (k in 1..n) {
            val m = major(k); val c = minor(k)
            if (c != row) { run(start, prev, row); start = m; row = c }
            prev = m
        }
        run(start, prev, row)
    }

    /** 화면 가운데에서 radiusPx 안의 가장 가까운 천체(해·달·행성·심원천체·별, 지금 보이는 한계등급까지) */
    private fun find(pr: Projector, radiusPx: Double): Target? {
        // 별·행성·달은 굴절해서 그리므로 조준 방향을 굴절 전으로 되돌려 비교, 빛무리는 조준 방향 그대로
        val raw = pr.center
        val center = Visibility.unrefractWorld(raw)
        val maxDeg = pr.pixelsToDeg(radiusPx)
        var best = Double.MAX_VALUE
        var found: (() -> Target)? = null
        fun offer(angle: Double, radiusDeg: Double, label: () -> Target) {
            val d = max(0.0, angle - radiusDeg)
            if (d <= maxDeg && d < best) { best = d; found = label }
        }
        fun offer(dir: Vec3, radiusDeg: Double, label: () -> Target) = offer(center.angleTo(dir.normalized()), radiusDeg, label)

        // ×200↑ 에서 가운데가 달 위면 그 자리 지명 (지형이 없으면 아래에서 "달")
        MoonNames.at(center)?.let { return MoonNames.target(it.first) }

        offer(CheonsangSky.sunWorld, 0.27) { InfoCard.sun() }
        offer(CheonsangSky.moonWorld, CheonsangSky.moonRadius) { InfoCard.moon() }
        // 목성 위성: 원판이 보일 만큼 확대했을 때만. 목성은 원판 크기만큼 넓게 잡아 원판 위면 목성
        for ((name, dir) in CheonsangSky.jovianMoonsWorld()) offer(dir, 0.0) {
            val (num, mag) = JOVIAN[name] ?: ("" to 5.5)
            InfoCard.jovianMoon(name, num, mag, dir)
        }
        // 토성 위성 (원판이 보일 만큼 확대했을 때만)
        for ((m, dir) in CheonsangSky.saturnMoonsWorld()) offer(dir, 0.0) { InfoCard.saturnMoon(m.name, m.num, m.mag, dir) }
        val limNow = Visibility.pointLimit(center) + 0.5  // 가시성 모델
        for (p in CheonsangSky.planets) if (p.mag <= limNow) offer(CheonsangSky.skyMat3 * p.dir, p.diameterArcsec / 7200.0) { InfoCard.planet(p) }
        for (a in CheonsangSky.asteroids) if (a.mag <= limNow) offer(CheonsangSky.skyMat3 * a.dir, 0.0) { InfoCard.asteroid(a) }
        // 빛무리가 조준 반경보다 넓으면(히아데스·플레이아데스 등) 가운데 25% 안에서만 거리 0 쪽으로, 그 밖은 조준 반경의 60% 로 쳐서 가까운 별·행성이 이기게 함
        for (o in SkyData.dso) {
            val ang = raw.angleTo(CheonsangSky.skyMat3 * Vec3.fromSpherical(o.ra, o.dec))
            val rad = o.major / 120.0
            if (rad > maxDeg && ang < rad) {
                val d = maxDeg * 0.6 * (ang / (rad * 0.25)).coerceAtMost(1.0)
                if (d < best) { best = d; found = { InfoCard.dso(o) } }
            } else offer(ang, rad) { InfoCard.dso(o) }
        }
        // 별: 가운데 방향을 J2000 으로 되돌려 내적으로 거름 (skyMat3 는 회전행렬 → 전치 = 역행렬)
        val cat = CheonsangSky.catalog
        if (cat != null) {
            val m = CheonsangSky.skyMat3.m
            val cx = m[0] * center.x + m[3] * center.y + m[6] * center.z
            val cy = m[1] * center.x + m[4] * center.y + m[7] * center.z
            val cz = m[2] * center.x + m[5] * center.y + m[8] * center.z
            val cosLimit = cos(Math.toRadians(maxDeg * 1.2))
            // 별 방향은 float (크기 1 에서 ±1e-8) → double 로 정규화한 뒤 atan2(|외적|, 내적) 로 정밀하게 계산
            fun cosTo(x: Float, y: Float, z: Float): Double {
                val nn = sqrt(x.toDouble() * x + y.toDouble() * y + z.toDouble() * z)
                return (x * cx + y * cy + z * cz) / nn
            }
            fun angleTo(x: Float, y: Float, z: Float): Double {
                val nn = sqrt(x.toDouble() * x + y.toDouble() * y + z.toDouble() * z)
                val ax = x / nn; val ay = y / nn; val az = z / nn
                val crx = ay * cz - az * cy; val cry = az * cx - ax * cz; val crz = ax * cy - ay * cx
                return Math.toDegrees(kotlin.math.atan2(sqrt(crx * crx + cry * cry + crz * crz), ax * cx + ay * cy + az * cz))
            }
            // 밝은 별 우대 폭에 상한 (조준 반경 5% 또는 2″ 중 큰 것)
            val biasCap = max(maxDeg * 0.05, 2.0 / 3600)
            val n = minOf(cat.countBrighterThan(limNow.toFloat()), cat.realCount)
            for (i in 0 until n) {
                if (cosTo(cat.dir[i * 3], cat.dir[i * 3 + 1], cat.dir[i * 3 + 2]) < cosLimit) continue
                // 변광성은 지금 등급으로 (어두워져 안 보이면 안 고름)
                kr.inf.cheonsang.client.sky.VariableStars.byHip[cat.hip[i]]?.let { v -> if (v.mag(CheonsangSky.clock.jd((Minecraft.getInstance().level?.defaultClockTime ?: 0L).toDouble())) > limNow) continue }
                val angle = angleTo(cat.dir[i * 3], cat.dir[i * 3 + 1], cat.dir[i * 3 + 2])
                // 거의 같은 거리면 밝은 별 우선
                offer(angle, min(maxDeg * 0.1 * (9 - cat.mag[i]) / 9, biasCap)) { starLabel(cat, i).let { (sh, ln) -> InfoCard.star(cat, i, sh, ln) } }
            }
            // 망원경으로 그리는 깊은 별 (9등 넘는 Tycho-2·Gaia) — 지금 그려진 목록에서
            if (TelescopeView.magnifying) {
                val ds = kr.inf.cheonsang.client.sky.DeepStars
                val od = ds.outDir
                for (k in 0 until ds.lastCount) {
                    val mg = ds.outMag[k]
                    if (mg > limNow) continue
                    if (cosTo(od[k * 3], od[k * 3 + 1], od[k * 3 + 2]) < cosLimit) continue
                    val angle = angleTo(od[k * 3], od[k * 3 + 1], od[k * 3 + 2])
                    val dirJ = Vec3(od[k * 3].toDouble(), od[k * 3 + 1].toDouble(), od[k * 3 + 2].toDouble())
                    val bvv = ds.outBv[k]
                    offer(angle, min(maxDeg * 0.1 * (9 - mg) / 9, biasCap)) { InfoCard.deep(cat, dirJ, mg, bvv) }
                }
            }
        }
        return found?.invoke()
    }

    /** 별 이름(별자리 기준), 예: "베텔게우스 (오리온자리 α)", "오리온자리 η", "오리온자리 HIP 27366", "오리온자리의 별" */
    private fun starLabel(cat: StarCatalog, i: Int): Pair<String, String> {
        val hip = cat.hip[i]
        val name = SkyData.starLabel(hip)
        val con = cat.con[i].takeIf { it >= 0 }?.let { SkyData.constellations.getOrNull(it) }
        val short = if (con == null) {
            name ?: if (hip > 0) "HIP $hip" else L.t("cheonsang.telescopehud.29")
        } else {
            val conName = L.name(con.ko, con.en)
            val desig = cat.designation(i, conName)
            when {
                name != null -> "$name (${desig ?: conName})"
                desig != null -> desig
                hip > 0 -> "$conName HIP $hip"
                else -> L.t("cheonsang.telescopehud.30", conName)
            }
        }
        return short to "$short | " + L.t("cheonsang.telescopehud.31").format(cat.mag[i])
    }

    /** 좌클릭: 화면 가운데에서 가장 가까운 천체 → 선택 (원 표시 + 정보 카드) + 화면 아래 한 줄. 빈 하늘 = 선택 해제 */
    fun pick() = Safety.run(Safety.TELESCOPE, Unit) { pickNow() }

    private fun pickNow() {
        val mc = Minecraft.getInstance()
        // Shift(웅크리기 키) + 좌클릭 = 선택 해제 (추적도 해제)
        if (mc.options.keyShift.isDown) {
            mc.player?.sendOverlayMessage(Component.literal(if (selected != null) L.t("cheonsang.telescopehud.33") else L.t("cheonsang.telescopehud.32")))
            selected = null; locked = null; measure = null
            return
        }
        // 각거리: 선택한 채 Ctrl + 좌클릭 = 가운데 천체까지 각도 (선택은 그대로)
        if (mc.hasControlDown() && selected != null) {
            val t = find(Projector(mc), PICK_RADIUS)
            val sel = selected!!
            if (t == null) { mc.player?.sendOverlayMessage(Component.literal(L.t("cheonsang.telescopehud.34"))); return }
            if (t.key == sel.key) { measure = null; mc.player?.sendOverlayMessage(Component.literal(L.t("cheonsang.telescopehud.35"))); return }
            measure = t
            mc.player?.sendOverlayMessage(Component.literal(L.t("cheonsang.telescopehud.37", sel.short, t.short, angleText(sel, t) ?: "-")))
            return
        }
        // 조작 설정에 등록한 추적 키 + 좌클릭 = 선택 + 추적 고정 (추적 중이면 해제)
        if (TelescopeFilter.trackKey.isDown) {
            if (locked != null) { locked = null; mc.player?.sendOverlayMessage(Component.literal(L.t("cheonsang.telescopehud.38"))); return }
            val t = find(Projector(mc), PICK_RADIUS)
            if (t == null) { mc.player?.sendOverlayMessage(Component.literal(L.t("cheonsang.telescopehud.34"))); return }
            selected = t; locked = t; measure = null
            mc.player?.sendOverlayMessage(Component.literal(L.t("cheonsang.telescopehud.39", t.short)))
            return
        }
        val t = find(Projector(mc), PICK_RADIUS)
        val msg = when {
            t != null -> t.line
            selected != null -> L.t("cheonsang.telescopehud.33")
            else -> L.t("cheonsang.telescopehud.34")
        }
        selected = t; measure = null
        mc.player?.sendOverlayMessage(Component.literal(msg))
    }

    /** /cfind … true: 시점을 돌린 뒤 몇 틱 지나 화면 가운데 천체를 골라 추적 고정 */
    private var lockIn = -1
    /** 정해진 대상 (위성 — 저배율에선 가운데 고르기로 안 잡힐 수 있음). null = 가운데 고르기 */
    private var lockTarget: (() -> Target)? = null
    private var lockNote: (() -> String?)? = null
    fun lockCenterSoon() { lockIn = 3; lockTarget = null; lockNote = null }
    internal fun lockSoon(target: () -> Target, note: () -> String?) { lockIn = 3; lockTarget = target; lockNote = note }
    fun tickLock() {
        if (lockIn < 0) return
        if (--lockIn > 0) return
        lockIn = -1
        val fixed = lockTarget; val note = lockNote
        lockTarget = null; lockNote = null
        if (!TelescopeView.active) return
        val mc = Minecraft.getInstance()
        val t = runCatching { fixed?.invoke() }.getOrNull() ?: find(Projector(mc), PICK_RADIUS) ?: return
        selected = t; locked = t; measure = null
        mc.player?.sendOverlayMessage(Component.literal(L.t("cheonsang.telescopehud.39", t.short) + (note?.invoke()?.let { " | $it" } ?: "")))
    }

    /** /cfind 행성·심원천체 추적용 (가운데를 다시 고르지 않음) */
    internal fun planetTarget(p: kr.inf.cheonsang.astro.PlanetPos): Target = InfoCard.planet(p)
    internal fun dsoTarget(o: kr.inf.cheonsang.client.sky.Dso): Target = InfoCard.dso(o)

    /** /cfind 위성 추적용 */
    internal fun moonTarget(ko: String, num: String, mag: Double, dir: Vec3, saturn: Boolean): Target =
        if (saturn) InfoCard.saturnMoon(ko, num, mag, dir) else InfoCard.jovianMoon(ko, num, mag, dir)

    /** 서버를 나가면 선택 해제 */
    fun clearSelection() { selected = null; locked = null; measure = null }
}
