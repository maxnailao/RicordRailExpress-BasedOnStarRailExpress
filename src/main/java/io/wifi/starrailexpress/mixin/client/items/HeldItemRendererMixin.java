package io.wifi.starrailexpress.mixin.client.items;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.client.ClawFlipManager;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.XingguangHandRenderer;
import io.wifi.starrailexpress.client.XingguangPoseManager;
import io.wifi.starrailexpress.content.item.api.SREItemProperties.HeldLikeRevolver;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import io.wifi.starrailexpress.util.MatrixParticleManager;
import io.wifi.starrailexpress.util.MatrixUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public class HeldItemRendererMixin {

    private int xg$probeCounter = 0;

    @Shadow
    private ItemStack mainHandItem;

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private ItemRenderer itemRenderer;

    @Inject(method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/ItemRenderer;renderStatic(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/level/Level;III)V", shift = At.Shift.AFTER))
    private void tmm$itemVFX(LivingEntity entity, ItemStack stack, ItemDisplayContext renderMode, boolean leftHanded,
            PoseStack matrices, MultiBufferSource vertexConsumers, int light, CallbackInfo ci) {
        if (SRE.isLobby)
            return;
        if (renderMode.firstPerson()) {
            SREClient.handParticleManager.render(matrices, vertexConsumers, light);
        }

        if (entity instanceof Player playerEntity
                && (stack.is(TMMItemTags.HELD_LIKE_GUNS_ITEMS)
                        || (this.mainHandItem.getItem() instanceof HeldLikeRevolver))) {
            if (playerEntity.getUUID() != Minecraft.getInstance().player.getUUID()) {
                MatrixParticleManager.muzzlePosForPlayer$set(playerEntity, MatrixUtils.matrixToVec(matrices));
            } else if (!renderMode.firstPerson()) {
                MatrixParticleManager.muzzlePosForPlayer$set(playerEntity, MatrixUtils.matrixToVec(matrices));
            }
        }
    }

    @WrapMethod(method = "renderArmWithItem")
    private void xingguang$armKnifePose(AbstractClientPlayer player, float partialTicks, float pitch,
            InteractionHand hand, float swingProgress, ItemStack stack, float equippedProgress,
            PoseStack poseStack, MultiBufferSource buffer, int packedLight, Operation<Void> original) {
        if (hand == InteractionHand.MAIN_HAND && player == this.minecraft.player
                && (++this.xg$probeCounter % 40 == 0)) {
            SRE.LOGGER.info("[XGHand] armWithItem path='{}' isXG={} skin='{}'",
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath(),
                    XingguangPoseManager.isXingguangKnife(stack),
                    io.wifi.starrailexpress.util.ItemSkinManager.getEquippedSkin(player, stack));
        }
        boolean override = !SRE.isLobby && hand == InteractionHand.MAIN_HAND
                && player == this.minecraft.player && XingguangPoseManager.isXingguangKnife(stack);
        // 猩红之爪：切刀窗口接管手臂链（常驻持握仍走原版，不影响已定稿姿态）；
        // 奔跑持刀通道叠加生效，且"退出奔跑回落期"也要保持接管，防止姿态突跳
        boolean clawFlip = !SRE.isLobby && hand == InteractionHand.MAIN_HAND
                && player == this.minecraft.player
                && XingguangPoseManager.isXinghongClaw(stack)
                && (ClawFlipManager.camActive() || ClawFlipManager.sprintHoldActive());
        if (!override && !clawFlip) {
            original.call(player, partialTicks, pitch, hand, swingProgress, stack, equippedProgress,
                    poseStack, buffer, packedLight);
            return;
        }
        // 星光皮肤独占：先施加我们自己的劈砍/持刀姿态，再以 swingProgress=0 调用原版，
        // 彻底掐掉原版"挥东西"动画，避免两套动作重合。其他物品/视角原样保留原版挥动。
        if (override) {
            XingguangPoseManager.applyKnife(poseStack);
        }
        if (clawFlip) {
            ClawFlipManager.applySprintCam(poseStack);
            ClawFlipManager.applyCam(poseStack);
        }
        // 星光分支与爪子动画窗口照旧掐零摆臂；仅"爪子+奔跑持刀无窗口"保留原版跑步摆臂
        float swingArg = (clawFlip && !override && !ClawFlipManager.camActive())
                ? swingProgress : 0.0F;
        original.call(player, partialTicks, pitch, hand, swingArg, stack, equippedProgress,
                poseStack, buffer, packedLight);
    }

    @ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;matches(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean tmm$ignoreNbtUpdateForRevolver(boolean original, @Local(ordinal = 0) ItemStack newItemStack) {
        if (SRE.isLobby)
            return original;
        if (!original) {
            if (this.mainHandItem.is(TMMItemTags.HELD_LIKE_GUNS_ITEMS) && newItemStack.is(TMMItemTags.HELD_LIKE_GUNS_ITEMS)
                    || (this.mainHandItem.getItem() instanceof HeldLikeRevolver)) {
                return true;
            }
        }
        return original;
    }
}
