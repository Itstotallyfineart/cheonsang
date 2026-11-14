package kr.inf.cheonsang.mixin.client;

import kr.inf.cheonsang.client.TelescopeHud;
import kr.inf.cheonsang.client.TelescopeView;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 망원경 켠 동안 좌클릭 = 가운데 천체 이름 (공격·블록 부수기 안 함) */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void cheonsang$pick(CallbackInfoReturnable<Boolean> cir) {
        if (TelescopeView.getActive()) {
            TelescopeHud.INSTANCE.pick();
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void cheonsang$hold(boolean leftClick, CallbackInfo ci) {
        if (TelescopeView.getActive()) ci.cancel();
    }
}
