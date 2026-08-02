package org.agmas.noellesroles.game.modes.werewolf.client;

import org.agmas.noellesroles.game.modes.werewolf.WerewolfPhase;

/**
 * 狼人杀客户端状态缓存
 * 通过 S2C 包更新，供 HUD 和 Screen 使用
 * Author: jiale
 */
public class WerewolfClientState {
    /** 游戏是否激活 */
    public static boolean active = false;
    /** 当前阶段 */
    public static WerewolfPhase phase = WerewolfPhase.GAME_OVER;
    /** 当前行动者座位（-1 表示无） */
    public static int currentActorSeat = -1;
    /** 阶段截止时间 tick */
    public static long deadlineTick = 0;
    /** 当前轮次 */
    public static int round = 0;
    /** 自己的座位编号 */
    public static int mySeat = -1;
    /** 自己的角色 ID */
    public static String myRoleId = "";
    /** 自己是否存活 */
    public static boolean myAlive = true;

    /**
     * 重置所有状态
     */
    public static void reset() {
        active = false;
        phase = WerewolfPhase.GAME_OVER;
        currentActorSeat = -1;
        deadlineTick = 0;
        round = 0;
        mySeat = -1;
        myRoleId = "";
        myAlive = true;
    }

    /**
     * 更新阶段信息（从 S2C 包）
     */
    public static void updatePhase(byte phaseId, int actorSeat, long deadline, int roundNum) {
        if (phaseId >= 0 && phaseId < WerewolfPhase.values().length) {
            phase = WerewolfPhase.values()[phaseId];
        }
        currentActorSeat = actorSeat;
        deadlineTick = deadline;
        round = roundNum;
        active = phase != WerewolfPhase.GAME_OVER;
    }

    /**
     * 更新自己的信息（从 CCA 组件同步）
     */
    public static void updateMyInfo(int seat, String roleId, boolean alive) {
        mySeat = seat;
        myRoleId = roleId;
        myAlive = alive;
        // 注意：不在此处设置 active，由 updatePhase 控制
    }

    /**
     * 获取阶段剩余时间（秒）
     */
    public static float getRemainingSeconds(long currentTick) {
        if (deadlineTick <= 0 || deadlineTick == Long.MAX_VALUE) {
            return -1;
        }
        return Math.max(0, (deadlineTick - currentTick) / 20.0f);
    }

    /**
     * 是否是夜晚阶段
     */
    public static boolean isNight() {
        return phase.isNight();
    }

    /**
     * 是否是白天阶段
     */
    public static boolean isDay() {
        return phase.isDay();
    }

    /**
     * 是否轮到自己行动
     */
    public static boolean isMyTurn() {
        return currentActorSeat == mySeat && mySeat >= 0;
    }

    /**
     * 是否需要显示投票 UI
     */
    public static boolean shouldShowVoteUI() {
        return (phase == WerewolfPhase.DAY_VOTE || phase == WerewolfPhase.DAY_VOTE_PK_RESULT) && myAlive;
    }

    /**
     * 是否需要显示夜晚行动 UI
     */
    public static boolean shouldShowNightUI() {
        if (phase == WerewolfPhase.NIGHT_RESOLVE) return false;
        if (!isNight()) return false;
        if (!myAlive) return false;
        // 狼方阶段：所有狼人都能行动
        if (phase == WerewolfPhase.NIGHT_WOLVES) {
            return org.agmas.noellesroles.game.modes.werewolf.WerewolfRoleDef.byId(myRoleId).isWolf();
        }
        // 其他夜晚阶段：只有当前行动者能行动
        return isMyTurn();
    }
}
