package org.agmas.noellesroles.mixin.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.game.roles.neutral.munaiyi_desert.MunaiyiDesertPlayerComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 木乃伊完整现身手势：双臂前平举，同原版僵尸。
 * 仅在技能3「现身」生效期间（组件 fullRevealTicks > 0）应用，
 * 组件状态全量广播，远端客户端同样可见。
 */
@Mixin(HumanoidModel.class)
public abstract class MunaiyiPoseMixin<T extends LivingEntity> {

    @Shadow
    @Final
    public ModelPart leftArm;

    @Shadow
    @Final
    public ModelPart rightArm;

    @Inject(method = "poseRightArm", at = @At("TAIL"))
    private void noellesroles$mummyRightArm(T entity, CallbackInfo ci) {
        if (isMummyRevealed(entity)) {
            applyMummyPose(this.rightArm, true);
        }
    }

    @Inject(method = "poseLeftArm", at = @At("TAIL"))
    private void noellesroles$mummyLeftArm(T entity, CallbackInfo ci) {
        if (isMummyRevealed(entity)) {
            applyMummyPose(this.leftArm, false);
        }
    }

    @Unique
    private boolean isMummyRevealed(T entity) {
        if (!(entity instanceof Player player)) {
            return false;
        }
        MunaiyiDesertPlayerComponent comp = MunaiyiDesertPlayerComponent.KEY.maybeGet(player).orElse(null);
        return comp != null && comp.getFullRevealTicks() > 0;
    }

    @Unique
    private static void applyMummyPose(ModelPart arm, boolean isRight) {
        // 僵尸式前平举：手臂绕 X 轴抬至水平向前，微微内收
        arm.xRot = -(float) (Math.PI / 2.0D);
        arm.yRot = isRight ? -0.1F : 0.1F;
        arm.zRot = 0.0F;
    }
}
