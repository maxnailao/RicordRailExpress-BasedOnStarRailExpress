package org.agmas.noellesroles.cs2;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREWorldBlackoutComponent;
import io.wifi.starrailexpress.event.OnGameStarted;
import io.wifi.starrailexpress.event.OnPlayerKilledPlayer;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.Noellesroles;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MVP 积分制管理器
 * <p>
 * 在游戏过程中根据击杀行为、停电事件和存活状况累计积分，
 * 游戏结束后根据积分确定 MVP。
 * </p>
 *
 * <h3>积分规则</h3>
 * <b>平民/警长阵营（isInnocent）：</b>
 * <ul>
 *   <li>击杀狼方玩家（杀手阵营）：+20</li>
 *   <li>击杀中立玩家：+10</li>
 *   <li>停电期间击杀（非平民阵营目标）：+10（额外奖励）</li>
 *   <li>击杀平民阵营：-25</li>
 *   <li>存活到最后（仅当获胜条件为 TIME）：+100</li>
 * </ul>
 *
 * <b>杀手阵营（isKillerTeam，含杀手方中立）：</b>
 * <ul>
 *   <li>击杀平民：+20</li>
 *   <li>关灯期间队友击杀平民：触发关灯的玩家 +10（自己击杀不算）</li>
 * </ul>
 */
public class CS2MvpScoreManager {

    /** 每局游戏的积分记录：playerUUID -> score */
    private static final Map<UUID, Integer> scores = new HashMap<>();
    /** 防止存活奖励重复累加（getMvp 可能被多次调用） */
    private static final AtomicBoolean survivalBonusApplied = new AtomicBoolean(false);

    private CS2MvpScoreManager() {}

    /**
     * 注册所有事件监听器
     */
    public static void register() {
        // 游戏开始：重置积分
        OnGameStarted.EVENT.register(CS2MvpScoreManager::onGameStart);

        // 击杀事件：计算积分
        OnPlayerKilledPlayer.EVENT.register(CS2MvpScoreManager::onPlayerKilled);
    }

    /**
     * 游戏开始时重置所有积分
     */
    private static void onGameStart(ServerLevel world) {
        scores.clear();
        survivalBonusApplied.set(false);
        Noellesroles.LOGGER.info("[MvpScore] 积分已重置");
    }

    /**
     * 处理击杀事件的积分变化
     */
    private static void onPlayerKilled(ServerPlayer victim, ServerPlayer killer,
                                       OnPlayerKilledPlayer.DeathReason reason) {
        if (killer == null || victim == null) return;
        if (killer.level().isClientSide) return;

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(killer.level());
        if (gameWorld == null || !gameWorld.isRunning()) return;

        SRERole killerRole = gameWorld.getRole(killer);
        SRERole victimRole = gameWorld.getRole(victim);
        if (killerRole == null || victimRole == null) return;

        // 自杀不计分
        if (killer.getUUID().equals(victim.getUUID())) return;

        boolean isBlackout = isBlackoutActive(killer.level());
        UUID blackoutTrigger = getBlackoutTrigger(killer.level());

        // ── 平民/警长阵营 ──
        if (killerRole.isInnocent()) {
            if (victimRole.isKillerTeam()) {
                // 击杀狼方玩家 +20
                addScore(killer.getUUID(), 20);
            } else if (victimRole.isNeutrals() && !victimRole.isNeutralForKiller()) {
                // 击杀中立玩家 +10
                addScore(killer.getUUID(), 10);
            } else if (victimRole.isInnocent()) {
                // 击杀平民阵营 -25
                addScore(killer.getUUID(), -25);
            }

            // 停电期间击杀非平民阵营 +10（额外奖励）
            if (isBlackout && !victimRole.isInnocent()) {
                addScore(killer.getUUID(), 10);
            }
        }

        // ── 杀手阵营（含杀手方中立） ──
        if (killerRole.isKillerTeam()) {
            if (victimRole.isInnocent()) {
                // 击杀平民 +20
                addScore(killer.getUUID(), 20);

                // 关灯期间队友击杀平民：触发关灯的玩家 +10（自己击杀不算）
                if (isBlackout && blackoutTrigger != null
                        && !blackoutTrigger.equals(killer.getUUID())) {
                    addScore(blackoutTrigger, 10);
                }
            }
        }
    }

    /**
     * 获取当前 MVP 玩家（胜利方中积分最高者）
     * <p>
     * 调用时自动计算存活奖励（仅 TIME 胜利条件），确保积分完整。
     * </p>
     */
    public static UUID getMvp(ServerLevel world, SREGameWorldComponent gameComponent) {
        SREGameRoundEndComponent roundEnd = SREGameRoundEndComponent.KEY.get(world);
        GameUtils.WinStatus winStatus = roundEnd.getWinStatus();

        // 存活到最后（仅当 TIME 胜利条件时给予 +100，只加一次）
        if (winStatus == GameUtils.WinStatus.TIME && survivalBonusApplied.compareAndSet(false, true)) {
            for (ServerPlayer player : world.players()) {
                if (!GameUtils.isPlayerEliminated(player)) {
                    addScore(player.getUUID(), 100);
                }
            }
        }

        // 收集胜利方玩家 UUID
        Set<UUID> winnerUuids = new LinkedHashSet<>();

        // 1. 优先使用 CustomWinnerPlayers
        if (roundEnd.CustomWinnerPlayers != null && !roundEnd.CustomWinnerPlayers.isEmpty()) {
            winnerUuids.addAll(roundEnd.CustomWinnerPlayers);
        }

        // 2. 回退：根据 winStatus 和角色判定
        if (winnerUuids.isEmpty()) {
            for (ServerPlayer player : world.players()) {
                var role = gameComponent.getRole(player);
                if (role == null) continue;
                boolean isWinner = false;
                switch (winStatus) {
                    case PASSENGERS:
                    case TIME:
                        isWinner = role.isInnocent();
                        break;
                    case KILLERS:
                        // 仅杀手团队成员（canUseKiller 或 neutralForKiller），与音乐盒判定一致
                        isWinner = SREGameWorldComponent.isKillerTeamRoleStatic(role) && !role.isInnocent();
                        break;
                    case LOOSE_END:
                        isWinner = player.getUUID().equals(gameComponent.getLooseEndWinner());
                        break;
                    case CUSTOM:
                    case CUSTOM_COMPONENT:
                        isWinner = roundEnd.CustomWinnerID != null
                                && roundEnd.CustomWinnerID.equals(role.identifier().getPath());
                        break;
                    default:
                        break;
                }
                if (isWinner) {
                    winnerUuids.add(player.getUUID());
                }
            }
        }

        // 3. 最终回退：所有玩家
        if (winnerUuids.isEmpty()) {
            for (ServerPlayer player : world.players()) {
                winnerUuids.add(player.getUUID());
            }
        }

        if (winnerUuids.isEmpty()) return null;

        // 在胜利方中找积分最高的
        UUID topScorerUuid = null;
        int topScore = -1;
        for (UUID uuid : winnerUuids) {
            int score = scores.getOrDefault(uuid, 0);
            if (score > topScore) {
                topScore = score;
                topScorerUuid = uuid;
            }
        }

        return topScorerUuid;
    }

    // ── 辅助方法 ──

    /**
     * 获取指定玩家的当前积分
     */
    public static int getScore(UUID playerUuid) {
        return scores.getOrDefault(playerUuid, 0);
    }

    private static void addScore(UUID playerUuid, int delta) {
        scores.merge(playerUuid, delta, Integer::sum);
    }

    private static boolean isBlackoutActive(Level world) {
        var comp = SREWorldBlackoutComponent.KEY.maybeGet(world).orElse(null);
        return comp != null && comp.isBlackoutActive();
    }

    private static UUID getBlackoutTrigger(Level world) {
        var comp = SREWorldBlackoutComponent.KEY.maybeGet(world).orElse(null);
        return comp != null ? comp.lastBlackoutTriggeredBy : null;
    }
}
