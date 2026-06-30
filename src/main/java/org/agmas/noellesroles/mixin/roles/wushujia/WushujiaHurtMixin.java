package org.agmas.noellesroles.mixin.roles.wushujia;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.game.roles.innocence.role.ModRoles;
import org.agmas.noellesroles.game.roles.innocence.wushujia.WushujiaPunchHandler;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 注入到 LivingEntity.hurt() 的 RETURN 点，
 * 仅在伤害实际造成（hurt 返回 true）后，
 * 检查攻击者是否为心流模式下的武术家，若是则追踪连击并施加效果。
 */
@Mixin(LivingEntity.class)
public abstract class WushujiaHurtMixin {

    @Inject(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At("RETURN"))
    private void wushujia$onHurtReturn(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        // 仅在伤害实际造成时处理
        if (!cir.getReturnValue()) return;

        LivingEntity self = (LivingEntity) (Object) this;
        // 仅服务端处理
        if (self.level().isClientSide) return;
        // 仅处理玩家受到的伤害
        if (!(self instanceof Player victim)) return;
        // 伤害来源必须是玩家
        if (!(source.getEntity() instanceof Player attacker)) return;
        // 排除自伤
        if (attacker.getUUID().equals(victim.getUUID())) return;
        // 攻击者必须是武术家
        if (!SREGameWorldComponent.KEY.get(attacker.level()).isRole(attacker, ModRoles.WUSHUJIA)) return;
        // 必须空手（武术家被动是空手击打）
        if (!attacker.getMainHandItem().isEmpty()) return;

        // 交由武术家处理器处理连击和效果
        WushujiaPunchHandler.onFlowHitConfirmed(attacker, victim);
    }
}
