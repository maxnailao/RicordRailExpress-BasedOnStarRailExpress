package org.agmas.noellesroles.mixin.client.roles.jidongzhe;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.client.JidongzheClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 机动者滑铲姿态：单膝前收、后腿拖曳、上身前倾、双臂后摆。
 *
 * <p>姿态不是直接赋值，而是从 vanilla 本帧算出的角度按 {@code blend} 插值到目标角度，
 * 所以起滑与收滑都是渐入渐出；同时照本仓库惯例把外套 / 帽子 / 袖管 / 裤腿重新 copyFrom，
 * 否则外层贴图会留在原姿势上。</p>
 */
@Mixin(PlayerModel.class)
public abstract class JidongzheSlidePoseMixin {

    // 目标角度（跪滑：前腿收向腹部、后腿向后拖、双臂甩向身后）
    private static final float BODY_X = 1.00F;
    private static final float HEAD_X = -0.70F;
    private static final float RIGHT_ARM_X = 1.30F;
    private static final float LEFT_ARM_X = 1.15F;
    private static final float ARM_Z = 0.35F;
    private static final float FRONT_LEG_X = -1.20F;
    private static final float REAR_LEG_X = 0.55F;
    private static final float LEG_Z = 0.12F;

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("RETURN"))
    private void noellesroles$applySlidePose(LivingEntity entity, float limbSwing, float limbSwingAmount,
            float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (!(entity instanceof Player player)) {
            return;
        }
        // ageInTicks - tickCount ≈ 帧内插值，保证不卡顿
        float blend = JidongzheClient.slideBlend(player.getId(), ageInTicks - entity.tickCount);
        if (blend <= 0.0F) {
            return;
        }
        PlayerModel<?> model = (PlayerModel<?>) (Object) this;

        // 轻微前冲起伏，避免定帧感；幅度也随 blend 缩放，淡出时不会突然抖一下
        float surge = (float) Math.sin(ageInTicks * 2.2) * 0.05F * blend;

        model.body.xRot = mix(model.body.xRot, BODY_X + surge, blend);
        // 上身已前倾，头部反向抬起才能让玩家看起来是朝前方看路
        model.head.xRot = mix(model.head.xRot, HEAD_X, blend);
        model.rightArm.xRot = mix(model.rightArm.xRot, RIGHT_ARM_X + surge, blend);
        model.leftArm.xRot = mix(model.leftArm.xRot, LEFT_ARM_X + surge, blend);
        model.rightArm.zRot = mix(model.rightArm.zRot, -ARM_Z, blend);
        model.leftArm.zRot = mix(model.leftArm.zRot, ARM_Z, blend);
        model.rightLeg.xRot = mix(model.rightLeg.xRot, FRONT_LEG_X, blend);
        model.leftLeg.xRot = mix(model.leftLeg.xRot, REAR_LEG_X, blend);
        model.rightLeg.zRot = mix(model.rightLeg.zRot, LEG_Z, blend);
        model.leftLeg.zRot = mix(model.leftLeg.zRot, -LEG_Z, blend);

        model.jacket.copyFrom(model.body);
        model.hat.copyFrom(model.head);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightPants.copyFrom(model.rightLeg);
        model.leftPants.copyFrom(model.leftLeg);
    }

    private static float mix(float from, float to, float blend) {
        return from + (to - from) * blend;
    }
}
