package kr.inf.cheonsang

import com.mojang.serialization.Codec
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.datafix.DataFixTypes
import net.minecraft.world.level.saveddata.SavedData
import net.minecraft.world.level.saveddata.SavedDataType
import java.util.UUID

/**
 * 1.4.0 관측 도감 (OBS-001) — 서버에 사람마다 저장. 최초 발견만 (이미 있으면 그대로).
 * 보이는지 판정은 클라이언트 ([kr.inf.cheonsang.client.Visibility]) — 서버는 받은 항목에 그 순간 하늘 시각(율리우스일)을 붙여 저장.
 * 발전과제는 따로 저장하지 않고 이 기록에서 계산 (11-11 지율님 "도감을 발전과제에 결합")
 *
 * 저장: 플레이어 UUID → (항목 키 → "율리우스일|배율|이름")  · 배율 1 = 맨눈
 */
object LogServer {
    /** 클라이언트 → 서버: 도감 등록 (키, 이름, 배율) */
    data class Add(val key: String, val name: String, val zoom: Double) : CustomPacketPayload {
        override fun type() = ADD
    }
    /** 서버 → 클라이언트: 내 도감 전체 ("키\t율리우스일\t배율\t이름" 줄마다) */
    data class All(val lines: List<String>) : CustomPacketPayload {
        override fun type() = ALL
    }

    val ADD = CustomPacketPayload.Type<Add>(Cheonsang.id("log_add"))
    val ALL = CustomPacketPayload.Type<All>(Cheonsang.id("log_all"))
    private val ADD_CODEC: StreamCodec<RegistryFriendlyByteBuf, Add> = StreamCodec.composite(
        ByteBufCodecs.stringUtf8(96), Add::key, ByteBufCodecs.stringUtf8(96), Add::name, ByteBufCodecs.DOUBLE, Add::zoom, ::Add,
    )
    private val ALL_CODEC: StreamCodec<RegistryFriendlyByteBuf, All> = StreamCodec.composite(
        ByteBufCodecs.stringUtf8(256).apply(ByteBufCodecs.list(20000)), All::lines, ::All,
    )

    class Data(val logs: MutableMap<String, MutableMap<String, String>> = HashMap()) : SavedData()
    private val DATA = SavedDataType(
        Cheonsang.id("log"), ::Data,
        Codec.unboundedMap(Codec.STRING, Codec.unboundedMap(Codec.STRING, Codec.STRING))
            .xmap({ m -> Data(HashMap(m.mapValues { HashMap(it.value) })) }, { it.logs }),
        DataFixTypes.SAVED_DATA_COMMAND_STORAGE,
    )
    private fun data(server: MinecraftServer) = server.dataStorage.computeIfAbsent(DATA)

    /** 한 사람 최대 항목 수 (별을 다 눌러도 넘치지 않게) */
    private const val MAX_ENTRIES = 15000
    private val KEY_OK = Regex("^[A-Za-z]+:[^\\t\\n|]{1,80}$|^(sun|moon)$")

    private val pending = HashSet<UUID>()

    fun register() {
        PayloadTypeRegistry.serverboundPlay().register(ADD, ADD_CODEC)
        PayloadTypeRegistry.clientboundPlay().register(ALL, ALL_CODEC)
        // 2.0.0-rc1 악용 방지: 도감·발전과제 패킷 사람마다 한꺼번에 300 · 초당 20 까지 (처음 접속 때 몰아 보내는 양보다 넉넉)
        ServerPlayNetworking.registerGlobalReceiver(ADD) { p, ctx -> if (limit.allow(ctx.player().uuid)) add(ctx.player(), p) }
        ServerPlayConnectionEvents.DISCONNECT.register { h, _ -> limit.forget(h.player.uuid); resend.remove(h.player.uuid) }
        ServerPlayConnectionEvents.JOIN.register { h, _, _ -> pending += h.player.uuid }
        ServerTickEvents.END_SERVER_TICK.register { server ->
            if (pending.isNotEmpty()) pending.removeAll { id -> server.playerList.getPlayer(id)?.also { send(it) } != null }
            // 2.0.0-rc1: 등록마다 전체 목록을 바로 보내지 않고 1초에 한 번 몰아서 (등록을 빠르게 반복해도 서버가 목록을 계속 다시 보내지 않게)
            if (resend.isNotEmpty() && ++tickN % 20L == 0L) resend.removeAll { id -> server.playerList.getPlayer(id)?.also { send(it) }; true }
        }
    }

    private val limit = RateLimit(300.0, 20.0)
    private val resend = HashSet<java.util.UUID>()
    private var tickN = 0L

    private fun add(player: ServerPlayer, p: Add) {
        if (p.key.startsWith("adv:")) return  // 2.0.2-dev1: 발전과제 삭제 — 옛 클라이언트가 보내면 무시
        if (!KEY_OK.matches(p.key)) return
        val d = data(player.level().server)
        val mine = d.logs.getOrPut(player.uuid.toString()) { HashMap() }
        if (p.key in mine || mine.size >= MAX_ENTRIES) return
        val level = player.level() as ServerLevel
        val jd = SkyServer.get(level).clock.jd(level.defaultClockTime.toDouble())
        val name = p.name.replace('\t', ' ').replace('\n', ' ').replace('|', '/').take(80)
        mine[p.key] = "%.6f|%.1f|%s".format(java.util.Locale.ROOT, jd, p.zoom.coerceIn(1.0, 1000.0), name)
        d.setDirty()
        resend += player.uuid
    }

    private fun send(player: ServerPlayer) {
        val mine = data(player.level().server).logs[player.uuid.toString()] ?: emptyMap<String, String>()
        val lines = mine.map { (k, v) ->
            val parts = v.split("|", limit = 3)
            "$k\t${parts.getOrElse(0) { "0" }}\t${parts.getOrElse(1) { "1" }}\t${parts.getOrElse(2) { k }}"
        }
        ServerPlayNetworking.send(player, All(lines))
    }
}
