package kr.inf.cheonsang

import net.fabricmc.api.ModInitializer
import net.minecraft.resources.Identifier
import org.slf4j.LoggerFactory

/** 천상 — 실제 천구 모드 (서버·공통 쪽 시작점) */
object Cheonsang : ModInitializer {
    const val MOD_ID = "cheonsang"
    val LOG = LoggerFactory.getLogger(MOD_ID)

    fun id(path: String): Identifier = Identifier.fromNamespaceAndPath(MOD_ID, path)

    override fun onInitialize() {
        LOG.info("Cheonsang started")
        SkyServer.register()
        LogServer.register()
        PoseServer.register()
        VanillaSync.register()
        Telescope.register()
    }
}
