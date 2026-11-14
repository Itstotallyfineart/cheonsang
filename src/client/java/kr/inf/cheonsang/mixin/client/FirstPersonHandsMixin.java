package kr.inf.cheonsang.mixin.client;

import kr.inf.cheonsang.client.TelescopeView;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 배율 ×1 넘으면 1인칭 손·망원경 안 그림 (바닐라 망원경 들여다볼 때와 같음) */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonHandsMixin {
    @Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void cheonsang$hideWhileMagnifying(CallbackInfo ci) {
        if (TelescopeView.getMagnifying()) ci.cancel();
    }
}
