package kr.inf.cheonsang

import java.util.UUID

/**
 * 2.0.0-rc1 악용 방지 (11-14 17:42 점검 → 19:28 지율님 "악용방지 넣어서"): 클라 → 서버 패킷을 사람마다 초당 횟수로 제한.
 * 토큰 통 — 한꺼번에 [burst] 개까지, 그 뒤로 초당 [perSec] 개씩 다시 참. 넘는 패킷은 버림 (정상 플레이는 닿지 않는 값)
 */
class RateLimit(private val burst: Double, private val perSec: Double) {
    private class Bucket(var tokens: Double, var at: Long)
    private val buckets = HashMap<UUID, Bucket>()

    fun allow(id: UUID): Boolean {
        val now = System.nanoTime()
        val b = buckets.getOrPut(id) { Bucket(burst, now) }
        b.tokens = minOf(burst, b.tokens + (now - b.at) / 1e9 * perSec)
        b.at = now
        if (b.tokens < 1.0) return false
        b.tokens -= 1.0
        return true
    }

    fun forget(id: UUID) { buckets.remove(id) }
}
