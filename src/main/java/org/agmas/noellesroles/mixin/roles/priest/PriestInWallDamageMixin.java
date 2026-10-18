package org.agmas.noellesroles.mixin.roles.priest;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 天堂制造期间穿墙时不受原版卡墙窒息伤害。 */
@Mixin(LivingEntity.class)
public abstract class PriestInWallDamageMixin {

    @Inject(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At("HEAD"), cancellable = true)
    private void noellesroles$ignorePriestInWallDamage(DamageSource source, float amount,
            CallbackInfoReturnable<Boolean> cir) {
        if (!source.is(DamageTypes.IN_WALL)) {
            return;
        }
        LivingEntity entity = (LivingEntity) (Object) this;
        if (!(entity instanceof Player player) || !player.hasEffect(ModEffects.NO_COLLIDE)) {
            return;
        }
        SREGameWorldComponent game = SREGameWorldComponent.KEY.maybeGet(player.level()).orElse(null);
        if (game != null && game.isRole(player, ModRoles.PRIEST)) {
            cir.setReturnValue(false);
        }
    }
}
