package io.wifi.starrailexpress.client.gui.screen.ingame;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import io.wifi.starrailexpress.SRE;
import org.agmas.noellesroles.client.screen.CS2WarehouseScreen;
import io.wifi.starrailexpress.content.mail.MailboxScreen;
import org.agmas.noellesroles.client.screen.GameManagementScreen;
import io.wifi.starrailexpress.client.gui.screen.roster.RoleRosterEditScreen;
import io.wifi.starrailexpress.client.gui.screen.roster.RoleRosterViewScreen;
import io.wifi.starrailexpress.SREClientConfig;

import java.util.List;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * 生存模式 E 键菜单（菜单栏）。
 * <p>
 * 左侧为人物皮肤预览（黑灰渐变背景，移动鼠标可旋转查看），
 * 中部 3x2 白色方块为功能按钮，右侧黑色方块为下一页按钮，顶部为标题栏。
 * 面板配色与 CS2 仓库/商店（原本菜单）保持一致。
 * </p>
 */
public class MenuHubScreen extends Screen {

    // 面板主体背景图（黄色区域，拉伸铺满）
    private static final ResourceLocation BG_TEXTURE =
            SRE.id("textures/gui/menu_background.png");

    // 与 CS2WarehouseScreen 同源的配色
    private static final int BG_COLOR = 0xE60C1020;
    private static final int PANEL_COLOR = 0xC0161B30;
    private static final int ACCENT = 0xFF4488FF;
    private static final int TEXT_COLOR = 0xFFFFFFFF;

    // 白色边框（替代原来的蓝色 ACCENT 描边）
    private static final int BORDER_COLOR = 0xFFFFFFFF;
    // 右上角现实时间格式（时:分:秒）
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final int SQUARE_BG = 0xF2FFFFFF;
    private static final int SQUARE_BG_HOVER = 0xFFE6F0FF;
    private static final int SQUARE_BG_DOWN = 0xFFBBD4FF;
    private static final int SQUARE_TEXT = 0xFF1A1F2E;
    private static final int NEXT_BG = 0xFF0A0A0A;
    private static final int NEXT_BG_HOVER = 0xFF1E2533;
    private static final int NEXT_BG_DOWN = 0xFF3A4A66;

    // 第二页按权限区分：管理项红字；普通玩家不可打开项用灰底白字（禁用态仍可读）
    private static final int RED_TEXT = 0xFFCC2222;
    private static final int WHITE_TEXT = 0xFFFFFFFF;
    private static final int DISABLE_BG = 0xFF556070;

    private static final int TOTAL_PAGES = 2;
    private int page = 1;

    // 布局（init 时计算，render/按钮共用）
    private int panelX, panelY, panelW, panelH;
    private int previewX, previewY, previewW, previewH;
    private int nextX, nextY, nextSize = 22;

    public MenuHubScreen() {
        super(Component.literal("菜单栏"));
    }

    @Override
    protected void init() {
        super.init();
        computeLayout();
        rebuildButtons();
    }

    private void computeLayout() {
        panelW = Math.min(width - 16, 360);
        panelH = Math.min(height - 48, 190);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2 + 14;

        int pad = 8;
        previewX = panelX + pad;
        previewY = panelY + pad;
        previewH = panelH - pad * 2;
        previewW = (int) (previewH * 0.62);
    }

    private void rebuildButtons() {
        clearWidgets();

        int gap = 10;
        int gridStartX = previewX + previewW + 12;
        int gridMaxX = panelX + panelW - nextSize - 14;
        int cell = Math.min((gridMaxX - gridStartX - gap * 2) / 3, (previewH - gap) / 2);
        cell = Math.max(cell, 20);
        int gridW = cell * 3 + gap * 2;
        int gridH = cell * 2 + gap;
        int gx = gridStartX + (gridMaxX - gridStartX - gridW) / 2;
        int gy = panelY + (panelH - gridH) / 2;

        boolean op = minecraft.player != null && minecraft.player.hasPermissions(2);

        String[] labels = {"饰品", "商城", "职业卡", "仓库", "开箱", "邮箱"};
        int[] texts = {SQUARE_TEXT, SQUARE_TEXT, SQUARE_TEXT, SQUARE_TEXT, SQUARE_TEXT, SQUARE_TEXT};
        int[] bgs = {SQUARE_BG, SQUARE_BG, SQUARE_BG, SQUARE_BG, SQUARE_BG, SQUARE_BG};
        boolean[] enabled = {true, true, true, true, true, true};

        if (page == 2) {
            labels = new String[]{"", "", "", "", "", ""};
            if (op) {
                // 管理：游戏管理 / 职业轮换管理 均红字、可打开
                labels[2] = "游戏管理";     texts[2] = RED_TEXT;
                labels[5] = "职业轮换管理"; texts[5] = RED_TEXT;
            } else {
                // 普通：右上=列车客户端选项（可打开）；右下=职业轮换管理（白字、不可打开）
                labels[2] = "列车客户端选项"; texts[2] = SQUARE_TEXT;
                labels[5] = "职业轮换管理";   texts[5] = WHITE_TEXT; bgs[5] = DISABLE_BG; enabled[5] = false;
            }
        }

        for (int i = 0; i < 6; i++) {
            int col = i % 3;
            int row = i / 3;
            final int idx = i;
            SquareButton sb = new SquareButton(
                    gx + col * (cell + gap), gy + row * (cell + gap), cell, cell,
                    labels[i], b -> onGridClick(idx),
                    bgs[i], SQUARE_BG_HOVER, SQUARE_BG_DOWN, texts[i]);
            sb.active = enabled[i];
            addRenderableWidget(sb);
        }

        // 黑色下一页按钮：目前仅切换页码，第二页内容为空壳
        nextX = panelX + panelW - nextSize - 6;
        nextY = gy + gridH / 2 - nextSize / 2;
        addRenderableWidget(new SquareButton(nextX, nextY, nextSize, nextSize, "▶", b -> {
            page = page >= TOTAL_PAGES ? 1 : page + 1;
            rebuildButtons();
        }, NEXT_BG, NEXT_BG_HOVER, NEXT_BG_DOWN, TEXT_COLOR));
    }

    private void onGridClick(int idx) {
        if (page == 2) {
            boolean op = minecraft.player != null && minecraft.player.hasPermissions(2);
            switch (idx) {
                case 2 -> { // 右上：管理=游戏管理；普通=列车客户端选项
                    if (op) minecraft.setScreen(new GameManagementScreen(this));
                    else minecraft.setScreen(SREClientConfig.HANDLER.generateGui().generateScreen(this));
                }
                case 5 -> { // 右下：仅管理可打开职业轮换管理；普通该格已禁用，不会触发
                    if (op) minecraft.setScreen(new RoleRosterEditScreen());
                }
                default -> tellDeveloping();
            }
            return;
        }
        if (page != 1) {
            tellDeveloping();
            return;
        }
        switch (idx) {
            case 0 -> minecraft.setScreen(new AccessoryScreen(this));
            case 1 -> minecraft.setScreen(new ShopScreen(this));
            case 2 -> minecraft.setScreen(new RoleCardScreen(this));
            case 3 -> minecraft.setScreen(new CS2WarehouseScreen());
            case 5 -> minecraft.setScreen(new MailboxScreen());
            default -> tellDeveloping();
        }
    }

    private void noPerm() {
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.literal("§c需要管理权限才能打开该功能"), true);
        }
    }

    private void tellDeveloping() {
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.literal("§e该功能开发中"), true);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 全屏背景 + 菜单面板（沿用原本菜单风格）
        graphics.fill(0, 0, width, height, BG_COLOR);

        int headerH = 24;
        // 顶部标题栏（114514.png 红色区域）改为白色
        graphics.fill(panelX, panelY - headerH, panelX + panelW, panelY, 0xFFFFFFFF);
        // 左上角：主菜单；右上角：当前现实时间（白底改用深色字，保证可见）
        graphics.drawString(font, "主菜单", panelX + 4, panelY - headerH + 8, SQUARE_TEXT);
        String timeStr = LocalTime.now().format(TIME_FMT);
        graphics.drawString(font, timeStr, panelX + panelW - 4 - font.width(timeStr), panelY - headerH + 8, SQUARE_TEXT);

        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_COLOR);
        // 面板主体（黄色区域）背景改用图片铺满
        graphics.blit(BG_TEXTURE, panelX, panelY, panelW, panelH, 0.0F, 0.0F, 1, 1, 1, 1);
        graphics.renderOutline(panelX, panelY, panelW, panelH, BORDER_COLOR);

        // 左侧：蓝白渐变的人物预览背景
        graphics.fillGradient(previewX, previewY, previewX + previewW, previewY + previewH,
                0xFFE6F0FF, 0xFF4488FF);
        graphics.renderOutline(previewX, previewY, previewW, previewH, 0xFF556178);

        super.render(graphics, mouseX, mouseY, partialTick);

        // 打开者自己的皮肤，跟随鼠标旋转查看
        if (minecraft.player != null) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 300F);
            int modelScale = Math.max(previewH / 5, 20);
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                    graphics, previewX, previewY, previewX + previewW, previewY + previewH,
                    modelScale, 0.0625F, mouseX, mouseY, minecraft.player);
            graphics.pose().popPose();
        }

        // 页码：位于下一页按钮上方
        graphics.drawCenteredString(font, page + "/" + TOTAL_PAGES,
                nextX + nextSize / 2, nextY - 13, TEXT_COLOR);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // 主菜单是普通 Screen，原版 E 键关闭只对容器界面生效；这里显式支持 E 键关闭。
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.minecraft != null && this.minecraft.options != null
                && this.minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            // 消费掉本次按键点击，避免下一 tick 的 handleKeybinds 再次打开主菜单。
            this.minecraft.options.keyInventory.consumeClick();
            this.minecraft.setScreen(null);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** 白色/黑色方块按钮：纯色底 + 描边 + 居中文字 */
    private class SquareButton extends Button {
        private final int bg, hover, down, textColor;
        private boolean pressed;

        SquareButton(int x, int y, int w, int h, String label, OnPress action) {
            this(x, y, w, h, label, action, SQUARE_BG, SQUARE_BG_HOVER, SQUARE_BG_DOWN, SQUARE_TEXT);
        }

        SquareButton(int x, int y, int w, int h, String label, OnPress action,
                int bg, int hover, int down, int textColor) {
            super(x, y, w, h, Component.literal(label), action, DEFAULT_NARRATION);
            this.bg = bg;
            this.hover = hover;
            this.down = down;
            this.textColor = textColor;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            this.pressed = true;
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            this.pressed = false;
            return super.mouseReleased(mouseX, mouseY, button);
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int color = !active ? (bg & 0x80FFFFFF)
                    : (isHoveredOrFocused() ? (pressed ? down : hover) : bg);
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), color);
            graphics.renderOutline(getX(), getY(), getWidth(), getHeight(), 0xFFB8C4DC);
            String msg = getMessage().getString();
            if (!msg.isEmpty()) {
                graphics.drawCenteredString(font, msg,
                        getX() + getWidth() / 2,
                        getY() + (getHeight() - font.lineHeight) / 2, textColor);
            }
        }
    }
}