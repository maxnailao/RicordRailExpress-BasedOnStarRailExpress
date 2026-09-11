package org.agmas.noellesroles.game.roles.neutral.convict;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.AllowGameEnd;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.GameUtils.WinStatus;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;

/**
 * 重刑犯「毁灭一切」独立胜利检测（阶段 5）。
 *
 * <p>复刻黑警（{@code CorruptCopWinChecker}）的「击杀所有人」独赢逻辑，但仅对
 * 抉择为 {@link ConvictPlayerComponent.Choice#DESTROY 毁灭一切} 的重刑犯生效：</p>
 * <ul>
 *   <li>当场上（除坠木/皮革嘎外）只剩该重刑犯一人存活时 → 自定义胜利，播报
 *       {@code announcement.star.win.convict} / {@code game.win.star.convict}；</li>
 *   <li>该重刑犯存活期间阻止普通结局，迫使其他玩家必须先解决掉他。</li>
 * </ul>
 *
 * <p>改过自新 / 加入组织分支不会触发独赢：他们会在解铐后转职（不再是重刑犯），
 * 或未满足毁灭一切条件，此检测对其返回 {@link WinStatus#NOT_MODIFY}。</p>
 */
public class ConvictWinChecker {

    public static void registerEvent() {
        AllowGameEnd.EVENT.register((serverLevel, winStatus, isLooseEnd) -> {
            if (isLooseEnd) {
                return WinStatus.NOT_MODIFY;
            }

            var gameComponent = SREGameWorldComponent.KEY.get(serverLevel);

            boolean hasDestroyConvictAlive = false;
            int alivePlayerCount = 0;

            for (var player : serverLevel.players()) {
                if (!GameUtils.isPlayerAliveAndSurvival(player)) {
                    continue;
                }
                // 坠木/皮革嘎的不计入击杀目标（同黑警/亡命徒）：无需击杀即可获胜
                if (gameComponent.isRole(player, ModRoles.ZHUIMU)
                        || gameComponent.isRole(player, ModRoles.PIGE)) {
                    continue;
                }
                alivePlayerCount++;

                if (gameComponent.isRole(player, ModRoles.CONVICT)) {
                    var comp = ConvictPlayerComponent.KEY.maybeGet(player).orElse(null);
                    if (comp != null && comp.choice == ConvictPlayerComponent.Choice.DESTROY) {
                        hasDestroyConvictAlive = true;
                    }
                }
            }

            // 没有存活的「毁灭一切」重刑犯 → 不干预正常结局
            if (!hasDestroyConvictAlive) {
                return WinStatus.NOT_MODIFY;
            }

            // 毁灭一切独赢：除坠木/皮革嘎外只剩重刑犯自己时直接自定义胜利
            if (alivePlayerCount == 1) {
                RoleUtils.customWinnerWin(serverLevel, "convict", ModRoles.CONVICT.color());
                return WinStatus.CUSTOM;
            }

            // 毁灭一切重刑犯存活时阻止普通结局结束
            return WinStatus.NONE;
        });
    }
}
