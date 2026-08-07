package org.agmas.noellesroles.game.modes.werewolf.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.game.modes.werewolf.WerewolfPhase;
import org.agmas.noellesroles.game.modes.werewolf.WerewolfRoleDef;
import org.agmas.noellesroles.game.modes.werewolf.network.WerewolfVoteC2SPacket;

/**
 * 狼人杀投票界面 v2（白天放逐投票 / PK投票）
 * 面板式布局：标题区 + 座位按钮网格 + 弃票按钮 + 倒计时条 + 身份脚注
 * Author: jiale
 */
public class WerewolfVoteScreen extends Screen {

    private static final int PANEL_WIDTH = 320;
    private static final int BTN_COLS = 3;
    private static final int BTN_H = 20;
    private static final int BTN_GAP = 4;
    private static final int COLOR_DAY_ACCENT = 0xFFFFC94A;

    public WerewolfVoteScreen() {
        super(Component.translatable("werewolf.screen.vote.title"));
    }

    @Override
    protected void init() {
        super.init();
        rebuildButtons();
    }

    private int seatButtonCount() {
        int count = 0;
        for (Integer seat : WerewolfClientState.aliveSeats) {
            if (seat == WerewolfClientState.mySeat) continue;
            count++;
        }
        return count;
    }

    private int panelHeight() {
        int rows = (seatButtonCount() + BTN_COLS - 1) / BTN_COLS;
        // 标题区 52 + 座位行 + 弃票按钮 + 底部倒计时区 30
        return 52 + (rows + 1) * (BTN_H + BTN_GAP) + 34;
    }

    private void rebuildButtons() {
        clearWidgets();

        int centerX = this.width / 2;
        int panelX = centerX - PANEL_WIDTH / 2;
        int startY = (this.height - panelHeight()) / 2 + 52;

        int btnW = (PANEL_WIDTH - 20 - (BTN_COLS - 1) * BTN_GAP) / BTN_COLS;
        int index = 0;
        for (Integer seat : WerewolfClientState.aliveSeats) {
            // 不能投自己（服务端同样校验）
            if (seat == WerewolfClientState.mySeat) {
                continue;
            }
            int col = index % BTN_COLS;
            int row = index / BTN_COLS;
            int x = panelX + 10 + col * (btnW + BTN_GAP);
            int y = startY + row * (BTN_H + BTN_GAP);
            final int targetSeat = seat;
            WerewolfSeatButton btn = new WerewolfSeatButton(x, y, btnW, BTN_H, targetSeat,
                    WerewolfClientState.getSeatName(targetSeat), b -> {
                        ClientPlayNetworking.send(new WerewolfVoteC2SPacket(targetSeat));
                        onClose();
                    });
            addRenderableWidget(btn);
            index++;
        }

        // 弃票按钮
        int skipY = startY + ((index + BTN_COLS - 1) / BTN_COLS) * (BTN_H + BTN_GAP);
        Button abstainBtn = Button.builder(Component.translatable("werewolf.screen.vote.abstain"), b -> {
            ClientPlayNetworking.send(new WerewolfVoteC2SPacket(-1));
            onClose();
        }).bounds(panelX + 10, skipY, PANEL_WIDTH - 20, BTN_H).build();
        addRenderableWidget(abstainBtn);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 全屏白天色调渐变背景
        graphics.fillGradient(0, 0, this.width, this.height, 0xE82A2416, 0xE83A3220);
        super.render(graphics, mouseX, mouseY, partialTick);

        boolean isPkVote = WerewolfClientState.phase == WerewolfPhase.DAY_VOTE_PK_RESULT;
        int centerX = this.width / 2;
        int panelX = centerX - PANEL_WIDTH / 2;
        int panelH = panelHeight();
        int panelY = (this.height - panelH) / 2;

        // === 面板容器 ===
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + panelH, 0xF0201C10);
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + 2, COLOR_DAY_ACCENT);
        graphics.fill(panelX, panelY + panelH - 1, panelX + PANEL_WIDTH, panelY + panelH, 0x55FFFFFF);
        graphics.fill(panelX, panelY, panelX + 1, panelY + panelH, 0x30FFFFFF);
        graphics.fill(panelX + PANEL_WIDTH - 1, panelY, panelX + PANEL_WIDTH, panelY + panelH, 0x30FFFFFF);

        // === 标题区 ===
        graphics.fill(panelX + 12, panelY + 11, panelX + 17, panelY + 16, COLOR_DAY_ACCENT);
        Component title = isPkVote
                ? Component.translatable("werewolf.screen.vote.pk_title")
                : Component.translatable("werewolf.screen.vote.title");
        graphics.drawString(this.font, title, panelX + 23, panelY + 10, COLOR_DAY_ACCENT, true);
        graphics.drawString(this.font, Component.translatable("werewolf.screen.vote.hint"),
                panelX + 12, panelY + 26, 0xFFCCCCCC, true);
        graphics.fill(panelX + 10, panelY + 42, panelX + PANEL_WIDTH - 10, panelY + 43, 0x30FFFFFF);

        // === 底部：倒计时进度条 ===
        var mc = net.minecraft.client.Minecraft.getInstance();
        long currentTick = mc.level != null ? mc.level.getGameTime() : 0;
        float remaining = WerewolfClientState.getRemainingSeconds(currentTick);
        int barY = panelY + panelH - 22;
        if (remaining >= 0) {
            int barW = PANEL_WIDTH - 70;
            float total = WerewolfClientState.phase.durationTicks / 20.0f;
            float frac = total > 0 ? Math.min(1.0f, remaining / total) : 0;

            graphics.fill(panelX + 12, barY, panelX + 12 + barW, barY + 4, 0x90000000);
            int filledW = (int) (barW * frac);
            int barColor = remaining < 5 ? 0xFFFF5555 : COLOR_DAY_ACCENT;
            if (filledW > 0) {
                graphics.fill(panelX + 12, barY, panelX + 12 + filledW, barY + 4, barColor);
            }
            int timerColor = remaining < 5 ? 0xFFFF5555 : 0xFFFFFFFF;
            graphics.drawString(this.font, String.format("%.0fs", remaining),
                    panelX + PANEL_WIDTH - 50, barY - 3, timerColor, true);
        }

        // === 轮次 + 身份脚注（左下） ===
        graphics.drawString(this.font,
                Component.translatable("werewolf.screen.round", WerewolfClientState.round),
                8, this.height - 26, 0xFF888888, true);
        if (WerewolfClientState.mySeat >= 0) {
            WerewolfRoleDef roleDef = WerewolfRoleDef.byId(WerewolfClientState.myRoleId);
            Component footer = Component.translatable("werewolf.screen.my_identity",
                    WerewolfClientState.mySeat,
                    Component.translatable(roleDef.getTranslationKey()));
            graphics.drawString(this.font, footer, 8, this.height - 14, 0xFF888888, true);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
