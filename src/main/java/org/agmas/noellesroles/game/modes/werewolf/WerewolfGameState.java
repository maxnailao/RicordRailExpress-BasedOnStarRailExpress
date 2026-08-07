package org.agmas.noellesroles.game.modes.werewolf;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

/**
 * 狼人杀游戏状态（服务端内存，非 CCA）
 * Author: jiale
 */
public class WerewolfGameState {
    // === 全局状态存储 ===
    private static final Map<ServerLevel, WerewolfGameState> STATES = new HashMap<>();

    public static WerewolfGameState get(ServerLevel level) {
        return STATES.computeIfAbsent(level, k -> new WerewolfGameState());
    }

    public static void remove(ServerLevel level) {
        STATES.remove(level);
    }

    public static boolean isActive(ServerLevel level) {
        WerewolfGameState state = STATES.get(level);
        return state != null && state.active;
    }

    // === 游戏状态 ===
    /** 游戏是否激活 */
    public boolean active = false;
    /** 当前阶段 */
    public WerewolfPhase phase = WerewolfPhase.GAME_OVER;
    /** 当前轮次 */
    public int round = 0;
    /** 阶段开始 tick */
    public long phaseStartTick = 0;
    /** 阶段超时 tick */
    public long phaseDeadlineTick = 0;
    /** 当前行动者 UUID */
    public UUID currentActor = null;
    /** 当前发言编号索引 */
    public int speechSeatIndex = 0;

    // === 夜晚数据 ===
    /** 狼方击杀目标 */
    public UUID wolfTarget = null;
    /** 狼方投票（狼人UUID -> 目标UUID） */
    public Map<UUID, UUID> wolfVotes = new HashMap<>();
    /** 已提交选择的狼人（含弃票者，用于提前结算判定） */
    public Set<UUID> wolfVoters = new HashSet<>();
    /** 守护者守护目标 */
    public UUID guardianTarget = null;
    /** 炼药师毒药目标 */
    public UUID alchemistPoisonTarget = null;
    /** 炼药师解药目标（被狼杀的人） */
    public UUID alchemistSaveTarget = null;
    /** 预言家查验目标 */
    public UUID prophetTarget = null;
    /** 骑士决斗目标 */
    public UUID knightTarget = null;
    /** 夜晚死亡列表 */
    public List<UUID> nightDeaths = new ArrayList<>();
    /** 夜晚被毒杀列表（用于猎人判断） */
    public List<UUID> poisonDeaths = new ArrayList<>();
    /** 夜晚是否已结算（防止重复结算） */
    public boolean nightResolved = false;

    // === 白天数据 ===
    /** 投票（投票者UUID -> 目标UUID） */
    public Map<UUID, UUID> votes = new HashMap<>();
    /** 被票出者 */
    public UUID votedOutPlayer = null;
    /** PK 玩家列表 */
    public List<UUID> pkPlayers = new ArrayList<>();
    /** 是否是 PK 投票轮 */
    public boolean isPkVote = false;
    /** 猎人是否因被票出而开枪（决定开枪后进入遗言还是发言） */
    public boolean hunterDiedByExecution = false;

    // === 玩家列表 ===
    /** 所有参战玩家 UUID */
    public List<UUID> players = new ArrayList<>();
    /** 座位编号 -> UUID 映射 */
    public Map<Integer, UUID> seatToPlayer = new HashMap<>();
    /** UUID -> 座位编号 映射 */
    public Map<UUID, Integer> playerToSeat = new HashMap<>();

    /**
     * 重置所有状态
     */
    public void reset() {
        this.active = false;
        this.phase = WerewolfPhase.GAME_OVER;
        this.round = 0;
        this.phaseStartTick = 0;
        this.phaseDeadlineTick = 0;
        this.currentActor = null;
        this.speechSeatIndex = 0;
        this.wolfTarget = null;
        this.wolfVotes.clear();
        this.wolfVoters.clear();
        this.guardianTarget = null;
        this.alchemistPoisonTarget = null;
        this.alchemistSaveTarget = null;
        this.prophetTarget = null;
        this.knightTarget = null;
        this.nightDeaths.clear();
        this.poisonDeaths.clear();
        this.nightResolved = false;
        this.votes.clear();
        this.votedOutPlayer = null;
        this.pkPlayers.clear();
        this.isPkVote = false;
        this.hunterDiedByExecution = false;
        this.players.clear();
        this.seatToPlayer.clear();
        this.playerToSeat.clear();
    }

    /**
     * 开始新夜晚：重置所有夜晚数据并递增轮次
     * 注意：startNight 可能跳过守护者阶段（守护者死亡），
     * 因此不能把夜晚重置绑定在 startPhase(NIGHT_GUARDIAN) 分支里
     */
    public void beginNewNight() {
        this.wolfTarget = null;
        this.wolfVotes.clear();
        this.wolfVoters.clear();
        this.guardianTarget = null;
        this.alchemistPoisonTarget = null;
        this.alchemistSaveTarget = null;
        this.prophetTarget = null;
        this.knightTarget = null;
        this.nightDeaths.clear();
        this.poisonDeaths.clear();
        this.nightResolved = false;
        this.round++;
    }

    /**
     * 开始新阶段
     */
    public void startPhase(WerewolfPhase newPhase, long currentTick) {
        this.phase = newPhase;
        this.phaseStartTick = currentTick;
        this.phaseDeadlineTick = newPhase.durationTicks > 0 
                ? currentTick + newPhase.durationTicks 
                : Long.MAX_VALUE;
        
        // 重置阶段相关数据
        if (newPhase == WerewolfPhase.DAY_VOTE) {
            this.votes.clear();
            this.votedOutPlayer = null;
            this.isPkVote = false;
        } else if (newPhase == WerewolfPhase.DAY_VOTE_PK_RESULT) {
            this.votes.clear();
            this.isPkVote = true;
        } else if (newPhase == WerewolfPhase.DAY_ANNOUNCE) {
            // 新白天开始，重置处决猎人标记
            this.hunterDiedByExecution = false;
        }
    }

    /**
     * 检查阶段是否超时
     */
    public boolean isPhaseTimeout(long currentTick) {
        return phaseDeadlineTick != Long.MAX_VALUE && currentTick >= phaseDeadlineTick;
    }

    /**
     * 获取存活玩家列表
     */
    public List<UUID> getAlivePlayers(ServerLevel level) {
        List<UUID> alive = new ArrayList<>();
        for (UUID uuid : players) {
            var player = level.getPlayerByUUID(uuid);
            if (player instanceof ServerPlayer sp) {
                WerewolfPlayerComponent comp = org.agmas.noellesroles.component.ModComponents.WEREWOLF.get(sp);
                if (comp.alive) {
                    alive.add(uuid);
                }
            }
        }
        return alive;
    }

    /**
     * 获取存活玩家数量
     */
    public int getAlivePlayerCount(ServerLevel level) {
        return getAlivePlayers(level).size();
    }

    /**
     * 获取指定角色的存活玩家
     */
    public List<UUID> getAlivePlayersByRole(ServerLevel level, WerewolfRoleDef roleDef) {
        List<UUID> result = new ArrayList<>();
        for (UUID uuid : players) {
            var player = level.getPlayerByUUID(uuid);
            if (player instanceof ServerPlayer sp) {
                WerewolfPlayerComponent comp = org.agmas.noellesroles.component.ModComponents.WEREWOLF.get(sp);
                if (comp.alive && comp.getRoleDef() == roleDef) {
                    result.add(uuid);
                }
            }
        }
        return result;
    }

    /**
     * 获取指定阵营的存活玩家
     */
    public List<UUID> getAlivePlayersByFaction(ServerLevel level, WerewolfRoleDef.Faction faction) {
        List<UUID> result = new ArrayList<>();
        for (UUID uuid : players) {
            var player = level.getPlayerByUUID(uuid);
            if (player instanceof ServerPlayer sp) {
                WerewolfPlayerComponent comp = org.agmas.noellesroles.component.ModComponents.WEREWOLF.get(sp);
                if (comp.alive && comp.getRoleDef().faction == faction) {
                    result.add(uuid);
                }
            }
        }
        return result;
    }

    /**
     * 获取玩家编号
     */
    public int getSeatNumber(UUID uuid) {
        return playerToSeat.getOrDefault(uuid, -1);
    }

    /**
     * 根据编号获取玩家
     */
    public UUID getPlayerBySeat(int seat) {
        return seatToPlayer.get(seat);
    }

    /**
     * 检查玩家是否是狼方
     */
    public boolean isWolf(ServerLevel level, UUID uuid) {
        var player = level.getPlayerByUUID(uuid);
        if (player instanceof ServerPlayer sp) {
            return org.agmas.noellesroles.component.ModComponents.WEREWOLF.get(sp).isWolf();
        }
        return false;
    }

    /**
     * 获取阶段剩余时间（tick）
     */
    public long getPhaseRemainingTicks(long currentTick) {
        if (phaseDeadlineTick == Long.MAX_VALUE) return Long.MAX_VALUE;
        return Math.max(0, phaseDeadlineTick - currentTick);
    }
}
