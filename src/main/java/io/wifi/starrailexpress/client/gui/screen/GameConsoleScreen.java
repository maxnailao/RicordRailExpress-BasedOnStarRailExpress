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
    private static final int PANEL_WIDTH = 240;
    private static final int ITEM_HEIGHT = 28;
    private static final int ITEM_SPACING = 2;
    private static final int VISIBLE_ITEMS = 6;

    // 滚动
    private int scrollOffset = 0;
    private int maxScroll = 0;

    // 选中的游戏
    private String selectedMinigameId = null;

    private static final Component TITLE = Component.translatable("screen.noellesroles.game_console.title");

    public GameConsoleScreen() {
        super(TITLE);
    }

    @Override
    protected void init() {
        super.init();

        // 计算最大滚动量
        List<QuestMinigame> allGames = GameConsoleGames.getAvailable();
        int listHeight = VISIBLE_ITEMS * (ITEM_HEIGHT + ITEM_SPACING);
        int totalHeight = allGames.size() * (ITEM_HEIGHT + ITEM_SPACING);
        maxScroll = Math.max(0, totalHeight - listHeight);

        // 默认选中第一个
        if (selectedMinigameId == null && !allGames.isEmpty()) {
            selectedMinigameId = allGames.get(0).id();
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // 标题
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int panelLeft = centerX - PANEL_WIDTH / 2;
        int panelRight = centerX + PANEL_WIDTH / 2;
        int panelTop = centerY - 80;
        int panelBottom = centerY + 80;

        // 面板背景
        guiGraphics.fill(panelLeft, panelTop, panelRight, panelBottom, 0xEE1A1A2E);
        guiGraphics.renderOutline(panelLeft, panelTop, PANEL_WIDTH, panelBottom - panelTop, 0xFF4A4A6A);

        // 游戏列表
        List<QuestMinigame> allGames = GameConsoleGames.getAvailable();
        int listStartX = panelLeft + 8;
        int listStartY = panelTop + 8;

        int firstVisible = scrollOffset / (ITEM_HEIGHT + ITEM_SPACING);
        int visibleCount = VISIBLE_ITEMS + 1;

        for (int i = firstVisible; i < Math.min(allGames.size(), firstVisible + visibleCount); i++) {
            QuestMinigame game = allGames.get(i);
            int itemY = listStartY + i * (ITEM_HEIGHT + ITEM_SPACING) - scrollOffset;
            if (itemY < panelTop - ITEM_HEIGHT || itemY > panelBottom) continue;

            boolean isSelected = game.id().equals(selectedMinigameId);
            boolean isHovered = mouseX >= listStartX && mouseX <= panelRight - 20
                    && mouseY >= itemY && mouseY <= itemY + ITEM_HEIGHT;

            // 背景色
            int bgColor = isSelected ? 0xFF4A6B9A : (isHovered ? 0xFF3A4B6A : 0xFF2A2A4A);
            guiGraphics.fill(listStartX, itemY, panelRight - 16, itemY + ITEM_HEIGHT, bgColor);

            // 选中指示条
            if (isSelected) {
                guiGraphics.fill(listStartX, itemY, listStartX + 3, itemY + ITEM_HEIGHT, 0xFF66BBFF);
            }

            // 游戏名称
            guiGraphics.drawString(this.font, game.displayName(),
                    listStartX + 10, itemY + (ITEM_HEIGHT - 8) / 2, 0xFFFFFF);
        }

        // 滚动条
        if (maxScroll > 0) {
            int scrollBarX = panelRight - 12;
            int scrollBarH = panelBottom - panelTop;
            int thumbH = Math.max(20, (int) ((float) (VISIBLE_ITEMS * (ITEM_HEIGHT + ITEM_SPACING))
                    / (allGames.size() * (ITEM_HEIGHT + ITEM_SPACING)) * scrollBarH));
            int thumbY = panelTop + (int) ((float) scrollOffset / maxScroll * (scrollBarH - thumbH));
            guiGraphics.fill(scrollBarX, panelTop, scrollBarX + 4, panelBottom, 0xFF1A1A2E);
            guiGraphics.fill(scrollBarX, thumbY, scrollBarX + 4, thumbY + thumbH, 0xFF6A8BAA);
        }

        // 底部提示
        if (selectedMinigameId != null) {
            guiGraphics.drawCenteredString(this.font,
                    Component.translatable("screen.noellesroles.game_console.hint"),
                    centerX, panelBottom + 12, 0x888888);
        } else {
            guiGraphics.drawCenteredString(this.font,
                    Component.translatable("screen.noellesroles.game_console.no_games"),
                    centerX, centerY, 0xFF5555);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int panelLeft = this.width / 2 - PANEL_WIDTH / 2;
            int panelRight = this.width / 2 + PANEL_WIDTH / 2;
            int panelTop = this.height / 2 - 80;
            int panelBottom = this.height / 2 + 80;
            int listStartX = panelLeft + 8;
            int listStartY = panelTop + 8;

            List<QuestMinigame> allGames = GameConsoleGames.getAvailable();
            for (int i = 0; i < allGames.size(); i++) {
                int itemY = listStartY + i * (ITEM_HEIGHT + ITEM_SPACING) - scrollOffset;
                if (mouseX >= listStartX && mouseX <= panelRight - 20
                        && mouseY >= itemY && mouseY <= itemY + ITEM_HEIGHT) {

                    String clickedId = allGames.get(i).id();

                    if (clickedId.equals(selectedMinigameId)) {
                        // 双击或再次点击已选中的游戏 → 启动游戏
                        launchGame(clickedId);
                    } else {
                        // 单击选中
                        selectedMinigameId = clickedId;
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        scrollOffset = Mth.clamp(
                (int) (scrollOffset - scrollY * (ITEM_HEIGHT + ITEM_SPACING)),
                0, maxScroll);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER && selectedMinigameId != null) {
            launchGame(selectedMinigameId);
            return true;
        }
        // ~ 键打开积分榜（仅对有积分榜的游戏有效）
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
