package io.wifi.starrailexpress.mixin.client.items;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.client.ClawFlipManager;
import io.wifi.starrailexpress.client.XingguangHandRenderer;
import io.wifi.starrailexpress.client.XingguangPoseManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 星光刀第一人称"只转刀、不翻手臂"的挥刀自转注入。
 * 手臂网格由 renderRightHand 单独渲染，物品由 renderItem 单独渲染；
 * 在 renderItem HEAD 施加绕手部锚点(≈握把)的 Z 轴自转，即可让刀原地翻腕、手臂保持持刀姿态。
 * 角度来自 XingguangPoseManager.itemSpinAngle()，仅在本地玩家第一人称主手+挥刀窗口生效。
 */
@Mixin(ItemInHandRenderer.class)
public class XingguangItemSpinMixin {

    // 握把相对手部锚点的偏移(渲染空间)。先取 0＝绕模型中心(拳头)转；
    // 若握把在翻腕时轻微漂移，按漂移反方向微调这两个值即可(只影响刀、不影响手臂)。
    private static final float GRIP_X = 0.0F;
    private static final float GRIP_Y = 0.0F;

    @Inject(method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;"
            + "Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"))
    private void xingguang$spinKnifeAboutGrip(LivingEntity entity, ItemStack stack, ItemDisplayContext displayContext,
            boolean leftHanded, PoseStack poseStack, MultiBufferSource buffer, int light, CallbackInfo ci) {
        if (SRE.isLobby || leftHanded || !displayContext.firstPerson()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || entity != mc.player) {
            return;
        }
        if (!XingguangPoseManager.isXingguangKnife(stack)) {
            // 猩红之爪：切刀窗口的侧刃俯仰与绕环翻腕，只转刀+手，手臂由相机层管；
            // 奔跑持刀偏移作为底层姿态先施加，动画窗口在其上叠加
            if (XingguangPoseManager.isXinghongClaw(stack)) {
                ClawFlipManager.applySprintItem(poseStack);
                ClawFlipManager.applyItem(poseStack);
            }
            return;
        }
        if (!XingguangPoseManager.itemSpinActive()) {
            return;
        }
        float a = XingguangPoseManager.itemSpinAngle();
        // 先按关键帧平移把刀整体挪近角色模型，再绕握把做"俯仰→偏转→自转"三连(检视用俯仰/偏转，挥刀只用自转)
        poseStack.translate(XingguangPoseManager.itemSpinOffX(),
                XingguangPoseManager.itemSpinOffY(),
                XingguangPoseManager.itemSpinOffZ());
        poseStack.translate(GRIP_X, GRIP_Y, 0.0F);
        poseStack.mulPose(Axis.XP.rotationDegrees(XingguangPoseManager.itemTiltX()));
        poseStack.mulPose(Axis.YP.rotationDegrees(XingguangPoseManager.itemTiltY()));
        poseStack.mulPose(Axis.ZP.rotationDegrees(a));
        poseStack.translate(-GRIP_X, -GRIP_Y, 0.0F);
    }

    @Inject(method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;"
            + "Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("TAIL"))
    private void xingguang$drawHand(LivingEntity entity, ItemStack stack, ItemDisplayContext displayContext,
            boolean leftHanded, PoseStack poseStack, MultiBufferSource buffer, int light, CallbackInfo ci) {
        if (SRE.isLobby || leftHanded || !displayContext.firstPerson()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || entity != mc.player) {
            return;
        }
        if (XingguangPoseManager.isXingguangKnife(stack)) {
            XingguangHandRenderer.renderInItemSpace(poseStack, buffer, light, mc.player);
        } else if (XingguangPoseManager.isXinghongClaw(stack)) {
            XingguangHandRenderer.renderClawInItemSpace(poseStack, buffer, light, mc.player);
        }
    }
}