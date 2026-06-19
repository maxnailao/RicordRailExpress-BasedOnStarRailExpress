package io.wifi.starrailexpress.content.minigame;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.agmas.noellesroles.Noellesroles;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 小游戏积分榜数据管理
 * <p>
 * 数据持久化到 world 目录下的 minigame_scoreboard.json。
 * 按小游戏 ID 分别维护排行榜列表。
 * </p>
 */
public class MinigameScoreboardData {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "minigame_scoreboard.json";
    private static final int MAX_ENTRIES = 50; // 每个游戏最多保留 50 条记录

    /** 积分榜条目 */
    public record ScoreEntry(String playerName, int score, long timestamp) {}

    /** 内存中的数据: minigameId -> 分数列表（降序） */
    private static Map<String, List<ScoreEntry>> data = new LinkedHashMap<>();

    /** 服务端 world 目录路径 */
    private static Path savePath = null;

    // ══════════════════════════════════════════════
    // 加载 / 保存
    // ══════════════════════════════════════════════

    /**
     * 服务端启动时调用，从 world 目录加载积分榜数据
     */
    public static void load(MinecraftServer server) {
        try {
            savePath = server.getWorldPath(LevelResource.ROOT).resolve(FILE_NAME);
            if (Files.exists(savePath)) {
                String json = Files.readString(savePath);
                Type type = new TypeToken<Map<String, List<ScoreEntry>>>() {}.getType();
                Map<String, List<ScoreEntry>> loaded = GSON.fromJson(json, type);
                if (loaded != null) {
                    data = loaded;
                }
            }
        } catch (IOException e) {
            Noellesroles.LOGGER.error("Failed to load minigame scoreboard data", e);
        }
    }

    /**
     * 保存积分榜数据到文件
     */
    public static void save() {
        if (savePath == null) return;
        try {
            String json = GSON.toJson(data);
            Files.writeString(savePath, json);
        } catch (IOException e) {
            Noellesroles.LOGGER.error("Failed to save minigame scoreboard data", e);
        }
    }

    // ══════════════════════════════════════════════
    // 数据操作
    // ══════════════════════════════════════════════

    /**
     * 添加分数条目（同一玩家仅保留最高分）
     *
     * @param minigameId   小游戏 ID
     * @param playerName   玩家名称
     * @param score        分数
     */
    public static void addScore(String minigameId, String playerName, int score) {
        List<ScoreEntry> entries = data.computeIfAbsent(minigameId, k -> new ArrayList<>());

        // 同一玩家仅保留最高分
        boolean updated = false;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).playerName().equals(playerName)) {
                if (score > entries.get(i).score()) {
                    entries.set(i, new ScoreEntry(playerName, score, System.currentTimeMillis()));
                }
                updated = true;
                break;
            }
        }
        if (!updated) {
            entries.add(new ScoreEntry(playerName, score, System.currentTimeMillis()));
        }

        // 按分数降序排列
        entries.sort((a, b) -> Integer.compare(b.score, a.score));

        // 限制条目数量
        if (entries.size() > MAX_ENTRIES) {
            entries = new ArrayList<>(entries.subList(0, MAX_ENTRIES));
            data.put(minigameId, entries);
        }

        save();
    }

    /**
     * 获取指定小游戏的排行榜
     *
     * @param minigameId 小游戏 ID
     * @return 分数列表（降序），不存在时返回空列表
     */
    public static List<ScoreEntry> getScores(String minigameId) {
        return data.getOrDefault(minigameId, Collections.emptyList());
    }

    /**
     * 清空指定小游戏的排行榜
     */
    public static void clearScores(String minigameId) {
        data.remove(minigameId);
        save();
    }

    /**
     * 检查指定小游戏是否有积分榜数据
     */
    public static boolean hasScores(String minigameId) {
        List<ScoreEntry> entries = data.get(minigameId);
        return entries != null && !entries.isEmpty();
    }
}
