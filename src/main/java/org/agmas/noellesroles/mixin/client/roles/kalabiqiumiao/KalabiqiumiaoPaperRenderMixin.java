package org.agmas.noellesroles.mixin.client.roles.kalabiqiumiao;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.agmas.noellesroles.client.KalabiqiumiaoClientHandle;
import org.agmas.noellesroles.game.roles.innocence.kalabiqiumiao.KalabiqiumiaoPlayerComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 纸片人渲染：弦化期间将玩家模型沿本地 X 轴（左右方向）压扁，
 * 使侧面宽度变扁、整个人看起来像一张纸片，任意视角都清晰可见。
 * 通过原版预留的动画扩展点 setupRotations 追加缩放，
 * 不包裹、不取消任何原渲染逻辑（阴影渲染同样生效）。
 */
@Environment(EnvType.CLIENT)
@Mixin(PlayerRenderer.class)
public abstract class KalabiqiumiaoPaperRenderMixin {

    @Inject(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V", at = @At("TAIL"))
    private void noellesroles$flattenPaperPlayer(AbstractClientPlayer player, PoseStack poseStack,
            float ageInTicks, float rotationYaw, float partialTick, float scale, CallbackInfo ci) {
        if (!KalabiqiumiaoClientHandle.isPaperVisible(player)) {
            return;
        }
        // 沿本地 X 轴（玩家左右方向）压扁：侧面宽度变扁成纸片，正面/背面视角也清晰可见
        poseStack.scale(KalabiqiumiaoPlayerComponent.PAPER_RENDER_SCALE, 1.0F, 1.0F);
    }
}
