package org.agmas.noellesroles.cs2;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import io.wifi.starrailexpress.cca.CS2InventoryComponent;
import io.wifi.starrailexpress.cca.SREPlayerSkinsComponent;
import io.wifi.starrailexpress.data.PlayerEconomyManager;
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

    /** 黑市挂单列表 */
    private final List<MarketListing> listings = new CopyOnWriteArrayList<>();

    /** 离线卖家待领取的货币：sellerUuid -> 累计待领金额 */
    private final Map<String, Integer> pendingCoins = new ConcurrentHashMap<>();

    /** 黑市税率（0.0 ~ 1.0），默认 15% */
    private double taxRate = 0.15;

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
        public long timestamp;

        public MarketListing() {}

        public MarketListing(String sellerUuid, String sellerName, String itemType, String itemId, int price) {
            this.listingId = UUID.randomUUID().toString().substring(0, 8);
            this.sellerUuid = sellerUuid;
            this.sellerName = sellerName;
            this.itemType = itemType;
            this.itemId = itemId;
            this.price = price;
            this.timestamp = System.currentTimeMillis();
        }
    }

    /** 持久化用包装类 */
    private static class BlackMarketData {
        public List<MarketListing> listings = new ArrayList<>();
        public Map<String, Integer> pendingCoins = new HashMap<>();
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
        load();
    }

    public List<MarketListing> getListings() {
        return Collections.unmodifiableList(listings);
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
     * 向指定玩家同步黑市数据
     */
    public void syncToPlayer(ServerPlayer player) {
        String json = GSON.toJson(listings);
        int myPending = getPendingCoins(player.getUUID().toString());
        ServerPlayNetworking.send(player, new BlackMarketSyncS2CPayload(json, myPending));
    }

    /**
     * 广播黑市数据给所有在线玩家
     */
    public void broadcastToAll(ServerPlayer trigger) {
        if (trigger.getServer() == null) return;
        String json = GSON.toJson(listings);
        for (ServerPlayer p : trigger.getServer().getPlayerList().getPlayers()) {
            int myPending = getPendingCoins(p.getUUID().toString());
            ServerPlayNetworking.send(p, new BlackMarketSyncS2CPayload(json, myPending));
        }
    }

    /**
     * 上架物品
     */
    public boolean list(ServerPlayer seller, String itemType, String itemId, int price) {
        if (price <= 0) return false;

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
        } else {
            return false; // 不支持的类型
        }
        inv.sync();

        MarketListing listing = new MarketListing(
                seller.getUUID().toString(),
                seller.getName().getString(),
                itemType, itemId, price);
        listings.add(listing);
        save();
        broadcastToAll(seller);

        Noellesroles.LOGGER.info("[CS2Market] {} listed {} {} for {} coins",
                seller.getName().getString(), itemType, itemId, price);
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

        int buyerCoins = PlayerEconomyManager.getCoinNum(buyer);
        if (buyerCoins < listing.price) return false;

        // 扣款
        PlayerEconomyManager.addCoinNum(buyer, -listing.price);

        // 给买家物品
        CS2InventoryComponent buyerInv = CS2InventoryComponent.KEY.get(buyer);
        if ("box".equals(listing.itemType)) {
            buyerInv.addBox(listing.itemId, 1);
        } else if ("skin".equals(listing.itemType)) {
            buyerInv.addSkin(listing.itemId, 1);
        }
        buyerInv.sync();

        // 卖家获得扣除税后的金额，始终存入 pendingCoins 等待手动领取
        int sellerIncome = (int) Math.round(listing.price * (1.0 - taxRate));
        pendingCoins.merge(listing.sellerUuid, sellerIncome, Integer::sum);
        Noellesroles.LOGGER.info("[CS2Market] Seller {} earned {} coins (price={}, tax={}%)",
                listing.sellerName, sellerIncome, listing.price, (int)(taxRate * 100));

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
        }
        inv.sync();

        listings.remove(listing);
        save();
        broadcastToAll(player);
        return true;
    }

    /**
     * 玩家手动领取待发放的黑市货币
     */
    public void claimPendingCoins(ServerPlayer player) {
        String uuid = player.getUUID().toString();
        Integer amount = pendingCoins.remove(uuid);
        if (amount != null && amount > 0) {
            PlayerEconomyManager.addCoinNum(player, amount);
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(
                            "\uD83D\uDCB0 领取成功: +" + amount + " 货币")
                            .withStyle(net.minecraft.ChatFormatting.GREEN), true);
            save();
            // 同步更新给客户端（刷新待领金额显示）
            syncToPlayer(player);
            Noellesroles.LOGGER.info("[CS2Market] {} claimed {} pending coins",
                    player.getName().getString(), amount);
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
        if (dataFile == null) return;
        try {
            Files.createDirectories(dataFile.getParent());
            BlackMarketData data = new BlackMarketData();
            data.listings = new ArrayList<>(listings);
            data.pendingCoins = new HashMap<>(pendingCoins);
            Files.writeString(dataFile, GSON.toJson(data));
        } catch (IOException e) {
            Noellesroles.LOGGER.error("[CS2Market] Failed to save black market data", e);
        }
    }
}
