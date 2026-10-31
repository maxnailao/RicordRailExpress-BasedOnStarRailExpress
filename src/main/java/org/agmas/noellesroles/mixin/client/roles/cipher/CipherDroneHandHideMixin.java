package org.agmas.noellesroles.mixin.client.roles.cipher;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.agmas.noellesroles.game.roles.neutral.cipher.CipherDroneEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 密客驾驶无人机期间取消第一人称手臂（相机已切到无人机，本体手臂无意义）。
 * 与神父天堂俯视镜头同一拦截点。
 */
@Mixin(ItemInHandRenderer.class)
public class CipherDroneHandHideMixin {
    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void noellesroles$hideHandsWhenDriving(float partialTick, PoseStack poseStack,
            MultiBufferSource.BufferSource bufferSource, LocalPlayer player, int packedLight, CallbackInfo ci) {
        if (Minecraft.getInstance().getCameraEntity() instanceof CipherDroneEntity) {
            ci.cancel();
        }
    }
}