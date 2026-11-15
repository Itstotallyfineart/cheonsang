package kr.inf.cheonsang.client

import kr.inf.cheonsang.L
import kr.inf.cheonsang.client.sky.CheonsangSky
import kr.inf.cheonsang.client.sky.Packs
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.client.KeyMapping
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

/**
 * 망원경 표시 필터: 별자리 선·이름, 행성·달 이름, 조준 천체 이름, 사진(장노출 은하수·성운, 기본 끔 = 현실 모습).
 * 키(기본 N)로 전부 → 선만 → 이름만 → 끄기 순환, 설정은 SettingsScreen. client.json 의 display 에 저장.
 */
object TelescopeFilter {
    var lines = true
    var names = true
    var bodies = true
    var aim = true
    var photo = false
    /** 적도 좌표 격자·천구 적도·황도 / 방위·고도 격자·지평선 / 별자리 경계선 / 좌클릭 정보 카드 */
    var grid = false
    var altaz = false
    var bounds = false
    var card = true
    /** 별자리 그림 (기본 끔) */
    var art = false
    /** 별자리 선·이름을 천상열차분야지도로 (끄면 서양 88) */
    var korean = false
    /** 화면 낮밤(하늘·안개·땅빛)을 천상 해 고도로 (끄면 바닐라 하루, 기본 켬) */
    var visualDay = true

    // 정보 카드·글씨 (설정 화면)
    /** 0 왼쪽 위 · 1 오른쪽 위 · 2 왼쪽 아래 · 3 오른쪽 아래 */
    var cardCorner = 0
    var cardOpacity = 0.69f
    var cardAltAz = true
    var cardRaDec = true
    var cardRiseSet = true
    /** 카드·하늘 글씨 배율 */
    var uiScale = 1f

    private val ITEMS = listOf("lines", "names", "planets", "aim", "photo", "grid", "altaz", "bounds", "card", "art")
    lateinit var key: CheonsangKey
    /** 천체 추적 키 (누른 채 좌클릭 = 추적 고정, 기본 왼쪽 Alt — 조작 설정 "기타"에서 바꿈) */
    lateinit var trackKey: CheonsangKey
    /** 누른 채 휠 = 대물 구경 (기본 왼쪽 Shift) */
    lateinit var apertureKey: CheonsangKey
    private lateinit var settingsKey: CheonsangKey
    private var keysChecked = false
    val extraKeys = ArrayList<CheonsangKey>()

    /**
     * 26.3 은 키 번호가 SDL 스캔코드(N=17, 왼쪽 Alt=226, 없음=0) — "없음=-1" 이 저장되면 KeyMapping.setAll 이 범위 밖을 읽어 튕김.
     * 범위 밖이거나 예전 GLFW 기본값(78·342)이면 새 기본값으로 되돌림.
     */
    class CheonsangKey(name: String, private val def: Int, private val oldDefault: Int?) : KeyMapping(name, def, KeyMapping.Category.MISC) {
        fun fix(): Boolean {
            val v = key.value
            if (key.type != InputConstants.Type.KEYBOARD) return false
            if (v < 0 || v >= 512 || (oldDefault != null && v == oldDefault)) {
                setKey(InputConstants.Type.KEYBOARD.getOrCreate(def))
                return true
            }
            return false
        }
    }

    fun register() {
        key = CheonsangKey("key.cheonsang.filter", InputConstants.KEY_N, 78).also { KeyMappingHelper.registerKeyMapping(it) }
        trackKey = CheonsangKey("key.cheonsang.track", InputConstants.KEY_LALT, 342).also { KeyMappingHelper.registerKeyMapping(it) }
        apertureKey = CheonsangKey("key.cheonsang.aperture", InputConstants.KEY_LSHIFT, 340).also { KeyMappingHelper.registerKeyMapping(it) }
        // 천상 설정 화면 키 (기본 없음 — 조작 설정에서 지정). 없음 = InputConstants.UNKNOWN (0)
        settingsKey = CheonsangKey("key.cheonsang.settings", InputConstants.UNKNOWN.value, null).also { KeyMappingHelper.registerKeyMapping(it) }
        // 옵션 파일을 읽은 뒤 첫 틱에 키 값 검사 (월드 들어가기 전)
        ClientTickEvents.START_CLIENT_TICK.register { mc ->
            if (keysChecked) return@register
            keysChecked = true
            val changed = listOf(key, trackKey, apertureKey, settingsKey).map { it.fix() }.any { it }
            if (changed) {
                KeyMapping.resetMapping()
                try { mc.options.save() } catch (_: Exception) {}
                kr.inf.cheonsang.Cheonsang.LOG.info("Cheonsang: invalid key setting reset to default")
            }
        }
        ClientTickEvents.END_CLIENT_TICK.register { _ ->
            Safety.run(Safety.KEYS, Unit) { while (key.consumeClick()) if (TelescopeView.active) cycle() }
            Safety.run(Safety.KEYS, Unit) { while (settingsKey.consumeClick()) if (Minecraft.getInstance().gui.screen() == null) SettingsScreen.open() }
        }
    }

    private fun set(item: String, on: Boolean) {
        when (item) {
            "lines" -> lines = on; "names" -> names = on; "planets" -> bodies = on; "aim" -> aim = on; "photo" -> photo = on
            "grid" -> grid = on; "altaz" -> altaz = on; "bounds" -> bounds = on; "card" -> card = on; "art" -> art = on
        }
        ClientConfig.saveDisplay()
    }

    /** 전부 → 선만 → 이름만 → 끄기 */
    private fun cycle() {
        val label = when {
            lines && (names || bodies) -> { lines = true; names = false; bodies = false; L.t("cheonsang.telescopefilter.1") }
            lines -> { lines = false; names = true; bodies = true; L.t("cheonsang.telescopefilter.2") }
            names || bodies -> { lines = false; names = false; bodies = false; L.t("cheonsang.telescopefilter.3") }
            else -> { lines = true; names = true; bodies = true; L.t("cheonsang.telescopefilter.4") }
        }
        ClientConfig.saveDisplay()
        Minecraft.getInstance().player?.sendOverlayMessage(Component.literal(L.t("cheonsang.telescopefilter.5", label)))
    }

    private fun state() = L.t("cheonsang.telescopefilter.6", onOff(lines), onOff(names), onOff(bodies), onOff(aim), onOff(CheonsangSky.photoOn)) +
        L.t("cheonsang.telescopefilter.7", onOff(grid), onOff(altaz), onOff(bounds), onOff(card), onOff(art)) + Packs.summary()

    private fun onOff(b: Boolean) = if (b) L.t("cheonsang.telescopefilter.9") else L.t("cheonsang.telescopefilter.8")
}
