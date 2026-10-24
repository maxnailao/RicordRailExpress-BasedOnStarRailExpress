package io.wifi.starrailexpress.mixin.client.items;

import io.wifi.starrailexpress.client.XingguangPoseManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 星光传承皮肤第三人称蓄力姿态：
 * 手持星光刀蓄力（isUsingItem）时，把右臂（连同袖子）平滑抬起成举刀姿势，
 * 使第三人称下能看到第一人称蓄力姿态的对应动作。
 * 注入 PlayerModel.setupAnim 末尾（TAIL），确保覆盖原版摆臂等动画。
 */
@Environment(EnvType.CLIENT)
@Mixin(PlayerModel.class)
public abstract class XingguangChargeThirdPersonMixin extends HumanoidModel<LivingEntity> {

    // Mixin 构造器仅满足继承需要，永远不会被调用
    private XingguangChargeThirdPersonMixin(ModelPart root) {
        super(root);
    }

    @Unique
    private float xingguang$blend;

    @Unique
    private long xingguang$lastT;

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void xingguang$chargeThirdPerson(LivingEntity entity, float limbSwing, float limbSwingAmount,
            float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (!(entity instanceof AbstractClientPlayer player)) return;
        if (player.isSpectator()) return;
        // 第一人称手臂模型也走 setupAnim：本姿态只给第三人称身体用，
        // 否则会把第一人称手臂转上画面（"拿东西没手臂"的根因）
        Minecraft mc = Minecraft.getInstance();
        if (entity == mc.player && mc.options.getCameraType().isFirstPerson()) return;
        ItemStack main = player.getMainHandItem();
        // 猩红之爪：原版 SPEAR 举刀被禁用（保第一人称蓄力转刀），第三人称举刀同样由本 Mixin 补，
        // 采用"举到头顶"式（手臂上举过头，仿原版三叉戟），与星光的平举前伸区分
        boolean xgCharge = player.isUsingItem() && XingguangPoseManager.isXingguangKnife(player, main);
        boolean clawCharge = player.isUsingItem() && XingguangPoseManager.isXinghongClaw(main);
        boolean charging = xgCharge || clawCharge;
        if (!charging && this.xingguang$blend < 0.01f) {
            this.xingguang$blend = 0f;
            this.xingguang$lastT = 0L;
            return;
        }
        long tick = player.level() != null ? player.level().getGameTime() : 0L;
        float dt = this.xingguang$blend <= 0.01f ? 0.05F
                : Mth.clamp((float) (tick - this.xingguang$lastT), 1, 5) * 0.05F;
        this.xingguang$lastT = tick;
        float f = 1f - (float) Math.pow(0.5, dt / 0.05f);
        this.xingguang$blend += ((charging ? 1f : 0f) - this.xingguang$blend) * f;
        if (charging && this.xingguang$blend < 0.6f) this.xingguang$blend += 0.02f;
        if (this.xingguang$blend <= 0.01f) return;
        float targetX = clawCharge ? -2.6f : -1.45f;
        float targetZ = clawCharge ? 0.25f : 0.15f;
        this.rightArm.xRot = Mth.lerp(this.xingguang$blend, this.rightArm.xRot, targetX);
        this.rightArm.yRot = Mth.lerp(this.xingguang$blend, this.rightArm.yRot, 0f);
        this.rightArm.zRot = Mth.lerp(this.xingguang$blend, this.rightArm.zRot, targetZ);
        if (charging && this.rightArm.xRot > targetX + 0.1f) this.rightArm.xRot -= 0.04f;
        PlayerModel<?> self = (PlayerModel<?>) (Object) this;
        self.rightSleeve.copyFrom(this.rightArm);
    }
}
