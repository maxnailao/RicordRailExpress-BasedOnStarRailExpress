package org.agmas.noellesroles.cs2;

import com.google.gson.*;
import io.wifi.starrailexpress.cca.CS2InventoryComponent;
import io.wifi.starrailexpress.content.musicbox.MusicBox;
import io.wifi.starrailexpress.content.musicbox.MusicBoxRegistry;
import io.wifi.starrailexpress.data.PlayerEconomyManager;
import io.wifi.starrailexpress.util.ItemSkinManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.cs2.network.DailyShopSyncS2CPayload;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 每日商店管理器
 * <p>
 * 每天 00:00（跨天）刷新，随机提供 4 件商品（武器皮肤 / 刀皮 / 音乐盒）。
 * 品质抽取概率：白25% 绿25% 蓝20% 紫15% 橙10% 红5%。
 * 数据实时写入 JSON 文件，跨重启保持当日商店与已购记录。
 * </p>
 */
public class DailyShopManager {

    private static DailyShopManager instance;

    /** 每日商店商品数量 */
    public static final int ITEM_COUNT = 4;

    /** 参与抽取的品质档位（白/绿/蓝/紫/橙/红 → 品质索引 0/1/2/3/4/5） */
    private static final int[] QUALITY_TIERS = {0, 1, 2, 3, 4, 5};
    /** 各档位抽取概率：白25% 绿25% 蓝20% 紫15% 橙10% 红5% */
    private static final double[] QUALITY_WEIGHTS = {0.25, 0.25, 0.20, 0.15, 0.10, 0.05};

    /** 不参与抽取的皮肤（测试皮 / 多形态变体） */
    private static final Set<String> EXCLUDED_SKINS = Set.of(
            "knife/testofknifeskin",
            "knife/knife_anxing_1", "knife/knife_anxing_2",
            "knife/knife_emozhidao_2",
            "revolver/revolver_shengxuan_1", "revolver/revolver_shengxuan_2");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private String refreshDate;
    private final List<DailyItem> items = new ArrayList<>();
    /** 当日各玩家已购买槽位：uuid -> slots */
    private final Map<String, Set<Integer>> purchases = new ConcurrentHashMap<>();

    private Path dataFile;

    public static class DailyItem {
        public String itemId;   // "knife/xxx" 或 "musicbox/xxx"
        public String itemType; // "skin" 或 "musicbox"
        public int quality;
        public int price;
    }

    private static class DailyShopData {
        String refreshDate;
        List<DailyItem> items = new ArrayList<>();
        Map<String, List<Integer>> purchases = new HashMap<>();
    }

    private DailyShopManager() {}

    public static DailyShopManager getInstance() {
        if (instance == null) instance = new DailyShopManager();
        return instance;
    }

    public void init(Path dataDir) {
        this.dataFile = dataDir.resolve("daily_shop_data.json");
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            Noellesroles.LOGGER.error("[DailyShop] Failed to create data dir", e);
        }
        load();
        ensureFresh();
        Noellesroles.LOGGER.info("[DailyShop] Initialized, {} items, refreshDate={}", items.size(), refreshDate);
    }

    /** 跨天则刷新（每日 00:00 后首次访问即触发） */
    public synchronized void ensureFresh() {
        String today = LocalDate.now().toString();
        if (!today.equals(refreshDate) || items.isEmpty()) {
            regenerate(today);
        }
    }

    private void regenerate(String today) {
        items.clear();
        purchases.clear();
        Map<Integer, List<String>> pool = buildPoolByQuality();
        RandomSource rand = RandomSource.create();
        for (int slot = 0; slot < ITEM_COUNT; slot++) {
            int quality = rollQuality(rand, pool);
            String itemId = pickItem(pool, quality, rand);
            if (itemId == null) continue;
            DailyItem item = new DailyItem();
            item.itemId = itemId;
            item.itemType = itemId.startsWith("musicbox/") ? "musicbox" : "skin";
            item.quality = quality;
            item.price = priceForQuality(quality);
            items.add(item);
        }
        refreshDate = today;
        save();
        Noellesroles.LOGGER.info("[DailyShop] Refreshed for {}: {} items", today, items.size());
    }

    /** 构建 品质档位 -> 候选物品ID 列表 */
    private Map<Integer, List<String>> buildPoolByQuality() {
        Map<Integer, List<String>> pool = new HashMap<>();
        for (int tier : QUALITY_TIERS) pool.put(tier, new ArrayList<>());

        // 皮肤（刀/左轮/球棒/手雷/帽子）：来自注册表，按颜色映射品质
        for (Map.Entry<String, HashMap<String, ItemSkinManager.Skin>> typeEntry
                : ItemSkinManager.getSkins().entrySet()) {
            String itemType = typeEntry.getKey();
            for (Map.Entry<String, ItemSkinManager.Skin> skinEntry : typeEntry.getValue().entrySet()) {
                String skinName = skinEntry.getKey();
                if (skinName.equalsIgnoreCase("default")) continue;
                String skinId = itemType + "/" + skinName;
                if (EXCLUDED_SKINS.contains(skinId)) continue;
                int rep = representativeTier(colorToQuality(skinEntry.getValue().getColor()));
                List<String> list = pool.get(rep);
                if (list != null) list.add(skinId);
            }
        }

        // 音乐盒：均匀分配到 6 个档位，参与品质抽取
        int i = 0;
        for (MusicBox box : MusicBoxRegistry.getAll()) {
            pool.get(QUALITY_TIERS[i % QUALITY_TIERS.length]).add("musicbox/" + box.id());
            i++;
        }
        return pool;
    }

    /** 品质档位直接对应（0~5），未收录的品质归入白档(0) */
    private static int representativeTier(int quality) {
        for (int tier : QUALITY_TIERS) if (tier == quality) return quality;
        return 0;
    }

    private static int colorToQuality(int color) {
        int argb = color | 0xFF000000;
        ItemSkinManager.QualityColor[] values = ItemSkinManager.QualityColor.values();
        for (int i = 0; i < values.length; i++) {
            if ((values[i].getColor() | 0xFF000000) == argb) return i;
        }
        return 0;
    }

    private int rollQuality(RandomSource rand, Map<Integer, List<String>> pool) {
        double total = 0;
        for (int i = 0; i < QUALITY_TIERS.length; i++) {
            if (!pool.get(QUALITY_TIERS[i]).isEmpty()) total += QUALITY_WEIGHTS[i];
        }
        if (total <= 0) return QUALITY_TIERS[0];
        double r = rand.nextDouble() * total;
        double acc = 0;
        for (int i = 0; i < QUALITY_TIERS.length; i++) {
            if (pool.get(QUALITY_TIERS[i]).isEmpty()) continue;
            acc += QUALITY_WEIGHTS[i];
            if (r < acc) return QUALITY_TIERS[i];
        }
        return QUALITY_TIERS[0];
    }

    private String pickItem(Map<Integer, List<String>> pool, int quality, RandomSource rand) {
        List<String> list = pool.get(quality);
        if (list != null && !list.isEmpty()) return list.get(rand.nextInt(list.size()));
        for (int tier : QUALITY_TIERS) {
            List<String> l = pool.get(tier);
            if (l != null && !l.isEmpty()) return l.get(rand.nextInt(l.size()));
        }
        return null;
    }

    private static int priceForQuality(int quality) {
        return switch (quality) {
            case 2 -> 400;
            case 3 -> 900;
            case 4 -> 2500;
            case 5 -> 6000;
            default -> 150;
        };
    }

    public boolean buy(ServerPlayer player, int slot) {
        ensureFresh();
        if (slot < 0 || slot >= items.size()) return false;
        String uuid = player.getUUID().toString();
        Set<Integer> bought = purchases.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet());
        if (bought.contains(slot)) {
            player.displayClientMessage(Component.literal("§c今日已购买过该商品"), true);
            return false;
        }
        DailyItem item = items.get(slot);
        int coins = PlayerEconomyManager.getCoinNum(player);
        if (coins < item.price) {
            player.displayClientMessage(Component.literal("§c货币不足，需要 " + item.price + " 货币"), true);
            return false;
        }
        PlayerEconomyManager.addCoinNum(player, -item.price);
        CS2InventoryComponent inv = CS2InventoryComponent.KEY.get(player);
        grant(player, inv, item.itemId);
        inv.sync();
        bought.add(slot);
        save();
        syncToPlayer(player);
        player.displayClientMessage(
                Component.literal("§a每日商店购买成功: " + displayName(item) + " (-" + item.price + " 货币)"), true);
        Noellesroles.LOGGER.info("[DailyShop] {} bought slot {} ({}) for {} coins",
                player.getName().getString(), slot, item.itemId, item.price);
        return true;
    }

    private void grant(ServerPlayer player, CS2InventoryComponent inv, String itemId) {
        if (itemId.startsWith("musicbox/")) {
            inv.addMusicBox(itemId.substring("musicbox/".length()), 1);
        } else {
            String[] parts = itemId.split("/", 2);
            if (parts.length == 2) {
                ItemSkinManager.unlockSkinForItemType(player, parts[0], parts[1]);
                inv.addSkin(itemId, 1);
            }
        }
    }

    private String displayName(DailyItem item) {
        if ("musicbox".equals(item.itemType)) {
            MusicBox box = MusicBoxRegistry.get(item.itemId.substring("musicbox/".length()));
            return box != null ? box.displayName().getString() : item.itemId;
        }
        return CS2SkinInfo.getName(item.itemId);
    }

    public void syncToPlayer(ServerPlayer player) {
        ensureFresh();
        String uuid = player.getUUID().toString();
        Set<Integer> bought = purchases.getOrDefault(uuid, Collections.emptySet());
        JsonObject root = new JsonObject();
        root.addProperty("refreshDate", refreshDate);
        JsonArray arr = new JsonArray();
        for (int i = 0; i < items.size(); i++) {
            DailyItem it = items.get(i);
            JsonObject o = new JsonObject();
            o.addProperty("slot", i);
            o.addProperty("itemId", it.itemId);
            o.addProperty("itemType", it.itemType);
            o.addProperty("quality", it.quality);
            o.addProperty("price", it.price);
            o.addProperty("purchased", bought.contains(i));
            arr.add(o);
        }
        root.add("items", arr);
        ServerPlayNetworking.send(player, new DailyShopSyncS2CPayload(GSON.toJson(root)));
    }

    // ── 持久化 ──

    private void load() {
        if (dataFile == null || !Files.exists(dataFile)) return;
        try {
            DailyShopData data = GSON.fromJson(Files.readString(dataFile), DailyShopData.class);
            if (data != null) {
                refreshDate = data.refreshDate;
                items.clear();
                if (data.items != null) items.addAll(data.items);
                purchases.clear();
                if (data.purchases != null) {
                    for (Map.Entry<String, List<Integer>> e : data.purchases.entrySet()) {
                        Set<Integer> set = ConcurrentHashMap.newKeySet();
                        if (e.getValue() != null) set.addAll(e.getValue());
                        purchases.put(e.getKey(), set);
                    }
                }
            }
        } catch (Exception e) {
            Noellesroles.LOGGER.error("[DailyShop] Failed to load data", e);
        }
    }

    private void save() {
        if (dataFile == null) return;
        try {
            Files.createDirectories(dataFile.getParent());
            DailyShopData data = new DailyShopData();
            data.refreshDate = refreshDate;
            data.items = new ArrayList<>(items);
            data.purchases = new HashMap<>();
            for (Map.Entry<String, Set<Integer>> e : purchases.entrySet()) {
                data.purchases.put(e.getKey(), new ArrayList<>(e.getValue()));
            }
            Files.writeString(dataFile, GSON.toJson(data));
        } catch (IOException e) {
            Noellesroles.LOGGER.error("[DailyShop] Failed to save data", e);
        }
    }
}
