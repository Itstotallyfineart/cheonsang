package kr.inf.cheonsang.mixin.client;

import kr.inf.cheonsang.client.sky.CheonsangSky;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 일식 (0.1.11): 땅에 비치는 하늘빛도 어둡게 (개기 때 해 질 녘 정도) */
@Mixin(LightmapRenderStateExtractor.class)
public abstract class LightmapMixin {
    @Unique private float cheonsang$lastDim;

    @Inject(method = "extract", at = @At("TAIL"))
    private void cheonsang$eclipse(LightmapRenderState state, float partialTick, CallbackInfo ci) {
        float d = CheonsangSky.isActive() ? CheonsangSky.getEclipseDim() : 0f;
        if (d > 0.001f) state.skyFactor *= 1f - 0.8f * d;
        if (Math.abs(d - cheonsang$lastDim) > 0.002f || (d == 0f && cheonsang$lastDim != 0f)) {
            state.needsUpdate = true;
            cheonsang$lastDim = d;
        }
    }
}
