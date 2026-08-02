package org.agmas.noellesroles.game.modes.werewolf;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.component.ModComponents;

import java.util.List;
import java.util.UUID;

/**
 * 狼人杀胜负判定
 * Author: jiale
 */
public class WerewolfWinChecker {

    /**
     * 检查是否有胜利方
     * @return "good" 好人胜, "wolf" 狼方胜, null 游戏继续
     */
    public static String checkWinner(ServerLevel level, WerewolfGameState state) {
        List<UUID> aliveGood = state.getAlivePlayersByFaction(level, WerewolfRoleDef.Faction.GOOD);
        List<UUID> aliveWolf = state.getAlivePlayersByFaction(level, WerewolfRoleDef.Faction.WOLF);

        // 好人胜：所有狼方死亡
        if (aliveWolf.isEmpty()) {
            return "good";
        }

        // 狼方胜条件1：所有平民（无辜者）死亡
        boolean allInnocentDead = true;
        for (UUID uuid : state.players) {
            if (level.getPlayerByUUID(uuid) instanceof ServerPlayer player) {
                WerewolfPlayerComponent comp = ModComponents.WEREWOLF.get(player);
                if (comp.alive && comp.getRoleDef() == WerewolfRoleDef.INNOCENT) {
                    allInnocentDead = false;
                    break;
                }
            }
        }
        if (allInnocentDead) {
            return "wolf";
        }

        // 狼方胜条件2：所有神职（非平民的好人）死亡
        boolean allGodDead = true;
        for (UUID uuid : state.players) {
            if (level.getPlayerByUUID(uuid) instanceof ServerPlayer player) {
                WerewolfPlayerComponent comp = ModComponents.WEREWOLF.get(player);
                if (comp.alive && comp.getRoleDef().isGood() && comp.getRoleDef().isGod) {
                    allGodDead = false;
                    break;
                }
            }
        }
        if (allGodDead) {
            return "wolf";
        }

        // 游戏继续
        return null;
    }
}
