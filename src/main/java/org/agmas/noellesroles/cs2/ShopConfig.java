package org.agmas.noellesroles.cs2;

import com.google.gson.*;
import org.agmas.noellesroles.Noellesroles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * CS2 商店配置和解析器
 */
public class ShopConfig {

    /** 商品项 */
    public static class ShopItem {
        public final String name;
        public final String type; // "box", "key", "skin"
        public final String id;
        public final int price;

        public ShopItem(String name, String type, String id, int price) {
            this.name = name;
            this.type = type;
            this.id = id;
            this.price = price;
        }
    }

    /** 出售价格表（按品质定价） */
    public static class SellPriceConfig {
        public int commonSkinPrice = 5;
        public int uncommonSkinPrice = 15;
        public int rareSkinPrice = 50;
        public int epicSkinPrice = 150;
        public int legendarySkinPrice = 500;
        public int unbelievableSkinPrice = 2000;
        /** 箱子出售价格 {boxId: price} */
        public Map<String, Integer> boxPrices = new HashMap<>();

        /**
         * 根据品质获取皮肤出售价格
         */
        public int getSkinPriceByQuality(int quality) {
            return switch (quality) {
                case 0 -> commonSkinPrice;
                case 1 -> uncommonSkinPrice;
                case 2 -> rareSkinPrice;
                case 3 -> epicSkinPrice;
                case 4 -> legendarySkinPrice;
                case 5 -> unbelievableSkinPrice;
                default -> commonSkinPrice;
            };
        }
    }

    // ── 单例 ──

    private static ShopConfig instance;

    /** 购买商品列表 */
    private final List<ShopItem> shopItems = new ArrayList<>();

    /** 出售价格配置 */
    private final SellPriceConfig sellPriceConfig = new SellPriceConfig();

    private ShopConfig() {}

    public static ShopConfig getInstance() {
        if (instance == null) {
            instance = new ShopConfig();
        }
        return instance;
    }

    public List<ShopItem> getShopItems() {
        return Collections.unmodifiableList(shopItems);
    }

    public SellPriceConfig getSellPriceConfig() {
        return sellPriceConfig;
    }

    /**
     * 加载商店配置
     */
    public void load(Path configFile) {
        shopItems.clear();

        if (!Files.exists(configFile)) {
            createDefaultConfig(configFile);
            return;
        }

        try {
            String json = Files.readString(configFile);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            // 解析购买商品
            if (root.has("shopprice")) {
                JsonObject shopObj = root.getAsJsonObject("shopprice");
                for (String key : shopObj.keySet()) {
                    JsonObject item = shopObj.getAsJsonObject(key);
                    String name = item.has("name") ? item.get("name").getAsString() : "";
                    String type = item.has("type") ? item.get("type").getAsString() : "";
                    String id = item.has("id") ? item.get("id").getAsString() : "";
                    int price = item.has("price") ? item.get("price").getAsInt() : 0;
                    if (!type.isEmpty() && !id.isEmpty()) {
                        shopItems.add(new ShopItem(name, type, id, price));
                    }
                }
            }

            // 解析出售价格
            if (root.has("sellprice")) {
                JsonObject sellObj = root.getAsJsonObject("sellprice");
                if (sellObj.has("common_skinsprice"))
                    sellPriceConfig.commonSkinPrice = sellObj.get("common_skinsprice").getAsInt();
                if (sellObj.has("uncommon_skinsprice"))
                    sellPriceConfig.uncommonSkinPrice = sellObj.get("uncommon_skinsprice").getAsInt();
                if (sellObj.has("rare_skinsprice"))
                    sellPriceConfig.rareSkinPrice = sellObj.get("rare_skinsprice").getAsInt();
                if (sellObj.has("epic_skinsprice"))
                    sellPriceConfig.epicSkinPrice = sellObj.get("epic_skinsprice").getAsInt();
                if (sellObj.has("legendary_skinsprice"))
                    sellPriceConfig.legendarySkinPrice = sellObj.get("legendary_skinsprice").getAsInt();
                if (sellObj.has("unbelievable_skinsprice"))
                    sellPriceConfig.unbelievableSkinPrice = sellObj.get("unbelievable_skinsprice").getAsInt();
                if (sellObj.has("box_price")) {
                    JsonObject boxPrices = sellObj.getAsJsonObject("box_price");
                    for (String boxId : boxPrices.keySet()) {
                        sellPriceConfig.boxPrices.put(boxId, boxPrices.get(boxId).getAsInt());
                    }
                }
            }

            Noellesroles.LOGGER.info("[CS2Shop] Loaded {} shop items", shopItems.size());

        } catch (Exception e) {
            Noellesroles.LOGGER.error("[CS2Shop] Failed to load shop config", e);
        }
    }

    /**
     * 从 JSON 字符串加载商店配置（客户端网络接收用）
     */
    public void loadFromJson(String json) {
        shopItems.clear();
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("shopprice")) {
                JsonObject shopObj = root.getAsJsonObject("shopprice");
                for (String key : shopObj.keySet()) {
                    JsonObject item = shopObj.getAsJsonObject(key);
                    String name = item.has("name") ? item.get("name").getAsString() : "";
                    String type = item.has("type") ? item.get("type").getAsString() : "";
                    String id = item.has("id") ? item.get("id").getAsString() : "";
                    int price = item.has("price") ? item.get("price").getAsInt() : 0;
                    if (!type.isEmpty() && !id.isEmpty()) {
                        shopItems.add(new ShopItem(name, type, id, price));
                    }
                }
            }
            if (root.has("sellprice")) {
                JsonObject sellObj = root.getAsJsonObject("sellprice");
                if (sellObj.has("common_skinsprice"))
                    sellPriceConfig.commonSkinPrice = sellObj.get("common_skinsprice").getAsInt();
                if (sellObj.has("uncommon_skinsprice"))
                    sellPriceConfig.uncommonSkinPrice = sellObj.get("uncommon_skinsprice").getAsInt();
                if (sellObj.has("rare_skinsprice"))
                    sellPriceConfig.rareSkinPrice = sellObj.get("rare_skinsprice").getAsInt();
                if (sellObj.has("epic_skinsprice"))
                    sellPriceConfig.epicSkinPrice = sellObj.get("epic_skinsprice").getAsInt();
                if (sellObj.has("legendary_skinsprice"))
                    sellPriceConfig.legendarySkinPrice = sellObj.get("legendary_skinsprice").getAsInt();
                if (sellObj.has("unbelievable_skinsprice"))
                    sellPriceConfig.unbelievableSkinPrice = sellObj.get("unbelievable_skinsprice").getAsInt();
                if (sellObj.has("box_price")) {
                    JsonObject boxPrices = sellObj.getAsJsonObject("box_price");
                    for (String boxId : boxPrices.keySet()) {
                        sellPriceConfig.boxPrices.put(boxId, boxPrices.get(boxId).getAsInt());
                    }
                }
            }
        } catch (Exception e) {
            Noellesroles.LOGGER.error("[CS2Shop] Failed to parse shop config from JSON", e);
        }
    }

    private void createDefaultConfig(Path configFile) {
        try {
            Files.createDirectories(configFile.getParent());
            JsonObject root = new JsonObject();

            JsonObject shopprice = new JsonObject();
            JsonObject item1 = new JsonObject();
            item1.addProperty("name", "武器箱I");
            item1.addProperty("type", "box");
            item1.addProperty("id", "weapon_case_1");
            item1.addProperty("price", 100);
            shopprice.add("1", item1);

            JsonObject item2 = new JsonObject();
            item2.addProperty("name", "武器箱钥匙");
            item2.addProperty("type", "key");
            item2.addProperty("id", "weapon_key_1");
            item2.addProperty("price", 50);
            shopprice.add("2", item2);

            root.add("shopprice", shopprice);

            JsonObject sellprice = new JsonObject();
            sellprice.addProperty("common_skinsprice", 5);
            sellprice.addProperty("uncommon_skinsprice", 15);
            sellprice.addProperty("rare_skinsprice", 50);
            sellprice.addProperty("epic_skinsprice", 150);
            sellprice.addProperty("legendary_skinsprice", 500);
            sellprice.addProperty("unbelievable_skinsprice", 2000);
            JsonObject boxPrice = new JsonObject();
            boxPrice.addProperty("weapon_case_1", 20);
            sellprice.add("box_price", boxPrice);
            root.add("sellprice", sellprice);

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(configFile, gson.toJson(root));
            Noellesroles.LOGGER.info("[CS2Shop] Created default shop config: {}", configFile);
        } catch (IOException e) {
            Noellesroles.LOGGER.error("[CS2Shop] Failed to create default shop config", e);
        }
    }
}
