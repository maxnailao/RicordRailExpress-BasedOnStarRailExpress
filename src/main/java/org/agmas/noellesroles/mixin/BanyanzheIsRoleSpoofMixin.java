package org.agmas.noellesroles.mixin;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.game.roles.killer.banyanzhe.BanyanzhePlayerComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 扮演者技能转发 Mixin
 *
 * 扮演者可以使用其扮演职业的技能。技能链路上的 isRole 检查在伪装上下文激活期间
 * （服务端技能包处理 / 客户端 G 键派发）将伪装中的扮演者视为其扮演职业。
 * 伪装上下文之外（胜利条件、击杀奖励等）完全不受影响。
 */
@Mixin(SREGameWorldComponent.class)
public class BanyanzheIsRoleSpoofMixin {

    @Inject(method = "isRole(Lnet/minecraft/world/entity/player/Player;Lio/wifi/starrailexpress/api/SRERole;)Z", at = @At("HEAD"), cancellable = true)
    private void banyanzhe$skillDispatchSpoof(Player player, SRERole role, CallbackInfoReturnable<Boolean> cir) {
        if (BanyanzhePlayerComponent.isServerSpoofedRole(player, role)
                || BanyanzhePlayerComponent.isClientSpoofedRole(player, role)) {
            cir.setReturnValue(true);
        }
    }
}
