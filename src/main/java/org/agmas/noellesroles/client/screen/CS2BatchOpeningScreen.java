package org.agmas.noellesroles.client.screen;

import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.agmas.noellesroles.cs2.CS2SkinInfo;

import java.util.List;

/**
 * CS2 批量开箱结果展示界面（数量>1 时使用）。
 * 以品质边框网格展示本次开出的全部物品，可滚动，关闭后释放开箱锁。
 */
public class CS2BatchOpeningScreen extends Screen {

    private static final int[] QUALITY_TEXT_COLORS = {
            0xFFEEEEEE, 0xFF33FF55, 0xFFAAAAFF, 0xFFAA55FF, 0xFFFFAA55, 0xFFFF3F3F,
    };
    private static final String[] QUALITY_NAMES = {"普通", "罕见", "稀有", "史诗", "传说", "神话"};
    private static final int BG_COLOR = 0xE60C1020;
    private static final int TEXT_COLOR = 0xFFFFFFFF;

    private final List<Integer> qualities;
    private final List<String> skinIds;
    private final List<Boolean> duplicates;

    private int cols;
    private final int cardW = 72;
    private final int cardH = 84;
    private final int gap = 8;
    private final int gridTop = 40;
    private int scroll = 0;

    public CS2BatchOpeningScreen(List<Integer> qualities, List<String> skinIds, List<Boolean> duplicates) {
        super(Component.literal("批量开箱结果"));
        this.qualities = qualities;
        this.skinIds = skinIds;
        this.duplicates = duplicates;
    }

    @Override
    protected void init() {
        super.init();
        int usableWidth = width - 32;
        cols = Math.max(1, usableWidth / (cardW + gap));
        addRenderableWidget(Button.builder(Component.literal("关闭"), b -> minecraft.setScreen(null))
                .pos(width / 2 - 50, height - 30).size(100, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, BG_COLOR);
        g.drawCenteredString(font, "批量开箱结果 (" + qualities.size() + " 件)", width / 2, 12, TEXT_COLOR);

        int maxRow = (qualities.size() + cols - 1) / cols;
        int visibleRows = Math.max(1, (height - gridTop - 40) / (cardH + gap));
        int maxScroll = Math.max(0, (maxRow - visibleRows)) * (cardH + gap);
        if (scroll > maxScroll) scroll = maxScroll;

        for (int i = 0; i < qualities.size(); i++) {
            int row = i / cols;
            int col = i % cols;
            int x = 16 + col * (cardW + gap);
            int y = gridTop + row * (cardH + gap) - scroll;
            if (y + cardH < gridTop || y > height - 34) continue;

            int q = clampQuality(qualities.get(i));
            String skinId = skinIds.get(i);
            boolean dup = duplicates.get(i);
            int borderColor = QUALITY_TEXT_COLORS[q];

            g.fill(x, y, x + cardW, y + cardH, 0x30FFFFFF);
            g.fill(x - 1, y - 1, x + cardW + 1, y, borderColor);
            g.fill(x - 1, y + cardH, x + cardW + 1, y + cardH + 1, borderColor);
            g.fill(x - 1, y, x, y + cardH, borderColor);
            g.fill(x + cardW, y, x + cardW + 1, y + cardH, borderColor);

            renderSkinCard(g, skinId, x + (cardW - 40) / 2, y + 8, 40);

            drawStringClamped(g, CS2SkinInfo.getName(skinId), x, y + cardH - 28, cardW, 0xFFDDDDDD);
            g.drawString(font, QUALITY_NAMES[q] + (dup ? " (重复)" : ""), x + 3, y + cardH - 14, borderColor, false);
        }

        super.render(g, mouseX, mouseY, delta);
    }

    private static int clampQuality(int q) {
        return Math.min(Math.max(q, 0), QUALITY_NAMES.length - 1);
    }

    private void drawStringClamped(GuiGraphics g, String text, int x, int y, int maxW, int color) {
        if (text == null || text.isEmpty()) return;
        if (font.width(text) <= maxW - 6) {
            g.drawString(font, text, x + 3, y, color, false);
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            sb.append(text.charAt(i));
            if (font.width(sb + "..") > maxW - 6) {
                sb.deleteCharAt(sb.length() - 1);
                break;
            }
        }
        sb.append("..");
        g.drawString(font, sb.toString(), x + 3, y, color, false);
    }

    private void renderSkinCard(GuiGraphics g, String skinId, int x, int y, int size) {
        ItemStack stack = getSkinItemStack(skinId);
        if (stack != null && !stack.isEmpty()) {
            String[] parts = skinId.split("/");
            if (parts.length >= 2) stack.set(SREDataComponentTypes.SKIN, parts[1]);
            float scale = size / 16f;
            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().scale(scale, scale, 1f);
            g.renderFakeItem(stack, 0, 0);
            g.pose().popPose();
        } else {
            String name = CS2SkinInfo.getName(skinId);
            String abbr = (name == null || name.isEmpty()) ? "?" : name.substring(0, Math.min(2, name.length()));
            g.drawCenteredString(font, abbr, x + size / 2, y + size / 2 - 4, 0xFFCCCCCC);
        }
    }

    private static ItemStack getSkinItemStack(String skinId) {
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

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        scroll -= (int) (deltaY * (cardH + gap));
        if (scroll < 0) scroll = 0;
        return true;
    }

    @Override
    public void removed() {
        // 无论以何种方式关闭，都释放开箱锁
        CS2WarehouseScreen.isBoxOpening = false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}