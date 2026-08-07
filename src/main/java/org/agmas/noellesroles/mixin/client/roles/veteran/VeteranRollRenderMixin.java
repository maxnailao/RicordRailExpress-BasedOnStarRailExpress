package org.agmas.noellesroles.mixin.client.roles.veteran;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.client.VeteranRollTracker;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 退伍军人冲刺翻滚动画渲染
 * 翻滚期间将整个玩家模型绕水平轴（垂直于冲刺方向、穿过身体中部）旋转 360°，
 * 纯视觉变换，不影响碰撞箱与实际移动。
 */
@Environment(EnvType.CLIENT)
@Mixin(PlayerRenderer.class)
public abstract class VeteranRollRenderMixin {

    /** 翻滚旋转轴心高度（方块单位），约身体中部 */
    private static final float ROLL_PIVOT_HEIGHT = 0.75F;

    @WrapOperation(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V")
    private void onRenderVeteranRoll(AbstractClientPlayer player, float entityYaw, float partialTick,
            PoseStack poseStack, MultiBufferSource buffer, int packedLight,
            Operation<Void> original) {
        var client = net.minecraft.client.Minecraft.getInstance();
        if (client.level == null) {
            original.call(player, entityYaw, partialTick, poseStack, buffer, packedLight);
            return;
        }
        float progress = VeteranRollTracker.getRollProgress(player.getId(),
                client.level.getGameTime(), partialTick);
        if (progress < 0.0F) {
            original.call(player, entityYaw, partialTick, poseStack, buffer, packedLight);
            return;
        }

        Vec3 dir = VeteranRollTracker.getRollDirection(player.getId());
        if (dir.lengthSqr() < 1.0E-4D) {
            original.call(player, entityYaw, partialTick, poseStack, buffer, packedLight);
            return;
        }

        // 旋转轴：垂直于冲刺方向的水平轴（右手定则），使模型沿冲刺方向前滚
        Vector3f axisVec = new Vector3f((float) dir.z, 0.0F, (float) -dir.x);
        axisVec.normalize();
        float angle = progress * 360.0F;

        poseStack.pushPose();
        poseStack.translate(0.0F, ROLL_PIVOT_HEIGHT, 0.0F);
        poseStack.mulPose(Axis.of(axisVec).rotationDegrees(angle));
        poseStack.translate(0.0F, -ROLL_PIVOT_HEIGHT, 0.0F);
        original.call(player, entityYaw, partialTick, poseStack, buffer, packedLight);
        poseStack.popPose();
    }
}
