package org.agmas.noellesroles.cs2;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import io.wifi.starrailexpress.backpack.BackpackManager;
import io.wifi.starrailexpress.cca.CS2InventoryComponent;
import io.wifi.starrailexpress.cca.SREPlayerSkinsComponent;
import io.wifi.starrailexpress.data.PlayerEconomyManager;
import io.wifi.starrailexpress.progression.ProgressionState.FactionCardType;
import io.wifi.starrailexpress.util.ItemSkinManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.cs2.network.BlackMarketSyncS2CPayload;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * CS2 黑市管理器
 * <p>
 * 管理玩家间的物品交易。数据实时写入 JSON 文件防止丢失。
 * </p>
 */
public class CS2BlackMarketManager {

    private static CS2BlackMarketManager instance;

    /** 每人最大上架数量 */
    public static final int MAX_LISTINGS_PER_PLAYER = 5;

    /** 黑市挂单列表 */
    private final List<MarketListing> listings = new CopyOnWriteArrayList<>();

    /** 离线卖家待领取的货币：sellerUuid -> 累计待领金额 */
    private final Map<String, Integer> pendingCoins = new ConcurrentHashMap<>();

    /** 离线卖家待领取的神话碎片：sellerUuid -> 累计待领碎片 */
    private final Map<String, Integer> pendingShards = new ConcurrentHashMap<>();

    /** 黑市税率（0.0 ~ 1.0），自由市场默认 1%（由卖家承担，买家支付全额） */
    private double taxRate = 0.01;

    /** 数据文件路径 */
    private Path dataFile;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** 黑市挂单 */
    public static class MarketListing {
        public String listingId;
        public String sellerUuid;
        public String sellerName;
        public String itemType; // "box", "skin"
        public String itemId;
        public int price;
        public String currency = "coin"; // "coin" | "shard"
        public long timestamp;

        public MarketListing() {}

        public MarketListing(String sellerUuid, String sellerName, String itemType, String itemId, int price) {
            this(sellerUuid, sellerName, itemType, itemId, price, "coin");
        }

        public MarketListing(String sellerUuid, String sellerName, String itemType, String itemId, int price, String currency) {
            this.listingId = UUID.randomUUID().toString().substring(0, 8);
            this.sellerUuid = sellerUuid;
            this.sellerName = sellerName;
            this.itemType = itemType;
            this.itemId = itemId;
            this.price = price;
            this.currency = currency;
            this.timestamp = System.currentTimeMillis();
        }
    }

    /** 持久化用包装类 */
    private static class BlackMarketData {
        public List<MarketListing> listings = new ArrayList<>();
        public Map<String, Integer> pendingCoins = new HashMap<>();
        public Map<String, Integer> pendingShards = new HashMap<>();
    }

    private CS2BlackMarketManager() {}

    public static CS2BlackMarketManager getInstance() {
        if (instance == null) {
            instance = new CS2BlackMarketManager();
        }
        return instance;
    }

    /**
     * 初始化，加载黑市数据
     */
    public void init(Path dataDir) {
        this.dataFile = dataDir.resolve("black_market_data.json");
        Noellesroles.LOGGER.info("[CS2Market] Data file path: {}", dataFile.toAbsolutePath());
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            Noellesroles.LOGGER.error("[CS2Market] Failed to create data directory: {}", dataDir.toAbsolutePath(), e);
        }
        load();
        // 如果文件不存在，创建空数据文件以便确认路径正确
        if (!Files.exists(dataFile)) {
            save();
            Noellesroles.LOGGER.info("[CS2Market] Created initial data file at: {}", dataFile.toAbsolutePath());
        }
    }

    public List<MarketListing> getListings() {
        return Collections.unmodifiableList(listings);
    }

    /**
     * 获取指定玩家的当前上架数量
     */
    public int getPlayerListingCount(String playerUuid) {
        int count = 0;
        for (MarketListing l : listings) {
            if (l.sellerUuid.equals(playerUuid)) count++;
        }
        return count;
    }

    /**
     * 获取当前黑市税率
     */
    public double getTaxRate() {
        return taxRate;
    }

    /**
     * 设置黑市税率（0.0 ~ 1.0）
     */
    public void setTaxRate(double taxRate) {
        this.taxRate = Math.max(0.0, Math.min(1.0, taxRate));
    }

    /**
     * 获取指定玩家的待领取货币金额
     */
    public int getPendingCoins(String playerUuid) {
        return pendingCoins.getOrDefault(playerUuid, 0);
    }

    /**
     * 获取指定玩家的待领取神话碎片
     */
    public int getPendingShards(String playerUuid) {
        return pendingShards.getOrDefault(playerUuid, 0);
    }

    /** 币种规范化：仅接受 "shard"，其余一律视为 "coin" */
    private static String normCurrency(String c) {
        return "shard".equals(c) ? "shard" : "coin";
    }

    /**
     * 向指定玩家同步黑市数据
     */
    public void syncToPlayer(ServerPlayer player) {
        String json = GSON.toJson(listings);
        int myPending = getPendingCoins(player.getUUID().toString());
        int myPendingShards = getPendingShards(player.getUUID().toString());
        ServerPlayNetworking.send(player, new BlackMarketSyncS2CPayload(json, myPending, myPendingShards));
    }

    /**
     * 广播黑市数据给所有在线玩家
     */
    public void broadcastToAll(ServerPlayer trigger) {
        if (trigger.getServer() == null) return;
        String json = GSON.toJson(listings);
        for (ServerPlayer p : trigger.getServer().getPlayerList().getPlayers()) {
            int myPending = getPendingCoins(p.getUUID().toString());
            int myPendingShards = getPendingShards(p.getUUID().toString());
            ServerPlayNetworking.send(p, new BlackMarketSyncS2CPayload(json, myPending, myPendingShards));
        }
    }

    /**
     * 上架物品
     */
    public boolean list(ServerPlayer seller, String itemType, String itemId, int price, String currency) {
        if (price <= 0) return false;
        currency = normCurrency(currency);

        // 检查上架数量限制
        String sellerUuid = seller.getUUID().toString();
        int currentCount = 0;
        for (MarketListing l : listings) {
            if (l.sellerUuid.equals(sellerUuid)) currentCount++;
        }
        if (currentCount >= MAX_LISTINGS_PER_PLAYER) {
            seller.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(
                            "§c上架数量已达上限（最多 " + MAX_LISTINGS_PER_PLAYER + " 件），请先下架其他商品"),
                    true);
            return false;
        }

        CS2InventoryComponent inv = CS2InventoryComponent.KEY.get(seller);

        // 验证物品所有权
        if ("box".equals(itemType)) {
            if (inv.getBoxCount(itemId) <= 0) return false;
            inv.removeBox(itemId, 1);
        } else if ("skin".equals(itemType)) {
            if (inv.getSkinCount(itemId) <= 0) return false;
            inv.removeSkin(itemId, 1);
            // 如果上架的皮肤正在装备中，卸下为原皮
            String[] parts = itemId.split("/", 2);
            if (parts.length == 2) {
                String equipItemType = parts[0];
                String skinName = parts[1];
                String currentEquipped = SREPlayerSkinsComponent.KEY.get(seller).getEquippedSkin(equipItemType);
                if (skinName.equals(currentEquipped)) {
                    ItemSkinManager.setEquippedSkinForItemType(seller, equipItemType, "default");
                    ItemSkinManager.sync(seller);
                }
            }
        } else if ("musicbox".equals(itemType)) {
            if (!inv.hasMusicBox(itemId)) return false;
            inv.removeMusicBox(itemId, 1);
            // 如果上架的音乐盒正在装备中，自动卸下
            io.wifi.starrailexpress.content.musicbox.MusicBoxPlayerComponent musicComp =
                    io.wifi.starrailexpress.content.musicbox.MusicBoxPlayerComponent.KEY.get(seller);
            if (itemId.equals(musicComp.getEquippedBox())) {
                musicComp.setEquippedBox(null);
            }
        } else if ("card".equals(itemType)) {
            FactionCardType cardType = FactionCardType.fromString(itemId);
            if (cardType == FactionCardType.NONE) return false;
            if (!BackpackManager.removeCard(seller, cardType, 1)) return false;
        } else {
            return false; // 不支持的类型
        }
        inv.sync();

        MarketListing listing = new MarketListing(
                seller.getUUID().toString(),
                seller.getName().getString(),
                itemType, itemId, price, currency);
        listings.add(listing);
        save();
        broadcastToAll(seller);

        Noellesroles.LOGGER.info("[CS2Market] {} listed {} {} for {} {}",
                seller.getName().getString(), itemType, itemId, price, currency);
        return true;
    }

    /**
     * 购买黑市物品
     * <p>物品给买家，卖家获得扣除税后的金额存入 pendingCoins（需手动领取）</p>
     */
    public boolean buy(ServerPlayer buyer, String listingId) {
        MarketListing listing = null;
        for (MarketListing l : listings) {
            if (l.listingId.equals(listingId)) {
                listing = l;
                break;
            }
        }

        if (listing == null) return false;

        // 不能买自己的
        if (listing.sellerUuid.equals(buyer.getUUID().toString())) return false;

        // 扣款 + 结算按挂单币种
        boolean shard = "shard".equals(listing.currency);
        CS2InventoryComponent buyerInv = CS2InventoryComponent.KEY.get(buyer);

        if (shard) {
            if (!buyerInv.spendMythicShards(listing.price)) return false;
        } else {
            int buyerCoins = PlayerEconomyManager.getCoinNum(buyer);
            if (buyerCoins < listing.price) return false;
            PlayerEconomyManager.addCoinNum(buyer, -listing.price);
        }

        // 给买家物品
        if ("box".equals(listing.itemType)) {
            buyerInv.addBox(listing.itemId, 1);
        } else if ("skin".equals(listing.itemType)) {
            buyerInv.addSkin(listing.itemId, 1);
            // 同步双系统解锁（PlayerEconomyManager + CCA），否则皮肤界面装备校验会被拒
            String[] parts = listing.itemId.split("/");
            if (parts.length >= 2) {
                io.wifi.starrailexpress.util.ItemSkinManager.unlockSkinForItemType(buyer, parts[0], parts[1]);
            }
        } else if ("musicbox".equals(listing.itemType)) {
            buyerInv.addMusicBox(listing.itemId, 1);
        } else if ("card".equals(listing.itemType)) {
            FactionCardType cardType = FactionCardType.fromString(listing.itemId);
            if (cardType != FactionCardType.NONE) {
                BackpackManager.addCard(buyer, cardType, 1);
            }
        }
        buyerInv.sync();

        // 卖家获得扣除税后的金额，按同币种存入对应待领池（需手动领取）
        int sellerIncome = (int) Math.round(listing.price * (1.0 - taxRate));
        if (shard) {
            pendingShards.merge(listing.sellerUuid, sellerIncome, Integer::sum);
        } else {
            pendingCoins.merge(listing.sellerUuid, sellerIncome, Integer::sum);
        }
        Noellesroles.LOGGER.info("[CS2Market] Seller {} earned {} {} (price={}, tax={}%)",
                listing.sellerName, sellerIncome, shard ? "shards" : "coins", listing.price, (int) (taxRate * 100));

        // 移除挂单
        listings.remove(listing);
        save();
        broadcastToAll(buyer);

        Noellesroles.LOGGER.info("[CS2Market] {} bought {} from {}", buyer.getName().getString(), listingId, listing.sellerName);
        return true;
    }

    /**
     * 取消挂单
     */
    public boolean cancelListing(ServerPlayer player, String listingId) {
        MarketListing listing = null;
        for (MarketListing l : listings) {
            if (l.listingId.equals(listingId) && l.sellerUuid.equals(player.getUUID().toString())) {
                listing = l;
                break;
            }
        }
        if (listing == null) return false;

        // 归还物品
        CS2InventoryComponent inv = CS2InventoryComponent.KEY.get(player);
        if ("box".equals(listing.itemType)) {
            inv.addBox(listing.itemId, 1);
        } else if ("skin".equals(listing.itemType)) {
            inv.addSkin(listing.itemId, 1);
        } else if ("musicbox".equals(listing.itemType)) {
            inv.addMusicBox(listing.itemId, 1);
        } else if ("card".equals(listing.itemType)) {
            FactionCardType cardType = FactionCardType.fromString(listing.itemId);
            if (cardType != FactionCardType.NONE) {
                BackpackManager.addCard(player, cardType, 1);
            }
        }
        inv.sync();

        listings.remove(listing);
        save();
        broadcastToAll(player);
        return true;
    }

    /**
     * 玩家手动领取待发放的黑市收入（货币 + 神话碎片一并结算）
     */
    public void claimPendingCoins(ServerPlayer player) {
        String uuid = player.getUUID().toString();
        Integer coins = pendingCoins.remove(uuid);
        Integer shards = pendingShards.remove(uuid);
        boolean gotCoins = coins != null && coins > 0;
        boolean gotShards = shards != null && shards > 0;

        if (gotCoins || gotShards) {
            StringBuilder msg = new StringBuilder("\uD83D\uDCB0 领取成功:");
            boolean first = true;
            if (gotCoins) {
                PlayerEconomyManager.addCoinNum(player, coins);
                msg.append(" +").append(coins).append(" 货币");
                first = false;
            }
            if (gotShards) {
                CS2InventoryComponent inv = CS2InventoryComponent.KEY.get(player);
                inv.addMythicShards(shards);
                inv.sync();
                if (!first) msg.append(" /");
                msg.append(" +").append(shards).append(" 神话碎片");
            }
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(msg.toString())
                            .withStyle(net.minecraft.ChatFormatting.GREEN), true);
            save();
            syncToPlayer(player);
            Noellesroles.LOGGER.info("[CS2Market] {} claimed {} coins + {} shards",
                    player.getName().getString(), gotCoins ? coins : 0, gotShards ? shards : 0);
        } else {
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(
                            "§c没有待领取的黑市收入")
                            .withStyle(net.minecraft.ChatFormatting.RED), true);
        }
    }

    // ── 持久化 ──

    private void load() {
        if (dataFile == null || !Files.exists(dataFile)) return;
        try {
            String json = Files.readString(dataFile);
            // 尝试新格式（包装对象）
            try {
                BlackMarketData data = GSON.fromJson(json, BlackMarketData.class);
                if (data != null) {
                    listings.clear();
                    if (data.listings != null) listings.addAll(data.listings);
                    pendingCoins.clear();
                    if (data.pendingCoins != null) pendingCoins.putAll(data.pendingCoins);
                    pendingShards.clear();
                    if (data.pendingShards != null) pendingShards.putAll(data.pendingShards);
                    Noellesroles.LOGGER.info("[CS2Market] Loaded {} listings, {} pending",
                            listings.size(), pendingCoins.size());
                    return;
                }
            } catch (Exception ignored) {}
            // 回退：旧格式（纯列表）
            Type listType = new TypeToken<List<MarketListing>>() {}.getType();
            List<MarketListing> loaded = GSON.fromJson(json, listType);
            if (loaded != null) {
                listings.clear();
                listings.addAll(loaded);
                Noellesroles.LOGGER.info("[CS2Market] Loaded {} listings (legacy format)", listings.size());
            }
        } catch (Exception e) {
            Noellesroles.LOGGER.error("[CS2Market] Failed to load black market data", e);
        }
    }

    private void save() {
        if (dataFile == null) {
            Noellesroles.LOGGER.warn("[CS2Market] save() called but dataFile is null!");
            return;
        }
        try {
            Files.createDirectories(dataFile.getParent());
            BlackMarketData data = new BlackMarketData();
            data.listings = new ArrayList<>(listings);
            data.pendingCoins = new HashMap<>(pendingCoins);
            data.pendingShards = new HashMap<>(pendingShards);
            Files.writeString(dataFile, GSON.toJson(data));
            Noellesroles.LOGGER.debug("[CS2Market] Saved {} listings, {} pending to {}",
                    listings.size(), pendingCoins.size(), dataFile.toAbsolutePath());
        } catch (IOException e) {
            Noellesroles.LOGGER.error("[CS2Market] Failed to save black market data to {}", dataFile.toAbsolutePath(), e);
        }
    }
}
