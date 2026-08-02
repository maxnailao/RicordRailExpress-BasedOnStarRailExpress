package org.agmas.noellesroles.game.modes.werewolf;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.component.ModComponents;

import java.util.*;

/**
 * 狼人杀夜晚行动管理器
 * Author: jiale
 */
public class WerewolfNightManager {

    /**
     * 处理狼方投票结果
     */
    public static void resolveWolfVotes(ServerLevel level, WerewolfGameState state) {
        if (state.wolfVotes.isEmpty()) {
            // 没有投票，随机选择一个目标
            List<UUID> aliveGood = state.getAlivePlayersByFaction(level, WerewolfRoleDef.Faction.GOOD);
            if (!aliveGood.isEmpty()) {
                state.wolfTarget = aliveGood.get(level.random.nextInt(aliveGood.size()));
            }
            return;
        }

        // 统计票数
        Map<UUID, Integer> voteCounts = new HashMap<>();
        for (UUID target : state.wolfVotes.values()) {
            voteCounts.put(target, voteCounts.getOrDefault(target, 0) + 1);
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

        // 平票则随机选择
        if (!topTargets.isEmpty()) {
            state.wolfTarget = topTargets.get(level.random.nextInt(topTargets.size()));
        }
    }

    /**
     * 结算夜晚
     */
    public static void resolveNight(ServerLevel level, WerewolfGameState state) {
        long currentTick = level.getGameTime();
        state.nightDeaths.clear();
        state.poisonDeaths.clear();

        // 1. 处理狼方击杀
        if (state.wolfTarget != null) {
            boolean saved = false;

            // 检查守护者
            if (state.guardianTarget != null && state.guardianTarget.equals(state.wolfTarget)) {
                saved = true; // 被守护，无事发生
            }

            // 检查炼药师解药
            if (!saved && state.alchemistSaveTarget != null && state.alchemistSaveTarget.equals(state.wolfTarget)) {
                saved = true;
                // 标记解药已使用
                ServerPlayer alchemist = findPlayerByRole(level, state, WerewolfRoleDef.ALCHEMIST);
                if (alchemist != null) {
                    ModComponents.WEREWOLF.get(alchemist).usedAntidote = true;
                }
            }

            if (!saved) {
                state.nightDeaths.add(state.wolfTarget);
            }
        }

        // 2. 处理炼药师毒药（独立于狼方击杀）
        if (state.alchemistPoisonTarget != null) {
            state.nightDeaths.add(state.alchemistPoisonTarget);
            state.poisonDeaths.add(state.alchemistPoisonTarget); // 标记为毒杀

            // 标记毒药已使用
            ServerPlayer alchemist = findPlayerByRole(level, state, WerewolfRoleDef.ALCHEMIST);
            if (alchemist != null) {
                ModComponents.WEREWOLF.get(alchemist).usedPoison = true;
            }
        }

        // 3. 处理骑士决斗
        if (state.knightTarget != null) {
            ServerPlayer knight = findPlayerByRole(level, state, WerewolfRoleDef.KNIGHT);
            if (knight != null) {
                boolean targetIsWolf = state.isWolf(level, state.knightTarget);
                if (targetIsWolf) {
                    // 目标是狼方，目标死亡
                    state.nightDeaths.add(state.knightTarget);
                } else {
                    // 目标是好人，骑士死亡
                    state.nightDeaths.add(knight.getUUID());
                }
            }
        }

        // 去重
        state.nightDeaths = new ArrayList<>(new LinkedHashSet<>(state.nightDeaths));

        // 执行淘汰
        for (UUID deathUuid : state.nightDeaths) {
            var player = level.getPlayerByUUID(deathUuid);
            if (player instanceof ServerPlayer deadPlayer) {
                WerewolfPlayerComponent comp = ModComponents.WEREWOLF.get(deadPlayer);
                comp.killedByPoison = state.poisonDeaths.contains(deathUuid);
                WerewolfGameMode.eliminatePlayer(deadPlayer);
            }
        }

        // 检查胜负
        String winner = WerewolfWinChecker.checkWinner(level, state);
        if (winner != null) {
            WerewolfGameMode gameMode = (WerewolfGameMode) io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(level).getGameMode();
            gameMode.endGame(level, state, winner);
            return;
        }

        // 进入白天公示
        state.startPhase(WerewolfPhase.DAY_ANNOUNCE, currentTick);
        WerewolfGameMode.broadcastPhaseStatic(level, state);
        announceDeaths(level, state);
    }

    /**
     * 公示死亡
     */
    private static void announceDeaths(ServerLevel level, WerewolfGameState state) {
        if (state.nightDeaths.isEmpty()) {
            // 平安夜
            for (ServerPlayer player : level.players()) {
                player.displayClientMessage(
                        Component.translatable("werewolf.msg.peaceful_night")
                                .withStyle(ChatFormatting.AQUA),
                        false);
            }
        } else {
            // 公布死亡玩家编号
            StringBuilder deathList = new StringBuilder();
            for (UUID uuid : state.nightDeaths) {
                int seat = state.getSeatNumber(uuid);
                if (seat > 0) {
                    if (deathList.length() > 0) deathList.append(", ");
                    deathList.append(seat).append("号");
                }
            }

            for (ServerPlayer player : level.players()) {
                player.displayClientMessage(
                        Component.translatable("werewolf.msg.death_announce", deathList.toString())
                                .withStyle(ChatFormatting.RED),
                        false);
            }
        }

        // 检查猎人是否需要开枪
        checkHunterShot(level, state);
    }

    /**
     * 检查猎人开枪
     */
    private static void checkHunterShot(ServerLevel level, WerewolfGameState state) {
        long currentTick = level.getGameTime();

        for (UUID deathUuid : state.nightDeaths) {
            // 被毒杀的猎人不能开枪
            if (state.poisonDeaths.contains(deathUuid)) {
                continue;
            }

            var player = level.getPlayerByUUID(deathUuid);
            if (player instanceof ServerPlayer deadPlayer) {
                WerewolfPlayerComponent comp = ModComponents.WEREWOLF.get(deadPlayer);
                if (comp.getRoleDef() == WerewolfRoleDef.HUNTER && !comp.usedDeathShot) {
                    // 猎人可以开枪
                    state.currentActor = deathUuid;
                    state.startPhase(WerewolfPhase.DAY_HUNTER_SHOT, currentTick);
                    WerewolfGameMode.broadcastPhaseStatic(level, state);

                    // 通知猎人（即使已死亡，也允许选择）
                    deadPlayer.displayClientMessage(
                            Component.translatable("werewolf.msg.hunter_shot_available")
                                    .withStyle(ChatFormatting.GOLD),
                            false);
                    return;
                }
            }
        }

        // 没有猎人需要开枪，直接进入发言阶段
        var gameMode = io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(level).getGameMode();
        if (gameMode instanceof WerewolfGameMode wwMode) {
            wwMode.startDaySpeechPublic(level, state);
        }
    }

    /**
     * 处理猎人开枪
     */
    public static void handleHunterShot(ServerLevel level, WerewolfGameState state, UUID targetUuid) {
        if (state.currentActor == null) return;

        var hunterPlayer = level.getPlayerByUUID(state.currentActor);
        if (!(hunterPlayer instanceof ServerPlayer hunter)) return;

        WerewolfPlayerComponent hunterComp = ModComponents.WEREWOLF.get(hunter);
        hunterComp.usedDeathShot = true;
        hunterComp.sync();

        if (targetUuid != null) {
            var targetPlayer = level.getPlayerByUUID(targetUuid);
            if (targetPlayer instanceof ServerPlayer target) {
                // 验证目标是否存活
                WerewolfPlayerComponent targetComp = ModComponents.WEREWOLF.get(target);
                if (!targetComp.alive) {
                    targetUuid = null; // 不能射击已死亡玩家，视为不开枪
                } else {
                    WerewolfGameMode.eliminatePlayer(target);
                    int targetSeat = state.getSeatNumber(targetUuid);

                    for (ServerPlayer player : level.players()) {
                        player.displayClientMessage(
                                Component.translatable("werewolf.msg.hunter_shot_result", targetSeat)
                                        .withStyle(ChatFormatting.GOLD),
                                false);
                    }

                    // 检查胜负
                    String winner = WerewolfWinChecker.checkWinner(level, state);
                    if (winner != null) {
                        WerewolfGameMode gameMode = (WerewolfGameMode) io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(level).getGameMode();
                        gameMode.endGame(level, state, winner);
                        return;
                    }
                }
            }
        }

        // 继续进入发言阶段（委托给 WerewolfGameMode 以正确设置发言者）
        var gameMode = io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(level).getGameMode();
        if (gameMode instanceof WerewolfGameMode wwMode) {
            wwMode.startDaySpeechPublic(level, state);
        }
    }

    /**
     * 根据角色查找玩家
     */
    private static ServerPlayer findPlayerByRole(ServerLevel level, WerewolfGameState state, WerewolfRoleDef roleDef) {
        for (UUID uuid : state.players) {
            var player = level.getPlayerByUUID(uuid);
            if (player instanceof ServerPlayer sp) {
                WerewolfPlayerComponent comp = ModComponents.WEREWOLF.get(sp);
                if (comp.getRoleDef() == roleDef && comp.alive) {
                    return sp;
                }
            }
        }
        return null;
    }
}
