package kr.inf.cheonsang.client.sky

import kr.inf.cheonsang.L
import com.google.gson.JsonParser
import kr.inf.cheonsang.Cheonsang
import net.minecraft.client.Minecraft

/**
 * 확장팩(그림만 든 모드 jar, 같은 이름공간 cheonsang) 확인 — photo(장노출·성운), hd(은하수 8K, 기본 포함),
 * ultra(장노출 전부 + 은하수 16K·32K + 달 8K), stars(Gaia 15등 별), extra(stars + ultra 비사진부 대체).
 * 팩의 `pack/<이름>.json` format 이 [FORMAT] 과 다르면 "업데이트 필요"로 무시. 5초마다 다시 확인 (F3+T 뒤에도 맞게)
 */
object Packs {
    const val FORMAT = 1

    enum class State(private val id: String) {
        OK("ok"), MISSING("missing"), OUTDATED("outdated");
        /** 화면 이름 */
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
    /** 맨눈 은하수 16K·32K 조각·달 8K — 초고화질팩 또는 extra */
    val hiOk get() = refresh().let { ultra == State.OK || extra == State.OK }

    private fun refresh() {
        val now = System.nanoTime()
        if (checkedAt != 0L && now - checkedAt < 5_000_000_000L) return
        checkedAt = now
        photo = check("photo")
        hd = check("hd")
        ultra = check("ultra", ULTRA_FORMAT)
        stars = check("stars")
        extra = check("extra")
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

    /** 고화질팩(은하수 8K)은 기본에 들어감 — 있어도 안 씀 */
    fun summary(): String = refresh().let {
        (if (extra != State.MISSING) L.t("cheonsang.packs.extra", extra.label) + (if (ultra != State.MISSING) L.t("cheonsang.packs.photo", ultra.label) else "") +
            (if (stars != State.MISSING) L.t("cheonsang.packs.stars_old") else "")
        else L.t("cheonsang.packs.1", ultra.label, stars.label)) +
            (if (photo != State.MISSING) if (ultra == State.OK) L.t("cheonsang.packs.3") else L.t("cheonsang.packs.2", photo.label) else "") +
            if (hd != State.MISSING) L.t("cheonsang.packs.4") else ""
    }
}
