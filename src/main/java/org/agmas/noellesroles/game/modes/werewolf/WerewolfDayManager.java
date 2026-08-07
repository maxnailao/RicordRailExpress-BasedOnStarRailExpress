package org.agmas.noellesroles.game.modes.werewolf;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.component.ModComponents;

import java.util.*;

/**
 * 狼人杀白天流程管理器
 * Author: jiale
 */
public class WerewolfDayManager {

    /**
     * 处理投票
     */
    public static void handleVote(ServerLevel level, WerewolfGameState state, UUID voterUuid, UUID targetUuid) {
        if (!state.getAlivePlayers(level).contains(voterUuid)) {
            return; // 死亡玩家不能投票
        }
        if (targetUuid != null && !state.getAlivePlayers(level).contains(targetUuid)) {
            return; // 不能投给死亡玩家
        }
        if (targetUuid != null && targetUuid.equals(voterUuid)) {
            return; // 不能自投
        }
        if (state.isPkVote && state.pkPlayers.contains(voterUuid)) {
            return; // PK玩家不能参与PK投票
        }

        state.votes.put(voterUuid, targetUuid);

        // 检查是否所有有投票资格的玩家都已投票（PK轮中PK玩家无投票资格）
        List<UUID> alivePlayers = state.getAlivePlayers(level);
        int eligibleVoters = alivePlayers.size();
        if (state.isPkVote) {
            for (UUID pk : state.pkPlayers) {
                if (alivePlayers.contains(pk)) eligibleVoters--;
            }
        }
        if (state.votes.size() >= eligibleVoters) {
            if (state.isPkVote) {
                resolvePkVote(level, state);
            } else {
                resolveVote(level, state);
            }
        }
    }

    /**
     * 结算投票
     */
    public static void resolveVote(ServerLevel level, WerewolfGameState state) {
        long currentTick = level.getGameTime();

        // 统计票数（排除弃票）
        Map<UUID, Integer> voteCounts = new HashMap<>();
        for (UUID target : state.votes.values()) {
            if (target != null) {
                voteCounts.put(target, voteCounts.getOrDefault(target, 0) + 1);
            }
        }

        if (voteCounts.isEmpty()) {
            // 全部弃票，无人出局
            announceNoExecution(level, state);
            startNextNight(level, state);
            return;
        }

        // 找出最高票
        int maxVotes = 0;
        List<UUID> topTargets = new ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : voteCounts.entrySet()) {
            if (entry.getValue() > maxVotes) {
                maxVotes = entry.getValue();
                topTargets.clear();
                topTargets.add(entry.getKey());
            } else if (entry.getValue() == maxVotes) {
                topTargets.add(entry.getKey());
            }
        }

        if (topTargets.size() == 1) {
            // 唯一最高票，票出
            executePlayer(level, state, topTargets.get(0));
        } else {
            // 平票，进入 PK
            state.pkPlayers = new ArrayList<>(topTargets);
            state.startPhase(WerewolfPhase.DAY_VOTE_PK, currentTick);
            WerewolfGameMode.broadcastPhaseStatic(level, state);

            // 广播 PK 信息
            StringBuilder pkList = new StringBuilder();
            for (UUID uuid : topTargets) {
                int seat = state.getSeatNumber(uuid);
                if (pkList.length() > 0) pkList.append(", ");
                pkList.append(seat).append("号");
            }

            for (ServerPlayer player : level.players()) {
                player.displayClientMessage(
                        Component.translatable("werewolf.msg.vote_tie", pkList.toString())
                                .withStyle(ChatFormatting.YELLOW),
                        false);
            }
        }
    }

    /**
     * 结算 PK 投票
     */
    public static void resolvePkVote(ServerLevel level, WerewolfGameState state) {
        // 统计 PK 玩家的票数
        Map<UUID, Integer> voteCounts = new HashMap<>();
        for (UUID target : state.votes.values()) {
            if (target != null && state.pkPlayers.contains(target)) {
                voteCounts.put(target, voteCounts.getOrDefault(target, 0) + 1);
            }
        }

        if (voteCounts.isEmpty()) {
            announceNoExecution(level, state);
            startNextNight(level, state);
            return;
        }

        // 找出最高票
        int maxVotes = 0;
        List<UUID> topTargets = new ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : voteCounts.entrySet()) {
            if (entry.getValue() > maxVotes) {
                maxVotes = entry.getValue();
                topTargets.clear();
                topTargets.add(entry.getKey());
            } else if (entry.getValue() == maxVotes) {
                topTargets.add(entry.getKey());
            }
        }

        if (topTargets.size() == 1) {
            executePlayer(level, state, topTargets.get(0));
        } else {
            // 仍然平票，无人出局
            announceNoExecution(level, state);
            startNextNight(level, state);
        }
    }

    /**
     * 处决玩家
     */
    private static void executePlayer(ServerLevel level, WerewolfGameState state, UUID targetUuid) {
        long currentTick = level.getGameTime();
        var targetPlayer = level.getPlayerByUUID(targetUuid);
        if (!(targetPlayer instanceof ServerPlayer target)) return;

        int targetSeat = state.getSeatNumber(targetUuid);
        WerewolfPlayerComponent comp = ModComponents.WEREWOLF.get(target);
        WerewolfRoleDef roleDef = comp.getRoleDef();

        state.votedOutPlayer = targetUuid;

        // 广播被票出者
        for (ServerPlayer player : level.players()) {
            player.displayClientMessage(
                    Component.translatable("werewolf.msg.voted_out", targetSeat)
                            .withStyle(ChatFormatting.RED),
                    false);
        }

        // 检查是否是白狼王
        if (roleDef == WerewolfRoleDef.WHITE_WOLF_KING && !comp.usedDeathShot) {
            // 白狼王可以带走一人
            state.currentActor = targetUuid;
            state.startPhase(WerewolfPhase.DAY_EXECUTE, currentTick);
            WerewolfGameMode.broadcastPhaseStatic(level, state);

            target.displayClientMessage(
                    Component.translatable("werewolf.msg.wolf_king_shot_available")
                            .withStyle(ChatFormatting.RED),
                    false);
        } else if (roleDef == WerewolfRoleDef.HUNTER && !comp.usedDeathShot) {
            // 被票出的猎人也能开枪（标准规则）：先淘汰→判胜→开枪
            WerewolfGameMode.eliminatePlayer(target, WerewolfGameMode.DEATH_EXECUTE);

            String hunterWinner = WerewolfWinChecker.checkWinner(level, state);
            if (hunterWinner != null) {
                WerewolfGameMode hunterGm = (WerewolfGameMode) io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(level).getGameMode();
                hunterGm.endGame(level, state, hunterWinner);
                return;
            }

            state.hunterDiedByExecution = true;
            state.currentActor = targetUuid;
            state.startPhase(WerewolfPhase.DAY_HUNTER_SHOT, currentTick);
            WerewolfGameMode.broadcastPhaseStatic(level, state);

            target.displayClientMessage(
                    Component.translatable("werewolf.msg.hunter_shot_available")
                            .withStyle(ChatFormatting.GOLD),
                    false);
        } else {
            // 直接淘汰
            WerewolfGameMode.eliminatePlayer(target, WerewolfGameMode.DEATH_EXECUTE);

            // 检查胜负
            String winner = WerewolfWinChecker.checkWinner(level, state);
            if (winner != null) {
                WerewolfGameMode gameMode = (WerewolfGameMode) io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(level).getGameMode();
                gameMode.endGame(level, state, winner);
                return;
            }

            // 进入遗言阶段
            state.startPhase(WerewolfPhase.DAY_LAST_WORDS, currentTick);
            WerewolfGameMode.broadcastPhaseStatic(level, state);
        }
    }

    /**
     * 处理白狼王带走技能
     */
    public static void handleWolfKingShot(ServerLevel level, WerewolfGameState state, UUID targetUuid) {
        if (state.currentActor == null) return;

        var wolfKingPlayer = level.getPlayerByUUID(state.currentActor);
        if (!(wolfKingPlayer instanceof ServerPlayer wolfKing)) return;

        WerewolfPlayerComponent wolfKingComp = ModComponents.WEREWOLF.get(wolfKing);
        wolfKingComp.usedDeathShot = true;
        wolfKingComp.sync();

        // 淘汰白狼王（本身是被票出）
        WerewolfGameMode.eliminatePlayer(wolfKing, WerewolfGameMode.DEATH_EXECUTE);

        if (targetUuid != null) {
            var targetPlayer = level.getPlayerByUUID(targetUuid);
            if (targetPlayer instanceof ServerPlayer target) {
                // 验证目标是否存活
                WerewolfPlayerComponent targetComp = ModComponents.WEREWOLF.get(target);
                if (targetComp.alive) {
                    WerewolfGameMode.eliminatePlayer(target, WerewolfGameMode.DEATH_WOLF_KING);
                    int targetSeat = state.getSeatNumber(targetUuid);

                    for (ServerPlayer player : level.players()) {
                        player.displayClientMessage(
                                Component.translatable("werewolf.msg.wolf_king_shot_result", targetSeat)
                                        .withStyle(ChatFormatting.RED),
                                false);
                    }
                }
            }
        }

        // 检查胜负
        String winner = WerewolfWinChecker.checkWinner(level, state);
        if (winner != null) {
            WerewolfGameMode gameMode = (WerewolfGameMode) io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(level).getGameMode();
            gameMode.endGame(level, state, winner);
            return;
        }

        // 进入遗言阶段
        long currentTick = level.getGameTime();
        state.startPhase(WerewolfPhase.DAY_LAST_WORDS, currentTick);
        WerewolfGameMode.broadcastPhaseStatic(level, state);
    }

    /**
     * 宣布无人出局
     */
    private static void announceNoExecution(ServerLevel level, WerewolfGameState state) {
        for (ServerPlayer player : level.players()) {
            player.displayClientMessage(
                    Component.translatable("werewolf.msg.no_execution")
                            .withStyle(ChatFormatting.GRAY),
                    false);
        }
    }

    /**
     * 开始下一夜晚（委托给 WerewolfGameMode.startNight 以跳过不存在的角色）
     */
    private static void startNextNight(ServerLevel level, WerewolfGameState state) {
        var gameMode = io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(level).getGameMode();
        if (gameMode instanceof org.agmas.noellesroles.game.modes.werewolf.WerewolfGameMode wwMode) {
            wwMode.startNight(level, state);
        }
    }
}
