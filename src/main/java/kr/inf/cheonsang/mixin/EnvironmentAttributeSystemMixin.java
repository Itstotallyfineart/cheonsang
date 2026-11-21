package kr.inf.cheonsang.mixin;

import kr.inf.cheonsang.VanillaSync;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.attribute.SpatialAttributeInterpolator;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.3.1 바닐라 맞추기: 서버 월드의 달 위상·지표 슬라임 확률·(설정 시) 하늘 빛 밝기를 천상 하늘 값으로.
 * VanillaSync 에 등록된 (서버 오버월드의) 속성 시스템만 바꿈 — 나머지는 바닐라 그대로
 */
@Mixin(EnvironmentAttributeSystem.class)
public abstract class EnvironmentAttributeSystemMixin {
    @SuppressWarnings("unchecked")
    @Inject(method = "getDimensionValue", at = @At("HEAD"), cancellable = true)
    private <V> void cheonsang$dimension(EnvironmentAttribute<V> attribute, CallbackInfoReturnable<V> cir) {
        Object o = VanillaSync.override(this, attribute);
        if (o != null) cir.setReturnValue((V) o);
    }

    @SuppressWarnings("unchecked")
    @Inject(method = "getValue(Lnet/minecraft/world/attribute/EnvironmentAttribute;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/attribute/SpatialAttributeInterpolator;)Ljava/lang/Object;", at = @At("HEAD"), cancellable = true)
    private <V> void cheonsang$value(EnvironmentAttribute<V> attribute, Vec3 pos, SpatialAttributeInterpolator interpolator, CallbackInfoReturnable<V> cir) {
        Object o = VanillaSync.override(this, attribute);
        if (o != null) cir.setReturnValue((V) o);
    }
}
