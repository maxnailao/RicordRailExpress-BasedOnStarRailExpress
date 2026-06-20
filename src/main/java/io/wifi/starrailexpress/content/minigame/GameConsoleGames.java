package io.wifi.starrailexpress.content.minigame;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 游戏掌机可用小游戏配置
 * <p>
 * - 复用 QuestMinigames 注册表
 * - 通过 ENABLED_IDS 白名单过滤掌机中可玩的小游戏
 * - 后续可在此处添加掌机专属小游戏
 * </p>
 */
public class GameConsoleGames {

    /**
     * 掌机可用的小游戏 ID 白名单
     * 按添加顺序显示在选择界面中
     */
    private static final Set<String> ENABLED_IDS = new LinkedHashSet<>(
            // 掌机内暂无游戏，后续按需添加
    );

    /**
     * 启用积分榜的小游戏 ID 集合
     * 只有在此集合中的游戏才能通过 ~ 键查看积分榜
     */
    private static final Set<String> SCOREBOARD_ENABLED_IDS = new LinkedHashSet<>();

    // 静态初始化：将五子棋、打靶和象棋加入掌机白名单
    static {
        ENABLED_IDS.add("gomoku");
        ENABLED_IDS.add("target_shooting");
        ENABLED_IDS.add("xiangqi");
        ENABLED_IDS.add("tetris");
        ENABLED_IDS.add("doudizhu");
        ENABLED_IDS.add("mahjong");
        ENABLED_IDS.add("lockpick_score");
        // 打靶小游戏启用积分榜
        SCOREBOARD_ENABLED_IDS.add("target_shooting");
        // 俄罗斯方块启用积分榜
        SCOREBOARD_ENABLED_IDS.add("tetris");
        // 撬锁积分启用积分榜
        SCOREBOARD_ENABLED_IDS.add("lockpick_score");
    }

    /**
     * 获取掌机中可用的小游戏列表
     * 
     * @return 经过过滤的小游戏列表
     */
    public static List<QuestMinigame> getAvailable() {
        List<QuestMinigame> result = new ArrayList<>();
        for (QuestMinigame game : QuestMinigames.getAll()) {
            if (ENABLED_IDS.contains(game.id())) {
                result.add(game);
            }
        }
        return result;
    }

    /**
     * 检查某个小游戏是否在掌机中可用
     * 
     * @param minigameId 小游戏 ID
     * @return 是否可用
     */
    public static boolean isAvailable(String minigameId) {
        return ENABLED_IDS.contains(minigameId);
    }

    /**
     * 动态添加小游戏到掌机（供其他模组或扩展调用）
     * 
     * @param minigameId 已在 QuestMinigames 注册的小游戏 ID
     */
    public static void enable(String minigameId) {
        ENABLED_IDS.add(minigameId);
    }

    /**
     * 从掌机中移除小游戏
     * 
     * @param minigameId 小游戏 ID
     */
    public static void disable(String minigameId) {
        ENABLED_IDS.remove(minigameId);
    }

    // ══════════════════════════════════════════════
    // 积分榜管理
    // ══════════════════════════════════════════════

    /**
     * 检查小游戏是否启用了积分榜
     */
    public static boolean hasScoreboard(String minigameId) {
        return SCOREBOARD_ENABLED_IDS.contains(minigameId);
    }

    /**
     * 为小游戏启用积分榜
     */
    public static void enableScoreboard(String minigameId) {
        SCOREBOARD_ENABLED_IDS.add(minigameId);
    }

    /**
     * 为小游戏禁用积分榜
     */
    public static void disableScoreboard(String minigameId) {
        SCOREBOARD_ENABLED_IDS.remove(minigameId);
    }
}
