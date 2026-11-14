package kr.inf.cheonsang.client

import kr.inf.cheonsang.PoseServer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import java.util.UUID

/** 망원경 자세 멀티 동기화 (클라이언트 쪽): 내 확대 여부를 바뀔 때만 보내고, 다른 사람 것을 받아 둠 */
object PoseSync {
    private val others = HashSet<UUID>()
    private var sent = false

    /** 그 사람이 망원경으로 확대 중인지 (다른 사람) */
    @JvmStatic
    fun isUsing(id: UUID): Boolean = id in others

    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(PoseServer.POSE) { p, _ -> if (p.on) others += p.id else others -= p.id }
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (mc.player == null || !ClientPlayNetworking.canSend(PoseServer.SET)) return@register
            val now = TelescopeView.magnifying
            if (now != sent) {
                sent = now
                ClientPlayNetworking.send(PoseServer.Set(now))
            }
        }
        ClientPlayConnectionEvents.JOIN.register { _, _, _ -> others.clear(); sent = false }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> others.clear(); sent = false }
    }
}
