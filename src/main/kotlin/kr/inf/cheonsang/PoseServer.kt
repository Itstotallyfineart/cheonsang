package kr.inf.cheonsang

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.core.UUIDUtil
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import java.util.UUID

/**
 * 1.9.2 망원경 눈에 대는 자세 멀티 동기화: 확대(×1 넘음) 중인지를 서버가 모아 다른 사람에게 알림
 * → 다른 사람 화면에서도 팔을 눈에 대고 망원경이 눈에 붙음 (전에는 내 화면에서만).
 * 저장 안 함 (접속 중에만), 나가면 끔.
 */
object PoseServer {
    /** 클라이언트 → 서버: 내가 망원경으로 확대 중인지 */
    data class Set(val on: Boolean) : CustomPacketPayload {
        override fun type() = SET
    }
    /** 서버 → 클라이언트: 그 사람이 망원경으로 확대 중인지 */
    data class Pose(val id: UUID, val on: Boolean) : CustomPacketPayload {
        override fun type() = POSE
    }

    val SET = CustomPacketPayload.Type<Set>(Cheonsang.id("pose_set"))
    val POSE = CustomPacketPayload.Type<Pose>(Cheonsang.id("pose"))
    private val SET_CODEC: StreamCodec<RegistryFriendlyByteBuf, Set> = StreamCodec.composite(ByteBufCodecs.BOOL, Set::on, ::Set)
    private val POSE_CODEC: StreamCodec<RegistryFriendlyByteBuf, Pose> = StreamCodec.composite(
        UUIDUtil.STREAM_CODEC, Pose::id, ByteBufCodecs.BOOL, Pose::on, ::Pose,
    )

    private val using = HashSet<UUID>()
    private val limit = kr.inf.cheonsang.RateLimit(6.0, 2.0)

    fun register() {
        PayloadTypeRegistry.serverboundPlay().register(SET, SET_CODEC)
        PayloadTypeRegistry.clientboundPlay().register(POSE, POSE_CODEC)
        ServerPlayNetworking.registerGlobalReceiver(SET) { p, ctx ->
            val me = ctx.player()
            // 2.0.0-rc1 악용 방지: 자세 켜고 끄기 사람마다 한꺼번에 6 · 초당 2 까지 (넘으면 버림 — 끄는 것만은 늘 받음)
            if (p.on && !limit.allow(me.uuid)) return@registerGlobalReceiver
            val changed = if (p.on) using.add(me.uuid) else using.remove(me.uuid)
            if (!changed) return@registerGlobalReceiver
            val out = Pose(me.uuid, p.on)
            for (o in ctx.server().playerList.players) if (o !== me && ServerPlayNetworking.canSend(o, POSE)) ServerPlayNetworking.send(o, out)
        }
        // 새로 들어온 사람에게 지금 확대 중인 사람들
        ServerPlayConnectionEvents.JOIN.register { h, sender, _ ->
            if (!ServerPlayNetworking.canSend(h.player, POSE)) return@register
            for (id in using) if (id != h.player.uuid) sender.sendPacket(Pose(id, true))
        }
        ServerPlayConnectionEvents.DISCONNECT.register { h, server ->
            limit.forget(h.player.uuid)
            if (!using.remove(h.player.uuid)) return@register
            val out = Pose(h.player.uuid, false)
            for (o in server.playerList.players) if (o !== h.player && ServerPlayNetworking.canSend(o, POSE)) ServerPlayNetworking.send(o, out)
        }
    }
}
