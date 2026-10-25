package io.wifi.starrailexpress.announcement;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.wifi.starrailexpress.announcement.network.AnnouncementNewS2CPayload;
import io.wifi.starrailexpress.announcement.network.AnnouncementSyncS2CPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 公告管理器（服务端权威）。
 * <p>数据实时写入 {@code config/announcements.json}，跨重启保持；
 * 发布时向所有在线玩家推送完整列表，并触发屏幕顶部横幅提示。</p>
 */
public class AnnouncementManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("Announcement");
    private static final com.google.gson.Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static AnnouncementManager instance;

    private final List<AnnouncementEntry> entries = new ArrayList<>();
    private Path dataFile;

    private AnnouncementManager() {}

    public static AnnouncementManager getInstance() {
        if (instance == null) instance = new AnnouncementManager();
        return instance;
    }

    public void init(Path dataDir) {
        this.dataFile = dataDir.resolve("announcements.json");
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            LOGGER.error("[Announcement] Failed to create data dir", e);
        }
        load();
        LOGGER.info("[Announcement] Loaded {} announcements", entries.size());
    }

    public synchronized List<AnnouncementEntry> getEntries() {
        return new ArrayList<>(entries);
    }

    /** 发布公告：追加 → 落盘 → 广播列表 → 广播顶部横幅 */
    public synchronized void publish(MinecraftServer server, String title, String content, String publisher) {
        entries.add(new AnnouncementEntry(title, content, publisher, System.currentTimeMillis()));
        save();
        broadcastAll(server);
        String banner = title;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(p, new AnnouncementNewS2CPayload(banner));
        }
    }

    /** 删除公告（index 从 1 开始，对应界面序号） */
    public synchronized boolean remove(int index, MinecraftServer server) {
        if (index < 1 || index > entries.size()) return false;
        entries.remove(index - 1);
        save();
        broadcastAll(server);
        return true;
    }

    public synchronized void clear(MinecraftServer server) {
        entries.clear();
        save();
        broadcastAll(server);
    }

    /** 向单个玩家推送完整列表 */
    public void syncToPlayer(ServerPlayer player) {
        ServerPlayNetworking.send(player, new AnnouncementSyncS2CPayload(toJson()));
    }

    /** 广播完整列表给所有在线玩家 */
    public void broadcastAll(MinecraftServer server) {
        String json = toJson();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(p, new AnnouncementSyncS2CPayload(json));
        }
    }

    private String toJson() {
        JsonArray arr = new JsonArray();
        for (AnnouncementEntry e : entries) {
            JsonObject o = new JsonObject();
            o.addProperty("title", e.title());
            o.addProperty("content", e.content());
            o.addProperty("publisher", e.publisher());
            o.addProperty("timestamp", e.timestamp());
            arr.add(o);
        }
        return GSON.toJson(arr);
    }

    private void load() {
        entries.clear();
        if (dataFile == null || !Files.exists(dataFile)) return;
        try {
            JsonElement root = JsonParser.parseString(Files.readString(dataFile));
            if (!root.isJsonArray()) return;
            for (JsonElement el : root.getAsJsonArray()) {
                JsonObject o = el.getAsJsonObject();
                entries.add(new AnnouncementEntry(
                        str(o, "title"), str(o, "content"), str(o, "publisher"),
                        o.has("timestamp") ? o.get("timestamp").getAsLong() : 0L));
            }
        } catch (Exception e) {
            LOGGER.error("[Announcement] Failed to load data", e);
        }
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }

    private void save() {
        if (dataFile == null) return;
        try {
            Files.createDirectories(dataFile.getParent());
            Files.writeString(dataFile, toJson());
        } catch (IOException e) {
            LOGGER.error("[Announcement] Failed to save data", e);
        }
    }
}