package kr.inf.cheonsang.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.commands.RenderPass;
import kr.inf.cheonsang.client.sky.CheonsangSky;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.world.level.MoonPhase;
import org.joml.Vector4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 바닐라 하늘에 천상 하늘을 끼워 넣는다.
 * 그리는 순서: 별 → 해 빛무리 → 해 → 달 가림막 → 달 (달이 해·별을 가려 일식이 저절로 생김)
 */
@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {
    /** 프레임마다 하늘 상태 계산 (시계·위도 → 회전 행렬, 해·달 위치) */
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void cheonsang$extract(ClientLevel level, float partialTick, Camera camera, SkyRenderState state, CallbackInfo ci) {
        CheonsangSky.extract(level, partialTick, state);
    }

    /** 바닐라 해(네모) 끄기 — 천상 해는 아래 TAIL 에서 */
    @Inject(method = "renderSun", at = @At("HEAD"), cancellable = true)
    private void cheonsang$sun(RenderPass pass, float alpha, PoseStack poseStack, CallbackInfo ci) {
        if (CheonsangSky.isActive()) ci.cancel();
    }

    /** 바닐라 달 끄기 */
    @Inject(method = "renderMoon", at = @At("HEAD"), cancellable = true)
    private void cheonsang$moon(RenderPass pass, MoonPhase phase, float alpha, PoseStack poseStack, CallbackInfo ci) {
        if (CheonsangSky.isActive()) ci.cancel();
    }

    /** 바닐라 해돋이·노을 부채꼴 끄기 (방향이 정동·정서 고정 + 가장자리 끊김) — 천상 박명 빛은 renderSunMoon 에서 */
    @Inject(method = "renderSunriseAndSunset", at = @At("HEAD"), cancellable = true)
    private void cheonsang$sunrise(RenderPass pass, PoseStack poseStack, float sunAngle, Vector4fc color, CallbackInfo ci) {
        if (CheonsangSky.replacesSunrise()) ci.cancel();
    }

    /** 바닐라 무작위 별 대신 실제 별 */
    @Inject(method = "renderStars", at = @At("HEAD"), cancellable = true)
    private void cheonsang$stars(RenderPass pass, float starBrightness, PoseStack poseStack, CallbackInfo ci) {
        if (CheonsangSky.renderStars(pass)) ci.cancel();
    }

    /** 별 다음에 해·달 (달이 맨 앞) */
    @Inject(method = "renderSunMoonAndStars", at = @At("TAIL"))
    private void cheonsang$sunMoon(RenderPass pass, PoseStack poseStack, float sunAngle, float moonAngle, float starAngle,
                                   MoonPhase phase, float rainBrightness, float starBrightness, CallbackInfo ci) {
        CheonsangSky.renderSunMoon(pass);
    }
}
