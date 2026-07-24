package org.agmas.noellesroles.client.screen;

import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ItemSkinManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.agmas.noellesroles.cs2.CS2BoxConfig;
import org.agmas.noellesroles.cs2.CS2SkinInfo;
import org.agmas.noellesroles.utils.Pair;

import java.util.ArrayList;
import java.util.List;

/**
 * CS2 风格箱子奖池预览界面
 * <p>
 * 展示箱子内所有皮肤，按品质分组显示，类似 CS2 检视箱子。
 * </p>
 */
public class CS2BoxPreviewScreen extends Screen {

    // 品质颜色 ARGB（与 CS2 一致）
    private static final int[] QUALITY_COLORS = {
            0xFFB0C3D9, // 0: common - 军规级（浅蓝灰）
            0xFF5E98D9, // 1: uncommon - 受限级（蓝色）
            0xFF4B69FF, // 2: rare - 保密级（紫色）
            0xFF8847FF, // 3: epic - 隐秘级（粉紫）
            0xFFD32CE6, // 4: legendary - 罕见特殊（品红）
            0xFFE4AE39, // 5: unbelievable - 金色（刀具/手套）
    };

    private static final String[] QUALITY_NAMES = {
            "军规级", "受限级", "保密级", "隐秘级", "罕见特殊", "极其罕见"
    };

    private static final int BG_COLOR = 0xF00A0E1A;
    private static final int PANEL_BG = 0x401A1F35;
    private static final int CARD_BG = 0x30FFFFFF;
    private static final int CARD_HOVER = 0x50FFFFFF;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xFF999999;
    private static final int ACCENT = 0xFF4488FF;

    private final CS2BoxConfig config;
    private final Screen parent;

    // 所有皮肤条目（带品质信息）
    private final List<PoolEntry> entries = new ArrayList<>();
    private int scrollOffset = 0;
    private PoolEntry hoveredEntry = null;

    // 布局
    private int headerHeight = 60;
    private int cardW = 48;
    private int cardH = 56;
    private int cardGap = 6;
    private int cols;

    public CS2BoxPreviewScreen(CS2BoxConfig config, Screen parent) {
        super(Component.literal(config.getBoxName()));
        this.config = config;
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        cols = Math.max(1, (width - 60) / (cardW + cardGap));

        // 构建条目列表
        entries.clear();
        List<Pair<Double, List<String>>> allGroups = config.getAllQualityGroups();
        for (int q = 0; q < allGroups.size(); q++) {
            Pair<Double, List<String>> group = allGroups.get(q);
            if (group.second != null && !group.second.isEmpty()) {
                for (String skinId : group.second) {
                    entries.add(new PoolEntry(skinId, q, group.first));
                }
            }
        }

        // 底部按钮
        int btnY = height - 32;
        addRenderableWidget(Button.builder(Component.literal("返回"), b -> {
            minecraft.setScreen(parent);
        }).pos(width / 2 - 70, btnY).size(60, 20).build());

        addRenderableWidget(Button.builder(Component.literal("开箱"), b -> {
            // 关闭预览回到仓库，执行开箱
            if (parent instanceof CS2WarehouseScreen) {
                minecraft.setScreen(parent);
                // 仓库界面会通过选中的箱子进行开箱
            }
        }).pos(width / 2 + 10, btnY).size(60, 20).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        guiGraphics.fill(0, 0, width, height, BG_COLOR);

        // 顶部标题栏
        renderHeader(guiGraphics);

        // 概率条
        renderProbabilityBar(guiGraphics);

        // 皮肤网格
        renderGrid(guiGraphics, mouseX, mouseY);

        // 悬浮提示
        renderTooltip(guiGraphics, mouseX, mouseY);

        super.render(guiGraphics, mouseX, mouseY, delta);
    }

    private void renderHeader(GuiGraphics guiGraphics) {
        // 箱子名称
        guiGraphics.drawCenteredString(font, config.getBoxName(),
                width / 2, 10, TEXT_COLOR);

        // 所需钥匙
        String keyInfo = (config.getKeyName() != null && !config.getKeyName().isEmpty())
                ? "需要钥匙: " + config.getKeyName().replace('_', ' ')
                : "无需钥匙";
        guiGraphics.drawCenteredString(font, keyInfo, width / 2, 28, TEXT_DIM);
    }

    private void renderProbabilityBar(GuiGraphics guiGraphics) {
        int barY = 46;
        int barX = 30;
        int barW = width - 60;
        int barH = 8;

        // 绘制品质概率条（CS2风格的彩色横条）
        int x = barX;
        List<Pair<Double, List<String>>> allGroups = config.getAllQualityGroups();
        for (int q = 0; q < allGroups.size(); q++) {
            Pair<Double, List<String>> group = allGroups.get(q);
            if (group.second == null || group.second.isEmpty()) continue;

            int segW = Math.max(2, (int) (group.first * barW));
            if (x + segW > barX + barW) segW = barX + barW - x;

            guiGraphics.fill(x, barY, x + segW, barY + barH, QUALITY_COLORS[q]);
            x += segW;
        }

        // 概率文字标注（在条下方）
        x = barX;
        for (int q = 0; q < allGroups.size(); q++) {
            Pair<Double, List<String>> group = allGroups.get(q);
            if (group.second == null || group.second.isEmpty()) continue;

            int segW = Math.max(2, (int) (group.first * barW));
            if (segW > 30) {
                String pct = String.format("%.1f%%", group.first * 100);
                int textW = font.width(pct);
                guiGraphics.drawString(font, pct,
                        x + (segW - textW) / 2, barY + barH + 2,
                        QUALITY_COLORS[q], false);
            }
            x += segW;
        }
    }

    private void renderGrid(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        hoveredEntry = null;
        int gridStartX = 30;
        int gridStartY = headerHeight + 24;

        for (int i = 0; i < entries.size(); i++) {
            int idx = i - scrollOffset;
            if (idx < 0) continue;

            int row = idx / cols;
            int col = idx % cols;
            int x = gridStartX + col * (cardW + cardGap);
            int y = gridStartY + row * (cardH + cardGap);

            if (y + cardH > height - 40) break;

            PoolEntry entry = entries.get(i);
            boolean hovered = mouseX >= x && mouseX < x + cardW
                    && mouseY >= y && mouseY < y + cardH;
            if (hovered) hoveredEntry = entry;

            // 卡片背景
            int bg = hovered ? CARD_HOVER : CARD_BG;
            guiGraphics.fill(x, y, x + cardW, y + cardH, bg);

            // 品质底部边框
            int qc = QUALITY_COLORS[entry.quality];
            guiGraphics.fill(x, y + cardH - 2, x + cardW, y + cardH, qc);

            // 渲染皮肤物品图标
            renderSkinIcon(guiGraphics, entry, x + (cardW - 16) / 2, y + 4);

            // 皮肤名称（底部）
            String name = getSkinDisplayName(entry.skinId);
            int nameW = font.width(name);
            if (nameW > cardW - 4) {
                int maxChars = (cardW - 6) / 5;
                if (name.length() > maxChars) {
                    name = name.substring(0, maxChars - 1) + "..";
                }
            }
            guiGraphics.drawCenteredString(font, name,
                    x + cardW / 2, y + cardH - 14, TEXT_DIM);
        }
    }

    private void renderSkinIcon(GuiGraphics guiGraphics, PoolEntry entry, int x, int y) {
        ItemStack stack = getSkinItemStack(entry.skinId);
        if (stack != null) {
            String[] parts = entry.skinId.split("/");
            if (parts.length >= 2) {
                stack.set(SREDataComponentTypes.SKIN, parts[1]);
            }
            guiGraphics.renderFakeItem(stack, x, y);
        } else {
            // 无法获取物品时显示缩写
            String abbr = getAbbreviation(getSkinDisplayName(entry.skinId));
            guiGraphics.drawString(font, abbr, x, y + 4, 0xFFCCCCCC, false);
        }
    }

    private void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (hoveredEntry == null) return;

        List<Component> tooltip = new ArrayList<>();
        String displayName = getSkinDisplayName(hoveredEntry.skinId);

        tooltip.add(Component.literal(displayName));
        String desc = CS2SkinInfo.getDescription(hoveredEntry.skinId);
        if (!desc.isEmpty()) {
            tooltip.add(Component.literal(desc).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.literal("品质: " + QUALITY_NAMES[hoveredEntry.quality])
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        tooltip.add(Component.literal(String.format("概率: %.2f%%", hoveredEntry.probability * 100))
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        tooltip.add(Component.literal("ID: " + hoveredEntry.skinId)
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));

        guiGraphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
    }

    // ── 辅助方法 ──

    private static String getSkinDisplayName(String skinId) {
        if (skinId == null) return "?";
        return CS2SkinInfo.getName(skinId);
    }

    private static ItemStack getSkinItemStack(String skinId) {
        if (skinId == null) return null;
        if (skinId.startsWith("knife/")) return TMMItems.KNIFE.getDefaultInstance();
        if (skinId.startsWith("gun/")) return TMMItems.REVOLVER.getDefaultInstance();
        if (skinId.startsWith("revolver/")) return TMMItems.REVOLVER.getDefaultInstance();
        if (skinId.startsWith("bat/")) return TMMItems.BAT.getDefaultInstance();
        if (skinId.startsWith("grenade/")) return TMMItems.GRENADE.getDefaultInstance();
        if (skinId.startsWith("hat/")) return new ItemStack(Items.LEATHER_HELMET);
        return null;
    }

    private String getAbbreviation(String name) {
        if (name == null || name.isEmpty()) return "?";
        String[] words = name.trim().split("\\s+");
        if (words.length >= 2) {
            return ("" + words[0].charAt(0) + words[1].charAt(0)).toUpperCase();
        }
        return name.substring(0, Math.min(2, name.length())).toUpperCase();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        scrollOffset -= (int) deltaY * 2;
        int maxScroll = Math.max(0, entries.size() - (cols * ((height - headerHeight - 80) / (cardH + cardGap))));
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 奖池条目 */
    private static class PoolEntry {
        final String skinId;
        final int quality;
        final double probability;

        PoolEntry(String skinId, int quality, double probability) {
            this.skinId = skinId;
            this.quality = quality;
            this.probability = probability;
        }
    }
}
