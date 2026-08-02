package org.agmas.noellesroles.game.modes.werewolf;

import io.wifi.starrailexpress.api.GameMode;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import org.agmas.noellesroles.component.ModComponents;

import java.util.*;

/**
 * 狼人杀游戏模式主类
 * 完全脱离主玩法，不使用商店、尸体、安全时间等系统
 * Author: jiale
 */
public class WerewolfGameMode extends GameMode {

    public WerewolfGameMode(ResourceLocation identifier) {
        super(identifier, 0, 6); // 无全局计时，最少6人
    }

    // === 框架重写：脱离主玩法 ===

    @Override
    public boolean shouldRecordPlayerStats() {
        return false;
    }

    @Override
    public boolean hasSafeTime() {
        return false;
    }

    @Override
    public boolean hasMood() {
        return false;
    }

    @Override
    public boolean enforcesPlayAreaElimination() {
        return false;
    }

    @Override
    public boolean autoTriggerGameTrueStarted() {
        return false;
    }

    @Override
    public boolean requiresAssignedRole() {
        return true;
    }

    // === 生命周期方法 ===

    @Override
    public void beforeInitializeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        // 不调用 baseInitialize()！仅做最小设置
        gameWorldComponent.setPlayerCount(players.size());

        // 游戏规则设置
        serverWorld.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, serverWorld.getServer());
        serverWorld.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, serverWorld.getServer());
        serverWorld.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false, serverWorld.getServer());
        serverWorld.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, serverWorld.getServer());
        serverWorld.getGameRules().getRule(GameRules.RULE_ANNOUNCE_ADVANCEMENTS).set(false, serverWorld.getServer());
        serverWorld.getGameRules().getRule(GameRules.RULE_DO_TRADER_SPAWNING).set(false, serverWorld.getServer());
        serverWorld.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, serverWorld.getServer());

        // 设置为夜晚（狼人杀氛围）
        serverWorld.setDayTime(18000);
        serverWorld.getServer().setDifficulty(net.minecraft.world.Difficulty.PEACEFUL, true);

        // 清空玩家背包和冷却
        for (ServerPlayer player : players) {
            player.getInventory().clearContent();
            player.removeVehicle();
            player.setGameMode(GameType.ADVENTURE);
            // 清除物品冷却
            HashSet<net.minecraft.world.item.Item> copy = new HashSet<>(player.getCooldowns().cooldowns.keySet());
            for (net.minecraft.world.item.Item item : copy) {
                player.getCooldowns().removeCooldown(item);
            }
            // 初始化狼人杀组件
            ModComponents.WEREWOLF.get(player).init();
        }

        // 清除角色映射
        gameWorldComponent.clearRoleMap(true);
    }

    @Override
    public void initializeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        // 确保空壳角色已初始化
        WerewolfRoles.init();

        // 获取游戏状态
        WerewolfGameState state = WerewolfGameState.get(serverWorld);
        state.reset();
        state.active = true;

        // 打乱玩家顺序
        List<ServerPlayer> shuffled = new ArrayList<>(players);
        Collections.shuffle(shuffled);

        // 获取角色配置
        List<WerewolfRoleDef> roleConfig = WerewolfRoleDef.getRoleConfig(shuffled.size());
        Collections.shuffle(roleConfig);

        // 分配角色和编号
        for (int i = 0; i < shuffled.size(); i++) {
            ServerPlayer player = shuffled.get(i);
            WerewolfRoleDef roleDef = roleConfig.get(i);
            int seatNumber = i + 1;

            // 设置框架角色（空壳）
            gameWorldComponent.addRole(player, roleDef.isWolf() ? WerewolfRoles.WOLF : WerewolfRoles.GOOD, false);

            // 设置狼人杀组件
            WerewolfPlayerComponent comp = ModComponents.WEREWOLF.get(player);
            comp.init();
            comp.seatNumber = seatNumber;
            comp.roleId = roleDef.id;
            comp.alive = true;
            comp.sync();

            // 记录到状态
            state.players.add(player.getUUID());
            state.seatToPlayer.put(seatNumber, player.getUUID());
            state.playerToSeat.put(player.getUUID(), seatNumber);

            // 发送角色通知
            player.displayClientMessage(
                    Component.translatable("werewolf.msg.role_assigned",
                            Component.translatable(roleDef.getTranslationKey()).withStyle(ChatFormatting.GOLD),
                            seatNumber)
                            .withStyle(ChatFormatting.GREEN),
                    false);

            // 如果是狼方，显示队友
            if (roleDef.isWolf()) {
                List<String> wolfTeammates = new ArrayList<>();
                for (int j = 0; j < shuffled.size(); j++) {
                    if (i != j && roleConfig.get(j).isWolf()) {
                        wolfTeammates.add(shuffled.get(j).getGameProfile().getName() + " (" + (j + 1) + "号)");
                    }
                }
                if (!wolfTeammates.isEmpty()) {
                    player.displayClientMessage(
                            Component.translatable("werewolf.msg.wolf_teammates", String.join(", ", wolfTeammates))
                                    .withStyle(ChatFormatting.RED),
                            false);
                }
            }
        }

        // 同步角色
        gameWorldComponent.syncRoles();

        // 广播游戏开始
        for (ServerPlayer player : serverWorld.players()) {
            player.displayClientMessage(
                    Component.translatable("werewolf.msg.game_started", shuffled.size())
                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                    false);
        }

        // 开始第一个夜晚（使用 startNight 以跳过不存在的角色）
        startNight(serverWorld, state);
    }

    @Override
    public void afterInitializeGame(ServerLevel serverWorld, SREGameWorldComponent gameComponent,
            ArrayList<ServerPlayer> readyPlayerList) {
        // 不调用 super（跳过回放系统初始化）
    }

    @Override
    public void gameStarted(ServerLevel serverWorld, SREGameWorldComponent gameComponent,
            ArrayList<ServerPlayer> readyPlayerList) {
        // 不调用 super（跳过安全时间冷却）
    }

    @Override
    public void tickServerGameLoop(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        WerewolfGameState state = WerewolfGameState.get(serverWorld);
        if (!state.active) {
            return;
        }

        long currentTick = serverWorld.getGameTime();

        // 检查阶段超时
        if (state.isPhaseTimeout(currentTick)) {
            handlePhaseTimeout(serverWorld, state);
        }

        // 处理当前阶段逻辑
        switch (state.phase) {
            case NIGHT_GUARDIAN, NIGHT_ALCHEMIST, NIGHT_PROPHET, NIGHT_KNIGHT -> {
                // 等待玩家操作（通过 C2S 包处理）
            }
            case NIGHT_WOLVES -> {
                // 狼方讨论阶段，等待投票
            }
            case NIGHT_RESOLVE -> {
                // 夜晚结算（瞬时）- 只执行一次，resolveNight 会改变阶段
                if (!state.nightResolved) {
                    state.nightResolved = true;
                    WerewolfNightManager.resolveNight(serverWorld, state);
                }
            }
            case DAY_ANNOUNCE -> {
                // 死亡公示阶段
            }
            case DAY_HUNTER_SHOT -> {
                // 猎人开枪阶段
            }
            case DAY_SPEECH -> {
                // 发言阶段（超时自动切换）
            }
            case DAY_VOTE, DAY_VOTE_PK_RESULT -> {
                // 投票阶段
            }
            case DAY_EXECUTE -> {
                // 处决阶段
            }
            case DAY_LAST_WORDS -> {
                // 遗言阶段
            }
            case GAME_OVER -> {
                // 游戏结束
            }
        }

        // 座位锁定：每 40 tick 检查位置
        if (currentTick % 40 == 0) {
            enforceSeatPositions(serverWorld, state);
        }
    }

    /**
     * 处理阶段超时
     */
    private void handlePhaseTimeout(ServerLevel serverWorld, WerewolfGameState state) {
        long currentTick = serverWorld.getGameTime();

        switch (state.phase) {
            case NIGHT_GUARDIAN -> {
                // 守护者超时，跳过
                state.guardianTarget = null;
                advanceNightPhase(serverWorld, state);
            }
            case NIGHT_WOLVES -> {
                // 狼方超时，随机选择目标或无人被杀
                WerewolfNightManager.resolveWolfVotes(serverWorld, state);
                advanceNightPhase(serverWorld, state);
            }
            case NIGHT_ALCHEMIST -> {
                // 炼药师超时，跳过
                state.alchemistPoisonTarget = null;
                state.alchemistSaveTarget = null;
                advanceNightPhase(serverWorld, state);
            }
            case NIGHT_PROPHET -> {
                // 预言家超时，跳过
                state.prophetTarget = null;
                advanceNightPhase(serverWorld, state);
            }
            case NIGHT_KNIGHT -> {
                // 骑士超时，跳过
                state.knightTarget = null;
                advanceNightPhase(serverWorld, state);
            }
            case DAY_ANNOUNCE -> {
                // 公示结束，进入发言
                startDaySpeech(serverWorld, state);
            }
            case DAY_HUNTER_SHOT -> {
                // 猎人超时，不开枪
                proceedAfterHunterShot(serverWorld, state);
            }
            case DAY_SPEECH -> {
                // 发言超时，切换下一位
                advanceSpeech(serverWorld, state);
            }
            case DAY_VOTE -> {
                // 投票超时，统计结果
                WerewolfDayManager.resolveVote(serverWorld, state);
            }
            case DAY_VOTE_PK -> {
                // PK发言超时，进入PK投票
                state.startPhase(WerewolfPhase.DAY_VOTE_PK_RESULT, currentTick);
                broadcastPhase(serverWorld, state);
            }
            case DAY_VOTE_PK_RESULT -> {
                // PK投票超时，统计结果
                WerewolfDayManager.resolvePkVote(serverWorld, state);
            }
            case DAY_EXECUTE -> {
                // 处决超时：白狼王未行动，强制淘汰白狼王后进入遗言
                if (state.currentActor != null) {
                    var wolfKingPlayer = serverWorld.getPlayerByUUID(state.currentActor);
                    if (wolfKingPlayer instanceof ServerPlayer wolfKing) {
                        WerewolfGameMode.eliminatePlayer(wolfKing);
                    }
                    // 检查胜负
                    String winner = WerewolfWinChecker.checkWinner(serverWorld, state);
                    if (winner != null) {
                        endGame(serverWorld, state, winner);
                        return;
                    }
                }
                startLastWords(serverWorld, state);
            }
            case DAY_LAST_WORDS -> {
                // 遗言超时，进入下一夜晚
                startNight(serverWorld, state);
            }
            default -> {}
        }
    }

    /**
     * 推进夜晚阶段（跳过不存在的角色）
     */
    private void advanceNightPhase(ServerLevel serverWorld, WerewolfGameState state) {
        long currentTick = serverWorld.getGameTime();
        WerewolfPhase nextPhase = state.phase.nextNightPhase();
        
        // 跳过不存在的角色阶段
        while (nextPhase != WerewolfPhase.NIGHT_RESOLVE && !roleExistsForPhase(serverWorld, state, nextPhase)) {
            nextPhase = nextPhase.nextNightPhase();
        }
        
        state.startPhase(nextPhase, currentTick);
        broadcastPhase(serverWorld, state);

        // 通知当前行动者
        if (nextPhase != WerewolfPhase.NIGHT_RESOLVE) {
            notifyCurrentActor(serverWorld, state);
        }
    }

    /**
     * 推进夜晚阶段（公开方法，供网络包调用）
     */
    public void advanceNightPhasePublic(ServerLevel serverWorld, WerewolfGameState state) {
        advanceNightPhase(serverWorld, state);
    }

    /**
     * 检查某个夜晚阶段对应的角色是否存在
     */
    private boolean roleExistsForPhase(ServerLevel serverWorld, WerewolfGameState state, WerewolfPhase phase) {
        WerewolfRoleDef requiredRole = switch (phase) {
            case NIGHT_GUARDIAN -> WerewolfRoleDef.GUARDIAN;
            case NIGHT_WOLVES -> null; // 狼方总是存在
            case NIGHT_ALCHEMIST -> WerewolfRoleDef.ALCHEMIST;
            case NIGHT_PROPHET -> WerewolfRoleDef.PROPHET;
            case NIGHT_KNIGHT -> WerewolfRoleDef.KNIGHT;
            default -> null;
        };
        
        if (requiredRole == null) {
            return true; // 狼方阶段总是存在
        }
        
        // 检查是否有存活的角色
        return !state.getAlivePlayersByRole(serverWorld, requiredRole).isEmpty();
    }

    /**
     * 开始夜晚（跳过不存在的角色）
     */
    public void startNight(ServerLevel serverWorld, WerewolfGameState state) {
        long currentTick = serverWorld.getGameTime();
        
        // 广播夜晚开始
        for (ServerPlayer player : serverWorld.players()) {
            player.displayClientMessage(
                    Component.translatable("werewolf.msg.night_falls", state.round + 1)
                            .withStyle(ChatFormatting.DARK_BLUE),
                    false);
        }
        
        // 找到第一个存在的夜晚阶段
        WerewolfPhase firstPhase = WerewolfPhase.NIGHT_GUARDIAN;
        while (firstPhase != WerewolfPhase.NIGHT_RESOLVE && !roleExistsForPhase(serverWorld, state, firstPhase)) {
            firstPhase = firstPhase.nextNightPhase();
        }
        
        state.startPhase(firstPhase, currentTick);
        broadcastPhase(serverWorld, state);
        
        if (firstPhase != WerewolfPhase.NIGHT_RESOLVE) {
            notifyCurrentActor(serverWorld, state);
        }
    }

    /**
     * 开始白天发言
     */
    private void startDaySpeech(ServerLevel serverWorld, WerewolfGameState state) {
        startDaySpeechPublic(serverWorld, state);
    }

    /**
     * 开始白天发言（公开方法，供 NightManager 调用）
     */
    public void startDaySpeechPublic(ServerLevel serverWorld, WerewolfGameState state) {
        long currentTick = serverWorld.getGameTime();
        List<UUID> alivePlayers = state.getAlivePlayers(serverWorld);

        // 找到第一个存活的发言者
        int firstSeat = 0;
        for (int seat = 1; seat <= state.players.size(); seat++) {
            UUID uuid = state.getPlayerBySeat(seat);
            if (uuid != null && alivePlayers.contains(uuid)) {
                firstSeat = seat;
                state.currentActor = uuid;
                break;
            }
        }

        if (firstSeat == 0) {
            // 没有存活玩家，直接进入投票
            state.startPhase(WerewolfPhase.DAY_VOTE, currentTick);
            broadcastPhase(serverWorld, state);
            return;
        }

        state.speechSeatIndex = firstSeat;
        state.startPhase(WerewolfPhase.DAY_SPEECH, currentTick);
        broadcastPhase(serverWorld, state);

        // 广播白天开始 + 当前发言者
        for (ServerPlayer player : serverWorld.players()) {
            player.displayClientMessage(
                    Component.translatable("werewolf.msg.day_breaks", state.round)
                            .withStyle(ChatFormatting.YELLOW),
                    false);
            player.displayClientMessage(
                    Component.translatable("werewolf.msg.speech_turn", firstSeat)
                            .withStyle(ChatFormatting.AQUA),
                    false);
        }
    }

    /**
     * 推进发言
     */
    private void advanceSpeech(ServerLevel serverWorld, WerewolfGameState state) {
        long currentTick = serverWorld.getGameTime();
        List<UUID> alivePlayers = state.getAlivePlayers(serverWorld);

        // 找到下一个存活的发言者
        int nextSeat = state.speechSeatIndex + 1;
        while (nextSeat <= state.players.size()) {
            UUID playerUuid = state.getPlayerBySeat(nextSeat);
            if (playerUuid != null && alivePlayers.contains(playerUuid)) {
                state.speechSeatIndex = nextSeat;
                state.currentActor = playerUuid;
                state.startPhase(WerewolfPhase.DAY_SPEECH, currentTick);
                broadcastPhase(serverWorld, state);

                // 广播当前发言者
                for (ServerPlayer player : serverWorld.players()) {
                    player.displayClientMessage(
                            Component.translatable("werewolf.msg.speech_turn", nextSeat)
                                    .withStyle(ChatFormatting.AQUA),
                            false);
                }
                return;
            }
            nextSeat++;
        }

        // 所有存活玩家都发言完毕，进入投票
        state.currentActor = null;
        state.startPhase(WerewolfPhase.DAY_VOTE, currentTick);
        broadcastPhase(serverWorld, state);

        for (ServerPlayer player : serverWorld.players()) {
            player.displayClientMessage(
                    Component.translatable("werewolf.msg.vote_started")
                            .withStyle(ChatFormatting.GOLD),
                    false);
        }
    }

    /**
     * 猎人开枪后继续
     */
    private void proceedAfterHunterShot(ServerLevel serverWorld, WerewolfGameState state) {
        startDaySpeech(serverWorld, state);
    }

    /**
     * 开始遗言阶段
     */
    private void startLastWords(ServerLevel serverWorld, WerewolfGameState state) {
        long currentTick = serverWorld.getGameTime();
        state.startPhase(WerewolfPhase.DAY_LAST_WORDS, currentTick);
        broadcastPhase(serverWorld, state);
    }

    /**
     * 通知当前行动者
     */
    private void notifyCurrentActor(ServerLevel serverWorld, WerewolfGameState state) {
        WerewolfRoleDef targetRole = switch (state.phase) {
            case NIGHT_GUARDIAN -> WerewolfRoleDef.GUARDIAN;
            case NIGHT_ALCHEMIST -> WerewolfRoleDef.ALCHEMIST;
            case NIGHT_PROPHET -> WerewolfRoleDef.PROPHET;
            case NIGHT_KNIGHT -> WerewolfRoleDef.KNIGHT;
            default -> null;
        };

        if (targetRole != null) {
            List<UUID> actors = state.getAlivePlayersByRole(serverWorld, targetRole);
            if (!actors.isEmpty()) {
                state.currentActor = actors.get(0);
                var actorPlayer = serverWorld.getPlayerByUUID(state.currentActor);
                if (actorPlayer instanceof ServerPlayer actor) {
                    actor.displayClientMessage(
                            Component.translatable("werewolf.msg.your_turn")
                                    .withStyle(ChatFormatting.GREEN),
                            true);
                }
            }
        } else if (state.phase == WerewolfPhase.NIGHT_WOLVES) {
            // 通知所有狼人
            List<UUID> wolves = state.getAlivePlayersByFaction(serverWorld, WerewolfRoleDef.Faction.WOLF);
            for (UUID wolfUuid : wolves) {
                var wolfPlayer = serverWorld.getPlayerByUUID(wolfUuid);
                if (wolfPlayer instanceof ServerPlayer wolf) {
                    wolf.displayClientMessage(
                            Component.translatable("werewolf.msg.wolf_discuss")
                                    .withStyle(ChatFormatting.RED),
                            true);
                }
            }
        }
    }

    /**
     * 广播阶段信息（发送 S2C 包 + 聊天消息）
     */
    private void broadcastPhase(ServerLevel serverWorld, WerewolfGameState state) {
        broadcastPhaseStatic(serverWorld, state);
    }

    /**
     * 广播阶段信息（静态方法，供 NightManager/DayManager 调用）
     */
    public static void broadcastPhaseStatic(ServerLevel serverWorld, WerewolfGameState state) {
        int actorSeat = state.currentActor != null ? state.getSeatNumber(state.currentActor) : -1;
        var packet = new org.agmas.noellesroles.game.modes.werewolf.network.WerewolfPhaseS2CPacket(
                (byte) state.phase.ordinal(),
                actorSeat,
                state.phaseDeadlineTick,
                state.round
        );
        
        for (ServerPlayer player : serverWorld.players()) {
            // 发送 S2C 包
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, packet);
            // 同时发送聊天消息（备用）
            player.displayClientMessage(
                    Component.translatable(state.phase.translationKey)
                            .withStyle(ChatFormatting.GRAY),
                    true);
        }
    }

    /**
     * 强制座位位置
     */
    private void enforceSeatPositions(ServerLevel serverWorld, WerewolfGameState state) {
        // TODO: 实现座位锁定逻辑
        // 需要地图配置中的座位坐标
    }

    /**
     * 淘汰玩家（不使用 killPlayer）
     */
    public static void eliminatePlayer(ServerPlayer player) {
        player.setGameMode(GameType.SPECTATOR);
        WerewolfPlayerComponent comp = ModComponents.WEREWOLF.get(player);
        comp.alive = false;
        comp.sync();
    }

    /**
     * 结束游戏
     */
    public void endGame(ServerLevel serverWorld, WerewolfGameState state, String winnerFaction) {
        state.active = false;
        state.phase = WerewolfPhase.GAME_OVER;

        SREGameRoundEndComponent roundEnd = SREGameRoundEndComponent.KEY.get(serverWorld);
        roundEnd.CustomWinnerID = "werewolf_" + winnerFaction;

        // 添加胜利方玩家
        for (UUID uuid : state.players) {
            var player = serverWorld.getPlayerByUUID(uuid);
            if (player instanceof ServerPlayer sp) {
                WerewolfPlayerComponent comp = ModComponents.WEREWOLF.get(sp);
                boolean isWinner = (winnerFaction.equals("good") && comp.isGood())
                        || (winnerFaction.equals("wolf") && comp.isWolf());
                if (isWinner) {
                    roundEnd.CustomWinnerPlayers.add(uuid);
                }
            }
        }

        // 揭示所有玩家身份
        revealAllRoles(serverWorld, state, winnerFaction);

        // 广播 GAME_OVER 阶段（触发客户端状态重置）
        broadcastPhase(serverWorld, state);

        roundEnd.setRoundEndData(serverWorld.players(), GameUtils.WinStatus.CUSTOM);
        GameUtils.stopGame(serverWorld);
    }

    /**
     * 揭示所有玩家身份
     */
    private void revealAllRoles(ServerLevel serverWorld, WerewolfGameState state, String winnerFaction) {
        // 广播胜利信息
        String winKey = winnerFaction.equals("good") ? "werewolf.msg.good_wins" : "werewolf.msg.wolf_wins";
        for (ServerPlayer player : serverWorld.players()) {
            player.displayClientMessage(
                    Component.translatable(winKey)
                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                    false);
        }

        // 揭示每个玩家的身份
        for (UUID uuid : state.players) {
            var player = serverWorld.getPlayerByUUID(uuid);
            if (player instanceof ServerPlayer sp) {
                WerewolfPlayerComponent comp = ModComponents.WEREWOLF.get(sp);
                int seat = comp.seatNumber;
                String roleName = comp.getRoleDef().getTranslationKey();
                ChatFormatting color = comp.isWolf() ? ChatFormatting.RED : ChatFormatting.GREEN;
                String aliveStatus = comp.alive ? "" : " (已死亡)";

                for (ServerPlayer viewer : serverWorld.players()) {
                    viewer.displayClientMessage(
                            Component.literal(seat + "号: ")
                                    .withStyle(ChatFormatting.WHITE)
                                    .append(Component.translatable(roleName).withStyle(color))
                                    .append(Component.literal(aliveStatus).withStyle(ChatFormatting.GRAY)),
                            false);
                }
            }
        }
    }

    @Override
    public void stopGame(ServerLevel world) {
        WerewolfGameState state = WerewolfGameState.get(world);
        state.reset();
        WerewolfGameState.remove(world);
    }

    @Override
    public void finalizeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        WerewolfGameState.remove(serverWorld);
    }
}
