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
import org.agmas.noellesroles.cs2.network.DestroyWarehouseItemC2SPayload;
import org.agmas.noellesroles.cs2.network.EquipMusicBoxC2SPayload;
import org.agmas.noellesroles.cs2.network.EquipSkinC2SPayload;
import org.agmas.noellesroles.cs2.network.OpenBoxC2SPayload;

import java.util.*;

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

    private enum Category { ALL, BOXES, KNIFE, REVOLVER, BAT, GRENADE, HAT, MUSIC, CARDS }
    private Category selectedCategory = Category.ALL;

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

    // 布局
    private int sidebarWidth;
    private int gridStartX;
    private int gridStartY;
    private int cardSize = 52;
    private int cardGap = 6;
    private int cols;
    private int scrollOffset = 0;

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
                ClientPlayNetworking.send(new OpenBoxC2SPayload(selectedItem.id));
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

        refreshItems();
    }

    /** 右下角销毁按钮；未选中物品时隐藏 */
    private void updateDestroyButton() {
        if (destroyButton == null) {
            return;
        }
        boolean canDestroy = selectedItem != null;
        destroyButton.visible = canDestroy;
        destroyButton.active = canDestroy;
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
                        // 服务端会同步回最新仓库；本地先把选中清掉，避免按钮指向已删物品
                        selectedItem = null;
                        selectedIndex = -1;
                        updateDestroyButton();
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
        var player = Minecraft.getInstance().player;
        if (player == null) return;

        CS2InventoryComponent inv = CS2InventoryComponent.KEY.get(player);

        // 箱子 — 同类堆叠，右下角显示 xN
        if (selectedCategory == Category.ALL || selectedCategory == Category.BOXES) {
            for (Map.Entry<String, Integer> entry : inv.getBoxes().entrySet()) {
                String boxId = entry.getKey();
                // 从客户端缓存获取中文名称（服务端登录时同步）
                String cachedName = org.agmas.noellesroles.client.data.CS2ClientBoxCache.getBoxName(boxId);
                String name = !cachedName.isEmpty() ? cachedName : formatBoxId(boxId);
                items.add(new WarehouseItem("box", boxId, name, "", entry.getValue(), 0));
            }
        }

        // 钥匙 — 同类堆叠，右下角显示 xN
        if (selectedCategory == Category.ALL || selectedCategory == Category.BOXES) {
            for (Map.Entry<String, Integer> entry : inv.getKeys().entrySet()) {
                items.add(new WarehouseItem("key", entry.getKey(),
                        entry.getKey().replace('_', ' '), "", entry.getValue(), 0));
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
                        count, quality));
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
                    items.add(new WarehouseItem("music", boxId, displayName, "", 1, 0));
                }
            }
        }

        // 职业卡 — 从场外背包读取
        if (selectedCategory == Category.ALL || selectedCategory == Category.CARDS) {
            var backpack = ClientPlayerDataCache.backpack(player.getUUID());
            for (FactionCardType type : CARD_DISPLAY_ORDER) {
                int count = backpack.cards.getOrDefault(type, 0);
                if (count <= 0) continue;
                items.add(new WarehouseItem("card", type.questKey, cardName(type), "", count, 0));
            }
            // 自选职业卡
            if (backpack.selfSelectCards > 0) {
                items.add(new WarehouseItem("selfselect", "selfselect", "自选职业卡", "", backpack.selfSelectCards, 0));
            }
        }

        // 按品质降序排序（高品质靠前）
        items.sort((a, b) -> Integer.compare(b.quality, a.quality));
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
        }
    }

    private void renderSidebar(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.fill(0, 0, sidebarWidth, height, SIDEBAR_COLOR);
        Category[] categories = {Category.ALL, Category.BOXES, Category.KNIFE, Category.REVOLVER,
                Category.BAT, Category.GRENADE, Category.HAT, Category.MUSIC, Category.CARDS};
        String[] labels = {"全部", "箱子/钥匙", "刀", "左轮手枪", "棒球棍", "手雷", "帽子", "音乐盒", "职业卡"};
        for (int i = 0; i < categories.length; i++) {
            int y = 40 + i * 32;
            boolean selected = categories[i] == selectedCategory;
            boolean hovered = mouseX < sidebarWidth && mouseY >= y && mouseY < y + 28;
            int bgColor = selected ? ACCENT : (hovered ? 0x30FFFFFF : 0x10FFFFFF);
            guiGraphics.fill(4, y, sidebarWidth - 4, y + 28, bgColor);
            guiGraphics.drawString(font, labels[i], 12, y + 9,
                    selected ? 0xFFFFFFFF : TEXT_DIM, false);
        }
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
            String[] qualityNames = {"普通", "罕见", "稀有", "史诗", "传说", "不可思议"};
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

        guiGraphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
    }

    private String getTypeLabel(String type) {
        return switch (type) {
            case "box" -> "箱子";
            case "key" -> "钥匙";
            case "skin" -> "皮肤";
            case "music" -> "音乐盒";
            case "card" -> "职业卡";
            default -> type;
        };
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 侧边栏分类点击
        if (mouseX < sidebarWidth) {
            Category[] categories = {Category.ALL, Category.BOXES, Category.KNIFE, Category.REVOLVER,
                    Category.BAT, Category.GRENADE, Category.HAT, Category.MUSIC, Category.CARDS};
            for (int i = 0; i < categories.length; i++) {
                int y = 40 + i * 32;
                if (mouseY >= y && mouseY < y + 28) {
                    selectedCategory = categories[i];
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
                }
            }
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        int maxScroll = Math.max(0, (items.size() / cols + 1) * (cardSize + cardGap) - (height - gridStartY - 50));
        scrollOffset -= (int) deltaY * 2;
        scrollOffset = Math.max(0, Math.min(scrollOffset, items.size()));
        return true;
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

        WarehouseItem(String type, String id, String displayName, String description, int count, int quality) {
            this.type = type;
            this.id = id;
            this.displayName = displayName;
            this.description = description;
            this.count = count;
            this.quality = quality;
        }
    }
}
