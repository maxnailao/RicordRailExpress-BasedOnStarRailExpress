package io.wifi.starrailexpress.mixin.client.items;

import io.wifi.starrailexpress.client.XingguangClientEffects;
import io.wifi.starrailexpress.content.item.KnifeItem;
import io.wifi.starrailexpress.util.ItemSkinManager;
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
        boolean charging = player.isUsingItem()
                && main.getItem() instanceof KnifeItem
                && XingguangClientEffects.SKIN_ID.equals(ItemSkinManager.getEquippedSkin(player, main))
                && XingguangClientEffects.hasXingguangEquipped(player);
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
        this.rightArm.xRot = Mth.lerp(this.xingguang$blend, this.rightArm.xRot, -1.45f);
        this.rightArm.yRot = Mth.lerp(this.xingguang$blend, this.rightArm.yRot, 0f);
        this.rightArm.zRot = Mth.lerp(this.xingguang$blend, this.rightArm.zRot, 0.15f);
        if (charging && this.rightArm.xRot > -1.35f) this.rightArm.xRot -= 0.04f;
        PlayerModel<?> self = (PlayerModel<?>) (Object) this;
        self.rightSleeve.copyFrom(this.rightArm);
    }
}