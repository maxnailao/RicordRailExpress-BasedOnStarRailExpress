package io.wifi.starrailexpress.event;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.wifi.starrailexpress.cca.CS2InventoryComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.data.PlayerEconomyManager;
import io.wifi.starrailexpress.event.network.EventProgressS2CPayload;
import io.wifi.starrailexpress.event.network.EventSyncS2CPayload;
import io.wifi.starrailexpress.util.ItemSkinManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 活动管理器（服务端权威）：
 * 列表落盘 config/sre_events/events.json，图片 config/sre_events/images/，
 * 击杀进度落盘 progress.json（uuid -> eventId -> [count, claimed]），
 * 达成 kill 条件自动发放结构化奖励（coin/shard/draw/skin）。
 */
public class EventManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("SREEvent");
    private static final com.google.gson.Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static EventManager instance;

    private final List<EventEntry> entries = new ArrayList<>();
    private final Map<String, Map<String, PackProgress>> progress = new HashMap<>();
    private Path dataFile;
    private Path imageDir;
    private Path progressFile;

    private EventManager() {}

    public static EventManager getInstance() {
        if (instance == null) instance = new EventManager();
        return instance;
    }

    public void init(Path configDir) {
        Path base = configDir.resolve("sre_events");
        this.dataFile = base.resolve("events.json");
        this.imageDir = base.resolve("images");
        this.progressFile = base.resolve("progress.json");
        try {
            Files.createDirectories(imageDir);
        } catch (IOException e) {
            LOGGER.error("[Event] Failed to create data dir", e);
        }
        load();
        loadProgress();
        LOGGER.info("[Event] Loaded {} events", entries.size());
    }

    public synchronized List<EventEntry> getEntries() {
        return new ArrayList<>(entries);
    }

    /** 添加活动（新活动一律未置顶，置顶用 setFeatured） */
    public synchronized void add(String title, String content, String rewards,
                                 String startDate, String endDate, String condition,
                                 String imageId, MinecraftServer server) {
        entries.add(new EventEntry("evt_" + System.currentTimeMillis(), title, content, rewards,
                condition == null ? "" : condition, startDate, endDate, false,
                imageId == null ? "" : imageId, System.currentTimeMillis()));
        save();
        broadcastAll(server);
    }

    /** 全局唯一大活动：id 命中者置顶，其余全部取消；id 为空 = 清空置顶 */
    public synchronized void setFeatured(String id, MinecraftServer server) {
        String targetId = id == null ? "" : id;
        for (int i = 0; i < entries.size(); i++) {
            EventEntry e = entries.get(i);
            entries.set(i, new EventEntry(e.id(), e.title(), e.content(), e.rewards(), e.condition(),
                    e.startDate(), e.endDate(), !targetId.isEmpty() && e.id().equals(targetId),
                    e.imageId(), e.createdAt()));
        }
        save();
        broadcastAll(server);
    }

    public synchronized boolean remove(String id, MinecraftServer server) {
        boolean removed = entries.removeIf(e -> e.id().equals(id));
        if (removed) {
            for (Map<String, PackProgress> m : progress.values()) {
                m.remove(id);
                m.keySet().removeIf(k -> k.startsWith(id + "#"));
            }
            save();
            saveProgress();
            broadcastAll(server);
        }
        return removed;
    }

    // ── 击杀条件与进度 ──

    /** 当前日期是否在活动起止窗口内（起止空则不限制；解析失败视为期内） */
    private static boolean inPeriod(EventEntry e) {
        LocalDate now = LocalDate.now();
        try {
            if (!e.startDate().isEmpty() && now.isBefore(LocalDate.parse(e.startDate().trim()))) return false;
            if (!e.endDate().isEmpty() && now.isAfter(LocalDate.parse(e.endDate().trim()))) return false;
        } catch (Exception ignored) {
        }
        return true;
    }

    /** 任务包进度键：活动id#包下标 */
    public static String packKey(String eventId, int idx) {
        return eventId + "#" + idx;
    }

    /** 击杀进度（模型 B：包内每条可计数条件独立累加，全部达标才发该包奖励）。 */
    public synchronized void onKill(ServerPlayer killer, OnPlayerKilledPlayer.DeathReason reason) {
        String uuid = killer.getUUID().toString();
        String roleId = currentRoleId(killer);
        String weapon = weaponTypeOf(reason);
        for (EventEntry e : new ArrayList<>(entries)) {
            if (!inPeriod(e)) continue;
            List<EventPack> packs = EventPack.parse(e.condition(), e.rewards());
            Map<String, PackProgress> mine = progress.computeIfAbsent(uuid, k -> new HashMap<>());
            boolean changed = false;
            for (int i = 0; i < packs.size(); i++) {
                EventPack pack = packs.get(i);
                List<EventCondition> conds = pack.parsedConditions();
                boolean trackable = false;
                for (EventCondition c : conds) {
                    if (c.countable()) { trackable = true; break; }
                }
                if (!trackable) continue;
                String key = packKey(e.id(), i);
                PackProgress p = mine.computeIfAbsent(key, k -> PackProgress.ofSize(conds.size()));
                p.counts = resizeCounts(p.counts, conds.size());
                if (p.claimed) continue;
                for (int j = 0; j < conds.size(); j++) {
                    EventCondition c = conds.get(j);
                    if (c.countable() && matches(c, roleId, weapon) && p.counts[j] < c.target()) {
                        p.counts[j]++;
                    }
                }
                boolean done = true;
                for (int j = 0; j < conds.size(); j++) {
                    EventCondition c = conds.get(j);
                    if (c.countable() && p.counts[j] < c.target()) { done = false; break; }
                }
                if (done) {
                    p.claimed = true;
                    grantRewards(killer, String.join(";", pack.rewards()));
                    killer.displayClientMessage(Component.literal("活动「" + e.title()
                            + "」任务" + (i + 1) + "完成，奖励已发放").withStyle(ChatFormatting.GREEN), false);
                    changed = true;
                }
                sendProgress(killer, key, p.counts, p.claimed, false);
            }
            if (changed) saveProgress();
        }
    }

    private static boolean matches(EventCondition c, String roleId, String weapon) {
        switch (c.kind()) {
            case EventCondition.ANY:
                return true;
            case EventCondition.ROLE:
                return roleId != null && c.arg() != null && roleId.equalsIgnoreCase(c.arg());
            case EventCondition.WEAPON:
                return weapon != null && weapon.equalsIgnoreCase(c.arg());
            default:
                return false;
        }
    }

    private static String currentRoleId(ServerPlayer killer) {
        try {
            SRERole role = SREGameWorldComponent.KEY.get(killer.level()).getRole(killer);
            return role != null ? role.identifier().toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** DeathReason → 武器类别字符串；非武器类（中毒/未知/其它）返回 null，不匹配任何 kill_weapon 条件 */
    private static String weaponTypeOf(OnPlayerKilledPlayer.DeathReason r) {
        if (r == null) return null;
        switch (r) {
            case KNIFE: return "knife";
            case GUN_SHOOT: return "gun";
            case BAT: return "bat";
            case GRENADE: return "grenade";
            case ARROW: return "bow";
            case TRIDENT: return "trident";
            default: return null;
        }
    }

    private static int[] resizeCounts(int[] old, int n) {
        if (old != null && old.length == n) return old;
        int[] arr = new int[Math.max(0, n)];
        if (old != null) System.arraycopy(old, 0, arr, 0, Math.min(old.length, arr.length));
        return arr;
    }

    private void grantRewards(ServerPlayer p, String rewards) {
        if (rewards == null || rewards.isEmpty()) return;
        for (String raw : rewards.split(";")) {
            String tok = raw.trim();
            if (tok.isEmpty()) continue;
            try {
                int c = tok.indexOf(':');
                if (c <= 0) continue; // 旧自由文本不发奖
                String key = tok.substring(0, c).trim();
                String val = tok.substring(c + 1).trim();
                switch (key) {
                    case "coin" -> PlayerEconomyManager.addCoinNum(p, Integer.parseInt(val));
                    case "shard" -> CS2InventoryComponent.KEY.get(p).addMythicShards(Integer.parseInt(val));
                    case "draw" -> PlayerEconomyManager.addLootChance(p, Integer.parseInt(val));
                    case "skin" -> {
                        int s = val.indexOf('/');
                        if (s > 0) {
                            ItemSkinManager.unlockSkinForItemType(p, val.substring(0, s), val.substring(s + 1));
                        }
                    }
                    default -> {
                    }
                }
            } catch (Exception ex) {
                LOGGER.warn("[Event] 发放奖励片段失败: {}", tok, ex);
            }
        }
    }

    /** 领取无条件任务包奖励：条件必须为空、奖励非空、每玩家每包仅一次 */
    public synchronized void claim(ServerPlayer p, String packKey) {
        int hash = packKey.lastIndexOf('#');
        if (hash < 0) {
            notifyClaim(p, "领取失败：任务键格式错误", true);
            return;
        }
        String eventId = packKey.substring(0, hash);
        int idx;
        try {
            idx = Integer.parseInt(packKey.substring(hash + 1));
        } catch (Exception e) {
            notifyClaim(p, "领取失败：任务键格式错误", true);
            return;
        }
        EventEntry e = null;
        for (EventEntry t : entries) {
            if (t.id().equals(eventId)) { e = t; break; }
        }
        if (e == null) { notifyClaim(p, "领取失败：活动不存在或已结束", true); return; }
        if (!inPeriod(e)) { notifyClaim(p, "领取失败：当前不在活动期内", true); return; }
        List<EventPack> packs = EventPack.parse(e.condition(), e.rewards());
        if (idx < 0 || idx >= packs.size()) { notifyClaim(p, "领取失败：任务不存在", true); return; }
        EventPack pack = packs.get(idx);
        if (!pack.conditions().isEmpty()) { notifyClaim(p, "该任务需完成条件后自动发放", true); return; }
        if (pack.rewards().isEmpty()) { notifyClaim(p, "该任务没有奖励", true); return; }
        String uuid = p.getUUID().toString();
        Map<String, PackProgress> mine = progress.computeIfAbsent(uuid, k -> new HashMap<>());
        PackProgress pr = mine.computeIfAbsent(packKey, k -> PackProgress.ofSize(0));
        if (pr.claimed) { notifyClaim(p, "奖励已经领取过了", true); return; }
        pr.claimed = true;
        grantRewards(p, String.join(";", pack.rewards()));
        saveProgress();
        sendProgress(p, packKey, pr.counts, true, false);
        notifyClaim(p, "活动「" + e.title() + "」奖励已领取", false);
    }

    private void notifyClaim(ServerPlayer p, String msg, boolean red) {
        p.displayClientMessage(Component.literal(msg)
                .withStyle(red ? ChatFormatting.RED : ChatFormatting.GREEN), false);
    }

    private synchronized void sendProgresses(ServerPlayer p) {
        String uuid = p.getUUID().toString();
        Map<String, PackProgress> mine = progress.get(uuid);
        for (EventEntry e : entries) {
            List<EventPack> packs = EventPack.parse(e.condition(), e.rewards());
            for (int i = 0; i < packs.size(); i++) {
                EventPack pack = packs.get(i);
                String key = packKey(e.id(), i);
                PackProgress pr = mine == null ? null : mine.get(key);
                boolean claimed = pr != null && pr.claimed;
                boolean manual = pack.conditions().isEmpty() && !pack.rewards().isEmpty();
                List<EventCondition> conds = pack.parsedConditions();
                boolean trackable = false;
                for (EventCondition c : conds) {
                    if (c.countable()) { trackable = true; break; }
                }
                if (!trackable && !manual) continue;
                if (manual) {
                    sendProgress(p, key, new int[0], claimed, !claimed);
                } else {
                    int[] counts = pr == null ? new int[conds.size()] : resizeCounts(pr.counts, conds.size());
                    sendProgress(p, key, counts, claimed, false);
                }
            }
        }
    }

    private void sendProgress(ServerPlayer p, String key, int[] counts, boolean claimed, boolean claimable) {
        try {
            ServerPlayNetworking.send(p, new EventProgressS2CPayload(key, counts, claimed, claimable));
        } catch (Exception ignored) {
        }
    }

    // ── 图片 ──

    public boolean writeImage(String imageId, byte[] data) {
        String safe = sanitize(imageId);
        if (safe.isEmpty() || data == null || data.length == 0 || data.length > 1_500_000) return false;
        try {
            Files.createDirectories(imageDir);
            Files.write(imageDir.resolve(safe + ".png"), data);
            return true;
        } catch (IOException e) {
            LOGGER.error("[Event] Failed to write image {}", imageId, e);
            return false;
        }
    }

    public byte[] readImage(String imageId) {
        String safe = sanitize(imageId);
        if (safe.isEmpty()) return null;
        try {
            Path file = imageDir.resolve(safe + ".png");
            return Files.exists(file) ? Files.readAllBytes(file) : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static String sanitize(String id) {
        if (id == null) return "";
        String s = id.replaceAll("[^A-Za-z0-9_.\\-]", "");
        return s.contains("..") ? "" : s;
    }

    // ── 同步 ──

    public void syncToPlayer(ServerPlayer player) {
        ServerPlayNetworking.send(player, new EventSyncS2CPayload(toJson()));
        sendProgresses(player);
    }

    public void broadcastAll(MinecraftServer server) {
        String json = toJson();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(p, new EventSyncS2CPayload(json));
            sendProgresses(p);
        }
    }

    // ── 序列化 ──

    private String toJson() {
        JsonArray arr = new JsonArray();
        for (EventEntry e : entries) {
            JsonObject o = new JsonObject();
            o.addProperty("id", e.id());
            o.addProperty("title", e.title());
            o.addProperty("content", e.content());
            o.addProperty("rewards", e.rewards());
            o.addProperty("condition", e.condition());
            o.addProperty("startDate", e.startDate());
            o.addProperty("endDate", e.endDate());
            o.addProperty("featured", e.featured());
            o.addProperty("imageId", e.imageId());
            o.addProperty("createdAt", e.createdAt());
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
                entries.add(new EventEntry(
                        str(o, "id"), str(o, "title"), str(o, "content"), str(o, "rewards"),
                        str(o, "condition"), str(o, "startDate"), str(o, "endDate"),
                        o.has("featured") && o.get("featured").getAsBoolean(),
                        str(o, "imageId"),
                        o.has("createdAt") ? o.get("createdAt").getAsLong() : 0L));
            }
        } catch (Exception e) {
            LOGGER.error("[Event] Failed to load data", e);
        }
    }

    private void loadProgress() {
        progress.clear();
        if (progressFile == null || !Files.exists(progressFile)) return;
        try {
            JsonElement rootEl = JsonParser.parseString(Files.readString(progressFile));
            if (!rootEl.isJsonObject()) return;
            JsonObject root = rootEl.getAsJsonObject();
            for (String uuid : root.keySet()) {
                JsonElement ue = root.get(uuid);
                if (!ue.isJsonObject()) continue;
                JsonObject evs = ue.getAsJsonObject();
                Map<String, PackProgress> m = new HashMap<>();
                for (String key : evs.keySet()) {
                    JsonElement v = evs.get(key);
                    if (v.isJsonArray()) {
                        // 旧格式 [count, claimed]：迁移为单条件计数向量
                        JsonArray a = v.getAsJsonArray();
                        int cnt = a.size() > 0 ? a.get(0).getAsInt() : 0;
                        boolean claimed = a.size() > 1 && a.get(1).getAsInt() == 1;
                        m.put(key, new PackProgress(claimed, false, new int[]{cnt}));
                    } else if (v.isJsonObject()) {
                        JsonObject o = v.getAsJsonObject();
                        boolean claimed = o.has("claimed") && o.get("claimed").getAsBoolean();
                        boolean claimable = o.has("claimable") && o.get("claimable").getAsBoolean();
                        JsonArray ca = o.has("counts") ? o.getAsJsonArray("counts") : new JsonArray();
                        int[] counts = new int[ca.size()];
                        for (int i = 0; i < ca.size(); i++) counts[i] = ca.get(i).getAsInt();
                        m.put(key, new PackProgress(claimed, claimable, counts));
                    }
                }
                progress.put(uuid, m);
            }
        } catch (Exception e) {
            LOGGER.error("[Event] Failed to load progress", e);
        }
    }

    private void saveProgress() {
        if (progressFile == null) return;
        try {
            Files.createDirectories(progressFile.getParent());
            JsonObject root = new JsonObject();
            for (Map.Entry<String, Map<String, PackProgress>> ue : progress.entrySet()) {
                JsonObject um = new JsonObject();
                for (Map.Entry<String, PackProgress> ke : ue.getValue().entrySet()) {
                    PackProgress p = ke.getValue();
                    JsonObject po = new JsonObject();
                    po.addProperty("claimed", p.claimed);
                    po.addProperty("claimable", p.claimable);
                    JsonArray ca = new JsonArray();
                    for (int v : p.counts) ca.add(v);
                    po.add("counts", ca);
                    um.add(ke.getKey(), po);
                }
                root.add(ue.getKey(), um);
            }
            Files.writeString(progressFile, GSON.toJson(root));
        } catch (IOException e) {
            LOGGER.error("[Event] Failed to save progress", e);
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
            LOGGER.error("[Event] Failed to save data", e);
        }
    }
}