package kr.inf.cheonsang.mixin.client;

import kr.inf.cheonsang.client.sky.VisualDay;
import kr.inf.cheonsang.client.sky.VisualDaySampler;
import net.minecraft.world.clock.ClockInstance;
import net.minecraft.world.timeline.AttributeTrackSampler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** 화면용 트랙은 바닐라 시계 대신 천상 해 고도에 맞춘 가상 시각으로 (클라이언트 렌더 스레드만) */
@Mixin(AttributeTrackSampler.class)
public abstract class AttributeTrackSamplerMixin implements VisualDaySampler {
    @Unique private boolean cheonsang$visual;

    @Override
    public void cheonsang$setVisual(boolean visual) { cheonsang$visual = visual; }

    @Redirect(method = "applyTimeBased", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/clock/ClockInstance;totalTicks()J"))
    private long cheonsang$visualTicks(ClockInstance clock) {
        long t = clock.totalTicks();
        return cheonsang$visual ? VisualDay.ticks(t) : t;
    }
}
