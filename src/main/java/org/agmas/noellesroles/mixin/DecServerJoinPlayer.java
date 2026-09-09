package org.agmas.noellesroles.mixin;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import org.agmas.noellesroles.ConfigWorldComponent;
import org.agmas.noellesroles.utils.MCItemsUtils;
import org.agmas.noellesroles.utils.RoleUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public class DecServerJoinPlayer {
    @Inject(method = "placeNewPlayer", at = @At("TAIL"))
    public void placeNewPlayer(Connection connection, ServerPlayer serverPlayer,
            CommonListenerCookie commonListenerCookie, CallbackInfo ci) {
        if (SRE.isLobby)
            return;
        MCItemsUtils.clearItem(serverPlayer);
        if (!serverPlayer.getActiveEffects().isEmpty()) {
            RoleUtils.removeAllEffects(serverPlayer);
        }
        RoleUtils.removeAllPlayerAttributes(serverPlayer);
        ConfigWorldComponent.KEY.get(serverPlayer.level()).sync();
        // 对局进行中才连进来的玩家（退出重进、或中途加入）标记为已退出，
        // 之后整局禁止使用技能，防止靠重连洗掉技能副作用。下一局 init() 会重置。
        if (GameUtils.isGameRunning(serverPlayer)) {
            SREAbilityPlayerComponent.KEY.get(serverPlayer).setExited(true);
        }
    }

}
