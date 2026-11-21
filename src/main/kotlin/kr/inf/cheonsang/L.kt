package kr.inf.cheonsang

import com.google.gson.JsonParser
import net.minecraft.locale.Language

/**
 * 1.9.0-dev2 영문화: 천상 글자 (assets/cheonsang/lang/ko_kr.json · en_us.json).
 * - 내 화면: 마크 언어가 한국어면 ko_kr, 그 밖은 en_us (언어 파일의 "cheonsang.lang" 으로 판단)
 * - 서버 명령 답: 그 사람 클라이언트 언어 ([tl]) — 전용 서버에는 모드 언어가 안 올라가서 직접 읽음
 * - {0} {1} … 자리에 값을 넣고, % 서식은 그대로 둠 (뒤에서 .format)
 */
object L {
    private fun load(name: String): Map<String, String> = try {
        val txt = L::class.java.getResourceAsStream("/assets/cheonsang/lang/$name.json")!!.readBytes().toString(Charsets.UTF_8)
        JsonParser.parseString(txt).asJsonObject.entrySet().associate { it.key to it.value.asString }
    } catch (_: Exception) { emptyMap() }

    private val koTable by lazy { load("ko_kr") }
    private val enTable by lazy { load("en_us") }

    /** 서버가 한 사람에게 답을 만드는 동안 그 사람 언어 ([with]) */
    @PublishedApi internal val override = ThreadLocal<Boolean?>()

    /** 내 화면이 한국어인지 (전용 서버에서는 false, [with] 안에서는 그 사람 언어) */
    val ko: Boolean get() = override.get() ?: (Language.getInstance().getOrDefault("cheonsang.lang", "en") == "ko")

    /** 언어 코드(ko_kr 등)로 block 안의 [t]·[name] 을 — 서버 명령 답용 */
    inline fun <T> with(lang: String?, block: () -> T): T {
        val prev = override.get()
        override.set(lang?.startsWith("ko") == true)
        try { return block() } finally { override.set(prev) }
    }

    private fun fill(s: String, a: Array<out Any?>): String {
        var out = s
        for (i in a.indices) out = out.replace("{$i}", a[i].toString())
        return out
    }

    private fun raw(korean: Boolean, key: String) = (if (korean) koTable[key] else enTable[key]) ?: enTable[key] ?: koTable[key] ?: key

    /** 내 화면 언어로 */
    fun t(key: String, vararg a: Any?): String = fill(raw(ko, key), a)

    /** 언어 코드(ko_kr 등)로 — 서버가 사람마다 답할 때 */
    fun tl(lang: String?, key: String, vararg a: Any?): String = fill(raw(lang?.startsWith("ko") == true, key), a)

    /** 이름 고르기: 한국어 화면이면 한국어 이름, 아니면 영어 이름 (빈 것은 다른 쪽으로) */
    fun name(koName: String, enName: String): String = if (ko) koName.ifEmpty { enName } else enName.ifEmpty { koName }

    /** 1.9.0: 위성 (한국어 이름이 식별자) → 화면 이름 */
    private val MOON_EN = mapOf("타이탄" to "Titan", "레아" to "Rhea", "이아페투스" to "Iapetus", "테티스" to "Tethys", "디오네" to "Dione",
        "엔셀라두스" to "Enceladus", "미마스" to "Mimas", "이오" to "Io", "유로파" to "Europa", "가니메데" to "Ganymede", "칼리스토" to "Callisto")
    fun moon(koName: String): String = if (ko) koName else MOON_EN[koName] ?: koName
}
