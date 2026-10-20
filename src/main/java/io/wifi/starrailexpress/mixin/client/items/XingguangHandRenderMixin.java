package io.wifi.starrailexpress.mixin.client.items;

import com.mojang.blaze3d.vertex.PoseStack;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.client.XingguangHandRenderer;
import io.wifi.starrailexpress.client.XingguangPoseManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 星光第一人称自定义手部模型渲染入口。renderRightHand/renderLeftHand 为 PlayerRenderer 的 public 方法，
 * 借此明确区分左右手：只在"主手且本地第一人称且持星光刀"那只手上叠加手部模型。
 * 手臂 ModelPart 通过继承 LivingEntityRenderer 的 model 字段访问（同 PlayerRendererHatLayerMixin 范式）。
 */
@Mixin(PlayerRenderer.class)
public abstract class XingguangHandRenderMixin
        extends LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    private static boolean xingguang$probed = false;

    protected XingguangHandRenderMixin(EntityRendererProvider.Context context,
            PlayerModel<AbstractClientPlayer> entityModel, float f) {
        super(context, entityModel, f);
    }

    @Inject(method = "renderRightHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;)V",
            at = @At("RETURN"))
    private void xingguang$handRight(PoseStack poseStack, MultiBufferSource buffer, int light,
            AbstractClientPlayer player, CallbackInfo ci) {
        if (!xingguang$probed) {
            net.minecraft.world.item.ItemStack main = player.getMainHandItem();
            String path = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(main.getItem()).getPath();
            if ("knife".equals(path)) {
                xingguang$probed = true;
                io.wifi.starrailexpress.SRE.LOGGER.info(
                        "[XGHand] knife probe. skin='{}' isXG={} mainEmpty={}",
                        io.wifi.starrailexpress.util.ItemSkinManager.getEquippedSkin(player, main),
                        XingguangPoseManager.isXingguangKnife(main), main.isEmpty());
            }
        }
        if (xingguang$active(player, HumanoidArm.RIGHT)) {
            XingguangHandRenderer.render(poseStack, buffer, light, player, this.model.rightArm);
        }
    }

    @Inject(method = "renderLeftHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;)V",
            at = @At("RETURN"))
    private void xingguang$handLeft(PoseStack poseStack, MultiBufferSource buffer, int light,
            AbstractClientPlayer player, CallbackInfo ci) {
        if (xingguang$active(player, HumanoidArm.LEFT)) {
            XingguangHandRenderer.render(poseStack, buffer, light, player, this.model.leftArm);
        }
    }

    private boolean xingguang$active(AbstractClientPlayer player, HumanoidArm side) {
        if (SRE.isLobby) {
            return false;
        }
        if (player.getMainArm() != side) {
            return false;
        }
        if (!XingguangPoseManager.isXingguangKnife(player.getMainHandItem())) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != player) {
            return false;
        }
        return mc.options.getCameraType().isFirstPerson();
    }
}