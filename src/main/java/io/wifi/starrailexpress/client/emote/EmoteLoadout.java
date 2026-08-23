package io.wifi.starrailexpress.client.emote;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.emote.EmoteType;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 表情装配（纯客户端）：罗盘共 {@value SLOT_COUNT} 个槽位，所有表情默认解锁，
 * 玩家可在装配界面自由装配/卸下。装配结果持久化到 config/emote_loadout.json。
 */
public final class EmoteLoadout {

    /** 表情罗盘槽位数量 */
    public static final int SLOT_COUNT = 8;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final EmoteType[] SLOTS = new EmoteType[SLOT_COUNT];
    private static boolean loaded = false;

    private EmoteLoadout() {
    }

    public static EmoteType get(int index) {
        ensureLoaded();
        return index >= 0 && index < SLOT_COUNT ? SLOTS[index] : null;
    }

    public static void set(int index, EmoteType emote) {
        ensureLoaded();
        if (index >= 0 && index < SLOT_COUNT) {
            SLOTS[index] = emote;
        }
    }

    /**
     * 恢复默认装配：按表情定义顺序填满前若干个槽位
     */
    public static void resetDefaults() {
        EmoteType[] all = EmoteType.values();
        for (int i = 0; i < SLOT_COUNT; i++) {
            SLOTS[i] = i < all.length ? all[i] : null;
        }
    }

    /**
     * 查找第一个空槽位，找不到返回 -1
     */
    public static int firstEmptySlot() {
        ensureLoaded();
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (SLOTS[i] == null) {
                return i;
            }
        }
        return -1;
    }

    private static void ensureLoaded() {
        if (!loaded) {
            loaded = true;
            resetDefaults();
            loadFromFile();
        }
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("emote_loadout.json");
    }

    private static void loadFromFile() {
        try {
            Path path = file();
            if (!Files.exists(path)) {
                save();
                return;
            }
            String json = Files.readString(path, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray arr = root.getAsJsonArray("slots");
            if (arr == null) {
                return;
            }
            for (int i = 0; i < SLOT_COUNT && i < arr.size(); i++) {
                JsonElement el = arr.get(i);
                SLOTS[i] = el.isJsonNull() ? null : EmoteType.byId(el.getAsString());
            }
        } catch (Exception e) {
            SRE.LOGGER.warn("[Emote] Failed to load emote loadout, using defaults", e);
        }
    }

    /**
     * 保存装配到文件（在装配界面关闭时调用）
     */
    public static void save() {
        ensureLoaded();
        try {
            JsonObject root = new JsonObject();
            JsonArray arr = new JsonArray();
            for (int i = 0; i < SLOT_COUNT; i++) {
                if (SLOTS[i] == null) {
                    arr.add(com.google.gson.JsonNull.INSTANCE);
                } else {
                    arr.add(SLOTS[i].id());
                }
            }
            root.add("slots", arr);
            Path path = file();
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception e) {
            SRE.LOGGER.warn("[Emote] Failed to save emote loadout", e);
        }
    }
}
