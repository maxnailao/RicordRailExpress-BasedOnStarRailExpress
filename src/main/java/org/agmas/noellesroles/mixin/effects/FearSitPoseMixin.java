package org.agmas.noellesroles.mixin.effects;

import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.content.effects.FearEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public class FearSitPoseMixin {
    @Inject(method = "updatePlayerPose", at = @At("HEAD"), cancellable = true)
    private void noellesroles$fearSit(CallbackInfo ci) {
        Player player = (Player) (Object) this;
        if (FearEffects.isSitting(player)) {
            player.setPose(Pose.SITTING);
            ci.cancel();
        }
    }
}
