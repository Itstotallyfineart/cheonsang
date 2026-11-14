package kr.inf.cheonsang

import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.level.Level

/**
 * 천체망원경 (망원경 + 석영, 조합법은 data/cheonsang/recipe/telescope.json).
 * 우클릭으로 켜고 끄기 — 보는 화면은 클라이언트에서 ([clientToggle]).
 */
object Telescope {
    /** 클라이언트가 채움: 우클릭했을 때 망원경 보기 켜기/끄기 */
    var clientToggle: (() -> Unit)? = null

    class TelescopeItem(props: Properties) : Item(props) {
        override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResult {
            if (level.isClientSide) clientToggle?.invoke()
            return InteractionResult.SUCCESS
        }
    }

    private val KEY: ResourceKey<Item> = ResourceKey.create(Registries.ITEM, Cheonsang.id("telescope"))

    val ITEM: Item = Registry.register(BuiltInRegistries.ITEM, KEY, TelescopeItem(Item.Properties().setId(KEY).stacksTo(1)))

    /** 크리에이티브 "도구 및 유틸리티" 탭 — 망원경(spyglass) 바로 뒤 */
    private val TOOLS_TAB: ResourceKey<net.minecraft.world.item.CreativeModeTab> =
        ResourceKey.create(Registries.CREATIVE_MODE_TAB, net.minecraft.resources.Identifier.withDefaultNamespace("tools_and_utilities"))

    fun register() {
        ITEM // 클래스 로딩 시 등록
        net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents.modifyOutputEvent(TOOLS_TAB).register { out ->
            out.insertAfter(net.minecraft.world.item.Items.SPYGLASS, ITEM)
        }
    }
}
