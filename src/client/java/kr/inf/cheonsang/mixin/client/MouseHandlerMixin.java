package kr.inf.cheonsang.mixin.client;

import kr.inf.cheonsang.client.TelescopeView;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 망원경: 휠 = 배율, 시점 이동은 배율만큼 느리게, 적도의 추적 (마우스 처리 직전) */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;

    /** 망원경 켜져 있으면 휠은 배율 (단축바 안 넘김) */
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void cheonsang$scroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
        if (TelescopeView.scroll(yOffset)) ci.cancel();
    }

    /** 1.9.0-dev5: 적도의·추적 고정은 매 프레임 (화면·채팅이 열려 turnPlayer 가 안 불려도 계속 따라감) */
    @Inject(method = "handleAccumulatedMovement", at = @At("HEAD"))
    private void cheonsang$track(CallbackInfo ci) {
        TelescopeView.track();
    }

    @Inject(method = "turnPlayer", at = @At("HEAD"))
    private void cheonsang$slow(double movementTime, CallbackInfo ci) {
        // 1.1.3: 추적 고정 중엔 마우스 움직임 버림 (크게 움직이면 해제)
        if (TelescopeView.consumeMouse(accumulatedDX, accumulatedDY)) { accumulatedDX = 0; accumulatedDY = 0; }
        if (TelescopeView.getActive()) {
            double f = 1.0 / TelescopeView.getZoom();
            accumulatedDX *= f;
            accumulatedDY *= f;
        }
    }
}
