package org.agmas.noellesroles.mixin.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.content.item.HandCuffsItem;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public abstract class HandCuffsPoseMixin<T extends LivingEntity> {

    @Shadow
    @Final
    public ModelPart leftArm;

    @Shadow
    @Final
    public ModelPart rightArm;

    @Inject(method = "poseRightArm", at = @At("TAIL"))
    private void noellesroles$cuffedRightArm(T entity, CallbackInfo ci) {
        if (isHandCuffed(entity)) {
            applyCuffedPose(this.rightArm, true);
        }
    }

    @Inject(method = "poseLeftArm", at = @At("TAIL"))
    private void noellesroles$cuffedLeftArm(T entity, CallbackInfo ci) {
        if (isHandCuffed(entity)) {
            applyCuffedPose(this.leftArm, false);
        }
    }

    @Unique
    private boolean isHandCuffed(T entity) {
        return entity instanceof Player player && HandCuffsItem.hasHandCuff(player);
    }

    @Unique
    private static void applyCuffedPose(ModelPart arm, boolean isRight) {
        float sign = isRight ? 1.0F : -1.0F;
        arm.xRot = (float) (Math.PI * 0.35);
        arm.yRot = sign * 0.7F;
        arm.zRot = sign * 0.2F;
    }
}