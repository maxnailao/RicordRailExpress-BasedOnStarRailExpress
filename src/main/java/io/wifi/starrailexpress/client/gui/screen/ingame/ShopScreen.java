package io.wifi.starrailexpress.client.gui.screen.ingame;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.cca.CS2InventoryComponent;
import io.wifi.starrailexpress.client.data.ClientPlayerDataCache;
import io.wifi.starrailexpress.data.PlayerEconomyManager;
import io.wifi.starrailexpress.util.ItemSkinManager;
import io.wifi.starrailexpress.progression.ProgressionState.FactionCardType;
import org.agmas.noellesroles.client.data.CS2ClientBoxCache;
import org.agmas.noellesroles.client.screen.CS2ShopScreen;
import org.agmas.noellesroles.cs2.CS2BlackMarketManager;
import org.agmas.noellesroles.cs2.CS2SkinInfo;
import org.agmas.noellesroles.cs2.ShopConfig;
import org.agmas.noellesroles.cs2.network.BlackMarketBuyC2SPayload;
import org.agmas.noellesroles.cs2.network.BlackMarketCancelC2SPayload;
import org.agmas.noellesroles.cs2.network.BlackMarketClaimC2SPayload;
import org.agmas.noellesroles.cs2.network.BlackMarketListC2SPayload;
import org.agmas.noellesroles.cs2.network.BlackMarketSyncRequestC2SPayload;
import org.agmas.noellesroles.cs2.network.ShopBuyC2SPayload;
import org.agmas.noellesroles.cs2.network.ShopSellC2SPayload;
import org.agmas.noellesroles.cs2.network.MythicShopBuyC2SPayload;
import org.agmas.noellesroles.cs2.network.DailyShopSyncRequestC2SPayload;
import org.agmas.noellesroles.cs2.network.DailyShopBuyC2SPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * 商店界面（从主菜单"商城"按钮进入）——重构版。
 * <p>
 * 顶部四个分页：商店 / 自由市场 / 每日商店 / 神话商城。
 * "商店"分页：类型（钥匙/箱子/皮肤）分类，商品每行四个，点击直接购买。
 * "自由市场"分页：复用黑市后端（每人上限 {@code MAX_LISTINGS_PER_PLAYER} 件、卖家承担 1% 手续费），
 * 每页 8 件、可翻页，支持上架/购买/下架/领取收入。
 * 每日商店、神话商城均已接入（每日 00:00 刷新，神话商城用神话碎片兑换）。
 * </p>
 */
public class ShopScreen extends Screen {

    private static final int BG_COLOR = LobbyUiTheme.BACKGROUND;
    private static final int PANEL_COLOR = LobbyUiTheme.PANEL;
    private static final int TEXT_COLOR = LobbyUiTheme.TEXT;
    private static final int TEXT_DIM = LobbyUiTheme.TEXT_DIM;
    private static final int GOLD = LobbyUiTheme.ACCENT_BRIGHT;
    private static final int ACCENT = LobbyUiTheme.ACCENT;

    // 白色标题栏/描边 + 深色标题文字（与主菜单统一）
    private static final int BORDER_COLOR = LobbyUiTheme.BORDER;
    private static final int HEADER_TEXT = LobbyUiTheme.TEXT;
    private static final int TAB_ACTIVE = LobbyUiTheme.ACCENT;
    private static final int TAB_IDLE = LobbyUiTheme.CARD;
    private static final int TAB_HOVER = LobbyUiTheme.CARD_HOVER;
    private static final int CELL_BG = LobbyUiTheme.CARD;
    private static final int CELL_HOVER = LobbyUiTheme.CARD_HOVER;
    private static final int CELL_DISABLE = 0x30888888;
    private static final int OUTLINE = 0xFFB8C4DC;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private enum Tab { SHOP, MARKET, DAILY, MYTH }
    private Tab tab = Tab.SHOP;
    private static final String[] TAB_LABELS = {"商店", "自由市场", "每日商店", "神话商城"};

    // 商店分页：类型分类（钥匙 / 箱子 / 皮肤）
    private static final String[] CATEGORY_LABELS = {"钥匙", "箱子", "皮肤"};
    private static final String[] CATEGORY_IDS = {"key", "box", "skin"};
    private int category = 0;

    // 商店分页：每个商品格子独立的购买数量（默认 1，格内 −/+ 调整；点"购买"按钮按该数量下单）
    private final java.util.Map<String, Integer> cellBuyCount = new java.util.HashMap<>();
    // 购买按钮按压动画：记录每个商品最后一次点击"购买"的时刻（毫秒），渲染时据此计算下陷/回弹
    private final java.util.Map<String, Long> buyPressTime = new java.util.HashMap<>();


    // 商品格子点击热区（每帧重建）
    private static final class CellHit {
        final int x, y, w, h, price;
        final String type, id, name;
        int minusX, minusY, minusW, minusH;
        int plusX, plusY, plusW, plusH;
        int buyX, buyY, buyW, buyH;
        CellHit(int x, int y, int w, int h, String type, String id, String name, int price) {
            this.x = x; this.y = y; this.w = w; this.h = h;
            this.type = type; this.id = id; this.name = name; this.price = price;
        }
        boolean contains(int mx, int my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
        boolean inMinus(int mx, int my) { return mx >= minusX && mx < minusX + minusW && my >= minusY && my < minusY + minusH; }
        boolean inPlus(int mx, int my) { return mx >= plusX && mx < plusX + plusW && my >= plusY && my < plusY + plusH; }
        boolean inBuy(int mx, int my) { return mx >= buyX && mx < buyX + buyW && my >= buyY && my < buyY + buyH; }
    }
    private final List<CellHit> shopCells = new ArrayList<>();

    // ── 自由市场状态 ──
    private static final Gson GSON = new Gson();
    private static final int MAX_LISTINGS = CS2BlackMarketManager.MAX_LISTINGS_PER_PLAYER;
    private static final int MARKET_PER_PAGE = 8; // 2 行 x 4 列
    private static final int MARKET_COLS = 4;

    private boolean listingMode = false; // true=选择要上架的物品
    private int marketPage = 0;
    private int listPage = 0;
    private int myPendingCoins = 0;
    private int myPendingShards = 0;
    private String listingCurrency = "coin"; // "coin" | "shard"
    private static final int[] QUALITY_COLORS = {0xFFEEEEEE, 0xFF33FF55, 0xFFAAAAFF, 0xFFAA55FF, 0xFFFFAA55, 0xFFFF3F3F, 0xFF8B7A2E};
    private static final String[] QUALITY_NAMES = {"普通", "罕见", "稀有", "史诗", "传说", "神话", "传承"};
    private EditBox priceInputBox;
    private String selListType = "", selListId = "", selListName = "";
    private int pBoxX, pBoxY;
    private final List<MarketRow> marketRows = new ArrayList<>();
    private final List<ListableItem> listableItems = new ArrayList<>();
    private final Set<String> ownListingIds = new HashSet<>();
    private final List<MarketHit> marketHits = new ArrayList<>();

    // ── 快速出售（复用旧 CS2 商店出售机制，走 ShopSellC2SPayload，服务端按配置定价） ──
    private boolean sellMode = false;
    private int sellPage = 0;
    private final List<ListableItem> sellableItems = new ArrayList<>();

    // ── 神话商城状态 ──
    private static final int MYTH_PER_PAGE = 5; // 一次显示 5 个
    private static final int MYTH_COLS = 5;
    private static final int MYTH_PRICE = CS2InventoryComponent.MYTHIC_SKIN_SHARD_PRICE; // 150
    private static final int MYTH_PURPLE = 0xFFCC88FF;
    private int mythPage = 0;
    private final List<String> mythSkins = new ArrayList<>();

    // ── 每日商店状态（复用服务端 dailyShopCache，机制与旧 CS2 每日商店一致） ──
    private static final int DAILY_PER_PAGE = 8; // 2 行 x 4 列
    private static final int DAILY_COLS = 4;
    private int dailyPage = 0;
    private final List<DailyRow> dailyRows = new ArrayList<>();

    private static final FactionCardType[] CARD_ORDER = {
            FactionCardType.KILLER, FactionCardType.CIVILIAN,
            FactionCardType.NEUTRAL, FactionCardType.NEUTRAL_FOR_KILLER };

    private static final class DailyRow {
        final int slot, price, quality;
        final String itemType, name, desc, skinId;
        final boolean purchased;
        DailyRow(int slot, String itemType, int quality, int price, boolean purchased, String name, String desc, String skinId) {
            this.slot = slot; this.itemType = itemType; this.quality = quality;
            this.price = price; this.purchased = purchased; this.name = name;
            this.desc = desc; this.skinId = skinId;
        }
    }

    private static final class MarketRow {
        final String listingId, sellerName, itemType, itemId, displayName, currency;
        final int price;
        boolean own;
        MarketRow(String listingId, String sellerName, String itemType, String itemId, String displayName, int price, String currency) {
            this.listingId = listingId; this.sellerName = sellerName; this.itemType = itemType;
            this.itemId = itemId; this.displayName = displayName; this.price = price;
            this.currency = currency == null ? "coin" : currency;
        }
    }

    private static final class ListableItem {
        final String type, id, name;
        final int count, quality;
        ListableItem(String type, String id, String name, int count, int quality) {
            this.type = type; this.id = id; this.name = name; this.count = count; this.quality = quality;
        }
    }

    private static final class MarketHit {
        final int x, y, w, h;
        final String action; // listBtn/claimBtn/row/listable/confirm/cancelList/prev:m/next:m/prev:l/next:l
        final MarketRow row;
        final ListableItem listable;
        MarketHit(int x, int y, int w, int h, String action, MarketRow row, ListableItem listable) {
            this.x = x; this.y = y; this.w = w; this.h = h; this.action = action;
            this.row = row; this.listable = listable;
        }
        boolean contains(int mx, int my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    /** 服务端挂单 JSON 反序列化用 */
    private static final class MarketListingData {
        String listingId, sellerUuid, sellerName, itemType, itemId, currency;
        int price;
    }

    private final Screen parent;
    private int panelX, panelY, panelW, panelH;
    private int tabH = 22;

    public ShopScreen(Screen parent) {
        super(Component.literal("商店"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        // 页面做大：明显大于主菜单面板
        panelW = Math.min(width - 12, 520);
        panelH = Math.min(height - 16, 300);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2 + 12;

        // 价格输入框：放在"返回"按钮（panelX+6, 宽48）右侧同一行
        pBoxX = panelX + 60;
        pBoxY = panelY + panelH - 25;
        priceInputBox = new EditBox(font, pBoxX, pBoxY, 80, 18, Component.literal("价格"));
        priceInputBox.setMaxLength(10);
        priceInputBox.setFilter(s -> s.matches("\\d*"));
        priceInputBox.setVisible(false);
        addRenderableWidget(priceInputBox);

        addRenderableWidget(Button.builder(Component.literal("返回"), b ->
                minecraft.setScreen(parent != null ? parent : new MenuHubScreen()))
                .pos(panelX + 6, panelY + panelH - 24).size(48, 18).build());

        // 打开即请求黑市数据
        ClientPlayNetworking.send(new BlackMarketSyncRequestC2SPayload());
        refreshMarketData();
        refreshMythData();
        // 请求每日商店数据
        ClientPlayNetworking.send(new DailyShopSyncRequestC2SPayload());
        refreshDailyData();
    }

    /** 服务端推送黑市数据后回调刷新 */
    public void onMarketSync() {
        refreshMarketData();
    }

    /** 服务端推送每日商店数据后回调刷新 */
    public void onDailySync() {
        refreshDailyData();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        LobbySubscreenRenderer.background(graphics, width, height);
        if (priceInputBox != null) priceInputBox.setVisible(false);

        int headerH = 24;
        LobbySubscreenRenderer.panel(graphics, panelX, panelY - headerH, panelW, panelH + headerH);
        LobbySubscreenRenderer.header(graphics, font, panelX, panelY - headerH, panelW, "商城", null);
        String timeStr = LocalTime.now().format(TIME_FMT);
        graphics.drawString(font, timeStr, panelX + panelW - font.width(timeStr) - 12, panelY - headerH + 10, TEXT_DIM);

        graphics.fill(panelX + 1, panelY + 30, panelX + panelW - 1, panelY + panelH - 1, 0x66131D1D);

        renderTabBar(graphics, mouseX, mouseY);

        int contentX = panelX + 8;
        int contentY = panelY + tabH + 4;
        int contentW = panelW - 16;

        // 顶栏下方显示货币与神话碎片
        if (minecraft.player != null) {
            int coins = PlayerEconomyManager.getCoinNum(minecraft.player);
            int shards = CS2InventoryComponent.KEY.get(minecraft.player).getMythicShards();
            String info = "货币: " + coins + "　|　神话碎片: " + shards;
            graphics.drawCenteredString(font, info, panelX + panelW / 2, panelY - headerH + 10, TEXT_DIM);
        }

        switch (tab) {
            case SHOP -> renderShopTab(graphics, mouseX, mouseY, contentX, contentY + 22, contentW);
            case MARKET -> renderMarketTab(graphics, mouseX, mouseY);
            case DAILY -> renderDailyTab(graphics, mouseX, mouseY);
            case MYTH -> renderMythTab(graphics, mouseX, mouseY);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    // 自绘界面已铺满整屏底色；屏蔽原版 renderBackground，避免背景模糊与泥土遮罩盖在上面发糊。
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    private void renderTabBar(GuiGraphics graphics, int mouseX, int mouseY) {
        int each = panelW / TAB_LABELS.length;
        for (int i = 0; i < TAB_LABELS.length; i++) {
            int x = panelX + i * each;
            Tab t = Tab.values()[i];
            boolean selected = t == tab;
            boolean hovered = mouseX >= x && mouseX < x + each && mouseY >= panelY && mouseY < panelY + tabH;
            int bg = selected ? TAB_ACTIVE : (hovered ? TAB_HOVER : TAB_IDLE);
            graphics.fill(x, panelY, x + each, panelY + tabH, bg);
            graphics.drawCenteredString(font, TAB_LABELS[i], x + each / 2, panelY + (tabH - font.lineHeight) / 2,
                    selected ? TEXT_COLOR : TEXT_DIM);
        }
        graphics.fill(panelX, panelY + tabH, panelX + panelW, panelY + tabH + 1, BORDER_COLOR);
    }

    private void renderShopTab(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w) {
        shopCells.clear();

        // 类型选择按钮（钥匙 / 箱子 / 皮肤）
        int catW = 56;
        int catGap = 6;
        int cx = x;
        for (int i = 0; i < CATEGORY_LABELS.length; i++) {
            boolean selected = i == category;
            boolean hovered = mouseX >= cx && mouseX < cx + catW && mouseY >= y - 20 && mouseY < y - 20 + 18;
            LobbySubscreenRenderer.card(graphics, cx, y - 20, catW, 18, selected, hovered);
            graphics.drawCenteredString(font, CATEGORY_LABELS[i], cx + catW / 2, y - 20 + (18 - font.lineHeight) / 2, TEXT_COLOR);
            cx += catW + catGap;
        }

        // 按类型过滤商品
        String wantType = CATEGORY_IDS[category];
        List<ShopConfig.ShopItem> items = new ArrayList<>();
        for (ShopConfig.ShopItem it : ShopConfig.getInstance().getShopItems()) {
            if (wantType.equals(it.type)) items.add(it);
        }
        if (items.isEmpty()) {
            graphics.drawString(font, "该分类暂无商品", x, y + 30, TEXT_DIM, false);
            return;
        }

        // 每行 4 个
        int cols = 4;
        int gap = 8;
        int cellW = (w - gap * (cols - 1)) / cols;
        int cellH = 56;
        for (int i = 0; i < items.size(); i++) {
            ShopConfig.ShopItem it = items.get(i);
            int col = i % cols;
            int row = i / cols;
            int ix = x + col * (cellW + gap);
            int iy = y + row * (cellH + gap);
            if (iy + cellH > panelY + panelH - 30) break;

            boolean hovered = mouseX >= ix && mouseX < ix + cellW && mouseY >= iy && mouseY < iy + cellH;
            LobbySubscreenRenderer.card(graphics, ix, iy, cellW, cellH, false, hovered);

            String label = truncate(it.name, cellW - 8);
            graphics.drawString(font, label, ix + 4, iy + 4, TEXT_COLOR, false);
            graphics.drawString(font, it.price + " 货币", ix + 4, iy + 15, GOLD, false);

            // 底部控制行： −  数量  +          [购买]
            int ctrlH = 13;
            int ctrlY = iy + cellH - ctrlH - 3;
            int count = cellBuyCount.getOrDefault(it.id, 1);
            String cs = String.valueOf(count);
            int minusX = ix + 3;
            int numX = minusX + ctrlH + 3;
            int numW = Math.max(font.width(cs), 10);
            int plusX = numX + numW + 3;
            int buyW = 30;
            int buyX = ix + cellW - 3 - buyW;

            drawButton(graphics, minusX, ctrlY, ctrlH, ctrlH, "-", CELL_BG, mouseX, mouseY);
            graphics.drawString(font, cs, numX + (numW - font.width(cs)) / 2, ctrlY + 3, TEXT_COLOR, false);
            drawButton(graphics, plusX, ctrlY, ctrlH, ctrlH, "+", CELL_BG, mouseX, mouseY);

            // 购买按钮：点击后"往里陷一下再弹回"（sin 曲线，约 220ms）
            int inset = 0, drop = 0;
            Long pt0 = buyPressTime.get(it.id);
            if (pt0 != null) {
                long el = System.currentTimeMillis() - pt0;
                final int DUR = 220;
                if (el >= 0 && el < DUR) {
                    float wave = (float) Math.sin((el / (float) DUR) * Math.PI); // 0→1→0
                    inset = Math.round(3f * wave);
                    drop = Math.round(2f * wave);
                } else {
                    buyPressTime.remove(it.id);
                }
            }
            int bx = buyX + inset, by = ctrlY + drop;
            int bw = Math.max(2, buyW - inset * 2), bh = Math.max(2, ctrlH - drop * 2);
            boolean buyHov = mouseX >= buyX && mouseX < buyX + buyW && mouseY >= ctrlY && mouseY < ctrlY + ctrlH;
            int buyCol = inset > 0 ? (ACCENT | 0x30000000) : (buyHov ? (ACCENT | 0x20000000) : ACCENT);
            graphics.fill(bx, by, bx + bw, by + bh, buyCol);
            graphics.renderOutline(bx, by, bw, bh, OUTLINE);
            graphics.drawCenteredString(font, "购买", bx + bw / 2, by + (bh - font.lineHeight) / 2, TEXT_COLOR);

            CellHit ch = new CellHit(ix, iy, cellW, cellH, it.type, it.id, it.name, it.price);
            ch.minusX = minusX; ch.minusY = ctrlY; ch.minusW = ctrlH; ch.minusH = ctrlH;
            ch.plusX = plusX; ch.plusY = ctrlY; ch.plusW = ctrlH; ch.plusH = ctrlH;
            ch.buyX = buyX; ch.buyY = ctrlY; ch.buyW = buyW; ch.buyH = ctrlH;
            shopCells.add(ch);
        }
    }

    // ───────────────────────── 自由市场 ─────────────────────────

    private void refreshMarketData() {
        marketRows.clear();
        ownListingIds.clear();
        myPendingCoins = CS2ShopScreen.getMyPendingCoins();
        myPendingShards = CS2ShopScreen.getMyPendingShards();
        String myUuid = minecraft.player != null ? minecraft.player.getUUID().toString() : "";
        try {
            Type lt = new TypeToken<List<MarketListingData>>() {}.getType();
            List<MarketListingData> data = GSON.fromJson(CS2ShopScreen.getMarketDataCache(), lt);
            if (data != null) {
                for (MarketListingData d : data) {
                    MarketRow r = new MarketRow(d.listingId, d.sellerName, d.itemType, d.itemId,
                            listingDisplayName(d.itemType, d.itemId), d.price, d.currency);
                    if (d.sellerUuid != null && d.sellerUuid.equals(myUuid)) {
                        r.own = true;
                        ownListingIds.add(d.listingId);
                    }
                    marketRows.add(r);
                }
            }
        } catch (Exception ignored) {}

        // 可上架物品（仓库中的箱子/皮肤/音乐盒/职业卡）
        listableItems.clear();
        if (minecraft.player != null) {
            CS2InventoryComponent inv = CS2InventoryComponent.KEY.get(minecraft.player);
            for (var e : inv.getBoxes().entrySet())
                if (e.getValue() > 0) listableItems.add(new ListableItem("box", e.getKey(), boxName(e.getKey()), e.getValue(), -1));
            for (var e : inv.getSkins().entrySet())
                if (e.getValue() > 0) listableItems.add(new ListableItem("skin", e.getKey(), CS2SkinInfo.getName(e.getKey()), e.getValue(), skinQuality(e.getKey())));
            for (var e : inv.getMusicBoxes().entrySet())
                if (e.getValue() > 0) listableItems.add(new ListableItem("musicbox", e.getKey(), musicBoxName(e.getKey()), e.getValue(), -1));
            var bp = ClientPlayerDataCache.backpack(minecraft.player.getUUID());
            for (FactionCardType t : CARD_ORDER) {
                int c = bp.cards.getOrDefault(t, 0);
                if (c > 0) listableItems.add(new ListableItem("card", t.questKey, cardName(t), c, -1));
            }
        }
        clampPages();
    }

    /** 品质判定：与商店/神话一致，用 ItemSkinManager 的颜色→品质 */
    private static int skinQuality(String skinId) {
        String[] p = skinId.split("/", 2);
        if (p.length < 2) return -1;
        var inner = ItemSkinManager.getSkins().get(p[0]);
        if (inner == null) return -1;
        var skin = inner.get(p[1]);
        if (skin == null) return -1;
        return ItemSkinManager.qualityFromColor(skin.getColor());
    }

    private void renderMarketTab(GuiGraphics g, int mouseX, int mouseY) {
        marketHits.clear();
        int cx = panelX + 8;
        int cw = panelW - 16;
        int cy = panelY + tabH + 6;
        int py = panelY + panelH - 42;

        if (listingMode) {
            renderListing(g, mouseX, mouseY, cx, cy, cw, py);
            return;
        }

        if (sellMode) {
            renderSell(g, mouseX, mouseY, cx, cy, cw, py);
            return;
        }

        // 操作按钮行
        int own = ownListingIds.size();
        boolean atLimit = own >= MAX_LISTINGS;
        drawButton(g, cx, cy, 100, 18, "+ 上架 (" + own + "/" + MAX_LISTINGS + ")", atLimit ? CELL_DISABLE : ACCENT, mouseX, mouseY);
        marketHits.add(new MarketHit(cx, cy, 100, 18, "listBtn", null, null));
        drawButton(g, cx + 108, cy, 100, 18, "快速出售", ACCENT, mouseX, mouseY);
        marketHits.add(new MarketHit(cx + 108, cy, 100, 18, "sellBtn", null, null));
        if (myPendingCoins > 0 || myPendingShards > 0) {
            StringBuilder sb = new StringBuilder("领取");
            boolean has = false;
            if (myPendingCoins > 0) { sb.append(" ").append(myPendingCoins).append(" 货币"); has = true; }
            if (myPendingShards > 0) { if (has) sb.append("/"); sb.append(" ").append(myPendingShards).append(" 碎片"); }
            String lbl = sb.toString();
            int w = Math.max(120, font.width(lbl) + 12);
            drawButton(g, cx + 216, cy, w, 18, lbl, 0x60FFAA00, mouseX, mouseY);
            marketHits.add(new MarketHit(cx + 216, cy, w, 18, "claimBtn", null, null));
        }

        int gy = cy + 24;
        int gap = 6;
        int cellW = (cw - gap * (MARKET_COLS - 1)) / MARKET_COLS;
        int cellH = (py - 6 - gy - gap) / 2;

        if (marketRows.isEmpty()) {
            g.drawString(font, "自由市场暂无商品", cx, gy + 10, TEXT_DIM, false);
        } else {
            int start = marketPage * MARKET_PER_PAGE;
            for (int i = 0; i < MARKET_PER_PAGE; i++) {
                int idx = start + i;
                if (idx >= marketRows.size()) break;
                int col = i % MARKET_COLS, row = i / MARKET_COLS;
                int ix = cx + col * (cellW + gap), iy = gy + row * (cellH + gap);
                MarketRow r = marketRows.get(idx);
                boolean hov = mouseX >= ix && mouseX < ix + cellW && mouseY >= iy && mouseY < iy + cellH;
                g.fill(ix, iy, ix + cellW, iy + cellH, hov ? CELL_HOVER : CELL_BG);
                g.renderOutline(ix, iy, cellW, cellH, OUTLINE);
                g.drawString(font, truncate(r.displayName, cellW - 8), ix + 4, iy + 6, TEXT_COLOR, false);
                g.drawString(font, "卖家:" + truncate(r.sellerName, cellW - 8), ix + 4, iy + 20, TEXT_DIM, false);
                boolean shardRow = "shard".equals(r.currency);
                g.drawString(font, r.price + (shardRow ? " 碎片" : " 货币"), ix + 4, iy + 34, shardRow ? MYTH_PURPLE : GOLD, false);
                String act = r.own ? "下架" : "购买";
                g.drawString(font, act, ix + cellW - 4 - font.width(act), iy + cellH - 12,
                        r.own ? 0xFFFF4444 : 0xFF44FF44, false);
                marketHits.add(new MarketHit(ix, iy, cellW, cellH, "row", r, null));
            }
        }
        renderPagination(g, mouseX, mouseY, cx, py, cw, marketPage, pageCount(marketRows.size()), "m");
    }

    private void renderListing(GuiGraphics g, int mouseX, int mouseY, int cx, int cy, int cw, int py) {
        g.drawString(font, "选择要上架的物品 (" + ownListingIds.size() + "/" + MAX_LISTINGS + ")", cx, cy, TEXT_COLOR, false);
        int gy = cy + 16;
        int gap = 6;
        int cellW = (cw - gap * (MARKET_COLS - 1)) / MARKET_COLS;
        int cellH = (py - gy - gap) / 2;

        if (listableItems.isEmpty()) {
            g.drawString(font, "仓库中没有可上架的物品", cx, gy + 10, TEXT_DIM, false);
        } else {
            int start = listPage * MARKET_PER_PAGE;
            for (int i = 0; i < MARKET_PER_PAGE; i++) {
                int idx = start + i;
                if (idx >= listableItems.size()) break;
                int col = i % MARKET_COLS, row = i / MARKET_COLS;
                int ix = cx + col * (cellW + gap), iy = gy + row * (cellH + gap);
                ListableItem it = listableItems.get(idx);
                boolean sel = it.type.equals(selListType) && it.id.equals(selListId);
                boolean hov = mouseX >= ix && mouseX < ix + cellW && mouseY >= iy && mouseY < iy + cellH;
                int qCol = (it.quality >= 0 && it.quality < QUALITY_COLORS.length) ? QUALITY_COLORS[it.quality] : OUTLINE;
                g.fill(ix, iy, ix + cellW, iy + cellH, sel ? 0x504488FF : (hov ? CELL_HOVER : CELL_BG));
                g.renderOutline(ix, iy, cellW, cellH, sel ? ACCENT : qCol);
                if ("skin".equals(it.type)) {
                    int iconSize = Math.min(cellW - 10, 30);
                    drawSkinIcon(g, it.id, ix + (cellW - iconSize) / 2, iy + 5, iconSize);
                    g.drawCenteredString(font, truncate(it.name, cellW - 6), ix + cellW / 2, iy + 7 + iconSize, TEXT_COLOR);
                    if (it.quality >= 0 && it.quality < QUALITY_NAMES.length)
                        g.drawCenteredString(font, QUALITY_NAMES[it.quality], ix + cellW / 2, iy + 7 + iconSize + 11, qCol);
                } else {
                    g.drawString(font, truncate(it.name, cellW - 8), ix + 4, iy + 6, TEXT_COLOR, false);
                    g.drawString(font, "x" + it.count, ix + 4, iy + 20, TEXT_DIM, false);
                }
                marketHits.add(new MarketHit(ix, iy, cellW, cellH, "listable", null, it));
            }
        }
        renderPagination(g, mouseX, mouseY, cx, py, cw, listPage, pageCount(listableItems.size()), "l");

        // 底部控制行：紧邻"返回"按钮（返回在 panelX+6, 宽48）。价格框为真实 Widget 位于 pBoxX/pBoxY
        if (!selListType.isEmpty()) {
            if (priceInputBox != null) priceInputBox.setVisible(true);
            int rowY = pBoxY;
            g.drawString(font, "已选: " + truncate(selListName, 150), cx, rowY - 12, ACCENT, false);
            int toggleX = pBoxX + 84;
            int confirmX = toggleX + 60;
            int cancelX = confirmX + 68;
            boolean shard = "shard".equals(listingCurrency);
            drawButton(g, toggleX, rowY, 56, 18, shard ? "碎片" : "货币", shard ? 0x60CC88FF : ACCENT, mouseX, mouseY);
            marketHits.add(new MarketHit(toggleX, rowY, 56, 18, "curToggle", null, null));
            drawButton(g, confirmX, rowY, 64, 18, "确认上架", 0x6044FF44, mouseX, mouseY);
            marketHits.add(new MarketHit(confirmX, rowY, 64, 18, "confirm", null, null));
            drawButton(g, cancelX, rowY, 50, 18, "取消", 0x60FF4444, mouseX, mouseY);
            marketHits.add(new MarketHit(cancelX, rowY, 50, 18, "cancelList", null, null));
        } else if (priceInputBox != null) {
            priceInputBox.setVisible(false);
        }
    }

// ───────────────────────── 快速出售 ─────────────────────────

    /** 每帧实时读取仓库，出售数量随组件同步更新（与旧 CS2 商店一致） */
    private void rebuildSellItems() {
        sellableItems.clear();
        if (minecraft.player == null) return;
        CS2InventoryComponent inv = CS2InventoryComponent.KEY.get(minecraft.player);
        for (var e : inv.getBoxes().entrySet())
            if (e.getValue() > 0) sellableItems.add(new ListableItem("box", e.getKey(), boxName(e.getKey()), e.getValue(), -1));
        for (var e : inv.getSkins().entrySet())
            if (e.getValue() > 0) sellableItems.add(new ListableItem("skin", e.getKey(), CS2SkinInfo.getName(e.getKey()), e.getValue(), skinQuality(e.getKey())));
        for (var e : inv.getMusicBoxes().entrySet())
            if (e.getValue() > 0) sellableItems.add(new ListableItem("musicbox", e.getKey(), musicBoxName(e.getKey()), e.getValue(), -1));
    }

    /** 客户端展示用的回收价（真正结算以服务端为准） */
    private static int sellPrice(ListableItem it) {
        var sp = ShopConfig.getInstance().getSellPriceConfig();
        return switch (it.type) {
            case "box" -> sp.boxPrices.getOrDefault(it.id, 10);
            case "musicbox" -> sp.musicBoxSellPrice;
            case "skin" -> it.quality == 5
                    ? CS2InventoryComponent.MYTHIC_SHARD_SELL_AMOUNT
                    : sp.getSkinPriceByQuality(it.quality);
            default -> 0;
        };
    }

    private void renderSell(GuiGraphics g, int mouseX, int mouseY, int cx, int cy, int cw, int py) {
        rebuildSellItems();
        g.drawString(font, "快速出售 · 点击物品立即回收", cx, cy, TEXT_COLOR, false);
        int backW = font.width("返回") + 12;
        drawButton(g, cx + cw - backW, cy - 2, backW, 18, "返回", 0x60FF4444, mouseX, mouseY);
        marketHits.add(new MarketHit(cx + cw - backW, cy - 2, backW, 18, "sellBack", null, null));

        int gy = cy + 16;
        int gap = 6;
        int cellW = (cw - gap * (MARKET_COLS - 1)) / MARKET_COLS;
        int cellH = (py - gy - gap) / 2;

        if (sellableItems.isEmpty()) {
            g.drawString(font, "仓库中没有可出售的物品", cx, gy + 10, TEXT_DIM, false);
        } else {
            int start = sellPage * MARKET_PER_PAGE;
            for (int i = 0; i < MARKET_PER_PAGE; i++) {
                int idx = start + i;
                if (idx >= sellableItems.size()) break;
                int col = i % MARKET_COLS, row = i / MARKET_COLS;
                int ix = cx + col * (cellW + gap), iy = gy + row * (cellH + gap);
                ListableItem it = sellableItems.get(idx);
                boolean hov = mouseX >= ix && mouseX < ix + cellW && mouseY >= iy && mouseY < iy + cellH;
                int qCol = (it.quality >= 0 && it.quality < QUALITY_COLORS.length) ? QUALITY_COLORS[it.quality] : OUTLINE;
                g.fill(ix, iy, ix + cellW, iy + cellH, hov ? CELL_HOVER : CELL_BG);
                g.renderOutline(ix, iy, cellW, cellH, qCol);
                if ("skin".equals(it.type)) {
                    int iconSize = Math.min(cellW - 10, 30);
                    drawSkinIcon(g, it.id, ix + (cellW - iconSize) / 2, iy + 5, iconSize);
                    g.drawCenteredString(font, truncate(it.name, cellW - 6), ix + cellW / 2, iy + 7 + iconSize, TEXT_COLOR);
                    if (it.quality >= 0 && it.quality < QUALITY_NAMES.length)
                        g.drawCenteredString(font, QUALITY_NAMES[it.quality], ix + cellW / 2, iy + 7 + iconSize + 11, qCol);
                } else {
                    g.drawString(font, truncate(it.name, cellW - 8), ix + 4, iy + 6, TEXT_COLOR, false);
                    g.drawString(font, "x" + it.count, ix + 4, iy + 20, TEXT_DIM, false);
                }
                boolean shard = "skin".equals(it.type) && it.quality == 5;
                g.drawString(font, "卖 " + sellPrice(it) + (shard ? " 碎片" : ""), ix + 4, iy + cellH - 24,
                        shard ? MYTH_PURPLE : GOLD, false);
                String act = "出售 x" + it.count;
                g.drawString(font, act, ix + cellW - 4 - font.width(act), iy + cellH - 12, 0xFF44FF44, false);
                marketHits.add(new MarketHit(ix, iy, cellW, cellH, "sellItem", null, it));
            }
        }
        renderPagination(g, mouseX, mouseY, cx, py, cw, sellPage, pageCount(sellableItems.size()), "s");
    }
    private void renderPagination(GuiGraphics g, int mouseX, int mouseY, int cx, int py, int cw, int page, int pages, String tag) {
        drawButton(g, cx, py, 20, 16, "◀", page > 0 ? CELL_BG : CELL_DISABLE, mouseX, mouseY);
        if (page > 0) marketHits.add(new MarketHit(cx, py, 20, 16, "prev:" + tag, null, null));
        g.drawCenteredString(font, (page + 1) + "/" + pages, cx + cw / 2, py + 4, TEXT_COLOR);
        drawButton(g, cx + cw - 20, py, 20, 16, "▶", page < pages - 1 ? CELL_BG : CELL_DISABLE, mouseX, mouseY);
        if (page < pages - 1) marketHits.add(new MarketHit(cx + cw - 20, py, 20, 16, "next:" + tag, null, null));
    }

    private void drawButton(GuiGraphics g, int x, int y, int w, int h, String label, int color, int mouseX, int mouseY) {
        boolean hov = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        g.fill(x, y, x + w, y + h, hov ? (color | 0x20000000) : color);
        g.renderOutline(x, y, w, h, OUTLINE);
        g.drawCenteredString(font, label, x + w / 2, y + (h - font.lineHeight) / 2, TEXT_COLOR);
    }

    private int pageCount(int total) {
        return Math.max(1, (int) Math.ceil(total / (double) MARKET_PER_PAGE));
    }

    private int pageCount(int total, int perPage) {
        return Math.max(1, (int) Math.ceil(total / (double) perPage));
    }

    private void clampPages() {
        int mp = pageCount(marketRows.size());
        if (marketPage > mp - 1) marketPage = mp - 1;
        if (marketPage < 0) marketPage = 0;
        int lp = pageCount(listableItems.size());
        if (listPage > lp - 1) listPage = lp - 1;
        if (listPage < 0) listPage = 0;
        int yp = pageCount(mythSkins.size(), MYTH_PER_PAGE);
        if (mythPage > yp - 1) mythPage = yp - 1;
        if (mythPage < 0) mythPage = 0;
        int dap = pageCount(dailyRows.size());
        if (dailyPage > dap - 1) dailyPage = dap - 1;
        if (dailyPage < 0) dailyPage = 0;
        int sp = pageCount(sellableItems.size());
        if (sellPage > sp - 1) sellPage = sp - 1;
        if (sellPage < 0) sellPage = 0;
    }

    private boolean doMarketAction(MarketHit h) {
        switch (h.action) {
            case "listBtn" -> {
                if (ownListingIds.size() >= MAX_LISTINGS) {
                    tell("§c上架数量已达上限（最多 " + MAX_LISTINGS + " 件），请先下架其他商品");
                    return true;
                }
                listingMode = true;
                listPage = 0;
                listingCurrency = "coin";
                selListType = ""; selListId = ""; selListName = "";
            }
            case "claimBtn" -> ClientPlayNetworking.send(new BlackMarketClaimC2SPayload());
            case "curToggle" -> listingCurrency = "coin".equals(listingCurrency) ? "shard" : "coin";
            case "sellBtn" -> {
                sellMode = true;
                listingMode = false;
                sellPage = 0;
                if (priceInputBox != null) priceInputBox.setVisible(false);
            }
            case "sellBack" -> sellMode = false;
            case "prev:s" -> sellPage--;
            case "next:s" -> sellPage++;
            case "sellItem" -> ClientPlayNetworking.send(new ShopSellC2SPayload(h.listable.type, h.listable.id));
            case "prev:m" -> marketPage--;
            case "next:m" -> marketPage++;
            case "prev:l" -> listPage--;
            case "next:l" -> listPage++;
            case "row" -> {
                if (h.row.own) {
                    ClientPlayNetworking.send(new BlackMarketCancelC2SPayload(h.row.listingId));
                } else {
                    boolean shardBuy = "shard".equals(h.row.currency);
                    if (minecraft.player != null) {
                        if (shardBuy) {
                            int sh = CS2InventoryComponent.KEY.get(minecraft.player).getMythicShards();
                            if (sh < h.row.price) { tell("§c神话碎片不足，需要 " + h.row.price + " 碎片"); return true; }
                        } else {
                            int coins = PlayerEconomyManager.getCoinNum(minecraft.player);
                            if (coins < h.row.price) { tell("§c货币不足，需要 " + h.row.price + " 货币"); return true; }
                        }
                    }
                    ClientPlayNetworking.send(new BlackMarketBuyC2SPayload(h.row.listingId));
                }
            }
            case "listable" -> {
                selListType = h.listable.type;
                selListId = h.listable.id;
                selListName = h.listable.name;
                if (priceInputBox != null) { priceInputBox.setValue(""); priceInputBox.setFocused(true); }
            }
            case "confirm" -> {
                String s = priceInputBox != null ? priceInputBox.getValue() : "";
                try {
                    int price = Integer.parseInt(s);
                    if (price > 0) {
                        ClientPlayNetworking.send(new BlackMarketListC2SPayload(selListType, selListId, price, listingCurrency));
                        listingMode = false;
                        selListType = ""; selListId = ""; selListName = "";
                        if (priceInputBox != null) priceInputBox.setVisible(false);
                    } else {
                        tell("§c请输入有效的价格");
                    }
                } catch (NumberFormatException e) {
                    tell("§c请输入有效的价格");
                }
            }
            case "cancelList" -> {
                listingMode = false;
                selListType = ""; selListId = ""; selListName = "";
                if (priceInputBox != null) priceInputBox.setVisible(false);
            }
        }
        clampPages();
        return true;
    }

    // ───────────────────────── 神话商城 ─────────────────────────

    private void refreshMythData() {
        mythSkins.clear();
        try {
            mythSkins.addAll(CS2ShopScreen.buildMythSkinIds());
        } catch (Exception ignored) {}
        clampPages();
    }

    private void renderMythTab(GuiGraphics g, int mouseX, int mouseY) {
        marketHits.clear();
        int cx = panelX + 8;
        int cw = panelW - 16;
        int cy = panelY + tabH + 6;
        int py = panelY + panelH - 42;

        int shards = minecraft.player != null
                ? CS2InventoryComponent.KEY.get(minecraft.player).getMythicShards() : 0;
        g.drawString(font, "持有神话碎片: " + shards + "　每件神话皮肤 " + MYTH_PRICE + " 碎片",
                cx, cy, MYTH_PURPLE, false);

        int gy = cy + 18;
        int gap = 6;
        int cellW = (cw - gap * (MYTH_COLS - 1)) / MYTH_COLS;
        int cellH = py - gy;

        if (mythSkins.isEmpty()) {
            g.drawString(font, "暂无可兑换的神话皮肤", cx, gy + 10, TEXT_DIM, false);
        } else {
            int start = mythPage * MYTH_PER_PAGE;
            for (int i = 0; i < MYTH_PER_PAGE; i++) {
                int idx = start + i;
                if (idx >= mythSkins.size()) break;
                String skinId = mythSkins.get(idx);
                int ix = cx + i * (cellW + gap), iy = gy;
                boolean hov = mouseX >= ix && mouseX < ix + cellW && mouseY >= iy && mouseY < iy + cellH;
                g.fill(ix, iy, ix + cellW, iy + cellH, hov ? CELL_HOVER : CELL_BG);
                g.renderOutline(ix, iy, cellW, cellH, MYTH_PURPLE);

                // 皮肤名（顶部居中）
                g.drawCenteredString(font, truncate(CS2SkinInfo.getName(skinId), cellW - 6),
                        ix + cellW / 2, iy + 6, TEXT_COLOR);

                // 皮肤贴图（卡片中间）
                int iconSize = Math.min(cellW - 10, 48);
                drawSkinIcon(g, skinId, ix + (cellW - iconSize) / 2, iy + 20, iconSize);

                // 品质
                int qy = iy + 24 + iconSize;
                g.drawCenteredString(font, "神话品质", ix + cellW / 2, qy, MYTH_PURPLE);

                // 灰字简介（自动换行，最多 3 行）
                String desc = CS2SkinInfo.getDescription(skinId);
                if (desc != null && !desc.isEmpty()) {
                    int dy = qy + 12;
                    List<String> lines = wrapText(desc, cellW - 8);
                    int shown = Math.min(lines.size(), 3);
                    for (int L = 0; L < shown; L++) {
                        g.drawCenteredString(font, lines.get(L), ix + cellW / 2,
                                dy + L * (font.lineHeight + 1), 0xFF888888);
                    }
                    if (lines.size() > shown) {
                        g.drawCenteredString(font, "…", ix + cellW / 2,
                                dy + shown * (font.lineHeight + 1), 0xFF888888);
                    }
                }

                // 价格 + 兑换按钮（底部）
                g.drawCenteredString(font, MYTH_PRICE + " 碎片", ix + cellW / 2, iy + cellH - 40, MYTH_PURPLE);
                boolean canBuy = shards >= MYTH_PRICE;
                drawButton(g, ix + 4, iy + cellH - 22, cellW - 8, 18, "兑换",
                        canBuy ? 0x60CC88FF : CELL_DISABLE, mouseX, mouseY);
                if (canBuy)
                    marketHits.add(new MarketHit(ix + 4, iy + cellH - 22, cellW - 8, 18, "buy:" + skinId, null, null));
            }
        }
        renderPagination(g, mouseX, mouseY, cx, py, cw, mythPage,
                pageCount(mythSkins.size(), MYTH_PER_PAGE), "my");
    }

    /** 渲染皮肤贴图：基础物品 + SKIN 组件 → 缩放渲染（与仓库/开箱同款链路） */
    private void drawSkinIcon(GuiGraphics g, String skinId, int x, int y, int size) {
        ItemStack stack = mythIconStack(skinId);
        if (stack == null) return;
        String[] parts = skinId.split("/");
        if (parts.length >= 2) stack.set(SREDataComponentTypes.SKIN, parts[1]);
        float scale = size / 16f;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.renderFakeItem(stack, 0, 0);
        g.pose().popPose();
    }

    private static ItemStack mythIconStack(String skinId) {
        if (skinId == null) return null;
        if (skinId.startsWith("knife/")) return TMMItems.KNIFE.getDefaultInstance();
        if (skinId.startsWith("revolver/") || skinId.startsWith("gun/")) return TMMItems.REVOLVER.getDefaultInstance();
        if (skinId.startsWith("bat/")) return TMMItems.BAT.getDefaultInstance();
        if (skinId.startsWith("grenade/")) return TMMItems.GRENADE.getDefaultInstance();
        if (skinId.startsWith("hat/")) {
            ItemStack icon = CS2SkinInfo.getIconStack(skinId);
            return icon != null ? icon : new ItemStack(Items.LEATHER_HELMET);
        }
        return null;
    }

    /** 按像素宽度逐字换行（适配中文简介） */
    private List<String> wrapText(String s, int maxW) {
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (cur.length() > 0 && font.width(cur.toString() + c) > maxW) {
                lines.add(cur.toString());
                cur.setLength(0);
            }
            cur.append(c);
        }
        if (cur.length() > 0) lines.add(cur.toString());
        return lines;
    }

    private void buyMyth(String skinId) {
        if (minecraft.player == null) return;
        int shards = CS2InventoryComponent.KEY.get(minecraft.player).getMythicShards();
        if (shards < MYTH_PRICE) {
            tell("§c神话碎片不足，需要 " + MYTH_PRICE + " 枚");
            return;
        }
        ClientPlayNetworking.send(new MythicShopBuyC2SPayload(skinId));
    }

    // ───────────────────────── 每日商店 ─────────────────────────

    private void refreshDailyData() {
        dailyRows.clear();
        try {
            com.google.gson.JsonObject root = com.google.gson.JsonParser
                    .parseString(CS2ShopScreen.getDailyShopCache()).getAsJsonObject();
            if (root.has("items")) {
                for (var el : root.getAsJsonArray("items")) {
                    com.google.gson.JsonObject o = el.getAsJsonObject();
                    int slot = o.get("slot").getAsInt();
                    String itemId = o.get("itemId").getAsString();
                    String itemType = o.get("itemType").getAsString();
                    int quality = o.get("quality").getAsInt();
                    int price = o.get("price").getAsInt();
                    boolean purchased = o.has("purchased") && o.get("purchased").getAsBoolean();
                    String name = "musicbox".equals(itemType)
                            ? musicBoxName(itemId.substring("musicbox/".length()))
                            : CS2SkinInfo.getName(itemId);
                    String desc = "musicbox".equals(itemType)
                            ? ""
                            : CS2SkinInfo.getDescription(itemId);
                    dailyRows.add(new DailyRow(slot, itemType, quality, price, purchased, name, desc,
                            "musicbox".equals(itemType) ? null : itemId));
                }
            }
        } catch (Exception ignored) {}
        clampPages();
    }

    private void renderDailyTab(GuiGraphics g, int mouseX, int mouseY) {
        marketHits.clear();
        int cx = panelX + 8;
        int cw = panelW - 16;
        int cy = panelY + tabH + 6;
        int py = panelY + panelH - 42;

        g.drawString(font, "每日 00:00 刷新  |  下次刷新: " + nextRefreshCountdown(),
                cx, cy, 0xFF88AACC, false);

        int gy = cy + 16;
        int gap = 6;
        int cellW = (cw - gap * (DAILY_COLS - 1)) / DAILY_COLS;
        int cellH = (py - 6 - gy - gap) / 2;

        if (dailyRows.isEmpty()) {
            g.drawString(font, "暂无每日商品", cx, gy + 10, TEXT_DIM, false);
        } else {
            int start = dailyPage * DAILY_PER_PAGE;
            for (int i = 0; i < DAILY_PER_PAGE; i++) {
                int idx = start + i;
                if (idx >= dailyRows.size()) break;
                int col = i % DAILY_COLS, row = i / DAILY_COLS;
                int ix = cx + col * (cellW + gap), iy = gy + row * (cellH + gap);
                DailyRow d = dailyRows.get(idx);
                boolean hov = mouseX >= ix && mouseX < ix + cellW && mouseY >= iy && mouseY < iy + cellH;
                int qCol = (d.quality >= 0 && d.quality < QUALITY_COLORS.length) ? QUALITY_COLORS[d.quality] : OUTLINE;
                g.fill(ix, iy, ix + cellW, iy + cellH, hov ? CELL_HOVER : CELL_BG);
                g.renderOutline(ix, iy, cellW, cellH, qCol);

                // 名称
                g.drawCenteredString(font, truncate(d.name, cellW - 6), ix + cellW / 2, iy + 5, TEXT_COLOR);

                // 皮肤贴图（音乐盒无贴图，跳过）
                int iconSize = Math.min(cellW - 10, 36);
                int curY = iy + 15;
                if (d.skinId != null) {
                    drawSkinIcon(g, d.skinId, ix + (cellW - iconSize) / 2, curY, iconSize);
                    curY += iconSize + 2;
                }

                // 品质标签
                if (d.quality >= 0 && d.quality < QUALITY_NAMES.length) {
                    String typeTag = "musicbox".equals(d.itemType) ? "音乐盒" : "皮肤";
                    g.drawCenteredString(font, "[" + QUALITY_NAMES[d.quality] + "·" + typeTag + "]",
                            ix + cellW / 2, curY, qCol);
                    curY += font.lineHeight + 2;
                }

                // 灰字介绍（自动换行，最多 2 行）
                if (d.desc != null && !d.desc.isEmpty()) {
                    List<String> lines = wrapText(d.desc, cellW - 8);
                    int shown = Math.min(lines.size(), 2);
                    for (int L = 0; L < shown; L++) {
                        g.drawCenteredString(font, lines.get(L), ix + cellW / 2, curY, 0xFF888888);
                        curY += font.lineHeight + 1;
                    }
                    if (lines.size() > shown) {
                        g.drawCenteredString(font, "…", ix + cellW / 2, curY, 0xFF888888);
                        curY += font.lineHeight + 1;
                    }
                }

                // 价格 + 购买/已购买（底部）
                g.drawString(font, d.price + " 货币", ix + 4, iy + cellH - 12, GOLD, false);
                if (d.purchased) {
                    String lbl = "已购买";
                    g.drawString(font, lbl, ix + cellW - 4 - font.width(lbl), iy + cellH - 12, TEXT_DIM, false);
                } else {
                    String lbl = "购买";
                    g.drawString(font, lbl, ix + cellW - 4 - font.width(lbl), iy + cellH - 12, 0xFF44FF44, false);
                    marketHits.add(new MarketHit(ix, iy, cellW, cellH, "dbuy:" + d.slot, null, null));
                }
            }
        }
        renderPagination(g, mouseX, mouseY, cx, py, cw, dailyPage, pageCount(dailyRows.size()), "d");
    }

    private void buyDaily(int slot) {
        if (minecraft.player == null) return;
        for (DailyRow d : dailyRows) {
            if (d.slot == slot) {
                if (d.purchased) { tell("§c今日已购买过该商品"); return; }
                int coins = PlayerEconomyManager.getCoinNum(minecraft.player);
                if (coins < d.price) { tell("§c货币不足，需要 " + d.price + " 货币"); return; }
                break;
            }
        }
        ClientPlayNetworking.send(new DailyShopBuyC2SPayload(slot));
    }

    private static String nextRefreshCountdown() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime next = now.toLocalDate().plusDays(1).atStartOfDay();
        long secs = java.time.Duration.between(now, next).getSeconds();
        return String.format("%02d:%02d:%02d", secs / 3600, (secs % 3600) / 60, secs % 60);
    }

    // ───────────────────────── 名称解析 ─────────────────────────

    private static String boxName(String id) {
        String n = CS2ClientBoxCache.getBoxName(id);
        return !n.isEmpty() ? n : id.replace('_', ' ');
    }

    private static String musicBoxName(String id) {
        var b = io.wifi.starrailexpress.content.musicbox.MusicBoxRegistry.get(id);
        return b != null ? b.displayName().getString() : id;
    }

    private static String cardName(FactionCardType t) {
        return switch (t) {
            case KILLER -> "杀手职业卡";
            case CIVILIAN -> "平民职业卡";
            case NEUTRAL -> "中立职业卡";
            case NEUTRAL_FOR_KILLER -> "杀手中立职业卡";
            default -> t.questKey;
        };
    }

    private String listingDisplayName(String itemType, String itemId) {
        return switch (itemType) {
            case "box" -> boxName(itemId);
            case "musicbox" -> musicBoxName(itemId);
            case "card" -> cardName(FactionCardType.fromString(itemId));
            default -> CS2SkinInfo.getName(itemId);
        };
    }

    private void tell(String msg) {
        if (minecraft.player != null) minecraft.player.displayClientMessage(Component.literal(msg), true);
    }

    private void renderPlaceholder(GuiGraphics graphics, int x, int y, int w, String title, String desc) {
        graphics.drawCenteredString(font, title + " · 建设中", x + w / 2, y + 40, TEXT_COLOR);
        graphics.drawCenteredString(font, desc, x + w / 2, y + 60, TEXT_DIM);
    }

    private String truncate(String s, int maxW) {
        if (font.width(s) <= maxW) return s;
        String t = s;
        while (!t.isEmpty() && font.width(t + "…") > maxW) {
            t = t.substring(0, t.length() - 1);
        }
        return t.isEmpty() ? "…" : t + "…";
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 自由市场上架时，价格输入框优先
        if (tab == Tab.MARKET && listingMode && priceInputBox != null && priceInputBox.isVisible()
                && priceInputBox.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        // 分页切换
        int each = panelW / TAB_LABELS.length;
        if (mouseY >= panelY && mouseY < panelY + tabH) {
            int idx = (int) ((mouseX - panelX) / each);
            if (idx >= 0 && idx < Tab.values().length) {
                tab = Tab.values()[idx];
                shopCells.clear();
                listingMode = false;
                sellMode = false;
                if (priceInputBox != null) priceInputBox.setVisible(false);
                if (tab == Tab.MARKET) {
                    ClientPlayNetworking.send(new BlackMarketSyncRequestC2SPayload());
                    refreshMarketData();
                } else if (tab == Tab.MYTH) {
                    refreshMythData();
                } else if (tab == Tab.DAILY) {
                    ClientPlayNetworking.send(new DailyShopSyncRequestC2SPayload());
                    refreshDailyData();
                }
                return true;
            }
        }

        if (tab == Tab.SHOP) {
            int x = panelX + 8;
            int catW = 56, catGap = 6;
            // 与 renderShopTab 中分类按钮绘制位置对齐：顶部 = contentY + 22 - 20 = panelY + tabH + 6
            int cy = panelY + tabH + 4 + 22 - 20;
            for (int i = 0; i < CATEGORY_LABELS.length; i++) {
                int cx = x + i * (catW + catGap);
                if (mouseX >= cx && mouseX < cx + catW && mouseY >= cy && mouseY < cy + 18) {
                    category = i;
                    shopCells.clear();
                    return true;
                }
            }
            for (CellHit c : shopCells) {
                if (c.contains((int) mouseX, (int) mouseY)) {
                    int mx = (int) mouseX, my = (int) mouseY;
                    if (c.inMinus(mx, my)) {
                        int cur = cellBuyCount.getOrDefault(c.id, 1);
                        if (cur > 1) cellBuyCount.put(c.id, cur - 1);
                        return true;
                    }
                    if (c.inPlus(mx, my)) {
                        int cur = cellBuyCount.getOrDefault(c.id, 1);
                        if (cur < 99) cellBuyCount.put(c.id, cur + 1);
                        return true;
                    }
                    if (c.inBuy(mx, my)) {
                        buyPressTime.put(c.id, System.currentTimeMillis());
                        buy(c);
                        return true;
                    }
                    return true;
                }
            }
        } else if (tab == Tab.MARKET) {
            for (MarketHit h : marketHits) {
                if (h.contains((int) mouseX, (int) mouseY)) {
                    return doMarketAction(h);
                }
            }
        } else if (tab == Tab.MYTH) {
            for (MarketHit h : marketHits) {
                if (h.contains((int) mouseX, (int) mouseY)) {
                    String a = h.action;
                    if (a.equals("prev:my")) mythPage--;
                    else if (a.equals("next:my")) mythPage++;
                    else if (a.startsWith("buy:")) buyMyth(a.substring(4));
                    clampPages();
                    return true;
                }
            }
        } else if (tab == Tab.DAILY) {
            for (MarketHit h : marketHits) {
                if (h.contains((int) mouseX, (int) mouseY)) {
                    String a = h.action;
                    if (a.equals("prev:d")) dailyPage--;
                    else if (a.equals("next:d")) dailyPage++;
                    else if (a.startsWith("dbuy:")) buyDaily(Integer.parseInt(a.substring(5)));
                    clampPages();
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (tab == Tab.MARKET && listingMode && priceInputBox != null && priceInputBox.isVisible()
                && priceInputBox.isFocused() && priceInputBox.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (tab == Tab.MARKET && listingMode && priceInputBox != null && priceInputBox.isVisible()
                && priceInputBox.isFocused() && priceInputBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    private void buy(CellHit c) {
        if (minecraft.player == null) return;
        int n = cellBuyCount.getOrDefault(c.id, 1);
        if (n < 1) n = 1;
        long total = (long) c.price * n;
        int coins = PlayerEconomyManager.getCoinNum(minecraft.player);
        if (coins < total) {
            minecraft.player.displayClientMessage(
                    Component.literal("§c货币不足，购买 " + n + " 个共需 " + total + " 货币"), true);
            return;
        }
        for (int i = 0; i < n; i++) {
            ClientPlayNetworking.send(new ShopBuyC2SPayload(c.type, c.id));
        }
        if (n > 1) {
            tell("§a已提交购买 " + n + " 个「" + c.name + "」");
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
