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
 * 机动者滑铲姿势：上身接近直立只微微前倾，前腿屈膝抬起、后腿向身后拖地，双臂屈在胸前外张的跪滑姿态。
 *
 * <p>按设计，滑铲不是一套动画而是**一个静态姿势**：只在滑铲位移阶段保持，位移结束或跃起时立刻取消，
 * 因此这里不做任何插值，命中状态就整帧套用。</p>
 *
 * <p>轮廓取自参考素材（现实滑铲视频抽帧 + 跪滑静帧）：共同点是躯干并不后倒、只是微微前倾，
 * 一膝收向前方、另一腿向后拖地，双手抬在胸腹前保持平衡与持枪姿态。</p>
 *
 * <p>角度约定取自本仓库已验证的姿态 mixin（{@code RoadRollerPlayerModelMixin} 俯身用正 body.xRot、
 * 收腿用负 leg.xRot；{@code KidnappedBoundPoseMixin} 手臂甩向背后用正 arm.xRot）：
 * 躯干正 xRot = 前倾，四肢负 xRot = 向前摆，所以后腿向身后拖要取正值。</p>
 *
 * <p>外套 / 帽子 / 袖管 / 裤腿都要重新 copyFrom，否则外层贴图会留在原姿势上。</p>
 */
@Mixin(PlayerModel.class)
public abstract class JidongzheSlidePoseMixin {

    /** 躯干：微微前倾（不是后仰），髋部带一点打开的扭转 */
    private static final float BODY_X = 0.20F;
    private static final float BODY_TWIST_Y = 0.16F;
    private static final float BODY_Z = 0.08F;
    /** 头：基本保持水平，只略微收一点，视线仍朝滑行方向看路；水平转向保留 vanilla 的跟随 */
    private static final float HEAD_X = 0.05F;
    /** 双臂屈在胸腹前，肘部向外张开 */
    private static final float LEAD_ARM_X = -0.90F;
    private static final float LEAD_ARM_Z = 0.34F;
    private static final float TRAIL_ARM_X = -0.65F;
    private static final float TRAIL_ARM_Z = -0.46F;
    /** 前腿屈膝抬向身前，后腿向身后拖地（正 xRot = 向后） */
    private static final float FRONT_LEG_X = -1.05F;
    private static final float FRONT_LEG_Z = 0.16F;
    private static final float REAR_LEG_X = 0.55F;
    private static final float REAR_LEG_Z = -0.10F;

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("RETURN"))
    private void noellesroles$applySlidePose(LivingEntity entity, float limbSwing, float limbSwingAmount,
            float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (!(entity instanceof Player player)) {
            return;
        }
        if (!JidongzheClient.isSlidingPose(player.getId())) {
            return;
        }
        JidongzheClient.logPoseAppliedOnce(player.getId());
        PlayerModel<?> model = (PlayerModel<?>) (Object) this;

        model.body.xRot = BODY_X;
        // 扭转用叠加而不是覆写：覆写会让上身不再跟随转向，滑铲中扭头就变成「头身分离」
        model.body.yRot += BODY_TWIST_Y;
        model.body.zRot = BODY_Z;
        model.head.xRot = HEAD_X;

        model.rightArm.xRot = LEAD_ARM_X;
        model.rightArm.yRot = 0.0F;
        model.rightArm.zRot = LEAD_ARM_Z;
        model.leftArm.xRot = TRAIL_ARM_X;
        model.leftArm.yRot = 0.0F;
        model.leftArm.zRot = TRAIL_ARM_Z;

        model.rightLeg.xRot = FRONT_LEG_X;
        model.rightLeg.yRot = 0.0F;
        model.rightLeg.zRot = FRONT_LEG_Z;
        model.leftLeg.xRot = REAR_LEG_X;
        model.leftLeg.yRot = 0.0F;
        model.leftLeg.zRot = REAR_LEG_Z;

        model.jacket.copyFrom(model.body);
        model.hat.copyFrom(model.head);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightPants.copyFrom(model.rightLeg);
        model.leftPants.copyFrom(model.leftLeg);
    }
}
