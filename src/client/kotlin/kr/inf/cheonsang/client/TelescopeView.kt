package kr.inf.cheonsang.client

import kr.inf.cheonsang.L
import kr.inf.cheonsang.Telescope
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import kr.inf.cheonsang.astro.Mat3
import kr.inf.cheonsang.astro.Vec3
import kr.inf.cheonsang.client.sky.CheonsangSky
import net.minecraft.util.Mth
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 망원경으로 보는 상태 (클라이언트). 우클릭 켜기/끄기, 휠 접안(×1~11단계)·Shift+휠 대물 구경(50~305mm), 전환 시 0.2초 부드럽게 전환.
 * 한계등급 = 맨눈 + 구경 이득(Optics.gain), 최대 13등, Gaia 팩 있으면 15.2등(9등 초과는 DeepStars, Tycho-2·Gaia 실측). 적도의 모드는 켜진 동안 별이 화면에 고정됨.
 */
object TelescopeView {
    /** 배율 단계 = ×1 + 접안마다 (대물 초점 ÷ 접안 초점, 구경을 바꾸면 같이 바뀜). 전자줌 없음 */
    private var levelsVersion = -1
    private var levelsCache = DoubleArray(Optics.EYEPIECES.size + 1) { 1.0 }
    @JvmStatic
    val LEVELS: DoubleArray
        get() {
            if (levelsVersion != Optics.version) {
                levelsCache = DoubleArray(Optics.EYEPIECES.size + 1) { if (it == 0) 1.0 else Optics.magnification(it - 1) }
                levelsVersion = Optics.version
            }
            return levelsCache
        }
    const val OPTICAL_MAX = 100_000.0
    private const val SWITCH_NANOS = 200_000_000L
    /** 본체 별 13등까지 (DeepStars), Gaia 별 팩 있으면 15.2등 */
    @JvmStatic
    val MAX_LIMIT: Float get() = if (kr.inf.cheonsang.client.sky.DeepStars.packAvailable()) 15.2f else 13.0f

    @JvmStatic
    var active = false
        private set

    /** 지금 단계 (LEVELS 번호) */
    var level = 1
        private set
    private var fromZoom = LEVELS[1]
    private var switchStart = 0L

    /** 지금 배율 — 단계가 바뀐 뒤 0.2초 동안 이전 배율에서 로그 비율로 넘어감 */
    @JvmStatic
    val zoom: Double
        get() {
            val to = LEVELS[level]
            val t = ((System.nanoTime() - switchStart).toDouble() / SWITCH_NANOS).coerceIn(0.0, 1.0)
            if (t >= 1.0) return to
            val e = t * t * (3 - 2 * t)
            return kotlin.math.exp(kotlin.math.ln(fromZoom) + (kotlin.math.ln(to) - kotlin.math.ln(fromZoom)) * e)
        }

    /** 확대해서 보는 중 (×1 이 아님) — 해·달 실제 크기, 자세한 달 지도, 사진 등 */
    @JvmStatic
    val magnifying get() = active && level > 0

    /** 광학 배율 (전자줌 빼고) — 한계등급·성운·은하수 곡선은 이것으로 */
    @JvmStatic
    val opticalZoom: Double get() = zoom.coerceAtMost(OPTICAL_MAX)

    /** 지금 보이는 한계등급 */
    fun limit(naked: Float): Float =
        if (!active) naked else (naked + Optics.gain(opticalZoom).toFloat()).coerceAtMost(MAX_LIMIT).coerceAtLeast(naked)

    /** 망원경을 손(주·보조)에 들고 있는지 — 큰 그림 미리 올리기 */
    fun held(): Boolean {
        val p = Minecraft.getInstance().player ?: return false
        return p.mainHandItem.`is`(Telescope.ITEM) || p.offhandItem.`is`(Telescope.ITEM)
    }

    fun register() {
        Telescope.clientToggle = { toggle() }
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            val p = mc.player
            if (active && (p == null || (!p.mainHandItem.`is`(Telescope.ITEM) && !p.offhandItem.`is`(Telescope.ITEM)))) active = false
        }
    }

    /** 지난번 추적 때의 하늘 회전 (J2000 → 월드) */
    private var trackMat: Mat3? = null

    /** 적도의: 프레임마다 지금 방향을 지난 하늘 기준 J2000 으로 되돌린 뒤 지금 하늘로 다시 보내 시점을 돌린다. */
    @JvmStatic
    fun track() = Safety.run(Safety.TELESCOPE, Unit) { trackNow() }

    /** 추적 고정: 고른 천체 방향으로 시점을 바로 맞춘다(행성·달도 가운데 유지). 지평선 아래로 지면 해제. */
    private fun lockNow(): Boolean {
        val t = TelescopeHud.locked ?: return false
        val p = Minecraft.getInstance().player ?: return false
        if (!active) { TelescopeHud.locked = null; return false }
        // 대상 방향을 이번 프레임 하늘로 옮겨서 맞춤 (지난 프레임 기준이면 한 프레임 늦어 고배율에서 떨림)
        val now = CheonsangSky.skyMatNow()
        val w = t.dir()?.normalized()?.let { if (now != null && CheonsangSky.isActive()) CheonsangSky.toNow(it, now).normalized() else it }
            ?.let { TelescopeHud.drawnDir(t, it) }  // 그려진(굴절 후) 위치를 가운데에
        if (w == null || w.y < -0.02) {
            TelescopeHud.locked = null
            p.sendOverlayMessage(Component.literal(L.t("cheonsang.telescopeview.1")))
            return false
        }
        val yaw = Math.toDegrees(atan2(-w.x, w.z)).toFloat()
        val pitch = Math.toDegrees(asin((-w.y).coerceIn(-1.0, 1.0))).toFloat()
        setView(p, yaw, pitch)
        trackMat = null
        return true
    }

    /** 추적 중엔 마우스로 시점이 안 움직임. 크게 움직이면 추적 해제. 반환 true = 마우스 움직임 버림 */
    @JvmStatic
    fun consumeMouse(dx: Double, dy: Double): Boolean {
        if (TelescopeHud.locked == null || !active) return false
        if (kotlin.math.hypot(dx, dy) > 60.0) {
            TelescopeHud.locked = null
            Minecraft.getInstance().player?.sendOverlayMessage(Component.literal(L.t("cheonsang.telescopeview.2")))
            return false
        }
        return true
    }

    private fun trackNow() {
        if (lockNow()) return
        val p = Minecraft.getInstance().player
        if (!magnifying || p == null || !CheonsangSky.isActive()) { trackMat = null; return }
        // 이번 프레임 시각의 하늘 행렬 (지난 프레임 기준이면 한 프레임 늦고 프레임 간격이 들쭉날쭉하면 떨림)
        val cur = CheonsangSky.skyMatNow() ?: return
        val prev = trackMat
        trackMat = cur
        if (prev == null || prev.m.contentEquals(cur.m)) return
        val yaw = Math.toRadians(p.yRot.toDouble()); val pitch = Math.toRadians(p.xRot.toDouble())
        val v = Vec3(-sin(yaw) * cos(pitch), -sin(pitch), cos(yaw) * cos(pitch))
        val m = prev.m // 회전행렬이라 전치 = 역행렬
        val j = Vec3(m[0] * v.x + m[3] * v.y + m[6] * v.z, m[1] * v.x + m[4] * v.y + m[7] * v.z, m[2] * v.x + m[5] * v.y + m[8] * v.z)
        val w = cur * j
        // 천정 근처(고도 84° 이상)는 좌우 방향이 불안정해 위아래만 따라가고 좌우는 유지. 한 프레임 회전은 5도로 제한
        val dYaw = if (cos(pitch) < 0.1 || kotlin.math.hypot(w.x, w.z) < 0.1) 0f
            else Mth.wrapDegrees(Math.toDegrees(atan2(-w.x, w.z)) - p.yRot).toFloat().coerceIn(-5f, 5f)
        val newPitch = Math.toDegrees(asin((-w.y).coerceIn(-1.0, 1.0))).coerceIn(-90.0, 90.0).toFloat()
        setView(p, p.yRot + dYaw, newPitch)
    }

    /** 시점을 (yaw, pitch) 로 맞추고 이전 프레임 값도 같이 옮겨 보간 끊김을 없앤다. yaw 는 -180~180 으로 접음(안 그러면 float 정밀도 부족으로 고배율에서 떨림) */
    private fun setView(p: net.minecraft.world.entity.player.Player, yaw: Float, pitch: Float) {
        val wrapped = Mth.wrapDegrees(yaw)
        val oldOffset = p.yRotO - p.yRot
        val dPitch = pitch - p.xRot
        p.setYRot(wrapped)
        p.yRotO = wrapped + oldOffset
        p.setXRot(pitch); p.xRotO += dPitch
    }

    /** /cbench: 망원경 켜기·끄기와 배율 단계를 바로 정함 (넘어가는 0.2초 없음) */
    internal fun benchSet(on: Boolean, lvl: Int) {
        active = on
        level = lvl.coerceIn(0, LEVELS.size - 1)
        fromZoom = LEVELS[level]; switchStart = 0L
    }

    /** /cfind … true: 망원경을 손에 들고 있으면 켬 (이미 켜져 있으면 그대로). 켜졌으면 true */
    fun ensureOn(): Boolean {
        if (active) return true
        if (!held()) return false
        toggle()
        return active
    }

    /** 시점을 월드 방향으로 돌림 */
    fun lookAt(w0: Vec3) {
        val p = Minecraft.getInstance().player ?: return
        val w = w0.normalized()
        setView(p, Math.toDegrees(atan2(-w.x, w.z)).toFloat(), Math.toDegrees(asin((-w.y).coerceIn(-1.0, 1.0))).toFloat())
        trackMat = null
    }

    private fun toggle() {
        active = !active
        // 켤 때마다 ×1, 기본 구경(80mm)으로 초기화
        if (active) { if (Optics.version != levelsVersion || Optics.apIdx != Optics.DEFAULT_APERTURE) { Optics.reset(); Visibility.bump() }; level = 0; fromZoom = LEVELS[0]; switchStart = 0L }
        if (active) firstGuide() else Minecraft.getInstance().player?.sendOverlayMessage(Component.literal(L.t("cheonsang.telescopeview.3")))
    }

    /** 휠 한 칸 (위 = 확대). 망원경이 켜져 있어 휠을 가져갔으면 true */
    @JvmStatic
    fun scroll(amount: Double): Boolean {
        if (!active || amount == 0.0) return false
        if (Minecraft.getInstance().gui.screen() != null) return false  // 화면이 열려 있으면 휠은 화면 몫
        // 구경 키(기본 Shift) + 휠 = 대물 구경, 휠만 = 접안 (배율)
        if (apertureHeld()) {
            val before = zoom
            if (Optics.stepAperture(if (amount > 0) 1 else -1)) {
                Visibility.bump()
                fromZoom = before; switchStart = System.nanoTime()
            }
            return true
        }
        val next = (level + if (amount > 0) 1 else -1).coerceIn(0, LEVELS.size - 1)
        if (next != level) {
            fromZoom = zoom
            switchStart = System.nanoTime()
            level = next
        }
        return true
    }

    /** 구경 키 — 웅크리기와 같은 키면 웅크리기 키 상태로 (같은 키 둘은 한쪽만 눌림 처리될 수 있음) */
    private fun apertureHeld(): Boolean {
        val mc = Minecraft.getInstance()
        val k = TelescopeFilter.apertureKey
        return k.isDown || (k.same(mc.options.keyShift) && mc.options.keyShift.isDown)
    }

    /** HUD 한 줄 (왼쪽 아래): 대물 구경 · 대물 초점거리 · 접안 초점거리 · 배율 — 예 "80mm · 초점 560mm · 접안 25mm · ×22" */
    fun opticsLine(): String {
        val d = Optics.apertureMm.roundToInt(); val f = Optics.focalMm.roundToInt()
        if (level == 0) return L.t("cheonsang.telescopeview.4", d, f)
        val ep = Optics.EYEPIECES[level - 1]
        val epText = if (ep == ep.roundToInt().toDouble()) "${ep.roundToInt()}mm" else "${ep}mm"
        return L.t("cheonsang.telescopeview.5", d, f, epText, LEVELS[level].roundToInt())
    }

    /** 처음 망원경을 켰을 때 조작법 한 번 (채팅, client.json guideShown) */
    private fun firstGuide() {
        if (ClientConfig.guideShown) return
        val mc = Minecraft.getInstance()
        fun k(m: net.minecraft.client.KeyMapping) = m.translatedKeyMessage.string
        mc.player?.sendSystemMessage(Component.literal(
            L.t("cheonsang.telescopeview.6", k(TelescopeFilter.apertureKey)) +
                L.t("cheonsang.telescopeview.7", k(TelescopeFilter.trackKey), k(TelescopeFilter.key))))
        ClientConfig.markGuideShown()
    }
}
