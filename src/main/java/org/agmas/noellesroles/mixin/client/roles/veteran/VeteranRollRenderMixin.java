package org.agmas.noellesroles.mixin.client.roles.veteran;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.agmas.noellesroles.client.VeteranRollTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 退伍军人冲刺翻滚动画渲染
 * 在原版冲刺（退伍军人冲刺技能）过程中叠加播放前滚翻动画：
 * 通过原版预留的动画扩展点 setupRotations 追加旋转（鞘翅俯仰、海豚翻滚等原版动画同样在此处叠加），
 * 不包裹、不取消任何原渲染逻辑。
 * 翻滚绕玩家本地 X 轴（垂直于朝向）旋转 360°，冲刺时玩家朝向即移动方向。
 * 纯视觉变换，不影响碰撞箱与实际移动。
 */
@Environment(EnvType.CLIENT)
@Mixin(PlayerRenderer.class)
public abstract class VeteranRollRenderMixin {

    /** 翻滚旋转轴心高度（方块单位），约身体中部 */
    private static final float ROLL_PIVOT_HEIGHT = 0.75F;

    @Inject(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V", at = @At("TAIL"))
    private void onVeteranRollSetupRotations(AbstractClientPlayer player, PoseStack poseStack,
            float ageInTicks, float rotationYaw, float partialTick, float scale, CallbackInfo ci) {
        var client = Minecraft.getInstance();
        if (client.level == null) {
            return;
        }
        float progress = VeteranRollTracker.getRollProgress(player.getId(),
                client.level.getGameTime(), partialTick);
        if (progress < 0.0F) {
            return;
        }

        // 沿冲刺方向前滚翻：绕本地 X 轴（垂直于玩家朝向的水平轴）旋转一整圈
        float angle = progress * 360.0F;
        poseStack.translate(0.0F, ROLL_PIVOT_HEIGHT, 0.0F);
        poseStack.mulPose(Axis.XN.rotationDegrees(angle));
        poseStack.translate(0.0F, -ROLL_PIVOT_HEIGHT, 0.0F);
    }
}
