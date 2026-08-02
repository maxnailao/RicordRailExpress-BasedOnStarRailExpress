package org.agmas.noellesroles.game.modes.werewolf.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.agmas.noellesroles.game.modes.werewolf.WerewolfPhase;
import org.agmas.noellesroles.game.modes.werewolf.WerewolfRoleDef;

/**
 * 狼人杀 HUD 渲染器
 * Author: jiale
 */
public class WerewolfHudRenderer {

    // 颜色常量
    private static final int COLOR_NIGHT_BG = 0x80000033;    // 夜晚背景（深蓝半透明）
    private static final int COLOR_DAY_BG = 0x80FFFF99;      // 白天背景（浅黄半透明）
    private static final int COLOR_WOLF = 0xFFFF5555;        // 狼方红色
    private static final int COLOR_GOOD = 0xFF55FF55;        // 好人绿色
    private static final int COLOR_TEXT = 0xFFFFFFFF;        // 白色文字
    private static final int COLOR_TIMER = 0xFFFFFF55;       // 计时器黄色
    private static final int COLOR_MY_TURN = 0xFF00FF00;     // 轮到自己（亮绿）

    /**
     * 渲染 HUD
     */
    public static void render(GuiGraphics graphics, float deltaTick) {
        if (!WerewolfClientState.active) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.screen != null) {
            return;
        }

        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        var font = client.font;

        // === 左上角：角色信息 ===
        renderRoleInfo(graphics, font, 10, 10);

        // === 顶部中央：阶段信息 + 计时器 ===
        renderPhaseInfo(graphics, font, screenWidth, client);

        // === 轮到自己时的提示 ===
        if (WerewolfClientState.isMyTurn()) {
            renderMyTurnHint(graphics, font, screenWidth, screenHeight);
        }
    }

    /**
     * 渲染角色信息（左上角）
     */
    private static void renderRoleInfo(GuiGraphics graphics, net.minecraft.client.gui.Font font, int x, int y) {
        if (WerewolfClientState.mySeat < 0) {
            return;
        }

        WerewolfRoleDef roleDef = WerewolfRoleDef.byId(WerewolfClientState.myRoleId);
        boolean isWolf = roleDef.isWolf();
        int roleColor = isWolf ? COLOR_WOLF : COLOR_GOOD;

        // 背景框
        int bgWidth = 120;
        int bgHeight = 40;
        graphics.fill(x - 2, y - 2, x + bgWidth, y + bgHeight, 0x80000000);

        // 编号
        String seatText = WerewolfClientState.mySeat + "号";
        graphics.drawString(font, seatText, x + 2, y + 2, COLOR_TEXT, true);

        // 角色名
        Component roleName = Component.translatable(roleDef.getTranslationKey());
        graphics.drawString(font, roleName, x + 2, y + 14, roleColor, true);

        // 存活状态
        if (!WerewolfClientState.myAlive) {
            graphics.drawString(font, "已死亡", x + 2, y + 26, 0xFF888888, true);
        }
    }

    /**
     * 渲染阶段信息（顶部中央）
     */
    private static void renderPhaseInfo(GuiGraphics graphics, net.minecraft.client.gui.Font font, 
            int screenWidth, Minecraft client) {
        WerewolfPhase phase = WerewolfClientState.phase;
        boolean isNight = phase.isNight();

        // 阶段名称
        Component phaseName = Component.translatable(phase.translationKey);
        int phaseWidth = font.width(phaseName);
        int centerX = screenWidth / 2;

        // 背景框
        int bgWidth = Math.max(phaseWidth + 20, 150);
        int bgColor = isNight ? COLOR_NIGHT_BG : COLOR_DAY_BG;
        graphics.fill(centerX - bgWidth / 2, 5, centerX + bgWidth / 2, 45, bgColor);

        // 阶段名
        int phaseColor = isNight ? 0xFF9999FF : 0xFF333333;
        graphics.drawCenteredString(font, phaseName, centerX, 10, phaseColor);

        // 轮次
        String roundText = "第 " + WerewolfClientState.round + " 轮";
        graphics.drawCenteredString(font, roundText, centerX, 22, COLOR_TEXT);

        // 计时器
        long currentTick = client.level != null ? client.level.getGameTime() : 0;
        float remaining = WerewolfClientState.getRemainingSeconds(currentTick);
        if (remaining >= 0) {
            String timerText = String.format("%.1fs", remaining);
            int timerColor = remaining < 5 ? 0xFFFF5555 : COLOR_TIMER;
            graphics.drawCenteredString(font, timerText, centerX, 34, timerColor);
        }

        // 当前行动者
        if (WerewolfClientState.currentActorSeat >= 0) {
            String actorText = "行动中: " + WerewolfClientState.currentActorSeat + "号";
            graphics.drawCenteredString(font, actorText, centerX, 50, 0xFFAAAAAA);
        }
    }

    /**
     * 渲染"轮到你"提示
     */
    private static void renderMyTurnHint(GuiGraphics graphics, net.minecraft.client.gui.Font font,
            int screenWidth, int screenHeight) {
        String hint = "▶ 轮到你行动了！";
        int hintWidth = font.width(hint);
        int centerX = screenWidth / 2;
        int y = screenHeight / 3;

        // 闪烁效果
        long time = System.currentTimeMillis();
        boolean blink = (time / 500) % 2 == 0;
        int color = blink ? COLOR_MY_TURN : 0xFF00AA00;

        graphics.drawCenteredString(font, hint, centerX, y, color);
    }
}
