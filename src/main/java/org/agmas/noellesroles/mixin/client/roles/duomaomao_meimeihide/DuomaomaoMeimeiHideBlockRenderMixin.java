package org.agmas.noellesroles.mixin.client.roles.duomaomao_meimeihide;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.Block;
import org.agmas.noellesroles.game.roles.innocence.duomaomao_meimeihide.DuomaomaoMeimeiHidePlayerComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 躲藏专家渲染：变身期间取消玩家本体渲染（含名牌与手持物），
 * 改为在玩家位置渲染所选方块的模型。
 * 渲染管线的 PoseStack 已平移到玩家脚底坐标，方块模型恰好落在玩家原地，
 * 玩家移动时模型自然跟随。
 */
@Environment(EnvType.CLIENT)
@Mixin(PlayerRenderer.class)
public abstract class DuomaomaoMeimeiHideBlockRenderMixin {

    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"), cancellable = true)
    private void noellesroles$renderHiddenBlock(AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
            PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, CallbackInfo ci) {
        DuomaomaoMeimeiHidePlayerComponent comp = DuomaomaoMeimeiHidePlayerComponent.KEY.maybeGet(player).orElse(null);
        if (comp == null || !comp.isHiding()) {
            return;
        }
        Block block = comp.getHiddenBlock();
        if (block == null) {
            return;
        }
        ci.cancel();
        Minecraft mc = Minecraft.getInstance();
        if (mc.getBlockRenderer() == null) {
            return;
        }
        poseStack.pushPose();
        mc.getBlockRenderer().renderSingleBlock(block.defaultBlockState(), poseStack, bufferSource,
                packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }
}
