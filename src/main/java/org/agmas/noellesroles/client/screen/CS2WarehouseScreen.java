package org.agmas.noellesroles.client.screen;

import io.wifi.starrailexpress.cca.CS2InventoryComponent;
import io.wifi.starrailexpress.cca.SREPlayerSkinsComponent;
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
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.agmas.noellesroles.cs2.CS2BoxConfig;
import org.agmas.noellesroles.cs2.CS2BoxManager;
import org.agmas.noellesroles.cs2.CS2SkinInfo;
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

    private enum Category { ALL, BOXES, SKINS, MUSIC }
    private Category selectedCategory = Category.ALL;

    // 物品网格数据
    private final List<WarehouseItem> items = new ArrayList<>();
    private WarehouseItem hoveredItem = null;
    private WarehouseItem selectedItem = null;
    private long lastClickTime = 0;
    private String lastClickItemId = null;

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
        sidebarWidth = width / 6;
        gridStartX = sidebarWidth + 16;
        gridStartY = 40;
        cols = (width - gridStartX - 16) / (cardSize + cardGap);
        if (cols < 1) cols = 1;

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

        refreshItems();
    }

    private void refreshItems() {
        items.clear();
        var player = Minecraft.getInstance().player;
        if (player == null) return;

        CS2InventoryComponent inv = CS2InventoryComponent.KEY.get(player);

        // 箱子 — 每个箱子占一格
        if (selectedCategory == Category.ALL || selectedCategory == Category.BOXES) {
            for (Map.Entry<String, Integer> entry : inv.getBoxes().entrySet()) {
                String boxId = entry.getKey();
                // 从箱子配置获取中文名称
                CS2BoxConfig config = CS2BoxManager.getInstance().getBox(boxId);
                String name = (config != null && !config.getBoxName().isEmpty())
                        ? config.getBoxName() : formatBoxId(boxId);
                for (int i = 0; i < entry.getValue(); i++) {
                    items.add(new WarehouseItem("box", boxId, name, "", 1, 0));
                }
            }
        }

        // 钥匙 — 每个钥匙占一格
        if (selectedCategory == Category.ALL || selectedCategory == Category.BOXES) {
            for (Map.Entry<String, Integer> entry : inv.getKeys().entrySet()) {
                for (int i = 0; i < entry.getValue(); i++) {
                    items.add(new WarehouseItem("key", entry.getKey(),
                            entry.getKey().replace('_', ' '), "", 1, 0));
                }
            }
        }

        // 皮肤 — 从 CS2InventoryComponent 仓库读取
        if (selectedCategory == Category.ALL || selectedCategory == Category.SKINS) {
            for (Map.Entry<String, Integer> entry : inv.getSkins().entrySet()) {
                String skinId = entry.getKey(); // 格式: "itemType/skinName"
                int count = entry.getValue();
                if (count <= 0) continue;

                String[] parts = skinId.split("/");
                if (parts.length < 2) continue;
                String itemType = parts[0];
                String skinName = parts[1];

                int quality = getSkinQuality(itemType, skinName);
                // 每个皮肤各占一格
                for (int i = 0; i < count; i++) {
                    items.add(new WarehouseItem("skin", skinId,
                            CS2SkinInfo.getName(skinId),
                            CS2SkinInfo.getDescription(skinId),
                            1, quality));
                }
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
        Category[] categories = {Category.ALL, Category.BOXES, Category.SKINS, Category.MUSIC};
        String[] labels = {"全部", "箱子/钥匙", "皮肤", "音乐盒"};
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
            boolean isSelected = selectedItem != null && selectedItem.id.equals(item.id);

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
        if (skinId.startsWith("hat/")) return new ItemStack(Items.LEATHER_HELMET);
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
            // 显示所需钥匙
            CS2BoxConfig boxConfig = CS2BoxManager.getInstance().getBox(hoveredItem.id);
            if (boxConfig != null && boxConfig.getKeyName() != null && !boxConfig.getKeyName().isEmpty()) {
                String keyDisplayName = boxConfig.getKeyName().replace('_', ' ');
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
            default -> type;
        };
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 侧边栏分类点击
        if (mouseX < sidebarWidth) {
            Category[] categories = {Category.ALL, Category.BOXES, Category.SKINS, Category.MUSIC};
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
                if (isDoubleClick && "box".equals(hoveredItem.type)) {
                    // 双击箱子 → 打开奖池预览UI
                    CS2BoxConfig config = CS2BoxManager.getInstance().getBox(hoveredItem.id);
                    if (config != null) {
                        minecraft.setScreen(new CS2BoxPreviewScreen(config, this));
                    }
                    return true;
                }
                selectedItem = hoveredItem;
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
