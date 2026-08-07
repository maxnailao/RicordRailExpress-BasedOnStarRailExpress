package org.agmas.noellesroles.game.modes.werewolf;

import io.wifi.starrailexpress.SRE;
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

    // === 狼人杀死亡原因（供回放时间线显示） ===
    public static final ResourceLocation DEATH_WOLF_BITE = SRE.jialeId("werewolf_bite");
    public static final ResourceLocation DEATH_WOLF_POISON = SRE.jialeId("werewolf_poison");
    public static final ResourceLocation DEATH_EXECUTE = SRE.jialeId("werewolf_execute");
    public static final ResourceLocation DEATH_HUNTER_SHOT = SRE.jialeId("werewolf_hunter_shot");
    public static final ResourceLocation DEATH_WOLF_KING = SRE.jialeId("werewolf_wolf_king");
    public static final ResourceLocation DEATH_KNIGHT_DUEL = SRE.jialeId("werewolf_knight_duel");

    static {
        // 注册狼人杀自定义胜利判定谓词（供 GameUtils CUSTOM 分支统计胜负/MVP 使用）
        // CustomWinnerID 为 werewolf_good / werewolf_wolf，按玩家狼人杀组件阵营判定
        GameUtils.CustomWinnersPredicates.add(entry -> {
            String winnerId = entry.getValue();
            if (winnerId == null || !winnerId.startsWith("werewolf_")) return false;
            WerewolfPlayerComponent comp = ModComponents.WEREWOLF.get(entry.getKey());
            if (comp.seatNumber < 0) return false; // 非参战玩家
            String faction = winnerId.substring("werewolf_".length());
            return ("good".equals(faction) && comp.isGood())
                    || ("wolf".equals(faction) && comp.isWolf());
        });
    }

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

        // 将玩家从大厅/准备区传送到地图出生点（修复"未在地图内游玩"问题）
        var areas = io.wifi.starrailexpress.cca.AreasWorldComponent.KEY.get(serverWorld);
        var spawnPos = areas.getSpawnPos();

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
            // 社交推理游戏：禁止环境伤害致死（摔落/窒息等）
            player.setInvulnerable(true);
            // 传送到地图出生点
            if (spawnPos != null) {
                player.teleportTo(serverWorld, spawnPos.pos.x(), spawnPos.pos.y(), spawnPos.pos.z(),
                        spawnPos.yaw, spawnPos.pitch);
            }
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

        // 获取地图配置中的座位坐标
        var areas = io.wifi.starrailexpress.cca.AreasWorldComponent.KEY.get(serverWorld);
        var werewolfConfig = areas.werewolfConfig;

        // 打乱玩家顺序
        List<ServerPlayer> shuffled = new ArrayList<>(players);
        Collections.shuffle(shuffled);

        // 匹配座位：玩家数超过座位数时截取前 N 名，多余玩家转为旁观
        if (werewolfConfig != null && !werewolfConfig.seats.isEmpty()
                && shuffled.size() > werewolfConfig.seats.size()) {
            int maxSeats = werewolfConfig.seats.size();
            List<ServerPlayer> excluded = new ArrayList<>(shuffled.subList(maxSeats, shuffled.size()));
            shuffled = new ArrayList<>(shuffled.subList(0, maxSeats));
            for (ServerPlayer ex : excluded) {
                ex.setGameMode(GameType.SPECTATOR);
                ex.displayClientMessage(
                        Component.translatable("werewolf.msg.seats_full")
                                .withStyle(ChatFormatting.RED),
                        false);
            }
        }

        // 获取角色配置
        List<WerewolfRoleDef> roleConfig = WerewolfRoleDef.getRoleConfig(shuffled.size());
        Collections.shuffle(roleConfig);

        // 分配角色和编号
        for (int i = 0; i < shuffled.size(); i++) {
            ServerPlayer player = shuffled.get(i);
            WerewolfRoleDef roleDef = roleConfig.get(i);
            int seatNumber = i + 1;

            // 设置框架角色（每个狼人杀身份独立注册，参考修机模式）
            gameWorldComponent.addRole(player, WerewolfRoles.forDef(roleDef), false);

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

            // 传送玩家到对应座位并坐上沙发（修复"未在地图内游玩"问题）
            if (werewolfConfig != null) {
                var seatPos = werewolfConfig.seats.get(String.valueOf(seatNumber));
                if (seatPos != null) {
                    sitPlayerOnSeat(player, new net.minecraft.core.BlockPos(seatPos.x, seatPos.y, seatPos.z));
                }
            }

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
        // 调用 super 初始化回放系统，记录游戏过程
        super.afterInitializeGame(serverWorld, gameComponent, readyPlayerList);
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
            case DAY_FREE_SPEECH -> {
                // 自由发言阶段（超时进入投票）
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

        // 座位锁定：每 tick 即时检测，离座立即传回
        enforceSeatPositions(serverWorld, state);

        // 夜晚致盲：非行动玩家夜晚致盲，行动/白天解除（每 20 tick 刷新）
        if (currentTick % 20 == 0) {
            updateNightBlindness(serverWorld, state);
        }
    }

    /**
     * 夜晚致盲管理：夜晚阶段非行动者施加致盲（闪光弹式全屏黑幕，由 BlindnessEffectMixin 对 UNLUCK 效果渲染）；
     * 行动者/白天解除。黑幕覆盖世界与 HUD，但操作界面（Screen）绘制在其上层，仍可正常操作。
     */
    private void updateNightBlindness(ServerLevel serverWorld, WerewolfGameState state) {
        boolean isNightPhase = state.phase.isNight();
        for (UUID uuid : state.players) {
            var player = serverWorld.getPlayerByUUID(uuid);
            if (!(player instanceof ServerPlayer sp)) continue;
            WerewolfPlayerComponent comp = ModComponents.WEREWOLF.get(sp);
            if (!comp.alive) continue;

            // 行动者判定：当前行动者，或狼方阶段的狼人们
            boolean isActor = uuid.equals(state.currentActor)
                    || (state.phase == WerewolfPhase.NIGHT_WOLVES && comp.isWolf());

            if (isNightPhase && !isActor) {
                if (!sp.hasEffect(net.minecraft.world.effect.MobEffects.UNLUCK)) {
                    sp.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                            net.minecraft.world.effect.MobEffects.UNLUCK, 60, 0, false, false, false));
                }
            } else {
                if (sp.hasEffect(net.minecraft.world.effect.MobEffects.UNLUCK)) {
                    sp.removeEffect(net.minecraft.world.effect.MobEffects.UNLUCK);
                }
            }
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
                // 公示期结束：检查猎人开枪或直接进入发言
                WerewolfNightManager.checkHunterShot(serverWorld, state);
            }
            case DAY_HUNTER_SHOT -> {
                // 猎人超时不开枪：处决猎人进遗言，夜晚死亡猎人进发言
                if (state.hunterDiedByExecution) {
                    state.hunterDiedByExecution = false;
                    startLastWords(serverWorld, state);
                } else {
                    proceedAfterHunterShot(serverWorld, state);
                }
            }
            case DAY_SPEECH -> {
                // 发言超时，切换下一位
                advanceSpeech(serverWorld, state);
            }
            case DAY_FREE_SPEECH -> {
                // 自由发言超时，进入投票
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

        // 顺序：分配行动者 → 广播阶段包（携带正确 actorSeat）→ 发送通知（聊天/私有包必须在阶段包之后，避免被重置）
        if (nextPhase != WerewolfPhase.NIGHT_RESOLVE) {
            assignCurrentActor(serverWorld, state);
            broadcastPhase(serverWorld, state);
            notifyCurrentActor(serverWorld, state);
        } else {
            broadcastPhase(serverWorld, state);
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

        // 重置夜晚数据并递增轮次（即使跳过守护者阶段也必须执行）
        state.beginNewNight();

        // 广播夜晚开始
        for (ServerPlayer player : serverWorld.players()) {
            player.displayClientMessage(
                    Component.translatable("werewolf.msg.night_falls", state.round)
                            .withStyle(ChatFormatting.DARK_BLUE),
                    false);
        }
        
        // 找到第一个存在的夜晚阶段
        WerewolfPhase firstPhase = WerewolfPhase.NIGHT_GUARDIAN;
        while (firstPhase != WerewolfPhase.NIGHT_RESOLVE && !roleExistsForPhase(serverWorld, state, firstPhase)) {
            firstPhase = firstPhase.nextNightPhase();
        }
        
        state.startPhase(firstPhase, currentTick);

        // 顺序：分配行动者 → 广播阶段包 → 发送通知（私有包必须在阶段包之后）
        if (firstPhase != WerewolfPhase.NIGHT_RESOLVE) {
            assignCurrentActor(serverWorld, state);
            broadcastPhase(serverWorld, state);
            notifyCurrentActor(serverWorld, state);
        } else {
            broadcastPhase(serverWorld, state);
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
     * 推进发言（公开方法，供网络包调用：发言者主动跳过）
     */
    public void advanceSpeechPublic(ServerLevel serverWorld, WerewolfGameState state) {
        advanceSpeech(serverWorld, state);
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

        // 所有存活玩家都发言完毕，进入自由发言阶段
        state.currentActor = null;
        state.startPhase(WerewolfPhase.DAY_FREE_SPEECH, currentTick);
        broadcastPhase(serverWorld, state);

        for (ServerPlayer player : serverWorld.players()) {
            player.displayClientMessage(
                    Component.translatable("werewolf.msg.free_speech_started")
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
     * 分配当前行动者（仅设置状态，不发包；供阶段包携带 actorSeat）
     */
    private void assignCurrentActor(ServerLevel serverWorld, WerewolfGameState state) {
        WerewolfRoleDef targetRole = switch (state.phase) {
            case NIGHT_GUARDIAN -> WerewolfRoleDef.GUARDIAN;
            case NIGHT_ALCHEMIST -> WerewolfRoleDef.ALCHEMIST;
            case NIGHT_PROPHET -> WerewolfRoleDef.PROPHET;
            case NIGHT_KNIGHT -> WerewolfRoleDef.KNIGHT;
            default -> null;
        };

        if (targetRole != null) {
            List<UUID> actors = state.getAlivePlayersByRole(serverWorld, targetRole);
            state.currentActor = actors.isEmpty() ? null : actors.get(0);
        } else {
            // 狼方阶段无单一行动者
            state.currentActor = null;
        }
    }

    /**
     * 通知当前行动者（发送聊天与私有包；必须在 broadcastPhase 之后调用）
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
            if (state.currentActor != null) {
                var actorPlayer = serverWorld.getPlayerByUUID(state.currentActor);
                if (actorPlayer instanceof ServerPlayer actor) {
                    actor.displayClientMessage(
                            Component.translatable("werewolf.msg.your_turn")
                                    .withStyle(ChatFormatting.GREEN),
                            true);

                    // 炼药师阶段：告知昨夜被狼杀的玩家（仅炼药师可见，私有包防泄密；仅持有解药时显示）
                    if (state.phase == WerewolfPhase.NIGHT_ALCHEMIST && state.wolfTarget != null
                            && !ModComponents.WEREWOLF.get(actor).usedAntidote) {
                        int victimSeat = state.getSeatNumber(state.wolfTarget);
                        if (victimSeat > 0) {
                            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(actor,
                                    new org.agmas.noellesroles.game.modes.werewolf.network.WerewolfPrivateInfoS2CPacket(
                                            (byte) 0, victimSeat));
                            actor.displayClientMessage(
                                    Component.translatable("werewolf.msg.alchemist_victim", victimSeat)
                                            .withStyle(ChatFormatting.LIGHT_PURPLE),
                                    false);
                        }
                    }
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

        // 收集存活玩家座位列表（供客户端 UI 使用）
        List<Integer> aliveSeats = new ArrayList<>();
        for (UUID uuid : state.getAlivePlayers(serverWorld)) {
            int seat = state.getSeatNumber(uuid);
            if (seat > 0) aliveSeats.add(seat);
        }
        Collections.sort(aliveSeats);

        // 收集座位→玩家名映射（供客户端显示头像）
        List<String> seatNames = new ArrayList<>();
        for (int s = 1; s <= state.players.size(); s++) {
            UUID uuid = state.getPlayerBySeat(s);
            String name = "";
            if (uuid != null) {
                var p = serverWorld.getServer().getPlayerList().getPlayer(uuid);
                if (p != null) name = p.getGameProfile().getName();
            }
            seatNames.add(name);
        }

        var packet = new org.agmas.noellesroles.game.modes.werewolf.network.WerewolfPhaseS2CPacket(
                (byte) state.phase.ordinal(),
                actorSeat,
                state.phaseDeadlineTick,
                state.round,
                aliveSeats,
                seatNames
        );
        
        for (ServerPlayer player : serverWorld.players()) {
            // 发送 S2C 包（客户端 HUD 根据是否行动者决定显示真实阶段名还是“等待其他玩家行动”，
            // 不在此处广播阶段名文本，避免泄露当前行动角色身份）
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, packet);
        }
    }

    /**
     * 强制座位位置（确保玩家坐在对应座位方块上）
     */
    private void enforceSeatPositions(ServerLevel serverWorld, WerewolfGameState state) {
        // 从 AreasWorldComponent 获取狼人杀配置
        var areas = io.wifi.starrailexpress.cca.AreasWorldComponent.KEY.get(serverWorld);
        if (areas.werewolfConfig == null || areas.werewolfConfig.seats.isEmpty()) {
            return; // 没有配置座位，跳过
        }

        for (UUID uuid : state.players) {
            var player = serverWorld.getPlayerByUUID(uuid);
            if (!(player instanceof ServerPlayer sp)) continue;

            WerewolfPlayerComponent comp = ModComponents.WEREWOLF.get(sp);
            if (!comp.alive) continue; // 死亡玩家不强制座位

            int seat = comp.seatNumber;
            var seatPos = areas.werewolfConfig.seats.get(String.valueOf(seat));
            if (seatPos == null) continue;

            net.minecraft.core.BlockPos blockPos = new net.minecraft.core.BlockPos(seatPos.x, seatPos.y, seatPos.z);

            // 已坐在正确座位上，跳过
            if (sp.getVehicle() instanceof io.wifi.starrailexpress.content.block.entity.SeatEntity seatEntity
                    && blockPos.equals(seatEntity.getSeatPos())) {
                continue;
            }

            // 未坐在正确座位上，重新入座
            sitPlayerOnSeat(sp, blockPos);
        }
    }

    /**
     * 让玩家坐上指定座位方块（复用 MountableBlock/SeatEntity 座位系统）
     */
    private static void sitPlayerOnSeat(ServerPlayer player, net.minecraft.core.BlockPos seatBlockPos) {
        ServerLevel level = player.serverLevel();

        // 已坐在正确座位上
        if (player.getVehicle() instanceof io.wifi.starrailexpress.content.block.entity.SeatEntity existingSeat
                && seatBlockPos.equals(existingSeat.getSeatPos())) {
            return;
        }

        // 计算所有座位的几何中心，入座后面向桌子中心
        float faceYaw = player.getYRot();
        var areas = io.wifi.starrailexpress.cca.AreasWorldComponent.KEY.get(level);
        if (areas.werewolfConfig != null && !areas.werewolfConfig.seats.isEmpty()) {
            double cx = 0, cz = 0;
            for (var p : areas.werewolfConfig.seats.values()) {
                cx += p.x + 0.5;
                cz += p.z + 0.5;
            }
            cx /= areas.werewolfConfig.seats.size();
            cz /= areas.werewolfConfig.seats.size();
            double dx = cx - (seatBlockPos.getX() + 0.5);
            double dz = cz - (seatBlockPos.getZ() + 0.5);
            if (dx * dx + dz * dz > 0.01) {
                faceYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            }
        }

        net.minecraft.world.level.block.state.BlockState blockState = level.getBlockState(seatBlockPos);
        if (!(blockState.getBlock() instanceof io.wifi.starrailexpress.content.block.MountableBlock mountable)) {
            // 非可骑坐方块：降级为传送到方块上方
            player.teleportTo(level, seatBlockPos.getX() + 0.5D, seatBlockPos.getY() + 1.0D,
                    seatBlockPos.getZ() + 0.5D, faceYaw, player.getXRot());
            return;
        }

        // 座位已被占用（存在存活的座位实体）
        if (!level.getEntitiesOfClass(io.wifi.starrailexpress.content.block.entity.SeatEntity.class,
                net.minecraft.world.phys.AABB.ofSize(seatBlockPos.getCenter(), 1, 1, 1),
                net.minecraft.world.entity.Entity::isAlive).isEmpty()) {
            return;
        }

        io.wifi.starrailexpress.content.block.entity.SeatEntity seatEntity =
                io.wifi.starrailexpress.index.TMMEntities.SEAT.create(level);
        if (seatEntity == null) return;

        // 先离开当前载具
        if (player.getVehicle() != null) {
            player.stopRiding();
        }

        // 先传送到座位旁并面向桌子中心（startRiding 有距离判定）
        player.teleportTo(level, seatBlockPos.getX() + 0.5D, seatBlockPos.getY() + 1.0D,
                seatBlockPos.getZ() + 0.5D, faceYaw, player.getXRot());

        // 按方块自身的坐点偏移创建座位实体（与右键坐下逻辑一致）
        net.minecraft.world.phys.Vec3 sitPos = mountable.getSitPos(level, blockState, seatBlockPos);
        net.minecraft.world.phys.Vec3 entityPos = net.minecraft.world.phys.Vec3.atLowerCornerOf(seatBlockPos).add(sitPos);
        seatEntity.moveTo(entityPos.x, entityPos.y, entityPos.z, 0, 0);
        seatEntity.setSeatPos(seatBlockPos);

        level.addFreshEntity(seatEntity);
        player.startRiding(seatEntity);
    }

    /**
     * 淘汰玩家（不使用 killPlayer）
     */
    public static void eliminatePlayer(ServerPlayer player) {
        eliminatePlayer(player, DEATH_EXECUTE);
    }

    /**
     * 淘汰玩家（携带死亡原因，供回放时间线显示）
     */
    public static void eliminatePlayer(ServerPlayer player, ResourceLocation deathReason) {
        player.setGameMode(GameType.SPECTATOR);
        WerewolfPlayerComponent comp = ModComponents.WEREWOLF.get(player);
        comp.alive = false;
        comp.sync();

        // 记录到回放系统（itemUsed 传死亡原因，回放会显示对应翻译文本）
        try {
            if (io.wifi.starrailexpress.SRE.REPLAY_MANAGER != null) {
                io.wifi.starrailexpress.SRE.REPLAY_MANAGER.addEvent(
                        io.wifi.starrailexpress.api.replay.GameReplayData.EventType.PLAYER_KILL,
                        null, player.getUUID(), deathReason.toString(), null);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * 结束游戏
     */
    public void endGame(ServerLevel serverWorld, WerewolfGameState state, String winnerFaction) {
        state.active = false;
        state.phase = WerewolfPhase.GAME_OVER;

        SREGameRoundEndComponent roundEnd = SREGameRoundEndComponent.KEY.get(serverWorld);
        roundEnd.CustomWinnerID = "werewolf_" + winnerFaction;
        // 结算界面文字颜色：好人绿 / 狼方红（默认 0 会显示为黑色）
        roundEnd.CustomWinnerColor = winnerFaction.equals("good") ? 0x55FF55 : 0xFF5555;

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

        // 结算界面标题：胜利阵营
        String winKey = winnerFaction.equals("good") ? "werewolf.msg.good_wins" : "werewolf.msg.wolf_wins";
        roundEnd.CustomWinnerTitle = Component.translatable(winKey);
        // 结算界面副标题：全员身份揭示（多行）
        roundEnd.CustomWinnerSubtitle = buildRoleRevealComponent(serverWorld, state);

        // 聊天频道同步揭示
        revealAllRoles(serverWorld, state, winnerFaction);

        // 广播 GAME_OVER 阶段（触发客户端状态重置）
        broadcastPhase(serverWorld, state);

        // CUSTOM_COMPONENT：结算界面直接显示 Title(胜利信息) + Subtitle(身份列表)
        roundEnd.setRoundEndData(serverWorld.players(), GameUtils.WinStatus.CUSTOM_COMPONENT);
        GameUtils.stopGame(serverWorld);
    }

    /**
     * 构建身份揭示组件（结算界面副标题，多行）
     */
    private Component buildRoleRevealComponent(ServerLevel serverWorld, WerewolfGameState state) {
        net.minecraft.network.chat.MutableComponent reveal = Component.empty();
        for (UUID uuid : state.players) {
            var player = serverWorld.getPlayerByUUID(uuid);
            if (player instanceof ServerPlayer sp) {
                WerewolfPlayerComponent comp = ModComponents.WEREWOLF.get(sp);
                int seat = comp.seatNumber;
                ChatFormatting color = comp.isWolf() ? ChatFormatting.RED : ChatFormatting.GREEN;
                reveal.append(Component.literal("\n" + seat + "号 ").withStyle(ChatFormatting.WHITE)
                        .append(sp.getDisplayName().copy().withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal(" · ").withStyle(ChatFormatting.GRAY))
                        .append(Component.translatable(comp.getRoleDef().getTranslationKey()).withStyle(color))
                        .append(comp.alive ? Component.empty()
                                : Component.translatable("werewolf.reveal.dead_tag").withStyle(ChatFormatting.GRAY)));
            }
        }
        return reveal;
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

                for (ServerPlayer viewer : serverWorld.players()) {
                    viewer.displayClientMessage(
                            Component.literal(seat + "号: ")
                                    .withStyle(ChatFormatting.WHITE)
                                    .append(Component.translatable(roleName).withStyle(color))
                                    .append(comp.alive ? Component.empty()
                                            : Component.translatable("werewolf.reveal.dead_tag").withStyle(ChatFormatting.GRAY)),
                            false);
                }
            }
        }
    }

    @Override
    public void stopGame(ServerLevel world) {
        // 让所有坐在座位上的玩家离开座位，并还原无敌/致盲状态
        for (ServerPlayer player : world.players()) {
            if (player.getVehicle() instanceof io.wifi.starrailexpress.content.block.entity.SeatEntity) {
                player.stopRiding();
            }
            player.setInvulnerable(false);
            if (player.hasEffect(net.minecraft.world.effect.MobEffects.UNLUCK)) {
                player.removeEffect(net.minecraft.world.effect.MobEffects.UNLUCK);
            }
        }
        WerewolfGameState state = WerewolfGameState.get(world);
        state.reset();
        WerewolfGameState.remove(world);
    }

    @Override
    public void finalizeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        WerewolfGameState.remove(serverWorld);
    }
}
