package kr.inf.cheonsang.client

import kr.inf.cheonsang.L
import kr.inf.cheonsang.Cheonsang
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

/**
 * 튕김 안전망: 천상 코드에서 오류가 나도 게임은 계속. 오류 난 부분(하늘·망원경·찾기·키)만 이번 접속 동안 끄고 바닐라로 두며, 로그에는 전체 오류를 한 번 남김.
 * 서버를 나갔다 들어오면 다시 켜 봄.
 */
object Safety {
    const val SKY = "하늘"
    const val TELESCOPE = "망원경"
    const val FIND = "찾기"
    const val KEYS = "키"
    /** 찾기 하나가 걸려도 나머지는 꺼지지 않게 따로 둠 */
    const val TONIGHT = "오늘 밤"
    const val BOOK = "수첩"
    const val LOG = "도감"

    @PublishedApi internal val off = HashSet<String>()

    @JvmStatic
    fun ok(part: String) = part !in off

    inline fun <T> run(part: String, fallback: T, block: () -> T): T {
        if (part in off) return fallback
        return try { block() } catch (e: Throwable) {
            if (e is VirtualMachineError) throw e
            trip(part, e)
            fallback
        }
    }

    @PublishedApi
    internal fun trip(part: String, e: Throwable) {
        if (!off.add(part)) return
        Cheonsang.LOG.error("Cheonsang: {} error — {} disabled for this session (game continues)", part, part, e)
        try {
            Minecraft.getInstance().player?.sendOverlayMessage(Component.literal(L.t("cheonsang.safety.1", partLabel(part))))
        } catch (_: Throwable) {}
    }

    fun reset() = off.clear()

    /** 기능 이름 (상수는 한국어 식별자) → 화면 이름 */
    private val PART_KEY = mapOf(SKY to "sky", TELESCOPE to "telescope", FIND to "find", KEYS to "keys", TONIGHT to "tonight", BOOK to "book", LOG to "log")
    @PublishedApi internal fun partLabel(part: String) = PART_KEY[part]?.let { L.t("cheonsang.part.$it") } ?: part
}
