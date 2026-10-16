package org.agmas.noellesroles.game.roles.neutral.priest;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.AllowGameEnd;
import io.wifi.starrailexpress.game.GameUtils.WinStatus;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;

/**
 * 神父序列事件：拦截胜负、每 tick 推进咏诵/加速。
 */
public final class PriestEventHandler {

    private PriestEventHandler() {
    }

    public static void register() {
        // 上游使用 AllowGameEnd.EVENT_START（开局阶段先行判定）；本仓库只有单一 EVENT 数组事件，
        // 语义一致，故改用 AllowGameEnd.EVENT。
        AllowGameEnd.EVENT.register((level, winStatus, looseEnds) -> {
            if (winStatus == WinStatus.NOT_MODIFY) {
                return WinStatus.NOT_MODIFY;
            }
            return PriestHeavenManager.allowGameEnd(level, winStatus);
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ServerLevel level = server.overworld();
            SREGameWorldComponent game = SREGameWorldComponent.KEY.maybeGet(level).orElse(null);
            if (game == null || !game.isRunning()) {
                return;
            }
            if (PriestHeavenManager.isActive()) {
                PriestHeavenManager.tick(level);
            } else if (level.getGameTime() % 20L == 7L) {
                PriestHeavenManager.tryTransform(level);
            }
        });
    }
}
