package io.wifi.starrailexpress.api.replay.board;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 「小脑 / 被小脑」统计（**跨局累计**）。
 *
 * <ul>
 * <li><b>小脑</b>：玩家误杀平民（触发小脑惩罚）的次数</li>
 * <li><b>被小脑</b>：玩家作为平民被误杀的次数</li>
 * </ul>
 *
 * <p>只统计真正触发了小脑惩罚的那一次击杀 —— 记录点与
 * {@code XiaoNaoHandler} 里执行惩罚的位置相邻，所以逃票者、家族成员、
 * 疯狂魔术师、复仇者正当击杀等「不算误杀」的分支不会被计入。
 *
 * <p><b>累计语义</b>：开新局不会清零，数据会一直累积到管理员执行
 * {@code /sre:xiaonao_board reset} 或手动删除存档为止。
 *
 * <p>用 {@link LinkedHashMap} 保证同名次时顺序稳定（不会每次刷新跳来跳去）。
 */
public final class XiaoNaoBoardStats {

    private XiaoNaoBoardStats() {
    }

    /** 误杀者 -> 次数 */
    private static final Map<UUID, Integer> XIAONAO = new LinkedHashMap<>();
    /** 被误杀者 -> 次数 */
    private static final Map<UUID, Integer> BEI_XIAONAO = new LinkedHashMap<>();
    /** UUID -> 显示名（记录时抓一份，避免离线后查不到名字） */
    private static final Map<UUID, String> NAMES = new LinkedHashMap<>();
    /**
     * 清空榜单统计（**管理员行为**，由 {@code /sre:xiaonao_board reset} 调用）。
     *
     * <p>统计是**跨局累计**的，不会随每局开新局自动清零，只有这条路径会清。
     */
    public static void resetAll(MinecraftServer server) {
        reset(XIAONAO, BEI_XIAONAO, NAMES);
        persist(server);
    }

    /** 重置核心逻辑，与 Minecraft 类型解耦（便于独立测试） */
    static void reset(Map<UUID, Integer> xiaoNao, Map<UUID, Integer> beiXiaoNao, Map<UUID, String> names) {
        xiaoNao.clear();
        beiXiaoNao.clear();
        names.clear();
    }

    /**
     * 服务器启动时把存档里的数据读回内存，避免重启后榜单是空的。
     */
    public static void restoreFromSaved(MinecraftServer server) {
        XiaoNaoStatsSavedData data = XiaoNaoStatsSavedData.get(server);
        data.restoreInto(XIAONAO, BEI_XIAONAO, NAMES);
    }

    /** 把当前内存数据落盘（server 为 null 时跳过，例如离线单测） */
    private static void persist(MinecraftServer server) {
        if (server == null) {
            return;
        }
        XiaoNaoStatsSavedData.get(server).mirror(XIAONAO, BEI_XIAONAO, NAMES);
    }

    /**
     * 记录一次小脑。
     *
     * @param victim 被误杀的平民
     * @param killer 误杀者
     */
    public static void record(Player victim, Player killer) {
        if (victim == null || killer == null) {
            return;
        }
        record(victim.getUUID(), victim.getScoreboardName(),
                killer.getUUID(), killer.getScoreboardName(),
                XIAONAO, BEI_XIAONAO, NAMES);
        persist(victim.getServer());
    }

    /**
     * 记录核心逻辑，与 Minecraft 类型解耦（便于独立测试）。
     * <p>被误杀者进 {@code beiXiaoNao}，误杀者进 {@code xiaoNao}。
     */
    static void record(UUID victimId, String victimName, UUID killerId, String killerName,
            Map<UUID, Integer> xiaoNao, Map<UUID, Integer> beiXiaoNao, Map<UUID, String> names) {
        beiXiaoNao.merge(victimId, 1, Integer::sum);
        xiaoNao.merge(killerId, 1, Integer::sum);
        // 名字每次都刷新：玩家可能改名
        names.put(victimId, victimName);
        names.put(killerId, killerName);
    }

    private static void bump(Player player, Map<UUID, Integer> target) {
        UUID id = player.getUUID();
        target.merge(id, 1, Integer::sum);
        // 名字每次都刷新：玩家可能改名
        NAMES.put(id, player.getScoreboardName());
    }

    // =========================================================================
    // 管理员单独改某个人的次数
    // =========================================================================

    /** 榜单种类（与 {@code XiaoNaoBoardService.Kind} 的语义一致） */
    public enum Board {
        /** 小脑榜：误杀别人的次数 */
        XIAONAO,
        /** 被小脑榜：被误杀的次数 */
        BEI_XIAONAO
    }

    /** 查某人在某个榜上的次数（0 表示没有记录） */
    public static int getCount(Board board, UUID id) {
        return getCount(mapOf(board), id);
    }

    /**
     * 直接设定某人的次数。
     *
     * @param count 小于等于 0 时等于把这个人从榜上移除
     * @return 设定后的次数
     */
    public static int setCount(Board board, UUID id, String name, int count) {
        Map<UUID, Integer> target = mapOf(board);
        Map<UUID, Integer> other = board == Board.XIAONAO ? BEI_XIAONAO : XIAONAO;
        return setCount(target, other, id, name, count, NAMES);
    }

    /** 在现有次数上增减（增量可为负），返回新值 */
    public static int addCount(Board board, UUID id, String name, int delta) {
        return setCount(board, id, name, getCount(board, id) + delta);
    }

    private static Map<UUID, Integer> mapOf(Board board) {
        return board == Board.XIAONAO ? XIAONAO : BEI_XIAONAO;
    }

    // ── 与 Minecraft 类型解耦的核心逻辑（便于独立测试）──

    static int getCount(Map<UUID, Integer> map, UUID id) {
        return map.getOrDefault(id, 0);
    }

    /**
     * 设定次数（核心逻辑）。
     * <p>归零时把该玩家从榜上移除；若两个榜都没有他了，连同名字缓存一起清掉，
     * 避免 {@code stats} 里查到一个已经不在任何榜上的"幽灵名字"。
     */
    static int setCount(Map<UUID, Integer> target, Map<UUID, Integer> other, UUID id, String name, int count,
            Map<UUID, String> names) {
        if (count <= 0) {
            target.remove(id);
            if (!other.containsKey(id)) {
                names.remove(id);
            }
            return 0;
        }
        target.put(id, count);
        if (name != null && !name.isBlank()) {
            names.put(id, name);
        }
        return count;
    }

    /** 改动后落盘（与 record 相同的持久化路径） */
    public static void save(MinecraftServer server) {
        persist(server);
    }

    /** 某人是否已在名字缓存里（用于确认解析到的是服务器已知玩家） */
    public static boolean isKnown(UUID id) {
        return NAMES.containsKey(id);
    }

    /** 已验证过的名字缓存（供命令补全离线玩家用） */
    public static Map<UUID, String> knownNames() {
        return java.util.Collections.unmodifiableMap(NAMES);
    }

    /** 小脑榜（误杀最多的人在前） */
    public static List<Entry> topXiaoNao(int limit) {
        return top(XIAONAO, limit);
    }

    /** 被小脑榜（被误杀最多的人在前） */
    public static List<Entry> topBeiXiaoNao(int limit) {
        return top(BEI_XIAONAO, limit);
    }

    private static List<Entry> top(Map<UUID, Integer> source, int limit) {
        return rank(source, NAMES, limit);
    }

    /**
     * 排名核心逻辑，与 Minecraft 类型解耦（便于独立测试）。
     * <p>次数降序；次数相同按名字排序，保证多次刷新顺序一致（不会跳来跳去）。
     */
    static List<Entry> rank(Map<UUID, Integer> source, Map<UUID, String> names, int limit) {
        List<Entry> list = new ArrayList<>();
        for (Map.Entry<UUID, Integer> e : source.entrySet()) {
            if (e.getValue() == null || e.getValue() <= 0) {
                continue;
            }
            list.add(new Entry(e.getKey(), names.getOrDefault(e.getKey(), "?"), e.getValue()));
        }
        list.sort(Comparator.comparingInt(Entry::count).reversed()
                .thenComparing(Entry::name, String.CASE_INSENSITIVE_ORDER));
        if (limit > 0 && list.size() > limit) {
            return list.subList(0, limit);
        }
        return list;
    }

    /** 是否还没有任何记录 */
    public static boolean isEmpty() {
        return XIAONAO.isEmpty() && BEI_XIAONAO.isEmpty();
    }

    /** 供调试/命令展示用的世界内玩家数（仅用于判断榜单是否为空） */
    public static int trackedPlayers() {
        return NAMES.size();
    }

    public record Entry(UUID uuid, String name, int count) {
    }

    /** 便捷：从服务器拿玩家名（拿不到就退回 uuid 前 8 位） */
    public static String nameOf(MinecraftServer server, UUID uuid) {
        String cached = NAMES.get(uuid);
        if (cached != null && !cached.isBlank()) {
            return cached;
        }
        if (server != null) {
            var player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                return player.getScoreboardName();
            }
        }
        return uuid.toString().substring(0, 8);
    }
}
