package io.wifi.starrailexpress.client.gui.screen;

import io.wifi.starrailexpress.content.minigame.GameConsoleGames;
import io.wifi.starrailexpress.content.minigame.MinigameScoreboardData;
import io.wifi.starrailexpress.content.minigame.QuestMinigame;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * 小游戏积分榜界面
 * <p>
 * 显示指定小游戏的排行榜数据。
 * 从 MinigameScoreboardData 静态数据中读取。
 * </p>
 */
public class MinigameScoreboardScreen extends Screen {

    private static final int PANEL_WIDTH = 260;
    private static final int ROW_HEIGHT = 22;
    private static final int VISIBLE_ROWS = 10;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MM/dd HH:mm");

    private final String minigameId;
    private final Screen parent;
    private int scrollOffset = 0;
    private int maxScroll = 0;

    public MinigameScoreboardScreen(String minigameId, Screen parent) {
        super(Component.translatable("screen.noellesroles.scoreboard.title"));
        this.minigameId = minigameId;
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        List<MinigameScoreboardData.ScoreEntry> scores = MinigameScoreboardData.getScores(minigameId);
        int totalHeight = scores.size() * ROW_HEIGHT;
        int visibleHeight = VISIBLE_ROWS * ROW_HEIGHT;
        maxScroll = Math.max(0, totalHeight - visibleHeight);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int panelLeft = centerX - PANEL_WIDTH / 2;
        int panelRight = centerX + PANEL_WIDTH / 2;
        int panelTop = centerY - 90;
        int panelBottom = centerY + 90;

        // 面板背景
        g.fill(panelLeft, panelTop, panelRight, panelBottom, 0xEE1A1A2E);
        g.renderOutline(panelLeft, panelTop, PANEL_WIDTH, panelBottom - panelTop, 0xFF4A4A6A);

        // 标题
        QuestMinigame game = io.wifi.starrailexpress.content.minigame.QuestMinigames.get(minigameId);
        String title = game != null ? game.displayName().getString() : minigameId;
        g.drawCenteredString(this.font,
                Component.translatable("screen.noellesroles.scoreboard.title").getString() + " - " + title,
                centerX, panelTop + 6, 0xFFFFFF);

        // 表头
        int headerY = panelTop + 22;
        g.drawString(this.font, "#", panelLeft + 8, headerY, 0xAAAAAA);
        g.drawString(this.font,
                Component.translatable("screen.noellesroles.scoreboard.player").getString(),
                panelLeft + 30, headerY, 0xAAAAAA);
        g.drawString(this.font,
                Component.translatable("screen.noellesroles.scoreboard.score").getString(),
                panelRight - 80, headerY, 0xAAAAAA);
        g.drawString(this.font,
                Component.translatable("screen.noellesroles.scoreboard.time").getString(),
                panelRight - 140, headerY, 0xAAAAAA);

        // 分割线
        g.fill(panelLeft + 4, headerY + 12, panelRight - 4, headerY + 13, 0xFF3A3A5A);

        // 数据行
        List<MinigameScoreboardData.ScoreEntry> scores = MinigameScoreboardData.getScores(minigameId);
        int dataStartY = headerY + 18;

        if (scores.isEmpty()) {
            g.drawCenteredString(this.font,
                    Component.translatable("screen.noellesroles.scoreboard.empty"),
                    centerX, centerY, 0x888888);
        } else {
            int firstVisible = scrollOffset / ROW_HEIGHT;
            for (int i = firstVisible; i < Math.min(scores.size(), firstVisible + VISIBLE_ROWS + 1); i++) {
                int rowY = dataStartY + i * ROW_HEIGHT - scrollOffset;
                if (rowY < panelTop + 36 || rowY > panelBottom - 10) continue;

                MinigameScoreboardData.ScoreEntry entry = scores.get(i);

                // 前三名高亮
                int rankColor = i < 3 ? 0xFFFFD700 : 0xFFFFFF;
                int bgColor = i % 2 == 0 ? 0xFF2A2A4A : 0xFF222244;
                g.fill(panelLeft + 4, rowY, panelRight - 4, rowY + ROW_HEIGHT - 2, bgColor);

                g.drawString(this.font, String.valueOf(i + 1), panelLeft + 10, rowY + 6, rankColor);
                g.drawString(this.font, entry.playerName(), panelLeft + 32, rowY + 6, 0xFFFFFF);
                g.drawString(this.font, String.valueOf(entry.score()), panelRight - 78, rowY + 6, 0x55FF55);
                g.drawString(this.font, DATE_FORMAT.format(new Date(entry.timestamp())),
                        panelRight - 138, rowY + 6, 0x888888);
            }
        }

        // 滚动条
        if (maxScroll > 0) {
            int scrollBarX = panelRight - 8;
            int scrollBarH = panelBottom - panelTop - 40;
            int thumbH = Math.max(16, (int) ((float) (VISIBLE_ROWS * ROW_HEIGHT)
                    / (scores.size() * ROW_HEIGHT) * scrollBarH));
            int thumbY = panelTop + 36 + (int) ((float) scrollOffset / maxScroll * (scrollBarH - thumbH));
            g.fill(scrollBarX, panelTop + 36, scrollBarX + 4, panelTop + 36 + scrollBarH, 0xFF1A1A2E);
            g.fill(scrollBarX, thumbY, scrollBarX + 4, thumbY + thumbH, 0xFF6A8BAA);
        }

        // 底部提示
        g.drawCenteredString(this.font,
                Component.translatable("screen.noellesroles.scoreboard.close_hint"),
                centerX, panelBottom + 8, 0x666666);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        scrollOffset = Mth.clamp((int) (scrollOffset - scrollY * ROW_HEIGHT), 0, maxScroll);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_GRAVE_ACCENT) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
