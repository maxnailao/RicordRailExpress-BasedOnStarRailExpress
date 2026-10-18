package io.wifi.starrailexpress.mixin.client.items;

import com.mojang.blaze3d.vertex.PoseStack;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.client.XingguangPoseManager;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 星光（传承）第一人称手臂姿态。
 * 原版第一人称左右手臂由 PlayerRenderer#renderLeftHand/renderLeftHand 绘制
 * （ItemInHandRenderer.renderPlayerArm 委托到这两个 public 方法），
 * 只在自己视角的手臂通道上生效，第三人称躯体模型走的是另外的渲染路径，不受影响。
 */
@Mixin(PlayerRenderer.class)
public class XingguangArmPoseMixin {

    @Inject(method = "renderLeftHand", at = @At("HEAD"))
    private void xingguang$leftArmPose(PoseStack poseStack, MultiBufferSource buffer, int light,
            AbstractClientPlayer player, CallbackInfo ci) {
        if (SRE.isLobby || !XingguangPoseManager.leftArmActive())
            return;
        XingguangPoseManager.applyLeftArm(poseStack);
    }
}