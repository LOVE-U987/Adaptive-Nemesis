package dev.shadowsoffire.apotheosis.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.shadowsoffire.apotheosis.mixin.AbstractSkeletonMixin;
import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.SkeletonModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.CrossbowItem;

/**
 * Mixin to {@link SkeletonModel} to support {@link AbstractSkeletonMixin}.
 * <p>
 * This component handles the arm animations while the skeleton is holding a crossbow and targetting an enemy.
 */
@Mixin(value = SkeletonModel.class, remap = false)
public abstract class SkeletonModelMixin extends HumanoidModel<Mob> {

    public SkeletonModelMixin(ModelPart root) {
        super(root);
    }

    @Inject(method = "setupAnim", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;isAggressive()Z"), cancellable = true)
    public void apoth_setupCrossbowAnimations(Mob entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (entity.getMainHandItem().getItem() instanceof CrossbowItem) {
            // While Illagers use a synced data bit to check if the crossbow is charging, this suffices
            // since the RangedCrossbowAttackGoal will set the using item while it charges.
            if (entity.isUsingItem()) {
                AnimationUtils.animateCrossbowCharge(this.rightArm, this.leftArm, entity, true);
            }
            else {
                AnimationUtils.animateCrossbowHold(this.rightArm, this.leftArm, this.head, true);
            }
            ci.cancel();
        }
    }

}
