package kr.inf.cheonsang.client.sky

import kr.inf.cheonsang.L
import com.google.gson.JsonParser
import kr.inf.cheonsang.Cheonsang
import net.minecraft.client.Minecraft

/**
 * 1.2.0: 확장팩 (그림만 든 모드 jar, 같은 이름공간 cheonsang) 확인.
 * - 사진팩: 장노출 은하수 4K·8K, 성운 사진 (`/cfilter photo`)
 * - 고화질팩: 현실 은하수 8K (망원경 확대) — 1.6.0 부터 기본에 들어감
 * - 1.6.1 초고화질팩 (cheonsang-ultra, format 2): 사진팩 자리 — 장노출 그림 전부 + 은하수 16K·32K 조각 + 달 8K
 * 팩 안의 `assets/cheonsang/pack/<이름>.json` 의 format 이 [FORMAT] 과 같아야 씀. 다르면 무시하고 "업데이트 필요".
 * - 1.8.0 Gaia 별 팩 (cheonsang-stars, format 1): 망원경 15등까지 실제 별 (DeepStars 가 직접 읽음, 여기선 표시만)
 * - 1.10.2 cheonsang-extra (format 1): Gaia 별 팩 전부 + 초고화질팩의 맨눈 은하수 16K·32K 조각·달 8K (사진 뺌). stars·ultra(사진 아닌 부분) 대신
 * 5초마다 다시 확인 (자원 다시 불러오기 F3+T 뒤에도 맞게)
 */
object Packs {
    const val FORMAT = 1

    enum class State(private val id: String) {
        OK("ok"), MISSING("missing"), OUTDATED("outdated");
        /** 1.9.0-dev2: 화면 이름 */
        val label: String get() = L.t("cheonsang.pack.$id")
    }

    var photo = State.MISSING
        private set
    var hd = State.MISSING
        private set
    var ultra = State.MISSING
        private set
    const val ULTRA_FORMAT = 2
    var stars = State.MISSING
        private set
    var extra = State.MISSING
        private set
    private var warned = false
    private var checkedAt = 0L

    /** 장노출 그림 — 사진팩 또는 초고화질팩 */
    val photoOk get() = refresh().let { photo == State.OK || ultra == State.OK }
    val ultraOk get() = refresh().let { ultra == State.OK }
    val hdOk get() = refresh().let { hd == State.OK }
    /** 1.10.2: 맨눈 은하수 16K·32K 조각·달 8K — 초고화질팩 또는 extra */
    val hiOk get() = refresh().let { ultra == State.OK || extra == State.OK }
    /** 2.0.2-dev4: extra 1.0.2↑ 의 달 8K 는 고도 자료를 담음 (extra.json moon_relief). 옛 초고화질팩이 같이 있으면 그쪽 8K 가 이길 수 있어 끔 */
    var moonRelief = false
        private set

    private fun refresh() {
        val now = System.nanoTime()
        if (checkedAt != 0L && now - checkedAt < 5_000_000_000L) return
        checkedAt = now
        photo = check("photo")
        hd = check("hd")
        ultra = check("ultra", ULTRA_FORMAT)
        stars = check("stars")
        extra = check("extra")
        moonRelief = extra == State.OK && !net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("cheonsang_ultra") && try {
            Minecraft.getInstance().resourceManager.getResource(Cheonsang.id("pack/extra.json")).orElse(null)
                ?.openAsReader()?.use { JsonParser.parseReader(it).asJsonObject.get("moon_relief")?.asInt == 1 } ?: false
        } catch (_: Exception) { false }
        if (!warned && extra == State.OK && (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("cheonsang_stars") || net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("cheonsang_ultra"))) {
            warned = true
            Cheonsang.LOG.info("Cheonsang: cheonsang-extra replaces cheonsang-stars and the non-photo part of cheonsang-ultra — the old pack can be removed (same files, extra is used)")
        }
    }

    private fun check(name: String, want: Int = FORMAT): State {
        val rm = Minecraft.getInstance().resourceManager
        val res = rm.getResource(Cheonsang.id("pack/$name.json")).orElse(null) ?: return State.MISSING
        return try {
            val format = res.openAsReader().use { JsonParser.parseReader(it).asJsonObject.get("format").asInt }
            if (format == want) State.OK else State.OUTDATED
        } catch (_: Exception) {
            State.OUTDATED
        }
    }

    /** 1.6.0: 고화질팩(은하수 8K)은 기본에 들어감 — 있어도 안 씀 */
    fun summary(): String = refresh().let {
        (if (extra != State.MISSING) L.t("cheonsang.packs.extra", extra.label) + (if (ultra != State.MISSING) L.t("cheonsang.packs.photo", ultra.label) else "") +
            (if (stars != State.MISSING) L.t("cheonsang.packs.stars_old") else "")
        else L.t("cheonsang.packs.1", ultra.label, stars.label)) +
            (if (photo != State.MISSING) if (ultra == State.OK) L.t("cheonsang.packs.3") else L.t("cheonsang.packs.2", photo.label) else "") +
            if (hd != State.MISSING) L.t("cheonsang.packs.4") else ""
    }
}
