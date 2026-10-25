package io.wifi.starrailexpress.announcement.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.wifi.starrailexpress.announcement.AnnouncementEntry;

import java.util.ArrayList;
import java.util.List;

/** 客户端公告缓存 + 待显示的顶部横幅 */
public final class AnnouncementClientCache {
    private static final List<AnnouncementEntry> entries = new ArrayList<>();
    private static volatile String bannerTitle = null;
    private static volatile long bannerExpireAt = 0L;

    public static void setFromJson(String json) {
        List<AnnouncementEntry> parsed = new ArrayList<>();
        try {
            JsonElement root = JsonParser.parseString(json);
            if (root.isJsonArray()) {
                JsonArray arr = root.getAsJsonArray();
                for (JsonElement el : arr) {
                    JsonObject o = el.getAsJsonObject();
                    parsed.add(new AnnouncementEntry(
                            str(o, "title"), str(o, "content"), str(o, "publisher"),
                            o.has("timestamp") ? o.get("timestamp").getAsLong() : 0L));
                }
            }
        } catch (Exception ignored) {}
        entries.clear();
        entries.addAll(parsed);
    }

    public static List<AnnouncementEntry> get() { return entries; }

    public static void showBanner(String title) {
        bannerTitle = title;
        bannerExpireAt = System.currentTimeMillis() + 6000L;
    }

    public static String bannerTitle() { return bannerTitle; }

    public static long bannerExpireAt() { return bannerExpireAt; }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }
}