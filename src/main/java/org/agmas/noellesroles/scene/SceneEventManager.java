package org.agmas.noellesroles.scene;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * 场景方块的瞬态运行状态管理器（按维度划分，回合内有效，不持久化）。
 *
 * <p>提供两类能力：
 * <ul>
 *   <li><b>驻留计时</b>：方块每 tick 调用 {@link #reportDwell}，自动处理“多个方块同 tick 报告同一玩家不重复计数”
 *       与“离开区域（出现 tick 间隙）后重置”。用于有毒区域(20s)、井盖窒息(10s)等。</li>
 *   <li><b>破坏任务/sabotage 状态</b>：场景大门、喷火装置、反应堆、滚石等在破坏任务激活时改变行为。</li>
 * </ul>
 */
public final class SceneEventManager {
    private SceneEventManager() {
    }

    // ───────────────────────── 驻留计时 ─────────────────────────

    private static final class Dwell {
        int ticks;
        long lastTick = Long.MIN_VALUE;
    }

    private static final Map<ResourceKey<Level>, Map<String, Dwell>> DWELL = new HashMap<>();

    private static String dwellKey(String channel, Player player) {
        return channel + "|" + player.getUUID();
    }

    /**
     * 报告玩家在某通道（区域类型）内停留了一 tick，返回累计连续停留的 tick 数。
     * 同一 tick 多次报告（多个方块）不会重复计数；出现 ≥2 tick 间隙视为离开并重置。
     */
    public static int reportDwell(ServerLevel level, Player player, String channel) {
        long now = level.getGameTime();
        Map<String, Dwell> map = DWELL.computeIfAbsent(level.dimension(), k -> new HashMap<>());
        Dwell d = map.computeIfAbsent(dwellKey(channel, player), k -> new Dwell());
        if (d.lastTick == now) {
            // 本 tick 已计数，跳过
        } else if (d.lastTick == now - 1) {
            d.ticks++;
        } else {
            d.ticks = 1;
        }
        d.lastTick = now;
        return d.ticks;
    }

    /** 主动重置某玩家在某通道的驻留计时。 */
    public static void resetDwell(ServerLevel level, Player player, String channel) {
        Map<String, Dwell> map = DWELL.get(level.dimension());
        if (map != null) {
            map.remove(dwellKey(channel, player));
        }
    }

    /** 查询当前连续停留 tick 数（若上一 tick 未报告则视为 0）。 */
    public static int getDwell(ServerLevel level, Player player, String channel) {
        Map<String, Dwell> map = DWELL.get(level.dimension());
        if (map == null) {
            return 0;
        }
        Dwell d = map.get(dwellKey(channel, player));
        if (d == null) {
            return 0;
        }
        return (d.lastTick >= level.getGameTime() - 1) ? d.ticks : 0;
    }

    // ───────────────────────── 破坏任务 / sabotage 状态 ─────────────────────────

    private static final Map<ResourceKey<Level>, Long> SABOTAGE_UNTIL = new HashMap<>();

    /** 开启破坏任务状态。durationTicks < 0 表示持续到手动停止。 */
    public static void startSabotage(ServerLevel level, int durationTicks) {
        long until = durationTicks < 0 ? Long.MAX_VALUE : level.getGameTime() + durationTicks;
        SABOTAGE_UNTIL.put(level.dimension(), until);
    }

    public static void stopSabotage(ServerLevel level) {
        SABOTAGE_UNTIL.remove(level.dimension());
    }

    public static boolean isSabotageActive(ServerLevel level) {
        Long until = SABOTAGE_UNTIL.get(level.dimension());
        return until != null && level.getGameTime() < until;
    }

    /** 破坏任务剩余 tick 数（不限时返回一个很大的值，未激活返回 0）。 */
    public static long sabotageRemaining(ServerLevel level) {
        Long until = SABOTAGE_UNTIL.get(level.dimension());
        if (until == null) {
            return 0;
        }
        return Math.max(0, until - level.getGameTime());
    }

    /** 清空指定维度的所有瞬态状态（回合重置时可调用）。 */
    public static void clear(ServerLevel level) {
        DWELL.remove(level.dimension());
        SABOTAGE_UNTIL.remove(level.dimension());
    }
}
