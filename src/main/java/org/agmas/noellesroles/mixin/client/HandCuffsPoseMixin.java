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
            applyCuffedPose(this.rightArm, true, isCuffedFromFront(entity));
        }
    }

    @Inject(method = "poseLeftArm", at = @At("TAIL"))
    private void noellesroles$cuffedLeftArm(T entity, CallbackInfo ci) {
        if (isHandCuffed(entity)) {
            applyCuffedPose(this.leftArm, false, isCuffedFromFront(entity));
        }
    }

    @Unique
    private boolean isHandCuffed(T entity) {
        return entity instanceof Player player && HandCuffsItem.hasHandCuff(player);
    }

    @Unique
    private boolean isCuffedFromFront(T entity) {
        return entity instanceof Player player && HandCuffsItem.isCuffedFromFront(player);
    }

    @Unique
    private static void applyCuffedPose(ModelPart arm, boolean isRight, boolean front) {
        float sign = isRight ? 1.0F : -1.0F;
        // 前铐时镜像后铐姿势：
        // xRot 取反使手臂从背后(-Z侧)摆到身前(模型正面为-Z，xRot正值朝背后)
        // yRot/zRot 取反保持双手在身前向中间相交
        float mirror = front ? -1.0F : 1.0F;
        arm.xRot = (float) (Math.PI * 0.35) * mirror;
        arm.yRot = sign * 0.7F * mirror;
        arm.zRot = sign * 0.2F * mirror;
    }
}