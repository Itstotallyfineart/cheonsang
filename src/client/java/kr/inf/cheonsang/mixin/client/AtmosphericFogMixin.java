package kr.inf.cheonsang.mixin.client;

import kr.inf.cheonsang.client.sky.CheonsangSky;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 노을: 바닐라는 동·서쪽을 볼 때만 안개에 노을색을 섞음 → 고개 돌리면 사라짐. 천상 박명 빛만 남김 */
@Mixin(AtmosphericFogEnvironment.class)
public abstract class AtmosphericFogMixin {
    @Redirect(method = "getBaseColor", at = @At(value = "INVOKE", target = "Lorg/joml/Vector3fc;dot(FFF)F"))
    private float cheonsang$noSunriseFog(Vector3fc forward, float x, float y, float z) {
        return CheonsangSky.isActive() ? 0f : forward.dot(x, y, z);
    }

    /** 밤: 안개색을 하늘색에 맞춤 / 일식: 안개색도 하늘처럼 어둡게 */
    @Inject(method = "getBaseColor", at = @At("RETURN"), cancellable = true)
    private void cheonsang$eclipseFog(CallbackInfoReturnable<Vector3fc> cir) {
        if (!CheonsangSky.isActive()) return;
        Vector3fc c = CheonsangSky.nightFog(cir.getReturnValue());
        float d = CheonsangSky.getEclipseDim();
        if (d > 0.001f) c = c.mul(1f - 0.92f * d, new org.joml.Vector3f());
        cir.setReturnValue(c);
    }
}
