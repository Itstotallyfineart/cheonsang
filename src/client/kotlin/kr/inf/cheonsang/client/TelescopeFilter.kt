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
 * 망원경 표시 필터 (0.1.12): 별자리 선 · 별자리 이름 · 행성·달 이름 · 조준 천체 이름 + 사진(0.1.13: 장노출 은하수·성운 사진, 기본 끔 = 현실 모습).
 * - 키 (기본 N, 조작 설정에서 바꿈): 망원경 켠 동안 누를 때마다 전부 → 선만 → 이름만 → 끄기 (조준 이름은 그대로)
 * - `/cfilter` 지금 상태, `/cfilter <lines|names|planets|aim|photo|grid|altaz|bounds|card> <on|off>` 하나씩 (0.1.13 명령어 영문, 1.1.0 격자·경계·카드)
 * - client.json 의 display 에 저장 (게임 껐다 켜도 유지)
 */
object TelescopeFilter {
    var lines = true
    var names = true
    var bodies = true
    var aim = true
    var photo = false
    /** 1.1.0: 적도 좌표 격자·천구 적도·황도 / 방위·고도 격자·지평선 / 별자리 경계선 / 좌클릭 정보 카드 */
    var grid = false
    var altaz = false
    var bounds = false
    var card = true
    /** 1.1.2: 별자리 그림 (기본 끔) */
    var art = false
    /** 1.9.0-dev4: 별자리 선·이름을 천상열차분야지도로 (끄면 서양 88) */
    var korean = false
    /** 1.10.1: 화면 낮밤(하늘·안개·땅빛)을 천상 해 고도로 (끄면 바닐라 하루, 기본 켬) */
    var visualDay = true

    // 1.2.1 정보 카드·글씨 (설정 화면)
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
    /** 1.1.4: 천체 추적 키 (누른 채 좌클릭 = 추적 고정, 기본 왼쪽 Alt — 조작 설정 "기타"에서 바꿈) */
    lateinit var trackKey: CheonsangKey
    /** 1.7.0: 누른 채 휠 = 대물 구경 (기본 왼쪽 Shift) */
    lateinit var apertureKey: CheonsangKey
    private lateinit var settingsKey: CheonsangKey
    private var keysChecked = false
    /** 1.4.0: 다른 곳에서 등록한 천상 키 (관측 수첩) — 첫 틱 검사에 같이 넣음 */
    val extraKeys = ArrayList<CheonsangKey>()

    /**
     * 1.2.5.1: 26.3 키 번호는 SDL 스캔코드 (N = 17, 왼쪽 Alt = 226, 없음 = 0).
     * 1.2.1 의 "없음 = -1" 이 저장되면 KeyMapping.setAll 이 키 상태 배열 밖을 읽어 튕김 → 범위 밖 값은 기본값으로 되돌림.
     * 옛 기본값(GLFW 번호 78·342 — 26.3 에서는 다른 키)도 새 기본값으로 바꿈
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
        // 1.2.1: 천상 설정 화면 키 (기본 없음 — 조작 설정에서 지정). 1.2.5.1: 없음 = InputConstants.UNKNOWN (0)
        settingsKey = CheonsangKey("key.cheonsang.settings", InputConstants.UNKNOWN.value, null).also { KeyMappingHelper.registerKeyMapping(it) }
        // 1.2.5.1: 옵션 파일을 읽은 뒤 첫 틱에 키 값 검사 (월드 들어가기 전)
        ClientTickEvents.START_CLIENT_TICK.register { mc ->
            if (keysChecked) return@register
            keysChecked = true
            val changed = (listOf(key, trackKey, apertureKey, settingsKey) + extraKeys).map { it.fix() }.any { it }
            if (changed) {
                KeyMapping.resetMapping()
                try { mc.options.save() } catch (_: Exception) {}
                kr.inf.cheonsang.Cheonsang.LOG.info("Cheonsang: invalid key setting reset to default")
            }
        }
        ClientTickEvents.END_CLIENT_TICK.register { _ ->
            Safety.run(Safety.KEYS, Unit) { while (key.consumeClick()) if (TelescopeView.active) cycle() }
            Safety.run(Safety.KEYS, Unit) { while (settingsKey.consumeClick()) if (Minecraft.getInstance().gui.screen() == null) CheonsangMenu.open() }
        }
        // 1.2.1: /cfilter 명령 없앰 → 설정 화면 (SettingsScreen)
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
