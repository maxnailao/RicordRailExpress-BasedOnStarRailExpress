package io.wifi.starrailexpress.client.gui.screen;

import io.wifi.starrailexpress.content.minigame.GameConsoleGames;
import io.wifi.starrailexpress.content.minigame.QuestMinigame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * 游戏掌机 - 游戏选择界面
 * <p>
 * - 显示可用的小游戏列表
 * - 点击选择游戏后通过 MinigameScreenFactory 创建游戏界面
 * - 纯娱乐模式，不影响游戏状态
 * </p>
 */
public class GameConsoleScreen extends Screen {

    // 布局常量
    private static final int PANEL_WIDTH = 260;
    private static final int ITEM_HEIGHT = 30;
    private static final int ITEM_SPACING = 3;
    private static final int SCROLLBAR_W = 8;
    private static final int SCROLLBAR_PAD = 4;

    // 滚动状态
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private boolean draggingScrollbar = false;
    private int dragStartY = 0;
    private int dragStartScroll = 0;

    // 动态计算
    private int panelLeft, panelRight, panelTop, panelBottom;
    private int listStartX, listStartY, listWidth, listHeight;

    // 选中的游戏
    private String selectedMinigameId = null;

    private static final Component TITLE = Component.translatable("screen.noellesroles.game_console.title");

    public GameConsoleScreen() {
        super(TITLE);
    }

    @Override
    protected void init() {
        super.init();

        // 动态计算面板尺寸（占屏幕60%高度，最小200px）
        int panelH = Math.max(200, (int) (this.height * 0.6));
        int panelW = Math.min(PANEL_WIDTH, this.width - 40);
        panelLeft = (this.width - panelW) / 2;
        panelRight = panelLeft + panelW;
        panelTop = (this.height - panelH) / 2;
        panelBottom = panelTop + panelH;

        // 列表区域
        listStartX = panelLeft + 8;
        listStartY = panelTop + 28; // 标题行下方
        listWidth = panelW - 16 - SCROLLBAR_W - SCROLLBAR_PAD;
        listHeight = panelBottom - panelTop - 36 - 24; // 顶部标题 + 底部提示

        // 计算最大滚动量
        List<QuestMinigame> allGames = GameConsoleGames.getAvailable();
        int totalHeight = allGames.size() * (ITEM_HEIGHT + ITEM_SPACING);
        maxScroll = Math.max(0, totalHeight - listHeight);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);

        // 默认选中第一个
        if (selectedMinigameId == null && !allGames.isEmpty()) {
            selectedMinigameId = allGames.get(0).id();
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);

        List<QuestMinigame> allGames = GameConsoleGames.getAvailable();

        // ── 面板背景 ──
        g.fill(panelLeft, panelTop, panelRight, panelBottom, 0xEE1A1A2E);
        g.renderOutline(panelLeft, panelTop, panelRight - panelLeft, panelBottom - panelTop, 0xFF4A4A6A);

        // ── 顶部标题栏 ──
        g.fill(panelLeft + 1, panelTop + 1, panelRight - 1, panelTop + 24, 0xFF2A2A5A);
        g.drawCenteredString(this.font, this.title, (panelLeft + panelRight) / 2, panelTop + 8, 0xFFEEEEFF);

        // ── 游戏列表（裁剪区域）──
        int itemAreaRight = panelRight - SCROLLBAR_W - SCROLLBAR_PAD - 4;
        int firstVisible = scrollOffset / (ITEM_HEIGHT + ITEM_SPACING);
        int visibleCount = listHeight / (ITEM_HEIGHT + ITEM_SPACING) + 2;

        for (int i = firstVisible; i < Math.min(allGames.size(), firstVisible + visibleCount); i++) {
            QuestMinigame game = allGames.get(i);
            int itemY = listStartY + i * (ITEM_HEIGHT + ITEM_SPACING) - scrollOffset;
            if (itemY + ITEM_HEIGHT < listStartY || itemY > listStartY + listHeight) continue;

            boolean isSelected = game.id().equals(selectedMinigameId);
            boolean isHovered = mouseX >= listStartX && mouseX <= itemAreaRight
                    && mouseY >= itemY && mouseY <= itemY + ITEM_HEIGHT
                    && mouseY >= listStartY && mouseY <= listStartY + listHeight;

            // 背景
            int bgColor = isSelected ? 0xFF4A6B9A : (isHovered ? 0xFF3A4B6A : 0xFF2A2A4A);
            g.fill(listStartX, itemY, itemAreaRight, itemY + ITEM_HEIGHT, bgColor);

            // 选中指示条
            if (isSelected) {
                g.fill(listStartX, itemY, listStartX + 3, itemY + ITEM_HEIGHT, 0xFF66BBFF);
                // 顶部和底部高光线
                g.fill(listStartX, itemY, itemAreaRight, itemY + 1, 0x4066BBFF);
                g.fill(listStartX, itemY + ITEM_HEIGHT - 1, itemAreaRight, itemY + ITEM_HEIGHT, 0x4066BBFF);
            }

            // 游戏名称
            g.drawString(this.font, game.displayName(),
                    listStartX + 10, itemY + (ITEM_HEIGHT - 9) / 2, 0xFFFFFFFF);

            // 积分榜标记
            if (GameConsoleGames.hasScoreboard(game.id())) {
                g.drawString(this.font, "\u2605", itemAreaRight - 12, itemY + (ITEM_HEIGHT - 9) / 2, 0xFFFFCC00, false);
            }
        }

        // ── 滚动条 ──
        if (maxScroll > 0) {
            int sbX = panelRight - SCROLLBAR_W - SCROLLBAR_PAD;
            int sbY = listStartY;
            int sbH = listHeight;
            int trackH = sbH;
            int thumbH = Math.max(20, (int) ((float) listHeight / (allGames.size() * (ITEM_HEIGHT + ITEM_SPACING)) * sbH));
            int thumbY = sbY + (maxScroll > 0 ? (int) ((float) scrollOffset / maxScroll * (sbH - thumbH)) : 0);
            boolean sbHovered = mouseX >= sbX && mouseX <= sbX + SCROLLBAR_W && mouseY >= sbY && mouseY <= sbY + sbH;

            // 滑轨
            g.fill(sbX, sbY, sbX + SCROLLBAR_W, sbY + sbH, 0xFF1A1A3E);
            g.renderOutline(sbX, sbY, SCROLLBAR_W, sbH, 0xFF3A3A5A);

            // 滑块
            int thumbColor = draggingScrollbar ? 0xFF88BBDD : (sbHovered ? 0xFF7AAACC : 0xFF5A8AAA);
            g.fill(sbX + 1, thumbY, sbX + SCROLLBAR_W - 1, thumbY + thumbH, thumbColor);
            // 滑块纹理线
            int midY = thumbY + thumbH / 2;
            g.fill(sbX + 2, midY - 2, sbX + SCROLLBAR_W - 2, midY - 1, 0x40FFFFFF);
            g.fill(sbX + 2, midY, sbX + SCROLLBAR_W - 2, midY + 1, 0x40FFFFFF);
            g.fill(sbX + 2, midY + 2, sbX + SCROLLBAR_W - 2, midY + 3, 0x40FFFFFF);
        }

        // ── 底部提示 ──
        if (selectedMinigameId != null) {
            String hint = "Enter/\u53cc\u51fb: \u5f00\u59cb    ~: \u79ef\u5206\u699c    \u2191\u2193: \u5bfc\u822a";
            g.drawCenteredString(this.font, hint, (panelLeft + panelRight) / 2, panelBottom - 16, 0xFF8888AA);
        } else if (allGames.isEmpty()) {
            g.drawCenteredString(this.font,
                    Component.translatable("screen.noellesroles.game_console.no_games"),
                    (panelLeft + panelRight) / 2, (panelTop + panelBottom) / 2, 0xFFFF5555);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // 滚动条拖拽
            int sbX = panelRight - SCROLLBAR_W - SCROLLBAR_PAD;
            if (maxScroll > 0 && mouseX >= sbX && mouseX <= sbX + SCROLLBAR_W
                    && mouseY >= listStartY && mouseY <= listStartY + listHeight) {
                draggingScrollbar = true;
                dragStartY = (int) mouseY;
                dragStartScroll = scrollOffset;
                return true;
            }

            // 游戏项点击
            int itemAreaRight = panelRight - SCROLLBAR_W - SCROLLBAR_PAD - 4;
            List<QuestMinigame> allGames = GameConsoleGames.getAvailable();
            for (int i = 0; i < allGames.size(); i++) {
                int itemY = listStartY + i * (ITEM_HEIGHT + ITEM_SPACING) - scrollOffset;
                if (itemY + ITEM_HEIGHT < listStartY || itemY > listStartY + listHeight) continue;
                if (mouseX >= listStartX && mouseX <= itemAreaRight
                        && mouseY >= itemY && mouseY <= itemY + ITEM_HEIGHT) {

                    String clickedId = allGames.get(i).id();
                    if (clickedId.equals(selectedMinigameId)) {
                        launchGame(clickedId);
                    } else {
                        selectedMinigameId = clickedId;
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar && maxScroll > 0) {
            List<QuestMinigame> allGames = GameConsoleGames.getAvailable();
            int totalH = allGames.size() * (ITEM_HEIGHT + ITEM_SPACING);
            int thumbH = Math.max(20, (int) ((float) listHeight / totalH * listHeight));
            int trackRange = listHeight - thumbH;
            if (trackRange > 0) {
                int delta = (int) mouseY - dragStartY;
                scrollOffset = Mth.clamp(dragStartScroll + (int) ((float) delta / trackRange * maxScroll), 0, maxScroll);
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        // 仅在鼠标位于面板区域时响应滚动
        if (mx >= panelLeft && mx <= panelRight && my >= panelTop && my <= panelBottom) {
            scrollOffset = Mth.clamp(
                    (int) (scrollOffset - scrollY * (ITEM_HEIGHT + ITEM_SPACING)),
                    0, maxScroll);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER && selectedMinigameId != null) {
            launchGame(selectedMinigameId);
            return true;
        }
        // 上下方向键导航
        if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) {
            List<QuestMinigame> allGames = GameConsoleGames.getAvailable();
            if (!allGames.isEmpty()) {
                int idx = -1;
                for (int i = 0; i < allGames.size(); i++) {
                    if (allGames.get(i).id().equals(selectedMinigameId)) { idx = i; break; }
                }
                if (keyCode == GLFW.GLFW_KEY_UP) idx = Math.max(0, idx - 1);
                else idx = Math.min(allGames.size() - 1, idx + 1);
                selectedMinigameId = allGames.get(idx).id();
                // 确保选中项可见
                int itemY = listStartY + idx * (ITEM_HEIGHT + ITEM_SPACING) - scrollOffset;
                if (itemY < listStartY) scrollOffset = idx * (ITEM_HEIGHT + ITEM_SPACING);
                if (itemY + ITEM_HEIGHT > listStartY + listHeight)
                    scrollOffset = idx * (ITEM_HEIGHT + ITEM_SPACING) + ITEM_HEIGHT + ITEM_SPACING - listHeight;
                scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);
            }
            return true;
        }
        // ~ 键打开积分榜
        if (keyCode == GLFW.GLFW_KEY_GRAVE_ACCENT && selectedMinigameId != null
                && GameConsoleGames.hasScoreboard(selectedMinigameId)) {
            if (minecraft != null) {
                minecraft.setScreen(new MinigameScoreboardScreen(selectedMinigameId, this));
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * 启动选中的小游戏
     * 
     * @param minigameId 小游戏 ID
     */
    private void launchGame(String minigameId) {
        // 使用 MinigameScreenFactory 创建游戏界面
        // BlockPos.ZERO 作为占位（掌机无实际位置）
        // onSuccess 回调：游戏结束后显示结果（预留计分板接口）
        Runnable onSuccess = () -> {
            // 纯娱乐：游戏成功后显示提示
            // TODO: 后续添加服务器计分板记录
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.displayClientMessage(
                        Component.translatable("message.noellesroles.game_console.game_complete")
                                .withStyle(net.minecraft.ChatFormatting.GREEN),
                        true);
            }
        };

        Screen gameScreen = MinigameScreenFactory.create(minigameId, BlockPos.ZERO, onSuccess);
        if (gameScreen != null && minecraft != null) {
            minecraft.setScreen(gameScreen);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
