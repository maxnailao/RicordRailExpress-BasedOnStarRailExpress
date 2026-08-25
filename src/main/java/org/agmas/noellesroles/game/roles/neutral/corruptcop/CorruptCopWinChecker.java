package org.agmas.noellesroles.game.roles.neutral.corruptcop;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.AllowGameEnd;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.GameUtils.WinStatus;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;

public class CorruptCopWinChecker {

    public static void registerEvent() {
        AllowGameEnd.EVENT.register((serverLevel, winStatus, isLooseEnd) -> {
            if (isLooseEnd) {
                return WinStatus.NOT_MODIFY;
            }

            var gameComponent = SREGameWorldComponent.KEY.get(serverLevel);

            boolean hasCorruptCopAlive = false;
            int alivePlayerCount = 0;

            for (var player : serverLevel.players()) {
                if (!GameUtils.isPlayerAliveAndSurvival(player)) {
                    continue;
                }
                // 坠木/皮革嘎的不计入击杀目标（同亡命徒）：无需击杀即可获胜
                if (gameComponent.isRole(player, ModRoles.ZHUIMU)
                        || gameComponent.isRole(player, ModRoles.PIGE)) {
                    continue;
                }
                alivePlayerCount++;

                if (gameComponent.isRole(player, ModRoles.CORRUPT_COP)) {
                    hasCorruptCopAlive = true;
                }
            }

            if (!hasCorruptCopAlive) {
                return WinStatus.NOT_MODIFY;
            }

            // 黑警独赢：除坠木/皮革嘎的外只剩黑警自己时直接自定义胜利
            if (alivePlayerCount == 1) {
                RoleUtils.customWinnerWin(serverLevel, "corrupt_cop", ModRoles.CORRUPT_COP.color());
                return WinStatus.CUSTOM;
            }

            // 黑警存活时阻止普通结局结束
            return WinStatus.NONE;
        });
    }
}