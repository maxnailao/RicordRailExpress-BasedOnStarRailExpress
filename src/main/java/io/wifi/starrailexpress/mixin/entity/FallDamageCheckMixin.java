package io.wifi.starrailexpress.mixin.entity;

import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class FallDamageCheckMixin {
    @Inject(method = "checkFallDamage", at = @At("HEAD"), cancellable = true)
    public void checkFallDamage(double y, boolean onGround, BlockState blockState, BlockPos blockPos, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (onGround) {
            // 落地了
            if (self instanceof ServerPlayer player) {
                // 是玩家（服务端检测）
                if (player.isSpectator() || player.isCreative())
                    return;
                var cca = AreasWorldComponent.KEY.get(player.level());
                if (cca.areasSettings.fallToDeathHeight > 0) {
                    if (self.fallDistance >= cca.areasSettings.fallToDeathHeight) {
                        // 摔到死亡地点也要归属最近攻击者（如铁傀儡击飞），
                        // 否则击杀者为 null 就不会触发小脑惩罚
                        Player lastAttacker = player.getLastAttacker() instanceof Player lp ? lp : null;
                        GameUtils.killPlayer(player, true, lastAttacker, GameConstants.DeathReasons.FALL_DAMAGE);
                    }
                    self.resetFallDistance();
                    ci.cancel();
                }
            }
        }
    }
}
