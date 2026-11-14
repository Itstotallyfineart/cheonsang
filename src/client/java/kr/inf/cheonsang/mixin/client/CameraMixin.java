package kr.inf.cheonsang.mixin.client;

import kr.inf.cheonsang.client.TelescopeView;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 망원경: 시야각 = 원래 시야 / 배율 */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void cheonsang$zoom(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (TelescopeView.getActive()) cir.setReturnValue((float) (cir.getReturnValueF() / TelescopeView.getZoom()));
    }
}
