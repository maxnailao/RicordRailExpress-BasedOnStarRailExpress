package org.agmas.noellesroles.mixin.client.roles.huanshushi;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.agmas.noellesroles.client.renderer.IllusionDecoyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 幻术师分身的「坐下」姿态。
 *
 * <p>原版玩家的 {@code PlayerModel} 从不设置 {@code riding}（那是 {@code LivingEntityRenderer}
 * 给非玩家模型设的），而且 {@code HumanoidModel.setupAnim} 每次开头都会把 {@code riding}
 * 重置为 {@code entity.isPassenger()}。幻术师分身是渲染期临时 new 出来的 {@code RemotePlayer} 假玩家，
 * 并不在实体列表里，没法让它真的「成为乘客」，因此这里在 {@code setupAnim} 末尾
 * 对标记为坐下的分身补上坐姿（腿向前伸、手臂略前收），与 {@code HumanoidModel}
 * 自身 riding 分支的数值保持一致。
 */
@Mixin(HumanoidModel.class)
public abstract class IllusionDecoySitPoseMixin {

    /** 与 HumanoidModel 的 riding 分支保持一致的数值 */
    private static final float ARM_X = -0.62831855F;
    private static final float LEG_X = -1.4137167F;
    private static final float LEG_Y = 0.31415927F;
    private static final float LEG_Z = 0.07853982F;

    @Inject(method = "setupAnim", at = @At("TAIL"))
    private void noellesroles$applyDecoySitPose(LivingEntity entity, float limbAngle, float limbDistance,
            float animationProgress, float headYaw, float headPitch, CallbackInfo ci) {
        if (!(entity instanceof AbstractClientPlayer player)) {
            return;
        }
        if (!IllusionDecoyRenderer.isMarkedSitting(player.getUUID())) {
            return;
        }
        @SuppressWarnings("unchecked")
        HumanoidModel<LivingEntity> self = (HumanoidModel<LivingEntity>) (Object) this;
        self.rightArm.xRot += ARM_X;
        self.leftArm.xRot += ARM_X;
        // 腿：向前抬起并略微外八，模拟坐下
        self.rightLeg.xRot = LEG_X;
        self.rightLeg.yRot = LEG_Y;
        self.rightLeg.zRot = LEG_Z;
        self.leftLeg.xRot = LEG_X;
        self.leftLeg.yRot = -LEG_Y;
        self.leftLeg.zRot = -LEG_Z;
    }
}
