package io.wifi.starrailexpress.backpack;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.progression.ProgressionState.FactionCardType;

import java.time.LocalDate;

/**
 * 每人每天的「卡片使用次数」上限。
 *
 * <p>与 {@code FactionCardCooldown}（同种阵营卡的使用间隔）是**两套独立限制**：
 * <ul>
 *   <li>间隔：管"多快能再用一次"（分钟级）；</li>
 *   <li>每日上限：管"一天总共能用几张"（天级）。</li>
 * </ul>
 * 两者都满足才能用。默认每人每天每种阵营卡 2 张、自选卡 2 张，可在
 * {@link SREConfig} 里动态调整（游戏管理界面 / {@code tmm:config}）。
 *
 * <h2>计数口径</h2>
 * <ul>
 *   <li>阵营卡：**每种类型各自计数**（杀手卡、平民卡互不影响）；</li>
 *   <li>自选卡：只有一种，统一计数；</li>
 *   <li>只统计**成功用掉**的卡。自选卡占了位又因地图/人数无效退款时，
 *       {@link #undoSelfSelect} 会把这一天的一次用掉撤销 —— 玩家不该为
 *      "系统判定无效"买单。</li>
 * </ul>
 *
 * <h2>跨天重置</h2>
 * {@link BackpackState#dailyUsageDay} 记录计数所属日期；任何读写前都会先调
 * {@link #rolloverIfNeeded}，日期不是今天就清零。存的是本地日期，
 * 因为玩家体感的"每天"是本地今天。
 */
public final class DailyCardUsage {

    private DailyCardUsage() {
    }

    /** 今天的日期键（本地时区）。 */
    public static String today() {
        return LocalDate.now().toString();
    }

    /** 若计数属于旧日期则清零。返回是否发生了重置。 */
    public static boolean rolloverIfNeeded(BackpackState state) {
        if (state == null) {
            return false;
        }
        String today = today();
        if (today.equals(state.dailyUsageDay)) {
            return false;
        }
        state.dailyUsageDay = today;
        if (state.dailyCardUses == null) {
            state.dailyCardUses = new java.util.EnumMap<>(FactionCardType.class);
        }
        state.dailyCardUses.replaceAll((type, count) -> 0);
        for (FactionCardType type : FactionCardType.values()) {
            if (type != FactionCardType.NONE) {
                state.dailyCardUses.put(type, 0);
            }
        }
        state.dailySelfSelectUses = 0;
        return true;
    }

    // ==================== 阵营卡 ====================

    /** 今天该种阵营卡已用张数（已处理跨天重置）。 */
    public static int usedFaction(BackpackState state, FactionCardType type) {
        if (state == null || type == null || type == FactionCardType.NONE) {
            return 0;
        }
        rolloverIfNeeded(state);
        Integer v = state.dailyCardUses == null ? null : state.dailyCardUses.get(type);
        return v == null ? 0 : Math.max(0, v);
    }

    /** 今天该种阵营卡还能用几张。 */
    public static int remainingFaction(BackpackState state, FactionCardType type) {
        int limit = factionLimit();
        if (limit <= 0) {
            return Integer.MAX_VALUE; // 0 / 负数视为不限
        }
        return Math.max(0, limit - usedFaction(state, type));
    }

    /** 今天该种阵营卡是否已用完。 */
    public static boolean factionLimitReached(BackpackState state, FactionCardType type) {
        SREConfig config = SREConfig.HANDLER.instance();
        if (config == null || !config.enableDailyCardLimit) {
            return false;
        }
        int limit = factionLimit();
        return limit > 0 && usedFaction(state, type) >= limit;
    }

    /** 记一次阵营卡使用。 */
    public static void recordFaction(BackpackState state, FactionCardType type) {
        if (state == null || type == null || type == FactionCardType.NONE) {
            return;
        }
        rolloverIfNeeded(state);
        if (state.dailyCardUses == null) {
            state.dailyCardUses = new java.util.EnumMap<>(FactionCardType.class);
        }
        state.dailyCardUses.merge(type, 1, Integer::sum);
    }

    public static void undoFaction(BackpackState state, FactionCardType type) {
        rolloverIfNeeded(state);
        state.dailyCardUses.computeIfPresent(type, (key, count) -> Math.max(0, count - 1));
    }

    // ==================== 自选卡 ====================

    /** 今天已用自选卡张数（已处理跨天重置）。 */
    public static int usedSelfSelect(BackpackState state) {
        if (state == null) {
            return 0;
        }
        rolloverIfNeeded(state);
        return Math.max(0, state.dailySelfSelectUses);
    }

    /** 今天还能用几张自选卡。 */
    public static int remainingSelfSelect(BackpackState state) {
        int limit = selfSelectLimit();
        if (limit <= 0) {
            return Integer.MAX_VALUE;
        }
        return Math.max(0, limit - usedSelfSelect(state));
    }

    /** 今天自选卡是否已用完。 */
    public static boolean selfSelectLimitReached(BackpackState state) {
        SREConfig config = SREConfig.HANDLER.instance();
        if (config == null || !config.enableDailyCardLimit) {
            return false;
        }
        int limit = selfSelectLimit();
        return limit > 0 && usedSelfSelect(state) >= limit;
    }

    /** 记一次自选卡使用。 */
    public static void recordSelfSelect(BackpackState state) {
        if (state == null) {
            return;
        }
        rolloverIfNeeded(state);
        state.dailySelfSelectUses = Math.max(0, state.dailySelfSelectUses) + 1;
    }

    /**
     * 撤销一次自选卡使用（退还卡时调用）。
     *
     * <p>只撤销**今天**的那一次：跨天后旧记录已经清零，不能减成负数。
     */
    public static void undoSelfSelect(BackpackState state) {
        if (state == null) {
            return;
        }
        rolloverIfNeeded(state);
        if (state.dailySelfSelectUses > 0) {
            state.dailySelfSelectUses--;
        }
    }

    // ==================== 配置读取 ====================

    private static int factionLimit() {
        SREConfig config = SREConfig.HANDLER.instance();
        return config == null ? 2 : config.dailyFactionCardLimit;
    }

    private static int selfSelectLimit() {
        SREConfig config = SREConfig.HANDLER.instance();
        return config == null ? 2 : config.dailySelfSelectCardLimit;
    }
}
