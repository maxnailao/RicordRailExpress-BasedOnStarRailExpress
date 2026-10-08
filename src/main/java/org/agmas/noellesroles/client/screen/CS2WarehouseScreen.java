package org.agmas.noellesroles.client.screen;

import io.wifi.starrailexpress.cca.CS2InventoryComponent;
import io.wifi.starrailexpress.cca.SREPlayerSkinsComponent;
import io.wifi.starrailexpress.client.data.ClientPlayerDataCache;
import io.wifi.starrailexpress.content.musicbox.MusicBox;
import io.wifi.starrailexpress.content.musicbox.MusicBoxPlayerComponent;
import io.wifi.starrailexpress.content.musicbox.MusicBoxRegistry;
import io.wifi.starrailexpress.data.PlayerEconomyManager;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ItemSkinManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import io.wifi.starrailexpress.progression.ProgressionState.FactionCardType;
import org.agmas.noellesroles.cs2.CS2BoxConfig;
import org.agmas.noellesroles.cs2.CS2BoxManager;
import org.agmas.noellesroles.cs2.CS2SkinInfo;
import org.agmas.noellesroles.cs2.network.ToggleFavoriteC2SPayload;
import org.agmas.noellesroles.cs2.network.DestroyWarehouseItemC2SPayload;
import org.agmas.noellesroles.cs2.network.EquipMusicBoxC2SPayload;
import org.agmas.noellesroles.cs2.network.EquipSkinC2SPayload;
import org.agmas.noellesroles.cs2.network.OpenBoxC2SPayload;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CS2 风格仓库界面
 * <p>
 * 左侧分类标签栏（全部/箱子/皮肤/音乐盒），右侧物品网格。
 * 左键检视，右键装备，双击箱子开箱。
 * </p>
 */
public class CS2WarehouseScreen extends Screen {

    /** 客户端开箱锁，防止重复发送请求 */
    public static boolean isBoxOpening = false;

    /** 本次开箱数量（1~100），由 +/- 按钮调节 */
    private int openCount = 1;
    /** 开箱按钮引用，用于动态更新显示的数量 */
    private Button openButton;

    /** 待预览的箱子 ID（双击时设置，收到服务端响应后清除） */
    private static String pendingPreviewBoxId = null;

    public static String getPendingPreviewBoxId() {
        return pendingPreviewBoxId;
    }

    public static void clearPendingPreviewBoxId() {
        pendingPreviewBoxId = null;
    }

    // 品质颜色 ARGB
    private static final int[] QUALITY_COLORS = {
            0xFFEEEEEE, // 0: common
            0xFF33FF55, // 1: uncommon
            0xFFAAAAFF, // 2: rare
            0xFFAA55FF, // 3: epic
            0xFFFFAA55, // 4: legendary
            0xFFFF3F3F, // 5: unbelievable
    };

    private static final int BG_COLOR = 0xE60C1020;
    private static final int SIDEBAR_COLOR = 0xC0161B30;
    private static final int CARD_BG_COLOR = 0x30FFFFFF;
    private static final int CARD_HOVER_COLOR = 0x50FFFFFF;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xFF999999;
    private static final int ACCENT = 0xFF4488FF;

    private enum Category { ALL, BOXES, KNIFE, REVOLVER, BAT, GRENADE, HAT, MUSIC, CARDS, TITLE }
    private Category selectedCategory = Category.ALL;

    /** 侧栏分类的显示顺序与文案（顺序即显示顺序） */
    private static final Category[] CATEGORY_ORDER = {
            Category.ALL, Category.BOXES, Category.KNIFE, Category.REVOLVER,
            Category.BAT, Category.GRENADE, Category.HAT, Category.MUSIC,
            Category.CARDS, Category.TITLE };
    private static final String[] CATEGORY_LABELS = {
            "全部", "箱子/钥匙", "刀", "左轮手枪", "棒球棍", "手雷",
            "帽子", "音乐盒", "职业卡", "称号" };

    /** 侧栏布局 */
    private static final int SIDEBAR_TOP = 40;
    private static final int SIDEBAR_ROW_H = 32;
    private static final int SIDEBAR_ROW_VISIBLE = 28;
    /** 侧栏滚动偏移（行） */
    private int sidebarScroll = 0;
    /** 侧栏滚动条是否正在拖拽 */
    private boolean draggingSidebar = false;

    /** 职业卡显示顺序与阵营名 */
    private static final FactionCardType[] CARD_DISPLAY_ORDER = {
            FactionCardType.KILLER, FactionCardType.CIVILIAN,
            FactionCardType.NEUTRAL, FactionCardType.NEUTRAL_FOR_KILLER };

    // 物品网格数据
    private final List<WarehouseItem> items = new ArrayList<>();
    private WarehouseItem hoveredItem = null;
    private WarehouseItem selectedItem = null;
    private int selectedIndex = -1;  // 用索引追踪选中项，避免相同ID全部高亮
    private long lastClickTime = 0;
    private String lastClickItemId = null;
    /** 右下角"销毁"按钮（选中物品时出现） */
    private Button destroyButton = null;
    /** 右下角"装备/卸下称号"按钮（选中称号时出现） */
    private Button equipTitleButton = null;
    /**
     * 本地未确认的收藏覆盖：key 为 {@code type/id}，value 为本地期望的收藏状态。
     * <p>按 F 收藏时服务端要一个往返才同步回来，这期间用它让界面先动起来；
     * 一旦服务端数据与本地期望一致，条目就被清掉，服务端始终是权威。
     */
    private final Map<String, Boolean> pendingFavorites = new ConcurrentHashMap<>();

    // 布局
    private int sidebarWidth;
    private int gridStartX;
    private int gridStartY;
    private int cardSize = 52;
    private int cardGap = 6;
    private int cols;
    private int scrollOffset = 0;
    /** 是否正在拖拽右侧滚动条滑块 */
    private boolean draggingScrollbar = false;

    public CS2WarehouseScreen() {
        super(Component.literal("CS2 仓库"));
    }

    @Override
    protected void init() {
        super.init();
        isBoxOpening = false; // 安全重置：仓库界面重新打开时始终解锁
        sidebarWidth = width / 6;
        gridStartX = sidebarWidth + 16;
        gridStartY = 40;
        cols = (width - gridStartX - 16) / (cardSize + cardGap);
        if (cols < 1) cols = 1;

        // 左上角：皮肤预览入口
        addRenderableWidget(Button.builder(Component.literal("皮肤预览"), b -> {
            minecraft.setScreen(new SkinPreviewScreen());
        }).pos(4, 8).size(sidebarWidth - 8, 20).build());

        // 底部按钮
        int btnY = height - 30;
        addRenderableWidget(Button.builder(Component.literal("商店"), b -> {
            minecraft.setScreen(new CS2ShopScreen());
        }).pos(width / 2 - 100, btnY).size(60, 20).build());

        addRenderableWidget(Button.builder(Component.literal("开箱"), b -> {
            if (isBoxOpening) {
                var p = Minecraft.getInstance().player;
                if (p != null) p.displayClientMessage(Component.literal("§c正在开箱，请稍候..."), true);
                return;
            }
            if (selectedItem != null && "box".equals(selectedItem.type)) {
                var p = Minecraft.getInstance().player;
                if (p != null) {
                    CS2InventoryComponent inv = CS2InventoryComponent.KEY.get(p);
                    if (inv.getBoxCount(selectedItem.id) <= 0) {
                        p.displayClientMessage(Component.literal("§c你没有该箱子"), true);
                        return;
                    }
                }
                isBoxOpening = true;
                ClientPlayNetworking.send(new OpenBoxC2SPayload(selectedItem.id, 1));
            }
        }).pos(width / 2 - 30, btnY).size(60, 20).build());

        addRenderableWidget(Button.builder(Component.literal("关闭"), b -> {
            minecraft.setScreen(null);
        }).pos(width / 2 + 40, btnY).size(60, 20).build());

        // 右下角：销毁选中物品（用于清理幽灵物品），选中物品后才出现
        destroyButton = Button.builder(Component.literal("销毁"), b -> askDestroy(selectedItem))
                .pos(width - 66, height - 26).size(60, 20).build();
        destroyButton.setTooltip(Tooltip.create(Component.literal(
                "永久删除选中的仓库物品\n用于清理抽不出来、也用不掉的幽灵物品")));
        addRenderableWidget(destroyButton);

        // 右下角：装备/卸下称号（选中称号时才出现）
        equipTitleButton = Button.builder(Component.literal("装备"), b -> toggleSelectedTitle())
                .pos(width - 132, height - 26).size(60, 20).build();
        equipTitleButton.setTooltip(Tooltip.create(Component.literal(
                "装备 / 卸下选中的称号\n装备后会显示在你的名字旁边")));
        addRenderableWidget(equipTitleButton);

        refreshItems();
    }

    /** 装备 / 卸下当前选中的称号 */
    private void toggleSelectedTitle() {
        var p = Minecraft.getInstance().player;
        if (p == null || selectedItem == null || !"title".equals(selectedItem.type)) {
            return;
        }
        var tc = io.wifi.starrailexpress.content.title.TitlePlayerComponent.KEY.get(p);
        ClientPlayNetworking.send(
                new io.wifi.starrailexpress.content.title.network.EquipTitleC2SPayload(
                        tc.isEquipped(selectedItem.id) ? "" : selectedItem.id));
    }

    /** 称号装备按钮：只在选中称号时出现 */
    private void updateEquipTitleButton() {
        if (equipTitleButton == null) {
            return;
        }
        boolean isTitle = selectedItem != null && "title".equals(selectedItem.type);
        equipTitleButton.visible = isTitle;
        equipTitleButton.active = isTitle;
        if (isTitle) {
            var p = Minecraft.getInstance().player;
            boolean equipped = false;
            if (p != null) {
                equipped = io.wifi.starrailexpress.content.title.TitlePlayerComponent.KEY.get(p)
                        .isEquipped(selectedItem.id);
            }
            equipTitleButton.setMessage(Component.literal(equipped ? "卸下" : "装备"));
        }
    }

    /** 右下角销毁按钮；未选中物品时隐藏，选中已收藏物品时禁用并说明原因 */
    private void updateDestroyButton() {
        if (destroyButton == null) {
            return;
        }
        boolean hasSelection = selectedItem != null;
        boolean locked = hasSelection && selectedItem.favorite;
        destroyButton.visible = hasSelection;
        destroyButton.active = hasSelection && !locked;
        destroyButton.setMessage(locked
                ? Component.literal("已收藏")
                : Component.literal("销毁"));
        destroyButton.setTooltip(Tooltip.create(locked
                ? Component.literal("该物品已收藏，无法销毁\n先在物品上按 F 取消收藏")
                : Component.literal("永久删除选中的仓库物品\n用于清理抽不出来、也用不掉的幽灵物品")));
    }

    /**
     * 弹出确认框，确认后请求服务端销毁。
     *
     * <p>走服务端而不是本地删：仓库数据是同步组件，客户端直接改会被服务端覆盖，
     * 而且"能销毁什么东西"必须由服务端说了算。
     */
    private void askDestroy(WarehouseItem item) {
        if (item == null || minecraft == null) {
            return;
        }
        String name = item.displayName == null || item.displayName.isEmpty()
                ? item.id : item.displayName;
        minecraft.setScreen(new ConfirmScreen(
                confirmed -> {
                    minecraft.setScreen(this);
                    if (confirmed) {
                        ClientPlayNetworking.send(new DestroyWarehouseItemC2SPayload(
                                item.type == null ? "" : item.type, item.id));
                        // 物品没了，本地那条未确认的收藏覆盖也就没意义了，顺手清掉
                        pendingFavorites.remove(CS2InventoryComponent.favoriteKey(item.type, item.id));
                        // 服务端会同步回最新仓库；本地先把选中清掉，避免按钮指向已删物品
                        selectedItem = null;
                        selectedIndex = -1;
                        updateDestroyButton();
        updateEquipTitleButton();
                    }
                },
                Component.literal("销毁物品"),
                Component.literal("确定要永久销毁「" + name + "」吗？\n此操作不可撤销，物品不会返还。"),
                Component.literal("销毁"),
                Component.literal("取消")));
    }

    private void refreshItems() {
        items.clear();
        selectedItem = null;
        selectedIndex = -1;
        updateDestroyButton();
        updateEquipTitleButton();
        var player = Minecraft.getInstance().player;
        if (player == null) return;

        CS2InventoryComponent inv = CS2InventoryComponent.KEY.get(player);
        final Set<String> favs = inv.getFavorites();
        // 收藏键 = type/id；皮肤本身就是 itemType/skinName，直接用 id
        java.util.function.BiFunction<String, String, Boolean> isFav = (type, id) -> {
            String key = CS2InventoryComponent.favoriteKey(type, id);
            boolean server = favs.contains(key);
            Boolean local = pendingFavorites.get(key);
            if (local == null) {
                return server;
            }
            if (local == server) {
                // 服务端已经跟上本地那次操作，覆盖可以撤掉了
                pendingFavorites.remove(key);
                return server;
            }
            return local;
        };

        // 箱子 — 同类堆叠，右下角显示 xN
        if (selectedCategory == Category.ALL || selectedCategory == Category.BOXES) {
            for (Map.Entry<String, Integer> entry : inv.getBoxes().entrySet()) {
                String boxId = entry.getKey();
                // 从客户端缓存获取中文名称（服务端登录时同步）
                String cachedName = org.agmas.noellesroles.client.data.CS2ClientBoxCache.getBoxName(boxId);
                String name = !cachedName.isEmpty() ? cachedName : formatBoxId(boxId);
                items.add(new WarehouseItem("box", boxId, name, "", entry.getValue(), 0, isFav.apply("box", boxId)));
            }
        }

        // 钥匙 — 同类堆叠，右下角显示 xN
        if (selectedCategory == Category.ALL || selectedCategory == Category.BOXES) {
            for (Map.Entry<String, Integer> entry : inv.getKeys().entrySet()) {
                items.add(new WarehouseItem("key", entry.getKey(),
                        entry.getKey().replace('_', ' '), "", entry.getValue(), 0,
                        isFav.apply("key", entry.getKey())));
            }
        }

        // 皮肤 — 按刀/左轮手枪/棒球棍/帽子分类显示
        if (isSkinCategory(selectedCategory)) {
            for (Map.Entry<String, Integer> entry : inv.getSkins().entrySet()) {
                String skinId = entry.getKey(); // 格式: "itemType/skinName"
                int count = entry.getValue();
                if (count <= 0) continue;

                String[] parts = skinId.split("/");
                if (parts.length < 2) continue;
                String itemType = parts[0];
                String skinName = parts[1];
                if (!skinCategoryMatches(selectedCategory, itemType)) continue;

                int quality = getSkinQuality(itemType, skinName);
                // 相同皮肤合并为一格，右下角显示 xN
                items.add(new WarehouseItem("skin", skinId,
                        CS2SkinInfo.getName(skinId),
                        CS2SkinInfo.getDescription(skinId),
                        count, quality, isFav.apply("skin", skinId)));
            }
        }

        // 称号 — 来自 TitlePlayerComponent（拥有的称号），定义在服务端存档里
        if (selectedCategory == Category.ALL || selectedCategory == Category.TITLE) {
            var titleComp = io.wifi.starrailexpress.content.title.TitlePlayerComponent.KEY.get(player);
            for (String titleId : titleComp.getOwned()) {
                var title = io.wifi.starrailexpress.content.title.TitleClientCache.get(titleId);
                if (title == null) {
                    // 定义还没同步到客户端：先用 id 占位，避免"拥有却看不到"
                    items.add(new WarehouseItem("title", titleId, titleId, "", 1, 0, false));
                    continue;
                }
                boolean equipped = titleComp.isEquipped(titleId);
                items.add(new WarehouseItem("title", titleId,
                        title.displayText(), "", 1, 0, isFav.apply("title", titleId), equipped));
            }
        }

        // 音乐盒 — 从 CS2InventoryComponent 仓库读取
        if (selectedCategory == Category.ALL || selectedCategory == Category.MUSIC) {
            for (Map.Entry<String, Integer> entry : inv.getMusicBoxes().entrySet()) {
                String boxId = entry.getKey();
                int count = entry.getValue();
                if (count <= 0) continue;

                MusicBox box = MusicBoxRegistry.get(boxId);
                String displayName = box != null
                        ? box.displayName().getString()
                        : boxId.replace('_', ' ');
                for (int i = 0; i < count; i++) {
                    items.add(new WarehouseItem("music", boxId, displayName, "", 1, 0, isFav.apply("music", boxId)));
                }
            }
        }

        // 职业卡 — 从场外背包读取
        if (selectedCategory == Category.ALL || selectedCategory == Category.CARDS) {
            var backpack = ClientPlayerDataCache.backpack(player.getUUID());
            for (FactionCardType type : CARD_DISPLAY_ORDER) {
                int count = backpack.cards.getOrDefault(type, 0);
                if (count <= 0) continue;
                items.add(new WarehouseItem("card", type.questKey, cardName(type), "", count, 0, isFav.apply("card", type.questKey)));
            }
            // 自选职业卡
            if (backpack.selfSelectCards > 0) {
                items.add(new WarehouseItem("selfselect", "selfselect", "自选职业卡", "", backpack.selfSelectCards, 0, isFav.apply("selfselect", "selfselect")));
            }
        }

        // 收藏的排最前，其次按品质降序（高品质靠前）
        items.sort((a, b) -> {
            if (a.favorite != b.favorite) {
                return a.favorite ? -1 : 1;
            }
            return Integer.compare(b.quality, a.quality);
        });
    }

    private static String cardName(FactionCardType type) {
        return switch (type) {
            case KILLER -> "杀手职业卡";
            case CIVILIAN -> "平民职业卡";
            case NEUTRAL -> "中立职业卡";
            case NEUTRAL_FOR_KILLER -> "杀手中立职业卡";
            default -> type.questKey;
        };
    }

    /** 是否为皮肤相关的分类（全部或各皮肤子类） */
    private static boolean isSkinCategory(Category c) {
        return c == Category.ALL || c == Category.KNIFE || c == Category.REVOLVER
                || c == Category.BAT || c == Category.GRENADE || c == Category.HAT;
    }

    /** 皮肤类型是否属于指定分类 */
    private static boolean skinCategoryMatches(Category c, String itemType) {
        if (c == Category.ALL) return true;
        return switch (c) {
            case KNIFE -> "knife".equals(itemType);
            case REVOLVER -> "revolver".equals(itemType) || "gun".equals(itemType);
            case BAT -> "bat".equals(itemType);
            case GRENADE -> "grenade".equals(itemType);
            case HAT -> "hat".equals(itemType);
            default -> false;
        };
    }

    private void sendCommand(String command) {
        if (minecraft == null || minecraft.player == null || minecraft.player.connection == null) {
            return;
        }
        minecraft.player.connection.sendCommand(command.startsWith("/") ? command.substring(1) : command);
    }

    private static String formatBoxId(String boxId) {
        if (boxId == null) return "";
        return boxId.replace('_', ' ');
    }

    private int getSkinQuality(String itemType, String skinName) {
        ItemSkinManager.Skin skin = ItemSkinManager.getSkinFromName(itemType, skinName);
        if (skin == null) return 0;
        int color = skin.getColor();
        for (int i = 0; i < QUALITY_COLORS.length; i++) {
            if (QUALITY_COLORS[i] == (color | 0xFF000000)) return i;
        }
        return 0;
    }


    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        guiGraphics.fill(0, 0, width, height, BG_COLOR);
        renderSidebar(guiGraphics, mouseX, mouseY);
        renderGrid(guiGraphics, mouseX, mouseY, delta);
        renderScrollbar(guiGraphics, mouseX, mouseY);
        renderHeader(guiGraphics);
        renderTooltip(guiGraphics, mouseX, mouseY);
        super.render(guiGraphics, mouseX, mouseY, delta);
    }

    private void renderHeader(GuiGraphics guiGraphics) {
        guiGraphics.drawCenteredString(font, "CS2 仓库", width / 2, 8, TEXT_COLOR);
        var player = Minecraft.getInstance().player;
        if (player != null) {
            int coins = PlayerEconomyManager.getCoinNum(player);
            guiGraphics.drawString(font, "货币: " + coins, width - 120, 8, 0xFFFFD700, false);
            io.wifi.starrailexpress.cca.CS2InventoryComponent inv =
                    io.wifi.starrailexpress.cca.CS2InventoryComponent.KEY.get(player);
            guiGraphics.drawString(font, "神话碎片: " + inv.getMythicShards(),
                    width - 120, 20, 0xFFCC66FF, false);
            guiGraphics.drawString(font,
                    "保底进度: " + inv.getBoxPityCounter() + "/"
                            + io.wifi.starrailexpress.cca.CS2InventoryComponent.PITY_THRESHOLD,
                    width - 120, 32, 0xFFAA88FF, false);
        }
    }

    // ── 侧栏（分类列表）滚动 ──

    /** 侧栏一屏能显示多少行分类 */
    private int sidebarVisibleRows() {
        return Math.max(1, (height - SIDEBAR_TOP - 8) / SIDEBAR_ROW_H);
    }

    private int sidebarMaxScroll() {
        return Math.max(0, CATEGORY_ORDER.length - sidebarVisibleRows());
    }

    private void scrollSidebar(int deltaRows) {
        sidebarScroll = Math.max(0, Math.min(sidebarMaxScroll(), sidebarScroll + deltaRows));
    }

    /** 侧栏滚动条轨道区域（只有分类多到放不下时才画） */
    private int sidebarBarX() {
        return Math.max(0, sidebarWidth - 6);
    }

    private int sidebarBarTop() {
        return SIDEBAR_TOP;
    }

    private int sidebarBarHeight() {
        return Math.max(20, (height - SIDEBAR_TOP - 8));
    }

    private int sidebarThumbHeight() {
        int track = sidebarBarHeight();
        int total = Math.max(1, CATEGORY_ORDER.length);
        return Math.max(16, (int) ((long) track * sidebarVisibleRows() / total));
    }

    private int sidebarThumbY() {
        int max = sidebarMaxScroll();
        if (max <= 0) {
            return sidebarBarTop();
        }
        int travel = sidebarBarHeight() - sidebarThumbHeight();
        return sidebarBarTop() + (int) ((long) travel * sidebarScroll / max);
    }

    private void dragSidebarTo(double mouseY) {
        int travel = sidebarBarHeight() - sidebarThumbHeight();
        if (travel <= 0) {
            sidebarScroll = 0;
            return;
        }
        double ratio = (mouseY - sidebarBarTop()) / (double) travel;
        ratio = Math.max(0.0, Math.min(1.0, ratio));
        sidebarScroll = (int) Math.round(ratio * sidebarMaxScroll());
    }

    private boolean overSidebarBar(double mouseX, double mouseY) {
        return sidebarMaxScroll() > 0
                && mouseX >= sidebarBarX() && mouseX < sidebarBarX() + 6
                && mouseY >= sidebarBarTop() && mouseY < sidebarBarTop() + sidebarBarHeight();
    }

    /** 某个分类行的 y 坐标（含滚动偏移）；不在可见范围内返回 -1 */
    private int sidebarRowY(int index) {
        int row = index - sidebarScroll;
        if (row < 0) {
            return -1;
        }
        int y = SIDEBAR_TOP + row * SIDEBAR_ROW_H;
        if (y + SIDEBAR_ROW_VISIBLE > height - 4) {
            return -1;
        }
        return y;
    }

    private void renderSidebar(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.fill(0, 0, sidebarWidth, height, SIDEBAR_COLOR);
        for (int i = 0; i < CATEGORY_ORDER.length; i++) {
            int y = sidebarRowY(i);
            if (y < 0) {
                continue;
            }
            boolean selected = CATEGORY_ORDER[i] == selectedCategory;
            boolean hovered = mouseX < sidebarBarX() && mouseY >= y && mouseY < y + SIDEBAR_ROW_VISIBLE;
            int bgColor = selected ? ACCENT : (hovered ? 0x30FFFFFF : 0x10FFFFFF);
            guiGraphics.fill(4, y, sidebarWidth - 4, y + SIDEBAR_ROW_VISIBLE, bgColor);
            // 标签过长时截断，避免压到滚动条上
            String label = CATEGORY_LABELS[i];
            int maxW = sidebarWidth - 12 - 8;
            if (font.width(label) > maxW) {
                while (label.length() > 1 && font.width(label + "..") > maxW) {
                    label = label.substring(0, label.length() - 1);
                }
                label = label + "..";
            }
            guiGraphics.drawString(font, label, 12, y + 9,
                    selected ? 0xFFFFFFFF : TEXT_DIM, false);
        }
        renderSidebarBar(guiGraphics, mouseX, mouseY);
    }

    private void renderSidebarBar(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (sidebarMaxScroll() <= 0) {
            return; // 分类放得下就不显示
        }
        int x = sidebarBarX();
        int top = sidebarBarTop();
        int h = sidebarBarHeight();
        guiGraphics.fill(x, top, x + 6, top + h, 0x30FFFFFF);
        boolean active = draggingSidebar || overSidebarBar(mouseX, mouseY);
        int thumb = sidebarThumbY();
        guiGraphics.fill(x, thumb, x + 6, thumb + sidebarThumbHeight(),
                active ? 0xCCFFFFFF : 0x88FFFFFF);
    }

    private void renderGrid(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        hoveredItem = null;
        for (int i = 0; i < items.size(); i++) {
            int idx = i - scrollOffset;
            if (idx < 0) continue;

            int row = idx / cols;
            int col = idx % cols;
            int x = gridStartX + col * (cardSize + cardGap);
            int y = gridStartY + row * (cardSize + cardGap);

            if (y + cardSize > height - 40) continue;

            WarehouseItem item = items.get(i);
            boolean hovered = mouseX >= x && mouseX < x + cardSize && mouseY >= y && mouseY < y + cardSize;
            boolean isSelected = selectedIndex == i;

            if (hovered) hoveredItem = item;

            // 卡片背景
            int bg = hovered ? CARD_HOVER_COLOR : CARD_BG_COLOR;
            guiGraphics.fill(x, y, x + cardSize, y + cardSize, bg);

            // 品质边框
            if (item.quality > 0) {
                int borderColor = QUALITY_COLORS[item.quality];
                guiGraphics.fill(x - 1, y - 1, x + cardSize + 1, y, borderColor);
                guiGraphics.fill(x - 1, y + cardSize, x + cardSize + 1, y + cardSize + 1, borderColor);
                guiGraphics.fill(x - 1, y, x, y + cardSize, borderColor);
                guiGraphics.fill(x + cardSize, y, x + cardSize + 1, y + cardSize, borderColor);
            }

            // 选中高亮
            if (isSelected) {
                guiGraphics.fill(x - 2, y - 2, x + cardSize + 2, y + cardSize + 2, 0x80FFFFFF);
            }

            renderItemIcon(guiGraphics, item, x, y);

            // 收藏标记（左上角 ★）
            if (item.favorite) {
                guiGraphics.drawString(font, "★", x + 3, y + 3, 0xFFFFD24A, true);
            }
            // 已装备标记（右上角）
            if (item.equipped) {
                guiGraphics.drawString(font, "E", x + cardSize - 9, y + 3, 0xFF7CFF7C, true);
            }

            // 数量
            if (item.count > 1) {
                guiGraphics.drawString(font, "x" + item.count,
                        x + cardSize - font.width("x" + item.count) - 2,
                        y + cardSize - 10, 0xFFCCCCCC, false);
            }

            // 名称（底部截断）
            String name = item.displayName;
            int nameWidth = font.width(name);
            if (nameWidth > cardSize - 4) {
                int maxChars = (cardSize - 8) / 6;
                if (maxChars > 3 && name.length() > maxChars) {
                    name = name.substring(0, maxChars - 2) + "..";
                }
            }
            guiGraphics.drawCenteredString(font, name, x + cardSize / 2, y + cardSize - 22, TEXT_DIM);
        }
    }

    private void renderItemIcon(GuiGraphics guiGraphics, WarehouseItem item, int x, int y) {
        int iconX = x + (cardSize - 16) / 2;
        int iconY = y + 6;

        switch (item.type) {
            case "box" -> {
                // 使用箱子物品渲染
                guiGraphics.renderFakeItem(new ItemStack(Items.CHEST), iconX, iconY);
            }
            case "key" -> {
                guiGraphics.renderFakeItem(new ItemStack(Items.TRIPWIRE_HOOK), iconX, iconY);
            }
            case "skin" -> {
                ItemStack skinStack = getSkinItemStack(item.id);
                if (skinStack != null) {
                    String[] parts = item.id.split("/");
                    if (parts.length >= 2) {
                        skinStack.set(SREDataComponentTypes.SKIN, parts[1]);
                    }
                    guiGraphics.renderFakeItem(skinStack, iconX, iconY);
                } else {
                    // 无法获取物品时显示文字缩写
                    String abbr = getAbbreviation(item.displayName);
                    int tw = font.width(abbr);
                    guiGraphics.drawString(font, abbr, iconX + (16 - tw) / 2,
                            iconY + 4, 0xFFCCCCCC, false);
                }
            }
            case "music" -> {
                guiGraphics.renderFakeItem(new ItemStack(Items.MUSIC_DISC_13), iconX, iconY);
            }
            case "card" -> {
                guiGraphics.renderFakeItem(new ItemStack(Items.PAPER), iconX, iconY);
            }
            case "selfselect" -> {
                guiGraphics.renderFakeItem(new ItemStack(Items.NAME_TAG), iconX, iconY);
            }
            case "title" -> {
                // 称号：直接渲染成带颜色的文字，比图标更直观
                var title = io.wifi.starrailexpress.content.title.TitleClientCache.get(item.id);
                if (title != null) {
                    var comp = title.component();
                    int tw = font.width(comp);
                    int maxW = cardSize - 6;
                    if (tw <= maxW) {
                        guiGraphics.drawCenteredString(font, comp, x + cardSize / 2, y + 14, 0xFFFFFFFF);
                    } else {
                        // 太长就截断显示，避免压出卡片
                        String t = title.displayText();
                        while (t.length() > 1 && font.width(t + "..") > maxW) {
                            t = t.substring(0, t.length() - 1);
                        }
                        guiGraphics.drawCenteredString(font, t + "..",
                                x + cardSize / 2, y + 14, title.rgb() | 0xFF000000);
                    }
                } else {
                    guiGraphics.renderFakeItem(new ItemStack(Items.NAME_TAG), iconX, iconY);
                }
            }
        }
    }

    /**
     * 根据 skinId 获取对应的基础物品 ItemStack
     * skinId 格式: "itemType/skinName"
     */
    private ItemStack getSkinItemStack(String skinId) {
        if (skinId == null) return null;
        if (skinId.startsWith("knife/")) return TMMItems.KNIFE.getDefaultInstance();
        if (skinId.startsWith("gun/")) return TMMItems.REVOLVER.getDefaultInstance();
        if (skinId.startsWith("revolver/")) return TMMItems.REVOLVER.getDefaultInstance();
        if (skinId.startsWith("bat/")) return TMMItems.BAT.getDefaultInstance();
        if (skinId.startsWith("grenade/")) return TMMItems.GRENADE.getDefaultInstance();
        if (skinId.startsWith("hat/")) {
            // 帽子皮肤优先使用 CS2SkinInfo 提供的专属图标（如玩偶物品），否则回退为皮革头盔
            ItemStack icon = CS2SkinInfo.getIconStack(skinId);
            return icon != null ? icon : new ItemStack(Items.LEATHER_HELMET);
        }
        return null;
    }

    /**
     * 获取名称缩写（取前两个词的首字母）
     */
    private String getAbbreviation(String name) {
        if (name == null || name.isEmpty()) return "?";
        String[] words = name.trim().split("\\s+");
        if (words.length >= 2) {
            return ("" + words[0].charAt(0) + words[1].charAt(0)).toUpperCase();
        }
        return name.substring(0, Math.min(2, name.length())).toUpperCase();
    }

    private void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (hoveredItem == null) return;

        List<Component> tooltip = new ArrayList<>();

        // 中文名称
        tooltip.add(Component.literal(hoveredItem.displayName));

        // 类型 + 品质
        String typeLabel = getTypeLabel(hoveredItem.type);
        if ("skin".equals(hoveredItem.type) && hoveredItem.id.contains("/")) {
            String itemType = hoveredItem.id.split("/")[0];
            typeLabel = CS2SkinInfo.getItemTypeName(itemType);
        }
        tooltip.add(Component.literal("类型: " + typeLabel).withStyle(
                net.minecraft.ChatFormatting.GRAY));

        if (hoveredItem.quality > 0) {
            String[] qualityNames = {"普通", "罕见", "稀有", "史诗", "传说", "神话"};
            int qIdx = Math.min(hoveredItem.quality, qualityNames.length - 1);
            tooltip.add(Component.literal("品质: " + qualityNames[qIdx]));
        }

        // 皮肤介绍
        if (!hoveredItem.description.isEmpty()) {
            tooltip.add(Component.literal(hoveredItem.description).withStyle(
                    net.minecraft.ChatFormatting.DARK_GRAY));
        }

        if ("box".equals(hoveredItem.type)) {
            // 显示所需钥匙（从客户端缓存读取）
            String keyName = org.agmas.noellesroles.client.data.CS2ClientBoxCache.getKeyName(hoveredItem.id);
            if (keyName != null && !keyName.isEmpty()) {
                String keyDisplayName = keyName.replace('_', ' ');
                tooltip.add(Component.literal("需要钥匙: " + keyDisplayName).withStyle(
                        net.minecraft.ChatFormatting.AQUA));
            }
            tooltip.add(Component.literal("双击查看奖池 | 选中后点击\"开箱\"").withStyle(
                    net.minecraft.ChatFormatting.YELLOW));
        }

        if ("skin".equals(hoveredItem.type)) {
            tooltip.add(Component.literal("右键装备/卸下皮肤").withStyle(
                    net.minecraft.ChatFormatting.YELLOW));
            // 显示装备状态（从同步的 SREPlayerSkinsComponent 查询）
            var p = Minecraft.getInstance().player;
            if (p != null) {
                SREPlayerSkinsComponent sc = SREPlayerSkinsComponent.KEY.get(p);
                String[] parts = hoveredItem.id.split("/");
                if (parts.length >= 2) {
                    String itemType = parts[0];
                    String skinName = parts[1];
                    String equipped = sc.getEquippedSkin(itemType);
                    if (skinName.equals(equipped)) {
                        tooltip.add(Component.literal("[已装备]").withStyle(
                                net.minecraft.ChatFormatting.GREEN));
                    }
                }
            }
        }

        if ("music".equals(hoveredItem.type)) {
            tooltip.add(Component.literal("右键装备/卸下音乐盒").withStyle(
                    net.minecraft.ChatFormatting.YELLOW));
            var p = Minecraft.getInstance().player;
            if (p != null) {
                MusicBoxPlayerComponent mc = MusicBoxPlayerComponent.KEY.get(p);
                if (hoveredItem.id.equals(mc.getEquippedBox())) {
                    tooltip.add(Component.literal("[已装备]").withStyle(
                            net.minecraft.ChatFormatting.GREEN));
                }
            }
        }

        if ("title".equals(hoveredItem.type)) {
            var title = io.wifi.starrailexpress.content.title.TitleClientCache.get(hoveredItem.id);
            if (title != null) {
                tooltip.add(Component.literal("颜色 #" + title.colorHex()
                        + "　位置 " + (title.suffix() ? "名字后" : "名字前"))
                        .withStyle(net.minecraft.ChatFormatting.GRAY));
            }
            tooltip.add(Component.literal("右键装备/卸下称号").withStyle(
                    net.minecraft.ChatFormatting.YELLOW));
            tooltip.add(Component.literal("同时只能装备一个称号").withStyle(
                    net.minecraft.ChatFormatting.DARK_GRAY));
            var p = Minecraft.getInstance().player;
            if (p != null) {
                var tc = io.wifi.starrailexpress.content.title.TitlePlayerComponent.KEY.get(p);
                if (tc.isEquipped(hoveredItem.id)) {
                    tooltip.add(Component.literal("[已装备]").withStyle(
                            net.minecraft.ChatFormatting.GREEN));
                }
            }
        }

        guiGraphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
    }

    private String getTypeLabel(String type) {
        return switch (type) {
            case "box" -> "箱子";
            case "key" -> "钥匙";
            case "skin" -> "皮肤";
            case "music" -> "音乐盒";
            case "card" -> "职业卡";
            case "title" -> "称号";
            default -> type;
        };
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 滚动条优先：点到轨道就跳过去，点住滑块开始拖拽
        if (button == 0 && overScrollbar(mouseX, mouseY)) {
            draggingScrollbar = true;
            dragScrollbarTo(mouseY);
            return true;
        }
        // 侧栏滚动条优先（它压在分类行右边）
        if (button == 0 && overSidebarBar(mouseX, mouseY)) {
            draggingSidebar = true;
            dragSidebarTo(mouseY);
            return true;
        }
        // 侧边栏分类点击
        if (mouseX < sidebarBarX()) {
            for (int i = 0; i < CATEGORY_ORDER.length; i++) {
                int y = sidebarRowY(i);
                if (y >= 0 && mouseY >= y && mouseY < y + SIDEBAR_ROW_VISIBLE) {
                    selectedCategory = CATEGORY_ORDER[i];
                    scrollOffset = 0;
                    refreshItems();
                    return true;
                }
            }
        }

        // 物品网格点击
        if (hoveredItem != null) {
            long now = System.currentTimeMillis();
            boolean isDoubleClick = hoveredItem.id.equals(lastClickItemId)
                    && (now - lastClickTime) < 400;
            lastClickTime = now;
            lastClickItemId = hoveredItem.id;

            if (button == 0) { // 左键
                if ("selfselect".equals(hoveredItem.type)) {
                    // 打开自选职业卡 GUI（先选阵营，再选具体职业）
                    minecraft.setScreen(new SelfSelectCardScreen());
                    return true;
                }
                if ("card".equals(hoveredItem.type)) {
                    // 左键弹出确认框，确认后才激活职业卡（沿用 sre:pass activate 路径）
                    String cardId = hoveredItem.id;
                    String cardDisplayName = hoveredItem.displayName;
                    minecraft.setScreen(new ConfirmScreen(
                            confirmed -> {
                                if (confirmed) {
                                    sendCommand("sre:pass activate " + cardId);
                                }
                                minecraft.setScreen(this);
                            },
                            Component.literal("确认使用职业卡"),
                            Component.literal("确认使用「" + cardDisplayName + "」吗？"),
                            Component.literal("使用"),
                            Component.literal("取消")));
                    return true;
                }
                if (isDoubleClick && "box".equals(hoveredItem.type)) {
                    // 双击箱子 → 向服务端请求奖池数据，收到后打开预览UI
                    pendingPreviewBoxId = hoveredItem.id;
                    ClientPlayNetworking.send(
                            new org.agmas.noellesroles.cs2.network.BoxPreviewRequestC2SPayload(hoveredItem.id));
                    return true;
                }
                selectedItem = hoveredItem;
                selectedIndex = items.indexOf(hoveredItem);
                updateDestroyButton();
        updateEquipTitleButton();
            } else if (button == 1) { // 右键装备/卸下 → 发送 C2S 网络包
                if ("skin".equals(hoveredItem.type)) {
                    String[] parts = hoveredItem.id.split("/");
                    if (parts.length >= 2) {
                        ClientPlayNetworking.send(new EquipSkinC2SPayload(parts[0], parts[1]));
                    }
                } else if ("music".equals(hoveredItem.type)) {
                    // 切换逻辑：如果已装备则卸下，否则装备
                    var p = Minecraft.getInstance().player;
                    if (p != null) {
                        MusicBoxPlayerComponent mc = MusicBoxPlayerComponent.KEY.get(p);
                        if (hoveredItem.id.equals(mc.getEquippedBox())) {
                            ClientPlayNetworking.send(new EquipMusicBoxC2SPayload(""));
                        } else {
                            ClientPlayNetworking.send(new EquipMusicBoxC2SPayload(hoveredItem.id));
                        }
                    }
                } else if ("title".equals(hoveredItem.type)) {
                    // 称号：已装备则卸下（发空串），否则装备
                    var p = Minecraft.getInstance().player;
                    if (p != null) {
                        var tc = io.wifi.starrailexpress.content.title.TitlePlayerComponent.KEY.get(p);
                        ClientPlayNetworking.send(
                                new io.wifi.starrailexpress.content.title.network.EquipTitleC2SPayload(
                                        tc.isEquipped(hoveredItem.id) ? "" : hoveredItem.id));
                    }
                }
            }
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        // 鼠标在侧栏上就滚分类列表，否则滚物品网格
        if (mouseX < sidebarWidth && sidebarMaxScroll() > 0) {
            scrollSidebar(-(int) deltaY);
            return true;
        }
        scrollRows(-(int) deltaY * 2);
        return true;
    }

    // =========================================================================
    // 收藏
    // =========================================================================

    /**
     * 切换选中物品的收藏状态。
     *
     * <p>发到服务端改（仓库数据是同步组件），服务端改完会同步回来。
     * 同时写一条 {@link #pendingFavorites} 本地覆盖并立刻重排，
     * 免得等服务端往返时界面看着没反应。
     */
    private void toggleFavorite(WarehouseItem item) {
        if (item == null) {
            return;
        }
        ClientPlayNetworking.send(new ToggleFavoriteC2SPayload(item.type, item.id));
        // 本地先翻转：服务端同步回来之前界面先动起来。
        // 服务端仍会以权威数据覆盖（并强制"收藏中不可销毁"）。
        String key = CS2InventoryComponent.favoriteKey(item.type, item.id);
        pendingFavorites.put(key, !item.favorite);

        String selType = item.type;
        String selId = item.id;
        refreshItems();
        reselect(selType, selId);
    }

    /** 重排后按 type/id 把选中项找回来（收藏会让顺序变化） */
    private void reselect(String type, String id) {
        for (int i = 0; i < items.size(); i++) {
            WarehouseItem it = items.get(i);
            if (it.type.equals(type) && it.id.equals(id)) {
                selectedItem = it;
                selectedIndex = i;
                break;
            }
        }
        updateDestroyButton();
        updateEquipTitleButton();
    }

    /** F 键收藏/取消收藏选中项（右键已被"装备"占用，避免语义冲突） */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_F && selectedItem != null) {
            toggleFavorite(selectedItem);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingSidebar && button == 0) {
            dragSidebarTo(mouseY);
            return true;
        }
        if (draggingScrollbar && button == 0) {
            dragScrollbarTo(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && (draggingScrollbar || draggingSidebar)) {
            draggingScrollbar = false;
            draggingSidebar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    // =========================================================================
    // 滚动条（右侧，样式同网页滚动条）
    // =========================================================================

    private static final int SCROLLBAR_W = 6;
    private static final int SCROLLBAR_MARGIN = 4;
    /** 一屏可见的行数（与 renderGrid 的裁剪条件一致：y + cardSize 不得越过 height-40） */
    private int visibleRows() {
        int usable = (height - 40) - gridStartY;
        return Math.max(1, usable / (cardSize + cardGap));
    }

    /** 内容总行数 */
    private int totalRows() {
        return (items.size() + cols - 1) / Math.max(1, cols);
    }

    /** 最大可滚动行数 */
    private int maxScrollRows() {
        return Math.max(0, totalRows() - visibleRows());
    }

    private int scrollbarX() {
        return width - SCROLLBAR_MARGIN - SCROLLBAR_W;
    }

    private int scrollbarTop() {
        return gridStartY;
    }

    private int scrollbarHeight() {
        return Math.max(20, (height - 40) - gridStartY);
    }

    /** 滑块高度按"可见比例"算，最少 16px（拖得动） */
    private int thumbHeight() {
        int track = scrollbarHeight();
        int total = Math.max(1, totalRows());
        return Math.max(16, (int) ((long) track * visibleRows() / total));
    }

    private int thumbY() {
        int maxRows = maxScrollRows();
        if (maxRows <= 0) {
            return scrollbarTop();
        }
        int travel = scrollbarHeight() - thumbHeight();
        return scrollbarTop() + (int) ((long) travel * scrollOffset / maxRows);
    }

    /** 按行滚动，并夹到合法范围 */
    private void scrollRows(int deltaRows) {
        scrollOffset = Math.max(0, Math.min(maxScrollRows(), scrollOffset + deltaRows));
    }

    /** 把滑块拖到鼠标处：按比例换算成 scrollOffset */
    private void dragScrollbarTo(double mouseY) {
        int travel = scrollbarHeight() - thumbHeight();
        if (travel <= 0) {
            scrollOffset = 0;
            return;
        }
        double ratio = (mouseY - scrollbarTop()) / (double) travel;
        ratio = Math.max(0.0, Math.min(1.0, ratio));
        scrollOffset = (int) Math.round(ratio * maxScrollRows());
    }

    /** 鼠标是否在滚动条轨道上（含滑块） */
    private boolean overScrollbar(double mouseX, double mouseY) {
        return maxScrollRows() > 0
                && mouseX >= scrollbarX() && mouseX < scrollbarX() + SCROLLBAR_W
                && mouseY >= scrollbarTop() && mouseY < scrollbarTop() + scrollbarHeight();
    }

    private void renderScrollbar(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (maxScrollRows() <= 0) {
            return; // 内容不足一屏，不需要滚动条
        }
        int x = scrollbarX();
        int top = scrollbarTop();
        int h = scrollbarHeight();
        // 轨道
        guiGraphics.fill(x, top, x + SCROLLBAR_W, top + h, 0x30FFFFFF);
        // 滑块：hover 或拖拽中更亮
        boolean active = draggingScrollbar || overScrollbar(mouseX, mouseY);
        int thumb = thumbY();
        int th = thumbHeight();
        guiGraphics.fill(x, thumb, x + SCROLLBAR_W, thumb + th,
                active ? 0xCCFFFFFF : 0x88FFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 仓库物品条目 */
    private static class WarehouseItem {
        final String type;
        final String id;
        final String displayName;
        final String description;
        final int count;
        final int quality;
        /** 是否已收藏（收藏排最前、且不可销毁） */
        boolean favorite;
        /** 是否处于"已装备"状态（称号 / 音乐盒用） */
        final boolean equipped;

        WarehouseItem(String type, String id, String displayName, String description, int count, int quality) {
            this(type, id, displayName, description, count, quality, false, false);
        }

        WarehouseItem(String type, String id, String displayName, String description, int count, int quality,
                boolean favorite) {
            this(type, id, displayName, description, count, quality, favorite, false);
        }

        WarehouseItem(String type, String id, String displayName, String description, int count, int quality,
                boolean favorite, boolean equipped) {
            this.type = type;
            this.id = id;
            this.displayName = displayName;
            this.description = description;
            this.count = count;
            this.quality = quality;
            this.favorite = favorite;
            this.equipped = equipped;
        }

        /** 服务端收藏表用的键：type/id（皮肤本身就是 itemType/skinName） */
        String favoriteKey() {
            return CS2InventoryComponent.favoriteKey(type, id);
        }
    }
}
