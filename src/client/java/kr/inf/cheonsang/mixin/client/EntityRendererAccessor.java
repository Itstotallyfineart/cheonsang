package kr.inf.cheonsang.mixin.client;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 렌더러의 아이템 모델 해석기 (망원경을 눈에 그릴 때) */
@Mixin(LivingEntityRenderer.class)
public interface EntityRendererAccessor {
    @Accessor("itemModelResolver")
    ItemModelResolver cheonsang$itemModelResolver();
}
