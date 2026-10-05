package io.wifi.starrailexpress.mixin.client.kidnap;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.game.roles.neutral.kidnapper.KidnappedCCA;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 绑匪人质「被放下」后的捆绑姿态：双手背在背后、头轻微低下。
 * 在 PlayerModel.setupAnim 末尾（TAIL）读取同步过来的 KidnappedCCA.boundIdle 标记，
 * 覆盖手臂/头部最终旋转，实现程序化捆绑动作（仅被绑且原地不动的人质可见）。
 */
@Environment(EnvType.CLIENT)
@Mixin(PlayerModel.class)
public abstract class KidnappedBoundPoseMixin extends HumanoidModel<LivingEntity> {

    // Mixin 构造器仅满足继承需要，永远不会被调用
    private KidnappedBoundPoseMixin(ModelPart root) {
        super(root);
    }

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void sre$applyKidnapBoundPose(LivingEntity entity, float limbSwing, float limbSwingAmount,
            float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (!(entity instanceof Player)) {
            return;
        }
        KidnappedCCA cca = KidnappedCCA.KEY.get(entity);
        if (cca == null || !cca.isKidnapped || !cca.boundIdle) {
            return;
        }

        // 极轻微呼吸起伏，避免姿态僵硬
        float breath = Mth.sin(ageInTicks * 0.05F) * 0.02F;

        // 双手在背后中线合拢（反剪）：
        // xRot 把双臂甩向背后；yRot 一正一负把两只手朝脊柱横扫收拢；
        // zRot 微收上臂让手臂贴背，双手在腰后碰在一起。
        this.rightArm.xRot = 0.75F;
        this.rightArm.yRot = 0.85F;
        this.rightArm.zRot = -0.15F;

        this.leftArm.xRot = 0.75F;
        this.leftArm.yRot = -0.85F;
        this.leftArm.zRot = 0.15F;

        // 头部轻微低下（正 xRot = 向下看）
        this.head.xRot = 0.30F + breath;

        // 外层部位在原版 setupAnim 内已提前复制，改完须再同步：帽子随头、袖子随手臂
        this.hat.copyFrom(this.head);
        PlayerModel<?> self = (PlayerModel<?>) (Object) this;
        self.leftSleeve.copyFrom(this.leftArm);
        self.rightSleeve.copyFrom(this.rightArm);
    }
}