package org.agmas.noellesroles.game.modes.werewolf.client;

import org.agmas.noellesroles.game.modes.werewolf.WerewolfPhase;

import java.util.ArrayList;
import java.util.List;

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
    /** 总座位数（由 seatNames 推导） */
    public static int totalSeats = 0;
    /** 存活玩家座位列表 */
    public static List<Integer> aliveSeats = new ArrayList<>();
    /** 座位 1~N 对应的玩家名（索引=座位-1），用于头像显示 */
    public static List<String> seatNames = new ArrayList<>();
    /** 炼药师视角：昨夜被狼杀的玩家座位（-1 表示未知/平安夜） */
    public static int alchemistVictimSeat = -1;
    /** 预言家最近查验的座位（-1 表示未查验） */
    public static int prophetResultSeat = -1;
    /** 预言家最近查验结果：true=狼人 */
    public static boolean prophetResultIsWolf = false;
    /** 自己的座位编号 */
    public static int mySeat = -1;
    /** 自己的角色 ID */
    public static String myRoleId = "";
    /** 自己是否存活 */
    public static boolean myAlive = true;
    /** 炼药师解药是否已用完 */
    public static boolean usedAntidote = false;
    /** 炼药师毒药是否已用完 */
    public static boolean usedPoison = false;
    /** 上次收到阶段包的阶段（用于自动打开界面判定） */
    public static WerewolfPhase lastOpenedUiPhase = null;
    /** 上次自动打开界面时的行动者座位（用于发言轮换时重新弹窗） */
    public static int lastOpenedUiActor = -2;

    /**
     * 重置所有状态
     */
    public static void reset() {
        active = false;
        phase = WerewolfPhase.GAME_OVER;
        currentActorSeat = -1;
        deadlineTick = 0;
        round = 0;
        totalSeats = 0;
        aliveSeats = new ArrayList<>();
        seatNames = new ArrayList<>();
        alchemistVictimSeat = -1;
        prophetResultSeat = -1;
        prophetResultIsWolf = false;
        mySeat = -1;
        myRoleId = "";
        myAlive = true;
        usedAntidote = false;
        usedPoison = false;
        lastOpenedUiPhase = null;
        lastOpenedUiActor = -2;
    }

    /**
     * 更新阶段信息（从 S2C 包）
     */
    public static void updatePhase(byte phaseId, int actorSeat, long deadline, int roundNum,
            List<Integer> alive, List<String> names) {
        if (phaseId >= 0 && phaseId < WerewolfPhase.values().length) {
            phase = WerewolfPhase.values()[phaseId];
        }
        currentActorSeat = actorSeat;
        deadlineTick = deadline;
        round = roundNum;
        aliveSeats = alive != null ? new ArrayList<>(alive) : new ArrayList<>();
        seatNames = names != null ? new ArrayList<>(names) : new ArrayList<>();
        totalSeats = seatNames.size();
        // 每个新阶段包重置炼药师受害者信息（随后由私有包重新设置）
        alchemistVictimSeat = -1;
        active = phase != WerewolfPhase.GAME_OVER;
    }

    /**
     * 处理私有信息包
     * type: 0=炼药师受害者, 1=查验为狼, 2=查验为好人
     */
    public static void handlePrivateInfo(byte type, int seat) {
        switch (type) {
            case 0 -> alchemistVictimSeat = seat;
            case 1 -> { prophetResultSeat = seat; prophetResultIsWolf = true; }
            case 2 -> { prophetResultSeat = seat; prophetResultIsWolf = false; }
            default -> {}
        }
    }

    /**
     * 获取指定座位的玩家名（未知返回空串）
     */
    public static String getSeatName(int seat) {
        if (seat < 1 || seat > seatNames.size()) return "";
        return seatNames.get(seat - 1);
    }

    /**
     * 更新自己的信息（从 CCA 组件同步，仅本地玩家）
     */
    public static void updateMyInfo(int seat, String roleId, boolean alive, boolean antidoteUsed, boolean poisonUsed) {
        mySeat = seat;
        myRoleId = roleId;
        myAlive = alive;
        usedAntidote = antidoteUsed;
        usedPoison = poisonUsed;
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
     * 是否需要显示投票 UI（所有存活玩家都参与投票）
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

    /**
     * 是否需要显示猎人开枪 UI（死亡猎人也能开枪）
     */
    public static boolean shouldShowHunterUI() {
        return phase == WerewolfPhase.DAY_HUNTER_SHOT && isMyTurn();
    }

    /**
     * 是否需要显示白狼王带人 UI
     */
    public static boolean shouldShowWolfKingUI() {
        return phase == WerewolfPhase.DAY_EXECUTE && isMyTurn();
    }

    /**
     * 是否需要显示发言跳过 UI（当前发言者可提前结束发言）
     */
    public static boolean shouldShowSpeechSkipUI() {
        return phase == WerewolfPhase.DAY_SPEECH && isMyTurn() && myAlive;
    }

    /**
     * 是否应该自动打开操作界面（任一行动界面）
     */
    public static boolean shouldOpenActionUI() {
        return shouldShowNightUI() || shouldShowVoteUI() || shouldShowHunterUI()
                || shouldShowWolfKingUI() || shouldShowSpeechSkipUI();
    }
}
