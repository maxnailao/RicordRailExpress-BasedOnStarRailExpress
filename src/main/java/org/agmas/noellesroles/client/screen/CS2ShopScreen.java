package org.agmas.noellesroles.client.screen;

import io.wifi.starrailexpress.cca.CS2InventoryComponent;
import io.wifi.starrailexpress.data.PlayerEconomyManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.cs2.CS2BoxConfig;
import org.agmas.noellesroles.cs2.CS2BoxManager;
import org.agmas.noellesroles.cs2.CS2SkinInfo;
import org.agmas.noellesroles.cs2.ShopConfig;
import org.agmas.noellesroles.cs2.network.*;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.*;

/**
 * CS2 商店界面
 * <p>
 * 三个标签页：购买 / 出售 / 黑市
 * </p>
 */
public class CS2ShopScreen extends Screen {

    private static final int BG_COLOR = 0xE60C1020;
    private static final int TAB_COLOR = 0xC0161B30;
    private static final int CARD_BG = 0x30FFFFFF;
    private static final int CARD_HOVER = 0x50FFFFFF;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xFF999999;
    private static final int GOLD = 0xFFFFD700;
    private static final int ACCENT = 0xFF4488FF;

    // 品质颜色 ARGB
    private static final int[] QUALITY_COLORS = {
            0xFFEEEEEE, // 0: common
            0xFF33FF55, // 1: uncommon
            0xFFAAAAFF, // 2: rare
            0xFFAA55FF, // 3: epic
            0xFFFFAA55, // 4: legendary
            0xFFFF3F3F, // 5: unbelievable
    };
    private static final String[] QUALITY_NAMES = {"\u666e\u901a", "\u7f55\u89c1", "\u7a00\u6709", "\u53f2\u8bd7", "\u4f20\u8bf4", "\u4e0d\u53ef\u601d\u8bae"};
    private static final int[] QUALITY_TEXT_COLORS = {0xFFEEEEEE, 0xFF33FF55, 0xFFAAAAFF, 0xFFAA55FF, 0xFFFFAA55, 0xFFFF3F3F};

    private enum Tab { BUY, SELL, MARKET }
    private Tab selectedTab = Tab.BUY;

    // 商店商品数据（从服务端配置加载，客户端使用缓存）
    private final List<ShopDisplayItem> buyItems = new ArrayList<>();
    private final List<ShopDisplayItem> sellItems = new ArrayList<>();
    private final List<MarketDisplayItem> marketItems = new ArrayList<>();
    private ShopDisplayItem hoveredBuyItem = null;
    private ShopDisplayItem hoveredSellItem = null;
    private MarketDisplayItem hoveredMarketItem = null;
    private int scrollOffset = 0;

    // 黑市上架模式
    private boolean listingMode = false; // true=显示"上架物品"列表
    private ShopDisplayItem hoveredListItem = null; // 待上架物品
    private final List<ShopDisplayItem> listableItems = new ArrayList<>(); // 可上架物品
    private String selectedListingType = "";
    private String selectedListingId = "";
    private String selectedListingName = "";
    private EditBox priceInputBox;

    /** 服务端同步的黑市数据缓存 */
    private static String marketDataCache = "[]";
    private static int myPendingCoinsCache = 0;
    private static final Gson GSON = new Gson();
    /** 缓存当前玩家自己的挂单 listingId 集合，避免每帧反序列化 */
    private Set<String> ownListingIds = null;

    // 布局
    private int listStartY = 60;
    private int itemHeight = 36;

    public CS2ShopScreen() {
        super(Component.literal("CS2 商店"));
    }

    /**
     * 服务端同步黑市数据时调用（静态缓存）
     */
    public static void setMarketDataCache(String json) {
        marketDataCache = json;
    }

    /**
     * 服务端同步待领取货币时调用（静态缓存）
     */
    public static void setMyPendingCoins(int amount) {
        myPendingCoinsCache = amount;
    }

    @Override
    protected void init() {
        super.init();
        scrollOffset = 0;
        listingMode = false;

        // 价格输入框（黑市上架用）
        priceInputBox = new EditBox(font, width / 2 - 60, height / 2 + 10, 120, 20, Component.literal("价格"));
        priceInputBox.setMaxLength(10);
        priceInputBox.setFilter(s -> s.matches("\\d*")); // 只允许数字
        priceInputBox.setVisible(false);
        addRenderableWidget(priceInputBox);

        // 请求服务端同步黑市数据
        ClientPlayNetworking.send(new BlackMarketSyncRequestC2SPayload());

        // 返回仓库按钮
        addRenderableWidget(Button.builder(Component.literal("< 仓库"), b -> {
            minecraft.setScreen(new CS2WarehouseScreen());
        }).pos(8, height - 30).size(60, 20).build());

        // 关闭按钮
        addRenderableWidget(Button.builder(Component.literal("关闭"), b -> {
            minecraft.setScreen(null);
        }).pos(width - 68, height - 30).size(60, 20).build());

        refreshData();
    }

    public void refreshData() {
        buyItems.clear();
        sellItems.clear();
        marketItems.clear();

        // 购买商品 (从 ShopConfig 单例获取 - 客户端同步)
        for (ShopConfig.ShopItem item : ShopConfig.getInstance().getShopItems()) {
            buyItems.add(new ShopDisplayItem(item.name, item.type, item.id, item.price, -1));
        }

        // 出售物品 (从玩家仓库获取 - 箱子 + 皮肤)
        var player = Minecraft.getInstance().player;
        if (player != null) {
            CS2InventoryComponent inv = CS2InventoryComponent.KEY.get(player);
            // 箱子
            for (Map.Entry<String, Integer> entry : inv.getBoxes().entrySet()) {
                int price = ShopConfig.getInstance().getSellPriceConfig()
                        .boxPrices.getOrDefault(entry.getKey(), 10);
                CS2BoxConfig boxCfg = CS2BoxManager.getInstance().getBox(entry.getKey());
                String boxName = (boxCfg != null && !boxCfg.getBoxName().isEmpty())
                        ? boxCfg.getBoxName() : entry.getKey().replace('_', ' ');
                ShopDisplayItem item = new ShopDisplayItem(
                        boxName,
                        "box", entry.getKey(), price, -1);
                item.count = entry.getValue();
                sellItems.add(item);
            }
            // 皮肤
            for (Map.Entry<String, Integer> entry : inv.getSkins().entrySet()) {
                String skinId = entry.getKey();
                int count = entry.getValue();
                if (count <= 0) continue;
                int quality = getSkinQuality(skinId);
                int price = ShopConfig.getInstance().getSellPriceConfig()
                        .getSkinPriceByQuality(quality);
                String displayName = org.agmas.noellesroles.cs2.CS2SkinInfo.getName(skinId);
                ShopDisplayItem item = new ShopDisplayItem(displayName, "skin", skinId, price, quality);
                item.count = count;
                sellItems.add(item);
            }
        }

        // 黑市商品 (从服务端缓存解析)
        try {
            Type listType = new TypeToken<List<MarketListingData>>() {}.getType();
            List<MarketListingData> serverListings = GSON.fromJson(marketDataCache, listType);
            if (serverListings != null) {
                // 预计算当前玩家自己的挂单 ID
                ownListingIds = new HashSet<>();
                String myUuid = player != null ? player.getUUID().toString() : "";
                for (MarketListingData d : serverListings) {
                    String displayName;
                    if ("box".equals(d.itemType)) {
                        CS2BoxConfig boxCfg = CS2BoxManager.getInstance().getBox(d.itemId);
                        displayName = (boxCfg != null && !boxCfg.getBoxName().isEmpty())
                                ? boxCfg.getBoxName() : d.itemId.replace('_', ' ');
                    } else {
                        displayName = CS2SkinInfo.getName(d.itemId);
                    }
                    marketItems.add(new MarketDisplayItem(d.listingId, d.sellerName, d.itemType, d.itemId, d.price, displayName));
                    if (d.sellerUuid.equals(myUuid)) {
                        ownListingIds.add(d.listingId);
                    }
                }
            }
        } catch (Exception ignored) {}

        // 可上架物品（玩家仓库中的箱子+皮肤）
        listableItems.clear();
        if (player != null) {
            CS2InventoryComponent listInv = CS2InventoryComponent.KEY.get(player);
            for (Map.Entry<String, Integer> entry : listInv.getBoxes().entrySet()) {
                if (entry.getValue() <= 0) continue;
                CS2BoxConfig boxCfg = CS2BoxManager.getInstance().getBox(entry.getKey());
                String boxName = (boxCfg != null && !boxCfg.getBoxName().isEmpty())
                        ? boxCfg.getBoxName() : entry.getKey().replace('_', ' ');
                ShopDisplayItem item = new ShopDisplayItem(boxName, "box", entry.getKey(), 0, -1);
                item.count = entry.getValue();
                listableItems.add(item);
            }
            for (Map.Entry<String, Integer> entry : listInv.getSkins().entrySet()) {
                if (entry.getValue() <= 0) continue;
                String displayName = CS2SkinInfo.getName(entry.getKey());
                int quality = getSkinQuality(entry.getKey());
                ShopDisplayItem item = new ShopDisplayItem(displayName, "skin", entry.getKey(), 0, quality);
                item.count = entry.getValue();
                listableItems.add(item);
            }
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        guiGraphics.fill(0, 0, width, height, BG_COLOR);
        renderTabs(guiGraphics, mouseX, mouseY);
        renderHeader(guiGraphics);

        hoveredBuyItem = null;
        hoveredSellItem = null;
        hoveredMarketItem = null;
        hoveredListItem = null;

        switch (selectedTab) {
            case BUY -> renderBuyTab(guiGraphics, mouseX, mouseY);
            case SELL -> renderSellTab(guiGraphics, mouseX, mouseY);
            case MARKET -> renderMarketTab(guiGraphics, mouseX, mouseY);
        }

        super.render(guiGraphics, mouseX, mouseY, delta);
    }

    private void renderHeader(GuiGraphics guiGraphics) {
        guiGraphics.drawCenteredString(font, "CS2 商店", width / 2, 8, TEXT_COLOR);

        var player = Minecraft.getInstance().player;
        if (player != null) {
            int coins = PlayerEconomyManager.getCoinNum(player);
            guiGraphics.drawString(font, "货币: " + coins, width - 120, 8, GOLD, false);
        }
    }

    private void renderTabs(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        Tab[] tabs = {Tab.BUY, Tab.SELL, Tab.MARKET};
        String[] labels = {"购买", "出售", "黑市"};
        int tabWidth = width / 3;

        for (int i = 0; i < tabs.length; i++) {
            int x = i * tabWidth;
            boolean selected = tabs[i] == selectedTab;
            boolean hovered = mouseX >= x && mouseX < x + tabWidth && mouseY >= 28 && mouseY < 52;

            int bg = selected ? ACCENT : (hovered ? 0x30FFFFFF : TAB_COLOR);
            guiGraphics.fill(x, 28, x + tabWidth, 52, bg);
            guiGraphics.drawCenteredString(font, labels[i],
                    x + tabWidth / 2, 35, selected ? 0xFFFFFFFF : TEXT_DIM);
        }
    }

    private void renderBuyTab(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (buyItems.isEmpty()) {
            guiGraphics.drawCenteredString(font, "暂无商品", width / 2, height / 2, TEXT_DIM);
            return;
        }

        for (int i = scrollOffset; i < buyItems.size(); i++) {
            int y = listStartY + (i - scrollOffset) * itemHeight;
            if (y + itemHeight > height - 40) break;

            ShopDisplayItem item = buyItems.get(i);
            boolean hovered = mouseX >= 20 && mouseX < width - 20 && mouseY >= y && mouseY < y + itemHeight - 2;
            if (hovered) hoveredBuyItem = item;

            int bg = hovered ? CARD_HOVER : CARD_BG;
            guiGraphics.fill(20, y, width - 20, y + itemHeight - 2, bg);

            // 商品名
            guiGraphics.drawString(font, item.name, 30, y + 6, TEXT_COLOR, false);
            // 类型
            guiGraphics.drawString(font, "[" + item.type + "]", 30, y + 20, TEXT_DIM, false);
            // 价格
            guiGraphics.drawString(font, item.price + " 货币",
                    width - 130, y + 10, GOLD, false);

            // 购买按钮
            if (hovered) {
                guiGraphics.fill(width - 80, y + 4, width - 30, y + itemHeight - 6, 0x6044FF44);
                guiGraphics.drawCenteredString(font, "购买",
                        width - 55, y + 10, 0xFF44FF44);
            }
        }
    }

    private void renderSellTab(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (sellItems.isEmpty()) {
            guiGraphics.drawCenteredString(font, "没有可出售的物品", width / 2, height / 2, TEXT_DIM);
            return;
        }

        for (int i = scrollOffset; i < sellItems.size(); i++) {
            int y = listStartY + (i - scrollOffset) * itemHeight;
            if (y + itemHeight > height - 40) break;

            ShopDisplayItem item = sellItems.get(i);
            boolean hovered = mouseX >= 20 && mouseX < width - 20 && mouseY >= y && mouseY < y + itemHeight - 2;
            if (hovered) hoveredSellItem = item;

            int bg = hovered ? CARD_HOVER : CARD_BG;
            guiGraphics.fill(20, y, width - 20, y + itemHeight - 2, bg);

            guiGraphics.drawString(font, item.name, 30, y + 6, TEXT_COLOR, false);
            // 品质标签（皮肤）
            if (item.quality >= 0 && item.quality < QUALITY_NAMES.length) {
                int qColor = QUALITY_TEXT_COLORS[item.quality];
                guiGraphics.drawString(font, "[" + QUALITY_NAMES[item.quality] + "]",
                        30, y + 20, qColor, false);
            } else {
                guiGraphics.drawString(font, "x" + item.count, 30, y + 20, TEXT_DIM, false);
            }
            guiGraphics.drawString(font, "+" + item.price + " 货币",
                    width - 130, y + 10, 0xFF44FF44, false);

            if (hovered) {
                guiGraphics.fill(width - 80, y + 4, width - 30, y + itemHeight - 6, 0x60FF8844);
                guiGraphics.drawCenteredString(font, "出售",
                        width - 55, y + 10, 0xFFFF8844);
            }
        }
    }

    private void renderMarketTab(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (listingMode) {
            // === 上架模式 ===
            guiGraphics.drawCenteredString(font, "选择要上架的物品", width / 2, listStartY - 12, TEXT_COLOR);

            if (listableItems.isEmpty()) {
                guiGraphics.drawCenteredString(font, "仓库中没有可上架的物品", width / 2, height / 2, TEXT_DIM);
                return;
            }

            for (int i = scrollOffset; i < listableItems.size(); i++) {
                int y = listStartY + (i - scrollOffset) * itemHeight;
                if (y + itemHeight > height - 40) break;

                ShopDisplayItem item = listableItems.get(i);
                boolean hovered = mouseX >= 20 && mouseX < width - 20 && mouseY >= y && mouseY < y + itemHeight - 2;
                if (hovered) hoveredListItem = item;

                boolean selected = item.type.equals(selectedListingType) && item.id.equals(selectedListingId);
                int bg = selected ? 0x504488FF : (hovered ? CARD_HOVER : CARD_BG);
                guiGraphics.fill(20, y, width - 20, y + itemHeight - 2, bg);

                guiGraphics.drawString(font, item.name, 30, y + 6, TEXT_COLOR, false);
                if (item.quality >= 0 && item.quality < QUALITY_NAMES.length) {
                    guiGraphics.drawString(font, "[" + QUALITY_NAMES[item.quality] + "]", 30, y + 20, QUALITY_TEXT_COLORS[item.quality], false);
                } else {
                    guiGraphics.drawString(font, "x" + item.count, 30, y + 20, TEXT_DIM, false);
                }
            }

            // 选中物品后显示价格输入和确认按钮
            if (!selectedListingType.isEmpty()) {
                guiGraphics.drawCenteredString(font, "已选: " + selectedListingName, width / 2, height / 2 - 10, ACCENT);
                guiGraphics.drawCenteredString(font, "设定价格:", width / 2, height / 2 + 2, TEXT_DIM);
                priceInputBox.setVisible(true);

                // 确认上架按钮
                int btnY = height / 2 + 36;
                boolean confirmHovered = mouseX >= width / 2 - 50 && mouseX < width / 2 + 50 && mouseY >= btnY && mouseY < btnY + 20;
                guiGraphics.fill(width / 2 - 50, btnY, width / 2 + 50, btnY + 20, confirmHovered ? 0x8044FF44 : 0x6044FF44);
                guiGraphics.drawCenteredString(font, "确认上架", width / 2, btnY + 6, 0xFF44FF44);

                // 取消按钮
                int cancelY = btnY + 24;
                boolean cancelHovered = mouseX >= width / 2 - 50 && mouseX < width / 2 + 50 && mouseY >= cancelY && mouseY < cancelY + 20;
                guiGraphics.fill(width / 2 - 50, cancelY, width / 2 + 50, cancelY + 20, cancelHovered ? 0x80FF4444 : 0x60FF4444);
                guiGraphics.drawCenteredString(font, "取消", width / 2, cancelY + 6, 0xFFFF4444);
            } else {
                priceInputBox.setVisible(false);
            }
        } else {
            // === 浏览模式 ===
            // 上架按钮
            boolean listBtnHovered = mouseX >= width / 2 - 50 && mouseX < width / 2 + 50 && mouseY >= listStartY && mouseY < listStartY + 22;
            guiGraphics.fill(width / 2 - 50, listStartY, width / 2 + 50, listStartY + 22, listBtnHovered ? 0x804488FF : 0x604488FF);
            guiGraphics.drawCenteredString(font, "+ 上架物品", width / 2, listStartY + 7, 0xFFFFFFFF);

            // 领取收入按钮（有待领货币时显示）
            int contentStartY = listStartY + 30;
            if (myPendingCoinsCache > 0) {
                int claimBtnY = listStartY;
                int claimBtnX = width / 2 + 60;
                boolean claimHovered = mouseX >= claimBtnX && mouseX < claimBtnX + 100 && mouseY >= claimBtnY && mouseY < claimBtnY + 22;
                guiGraphics.fill(claimBtnX, claimBtnY, claimBtnX + 100, claimBtnY + 22, claimHovered ? 0x80FFAA00 : 0x60FFAA00);
                guiGraphics.drawCenteredString(font, "领取 " + myPendingCoinsCache + " \u8d27\u5e01", claimBtnX + 50, claimBtnY + 7, 0xFFFFDD44);
                contentStartY = listStartY + 30;
            }
            if (marketItems.isEmpty()) {
                guiGraphics.drawCenteredString(font, "黑市暂无商品", width / 2, height / 2, TEXT_DIM);
                return;
            }

            hoveredMarketItem = null;

            for (int i = scrollOffset; i < marketItems.size(); i++) {
                int y = contentStartY + (i - scrollOffset) * itemHeight;
                if (y + itemHeight > height - 40) break;

                MarketDisplayItem item = marketItems.get(i);
                boolean hovered = mouseX >= 20 && mouseX < width - 20 && mouseY >= y && mouseY < y + itemHeight - 2;
                if (hovered) hoveredMarketItem = item;

                int bg = hovered ? CARD_HOVER : CARD_BG;
                guiGraphics.fill(20, y, width - 20, y + itemHeight - 2, bg);

                guiGraphics.drawString(font, item.displayName, 30, y + 6, TEXT_COLOR, false);
                guiGraphics.drawString(font, "卖家: " + item.sellerName, 30, y + 20, TEXT_DIM, false);
                guiGraphics.drawString(font, item.price + " 货币", width - 150, y + 10, GOLD, false);

                // 操作按钮
                if (hovered) {
                    // 使用预缓存的所有权集合判断
                    boolean isOwn = ownListingIds != null && ownListingIds.contains(item.listingId);

                    if (isOwn) {
                        // 取消挂单按钮
                        guiGraphics.fill(width - 80, y + 4, width - 30, y + itemHeight - 6, 0x60FF4444);
                        guiGraphics.drawCenteredString(font, "下架", width - 55, y + 10, 0xFFFF4444);
                    } else {
                        // 购买按钮
                        guiGraphics.fill(width - 80, y + 4, width - 30, y + itemHeight - 6, 0x6044FF44);
                        guiGraphics.drawCenteredString(font, "购买", width - 55, y + 10, 0xFF44FF44);
                    }
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 如果价格输入框可见且点击在它上面，优先处理
        if (priceInputBox != null && priceInputBox.isVisible()) {
            if (priceInputBox.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }

        // Tab 切换
        Tab[] tabs = {Tab.BUY, Tab.SELL, Tab.MARKET};
        int tabWidth = width / 3;
        if (mouseY >= 28 && mouseY < 52) {
            for (int i = 0; i < tabs.length; i++) {
                int x = i * tabWidth;
                if (mouseX >= x && mouseX < x + tabWidth) {
                    selectedTab = tabs[i];
                    scrollOffset = 0;
                    listingMode = false;
                    priceInputBox.setVisible(false);
                    return true;
                }
            }
        }

        // 购买点击
        if (selectedTab == Tab.BUY && hoveredBuyItem != null && mouseX >= width - 80) {
            var p = Minecraft.getInstance().player;
            if (p != null) {
                int coins = PlayerEconomyManager.getCoinNum(p);
                if (coins < hoveredBuyItem.price) {
                    p.displayClientMessage(Component.literal("§c货币不足，需要 " + hoveredBuyItem.price + " 货币"), true);
                    return true;
                }
            }
            ClientPlayNetworking.send(new ShopBuyC2SPayload(
                    hoveredBuyItem.type, hoveredBuyItem.id));
            return true;
        }

        // 出售点击
        if (selectedTab == Tab.SELL && hoveredSellItem != null && mouseX >= width - 80) {
            ClientPlayNetworking.send(new ShopSellC2SPayload(
                    hoveredSellItem.type, hoveredSellItem.id));
            return true;
        }

        // 黑市操作
        if (selectedTab == Tab.MARKET) {
            boolean handled = handleMarketClick(mouseX, mouseY);
            if (handled) return true;
            // 在 listing 模式下不转发给 super，避免 EditBox 焦点被偷走
            if (listingMode) return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleMarketClick(double mouseX, double mouseY) {
        if (listingMode) {
            // 上架模式
            // 确认上架按钮（优先级最高，避免被物品列表拦截）
            if (!selectedListingType.isEmpty()) {
                int btnY = height / 2 + 36;
                if (mouseX >= width / 2 - 50 && mouseX < width / 2 + 50
                        && mouseY >= btnY && mouseY < btnY + 20) {
                    String priceStr = priceInputBox.getValue();
                    if (!priceStr.isEmpty()) {
                        try {
                            int price = Integer.parseInt(priceStr);
                            if (price > 0) {
                                ClientPlayNetworking.send(new BlackMarketListC2SPayload(
                                        selectedListingType, selectedListingId, price));
                                listingMode = false;
                                priceInputBox.setVisible(false);
                                selectedListingType = "";
                                selectedListingId = "";
                                selectedListingName = "";
                                return true;
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                    var p = Minecraft.getInstance().player;
                    if (p != null) p.displayClientMessage(Component.literal("§c请输入有效的价格"), true);
                    return true;
                }

                // 取消按钮
                int cancelY = btnY + 24;
                if (mouseX >= width / 2 - 50 && mouseX < width / 2 + 50
                        && mouseY >= cancelY && mouseY < cancelY + 20) {
                    listingMode = false;
                    priceInputBox.setVisible(false);
                    selectedListingType = "";
                    selectedListingId = "";
                    selectedListingName = "";
                    return true;
                }
            }

            // 选择物品（在按钮检查之后）
            if (hoveredListItem != null) {
                selectedListingType = hoveredListItem.type;
                selectedListingId = hoveredListItem.id;
                selectedListingName = hoveredListItem.name;
                priceInputBox.setValue("");
                priceInputBox.setFocused(true);
                return true;
            }
        } else {
            // 浏览模式
            // “领取收入”按钮
            if (myPendingCoinsCache > 0) {
                int claimBtnX = width / 2 + 60;
                if (mouseX >= claimBtnX && mouseX < claimBtnX + 100
                        && mouseY >= listStartY && mouseY < listStartY + 22) {
                    ClientPlayNetworking.send(new BlackMarketClaimC2SPayload());
                    return true;
                }
            }

            // "上架物品" 按钮
            if (mouseX >= width / 2 - 50 && mouseX < width / 2 + 50
                    && mouseY >= listStartY && mouseY < listStartY + 22) {
                listingMode = true;
                scrollOffset = 0;
                return true;
            }

            // 购买/下架点击
            if (hoveredMarketItem != null && mouseX >= width - 80) {
                var player = Minecraft.getInstance().player;

                // 使用预缓存的所有权集合判断
                boolean isOwn = ownListingIds != null && ownListingIds.contains(hoveredMarketItem.listingId);

                if (isOwn) {
                    // 下架
                    ClientPlayNetworking.send(new BlackMarketCancelC2SPayload(hoveredMarketItem.listingId));
                } else {
                    // 购买
                    if (player != null) {
                        int coins = PlayerEconomyManager.getCoinNum(player);
                        if (coins < hoveredMarketItem.price) {
                            player.displayClientMessage(Component.literal("§c货币不足，需要 " + hoveredMarketItem.price + " 货币"), true);
                            return true;
                        }
                    }
                    ClientPlayNetworking.send(new BlackMarketBuyC2SPayload(hoveredMarketItem.listingId));
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        scrollOffset -= (int) deltaY * 2;
        int maxItems = (height - listStartY - 40) / itemHeight;
        int total;
        if (selectedTab == Tab.BUY) {
            total = buyItems.size();
        } else if (selectedTab == Tab.MARKET) {
            total = listingMode ? listableItems.size() : marketItems.size();
        } else {
            total = sellItems.size();
        }
        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, total - maxItems)));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 优先将按键转发给价格输入框
        if (priceInputBox != null && priceInputBox.isVisible() && priceInputBox.isFocused()) {
            if (priceInputBox.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        // 优先将字符输入转发给价格输入框
        if (priceInputBox != null && priceInputBox.isVisible() && priceInputBox.isFocused()) {
            if (priceInputBox.charTyped(codePoint, modifiers)) {
                return true;
            }
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 商店展示条目 */
    private static class ShopDisplayItem {
        final String name;
        final String type;
        final String id;
        final int price;
        final int quality; // -1 表示非皮肤
        int count = 1;

        ShopDisplayItem(String name, String type, String id, int price, int quality) {
            this.name = name;
            this.type = type;
            this.id = id;
            this.price = price;
            this.quality = quality;
        }
    }

    /** 黑市展示条目 */
    private static class MarketDisplayItem {
        final String listingId;
        final String sellerName;
        final String itemType;
        final String itemId;
        final int price;
        final String displayName;

        MarketDisplayItem(String listingId, String sellerName, String itemType, String itemId, int price, String displayName) {
            this.listingId = listingId;
            this.sellerName = sellerName;
            this.itemType = itemType;
            this.itemId = itemId;
            this.price = price;
            this.displayName = displayName;
        }
    }

    /** 服务端黑市挂单 JSON 反序列化用 */
    private static class MarketListingData {
        String listingId;
        String sellerUuid;
        String sellerName;
        String itemType;
        String itemId;
        int price;
    }

    /**
     * 根据皮肤 ID 查找品质（通过 ItemSkinManager 的颜色映射）
     */
    private static int getSkinQuality(String skinId) {
        String[] parts = skinId.split("/");
        if (parts.length < 2) return 0;
        io.wifi.starrailexpress.util.ItemSkinManager.Skin skin =
                io.wifi.starrailexpress.util.ItemSkinManager.getSkinFromName(parts[0], parts[1]);
        if (skin == null) return 0;
        int color = skin.color;
        for (int i = 0; i < QUALITY_COLORS.length; i++) {
            if (QUALITY_COLORS[i] == (color | 0xFF000000)) return i;
        }
        return 0;
    }
}
