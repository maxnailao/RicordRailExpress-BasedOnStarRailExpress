package org.agmas.noellesroles.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.init.ModEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
public class AttackCooldownCancelMixin {

    /**
     * 当玩家拥有 GONGJIJIANGEOFF 效果时，攻击强度始终为 1.0（满值），
     * 从而取消左键攻击间隔。
     */
    @ModifyReturnValue(method = "getAttackStrengthScale", at = @At("RETURN"))
    private float noellesroles$cancelAttackCooldown(float original) {
        Player self = (Player) (Object) this;
        if (self.hasEffect(ModEffects.GONGJIJIANGEOFF)) {
            return 1.0f;
        }
        return original;
    }
}
