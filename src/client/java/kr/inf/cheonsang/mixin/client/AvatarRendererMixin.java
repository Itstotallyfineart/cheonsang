package kr.inf.cheonsang.mixin.client;

import kr.inf.cheonsang.Telescope;
import kr.inf.cheonsang.client.PoseSync;
import kr.inf.cheonsang.client.TelescopeView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.7.1: 망원경 배율 ×1 넘을 때 = 바닐라 망원경과 같게 — 팔은 눈에 대는 자세, 모델은 손이 아니라 머리(눈)에 붙임 (3인칭, 내 캐릭터).
 * ×1 은 그냥 손에 든 모습.
 * 1.9.2: 다른 사람도 — 서버가 알려 준 확대 여부 ({@link PoseSync}) 로 같은 자세.
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
    @Inject(method = "getArmPose(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/client/model/HumanoidModel$ArmPose;",
            at = @At("HEAD"), cancellable = true)
    private static void cheonsang$telescopePose(Avatar avatar, ItemStack stack, InteractionHand hand, CallbackInfoReturnable<HumanoidModel.ArmPose> cir) {
        if (stack.is(Telescope.INSTANCE.getITEM()) && cheonsang$using(avatar))
            cir.setReturnValue(HumanoidModel.ArmPose.SPYGLASS);
    }

    /** 내 캐릭터는 내 확대 상태, 다른 사람은 서버가 알려 준 상태 */
    private static boolean cheonsang$using(Avatar avatar) {
        return avatar == Minecraft.getInstance().player ? TelescopeView.getMagnifying() : PoseSync.isUsing(avatar.getUUID());
    }

    /** 바닐라는 망원경을 "쓰는 중"일 때 heldOnHead 를 채우고 PlayerItemInHandLayer 가 눈에 그림 — 같은 값을 넣어 줌 */
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
    private void cheonsang$telescopeOnEye(Avatar avatar, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        if (!cheonsang$using(avatar)) return;
        InteractionHand hand = avatar.getMainHandItem().is(Telescope.INSTANCE.getITEM()) ? InteractionHand.MAIN_HAND
            : avatar.getOffhandItem().is(Telescope.INSTANCE.getITEM()) ? InteractionHand.OFF_HAND : null;
        if (hand == null) return;
        state.isUsingItem = true;
        state.useItemHand = hand;
        ItemModelResolver r = ((EntityRendererAccessor) (Object) this).cheonsang$itemModelResolver();
        r.updateForLiving(state.heldOnHead, avatar.getItemInHand(hand), ItemDisplayContext.HEAD, avatar);
    }
}
