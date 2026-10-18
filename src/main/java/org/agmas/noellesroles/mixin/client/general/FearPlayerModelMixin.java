package org.agmas.noellesroles.mixin.client.general;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.content.effects.FearEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = HumanoidModel.class, priority = 1200)
public abstract class FearPlayerModelMixin<T extends LivingEntity> {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("HEAD"))
    private void noellesroles$fearPrepare(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch, CallbackInfo ci) {
        HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
        if (FearEffects.isSitting(entity)) {
            model.riding = true;
            return;
        }
        if (entity instanceof Player) {
            model.body.zRot = 0.0f;
            model.head.zRot = 0.0f;
        }
    }

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("RETURN"))
    private void noellesroles$fearShake(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch, CallbackInfo ci) {
        HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
        if (!FearEffects.isTrembling(entity)) {
            return;
        }
        float lean = Mth.sin(ageInTicks * 1.35f) * 0.035f;
        float nod = Mth.cos(ageInTicks * 1.7f) * 0.018f;
        model.head.zRot = lean;
        model.body.zRot = lean;
        model.rightArm.zRot += lean;
        model.leftArm.zRot += lean;
        model.rightLeg.zRot += lean;
        model.leftLeg.zRot += lean;
        model.head.xRot += nod;
        noellesroles$copyOverlays(model);
    }

    @Unique
    private static void noellesroles$copyOverlays(HumanoidModel<?> model) {
        if (!(model instanceof PlayerModel<?> playerModel)) {
            return;
        }
        playerModel.hat.copyFrom(model.head);
        playerModel.jacket.copyFrom(model.body);
        playerModel.rightSleeve.copyFrom(model.rightArm);
        playerModel.leftSleeve.copyFrom(model.leftArm);
        playerModel.rightPants.copyFrom(model.rightLeg);
        playerModel.leftPants.copyFrom(model.leftLeg);
    }
}
