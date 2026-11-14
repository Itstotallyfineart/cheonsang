package kr.inf.cheonsang.mixin.client;

import kr.inf.cheonsang.client.sky.VisualDay;
import kr.inf.cheonsang.client.sky.VisualDaySampler;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.clock.ClockManager;
import net.minecraft.world.timeline.AttributeTrackSampler;
import net.minecraft.world.timeline.Timeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/** 하루(24000틱) 타임라인의 하늘색·안개·구름·땅 빛 트랙만 표시 → 화면 낮밤을 천상 해 고도로. 몹·잠자기(gameplay)는 그대로 */
@Mixin(Timeline.class)
public abstract class TimelineMixin {
    @Shadow public abstract Optional<Integer> periodTicks();

    @Inject(method = "createTrackSampler", at = @At("RETURN"))
    private void cheonsang$markVisual(EnvironmentAttribute<?> attribute, ClockManager clocks, CallbackInfoReturnable<AttributeTrackSampler<?, ?>> cir) {
        AttributeTrackSampler<?, ?> s = cir.getReturnValue();
        if (s != null && VisualDay.isVisual(attribute) && periodTicks().orElse(0) == 24000)
            ((VisualDaySampler) s).cheonsang$setVisual(true);
    }
}
