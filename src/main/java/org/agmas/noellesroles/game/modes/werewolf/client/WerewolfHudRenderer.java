package org.agmas.noellesroles.game.modes.werewolf.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.game.modes.werewolf.WerewolfPhase;
import org.agmas.noellesroles.game.modes.werewolf.WerewolfRoleDef;

/**
 * 狼人杀 HUD 渲染器 v2
 * 布局：
 * - 左上角：身份卡片（阵营色边条 + 编号/身份/阵营/死亡状态）
 * - 顶部中央：阶段横幅（昼夜指示点 + 阶段名 + 轮次 + 倒计时进度条）
 * - 右上角：玩家座位存活列表（▶行动中 ★自己 ✕死亡）
 * - 底部中央：行动提示（闪烁 + 按键提示）
 * Author: jiale
 */
public class WerewolfHudRenderer {

    // === 配色 ===
    private static final int COLOR_NIGHT_ACCENT = 0xFF7B68EE;   // 夜晚紫蓝
    private static final int COLOR_DAY_ACCENT = 0xFFFFC94A;     // 白天金黄
    private static final int COLOR_WOLF = 0xFFFF5555;           // 狼方红
    private static final int COLOR_GOOD = 0xFF55FF55;           // 好人绿
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_DIM = 0xFF999999;
    private static final int COLOR_DEAD = 0xFF5A5A5A;
    private static final int COLOR_PANEL = 0xB8101018;          // 面板底色
    private static final int COLOR_PANEL_EDGE = 0x55FFFFFF;     // 面板高光边

    public static void render(GuiGraphics graphics, float deltaTick) {
        if (!WerewolfClientState.active) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.screen != null) {
            return;
        }

        int screenWidth = graphics.guiWidth();
        var font = client.font;

        renderRoleCard(graphics, font);
        renderPhaseBanner(graphics, font, screenWidth, client);
        renderPlayerList(graphics, font, screenWidth);
        renderActionHint(graphics, font, screenWidth, graphics.guiHeight());
    }

    // ============================================================
    // 左上角：身份卡片
    // ============================================================
    private static void renderRoleCard(GuiGraphics graphics, Font font) {
        if (WerewolfClientState.mySeat < 0) {
            return;
        }

        WerewolfRoleDef roleDef = WerewolfRoleDef.byId(WerewolfClientState.myRoleId);
        boolean isWolf = roleDef.isWolf();
        int accent = isWolf ? COLOR_WOLF : COLOR_GOOD;

        int x = 8, y = 8;
        int w = 118, h = 58;

        // 面板底 + 高光边
        graphics.fill(x, y, x + w, y + h, COLOR_PANEL);
        graphics.fill(x, y, x + w, y + 1, COLOR_PANEL_EDGE);
        graphics.fill(x, y + h - 1, x + w, y + h, COLOR_PANEL_EDGE);
        // 左侧阵营色条
        graphics.fill(x, y, x + 3, y + h, accent);

        // 标题行：编号
        graphics.drawString(font,
                Component.translatable("werewolf.hud.seat", WerewolfClientState.mySeat),
                x + 9, y + 6, COLOR_TEXT, true);
        // 身份名（阵营色）
        graphics.drawString(font, Component.translatable(roleDef.getTranslationKey()),
                x + 9, y + 19, accent, true);
        // 阵营标签
        String factionKey = isWolf ? "werewolf.hud.faction.wolf" : "werewolf.hud.faction.good";
        graphics.drawString(font, Component.translatable(factionKey),
                x + 9, y + 32, COLOR_DIM, true);
        // 死亡状态
        if (!WerewolfClientState.myAlive) {
            graphics.fill(x + 6, y + 44, x + w - 4, y + 45, 0x40FFFFFF);
            graphics.drawString(font, Component.translatable("werewolf.hud.dead"),
                    x + 9, y + 47, COLOR_DEAD, true);
        }

        // 预言家查验结果（仅预言家自己可见，私有包下发）
        if (WerewolfClientState.prophetResultSeat >= 0) {
            boolean isWolfResult = WerewolfClientState.prophetResultIsWolf;
            String key = isWolfResult ? "werewolf.hud.prophet_result_wolf" : "werewolf.hud.prophet_result_good";
            int resultColor = isWolfResult ? COLOR_WOLF : COLOR_GOOD;
            graphics.fill(x, y + h + 4, x + w, y + h + 20, COLOR_PANEL);
            graphics.drawString(font, Component.translatable(key, WerewolfClientState.prophetResultSeat),
                    x + 6, y + h + 9, resultColor, true);
        }
    }

    // ============================================================
    // 顶部中央：阶段横幅
    // ============================================================
    private static void renderPhaseBanner(GuiGraphics graphics, Font font, int screenWidth, Minecraft client) {
        WerewolfPhase phase = WerewolfClientState.phase;
        boolean isNight = phase.isNight();
        int accent = isNight ? COLOR_NIGHT_ACCENT : COLOR_DAY_ACCENT;

        // 夜晚阶段非行动者：隐藏真实阶段名与倒计时（防止通过阶段名/时长推断行动角色身份）
        boolean isWolfActing = phase == WerewolfPhase.NIGHT_WOLVES
                && WerewolfRoleDef.byId(WerewolfClientState.myRoleId).isWolf();
        boolean hideDetails = isNight && !WerewolfClientState.isMyTurn() && !isWolfActing;

        int centerX = screenWidth / 2;
        int bannerW = 210;
        int bx = centerX - bannerW / 2;
        int by = 4;
        int bh = 40;

        // 横幅面板 + 底部强调线 + 高光边
        graphics.fill(bx, by, bx + bannerW, by + bh, COLOR_PANEL);
        graphics.fill(bx, by, bx + bannerW, by + 1, COLOR_PANEL_EDGE);
        graphics.fill(bx, by + bh, bx + bannerW, by + bh + 2, accent);

        // 昼夜指示点（左侧小方块）
        graphics.fill(bx + 8, by + 7, bx + 13, by + 12, accent);

        // 阶段名：夜晚非行动者统一显示“等待其他玩家行动”，不暴露具体行动职业
        Component phaseName = hideDetails
                ? Component.translatable("werewolf.phase.waiting")
                : Component.translatable(phase.translationKey);
        graphics.drawCenteredString(font, phaseName, centerX + 4, by + 5, accent);
        // 轮次
        graphics.drawCenteredString(font,
                Component.translatable("werewolf.screen.round", WerewolfClientState.round),
                centerX + 4, by + 17, COLOR_DIM);

        // 倒计时进度条（隐藏阶段详情时一并隐藏，防止通过时长推断阶段）
        if (!hideDetails) {
            long currentTick = client.level != null ? client.level.getGameTime() : 0;
            float remaining = WerewolfClientState.getRemainingSeconds(currentTick);
            if (remaining >= 0) {
                int barW = bannerW - 16;
                int barX = bx + 8;
                int barY = by + bh - 8;
                float total = phase.durationTicks / 20.0f;
                float frac = total > 0 ? Math.min(1.0f, remaining / total) : 0;

                // 背景槽 + 填充
                graphics.fill(barX, barY, barX + barW, barY + 3, 0x90000000);
                int filledW = (int) (barW * frac);
                int barColor = remaining < 5 ? 0xFFFF5555 : accent;
                if (filledW > 0) {
                    graphics.fill(barX, barY, barX + filledW, barY + 3, barColor);
                }

                // 秒数（右侧横幅外沿）
                int timerColor = remaining < 5 ? 0xFFFF5555 : COLOR_TEXT;
                graphics.drawString(font, String.format("%.0fs", remaining),
                        bx + bannerW + 5, by + bh - 12, timerColor, true);
            }
        }
        // 行动者不对外显示（避免暴露当前行动角色身份）
    }

    // ============================================================
    // 右上角：玩家座位存活列表
    // ============================================================
    private static void renderPlayerList(GuiGraphics graphics, Font font, int screenWidth) {
        if (WerewolfClientState.totalSeats <= 0) {
            return;
        }

        int w = 96;
        int x = screenWidth - w - 8;
        int y = 8;
        int total = WerewolfClientState.totalSeats;
        int rowH = 13;
        int h = total * rowH + 21;

        // 面板
        graphics.fill(x, y, x + w, y + h, COLOR_PANEL);
        graphics.fill(x, y, x + w, y + 1, COLOR_PANEL_EDGE);
        graphics.fill(x, y + h - 1, x + w, y + h, COLOR_PANEL_EDGE);

        // 标题
        graphics.drawString(font, Component.translatable("werewolf.hud.players"),
                x + 6, y + 5, COLOR_TEXT, true);
        graphics.fill(x + 4, y + 15, x + w - 4, y + 16, 0x40FFFFFF);

        for (int seat = 1; seat <= total; seat++) {
            boolean alive = WerewolfClientState.aliveSeats.contains(seat);
            boolean isMe = seat == WerewolfClientState.mySeat;

            int lineY = y + 21 + (seat - 1) * rowH;

            // 头像（8x8，存活才显示彩色，死亡灰暗）
            var skin = WerewolfSeatButton.findSkin(WerewolfClientState.getSeatName(seat));
            if (skin != null) {
                if (!alive) {
                    // 死亡玩家头像变暗：覆盖半透明黑层
                    net.minecraft.client.gui.components.PlayerFaceRenderer.draw(
                            graphics, skin, x + 5, lineY - 1, 8);
                    graphics.fill(x + 5, lineY - 1, x + 13, lineY + 7, 0x90000000);
                } else {
                    net.minecraft.client.gui.components.PlayerFaceRenderer.draw(
                            graphics, skin, x + 5, lineY - 1, 8);
                }
            }

            int color;
            String text;
            if (!alive) {
                color = COLOR_DEAD;
                text = seat + " ✕";
            } else {
                color = isMe ? 0xFF55FFFF : COLOR_TEXT;
                text = String.valueOf(seat);
            }
            if (isMe) {
                text = text + " ★";
            }
            graphics.drawString(font, text, x + 17, lineY, color, true);
        }
    }

    // ============================================================
    // 底部中央：行动提示
    // ============================================================
    private static void renderActionHint(GuiGraphics graphics, Font font, int screenWidth, int screenHeight) {
        if (!WerewolfClientState.shouldOpenActionUI()) {
            return;
        }

        String hintKey;
        if (WerewolfClientState.shouldShowVoteUI()) {
            hintKey = "werewolf.hud.hint.vote";
        } else if (WerewolfClientState.shouldShowHunterUI()) {
            hintKey = "werewolf.hud.hint.hunter";
        } else if (WerewolfClientState.shouldShowWolfKingUI()) {
            hintKey = "werewolf.hud.hint.wolfking";
        } else {
            hintKey = "werewolf.hud.hint.action";
        }

        int centerX = screenWidth / 2;
        Component hint = Component.translatable(hintKey);
        Component keyHint = Component.translatable("werewolf.hud.hint.open_key");
        int hintW = font.width(hint);
        int keyW = font.width(keyHint);
        int pillW = Math.max(hintW, keyW) + 24;

        // 胶囊背景
        int pillY = screenHeight - 66;
        graphics.fill(centerX - pillW / 2, pillY - 5, centerX + pillW / 2, pillY + 27, 0xC0101018);
        graphics.fill(centerX - pillW / 2, pillY - 5, centerX + pillW / 2, pillY - 4, 0x55FFFFFF);

        // 闪烁主提示
        long time = System.currentTimeMillis();
        boolean blink = (time / 500) % 2 == 0;
        int color = blink ? 0xFF00FF00 : 0xFF00AA00;
        graphics.drawCenteredString(font, hint, centerX, pillY, color);
        graphics.drawCenteredString(font, keyHint, centerX, pillY + 13, COLOR_DIM);
    }
}
