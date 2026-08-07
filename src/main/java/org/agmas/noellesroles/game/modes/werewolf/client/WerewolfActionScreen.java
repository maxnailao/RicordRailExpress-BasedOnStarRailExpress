package org.agmas.noellesroles.game.modes.werewolf.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.game.modes.werewolf.WerewolfPhase;
import org.agmas.noellesroles.game.modes.werewolf.WerewolfRoleDef;
import org.agmas.noellesroles.game.modes.werewolf.network.WerewolfActionC2SPacket;

/**
 * 狼人杀行动界面 v2（夜晚行动 / 猎人开枪 / 白狼王带人）
 * 面板式布局：标题区 + 座位按钮网格 + 跳过按钮 + 倒计时条 + 身份脚注
 * Author: jiale
 */
public class WerewolfActionScreen extends Screen {
    /** 炼药师操作模式：0=未选择, 1=解药, 2=毒药 */
    private int alchemistMode = 0;

    private static final int PANEL_WIDTH = 320;
    private static final int BTN_COLS = 3;
    private static final int BTN_H = 20;
    private static final int BTN_GAP = 4;

    public WerewolfActionScreen() {
        super(Component.translatable("werewolf.screen.action.title"));
    }

    @Override
    protected void init() {
        super.init();
        rebuildButtons();
    }

    /**
     * 当前阶段的强调色
     */
    private int accentColor() {
        WerewolfPhase phase = WerewolfClientState.phase;
        if (phase.isNight()) return 0xFF7B68EE;         // 夜晚紫蓝
        if (phase == WerewolfPhase.DAY_EXECUTE) return 0xFFFF5555; // 白狼王红
        return 0xFFFFC94A;                               // 猎人金黄
    }

    /**
     * 面板高度估算（供绘制背景使用）
     */
    private int panelHeight() {
        WerewolfPhase phase = WerewolfClientState.phase;
        boolean isAlchemistPhase = phase == WerewolfPhase.NIGHT_ALCHEMIST
                && alchemistMode == 0;
        int contentRows;
        if (isAlchemistPhase) {
            contentRows = 3; // 解药 + 毒药 + 跳过
        } else if (phase == WerewolfPhase.DAY_SPEECH) {
            contentRows = 1; // 结束发言按钮
        } else {
            int seatCount = seatButtonCount();
            int rows = (seatCount + BTN_COLS - 1) / BTN_COLS;
            contentRows = rows + 1; // 座位行 + 跳过按钮
        }
        // 标题区 52 + 炼药师受害者提示行 + 内容区 + 底部倒计时区 30
        int extra = (phase == WerewolfPhase.NIGHT_ALCHEMIST
                && WerewolfClientState.alchemistVictimSeat >= 0) ? 14 : 0;
        return 52 + extra + contentRows * (BTN_H + BTN_GAP) + 34;
    }

    private int seatButtonCount() {
        int count = 0;
        for (Integer seat : WerewolfClientState.aliveSeats) {
            if (seat == WerewolfClientState.mySeat && !canTargetSelf()) continue;
            count++;
        }
        return count;
    }

    private void rebuildButtons() {
        clearWidgets();

        WerewolfPhase phase = WerewolfClientState.phase;
        boolean isAlchemistPhase = phase == WerewolfPhase.NIGHT_ALCHEMIST;

        int centerX = this.width / 2;
        int panelX = centerX - PANEL_WIDTH / 2;
        int extra = (isAlchemistPhase && WerewolfClientState.alchemistVictimSeat >= 0) ? 14 : 0;
        int startY = (this.height - panelHeight()) / 2 + 52 + extra;

        // 发言阶段：仅提供“结束发言”按钮（发言者跳过自己的回合）
        if (phase == WerewolfPhase.DAY_SPEECH) {
            Button endSpeechBtn = Button.builder(
                    Component.translatable("werewolf.screen.action.end_speech"), b -> {
                        ClientPlayNetworking.send(new WerewolfActionC2SPacket(-1, (byte) 0));
                        onClose();
                    }).bounds(panelX + 10, startY, PANEL_WIDTH - 20, BTN_H).build();
            addRenderableWidget(endSpeechBtn);
            return;
        }

        // 炼药师：未选择模式时显示 解药/毒药/跳过
        if (isAlchemistPhase && alchemistMode == 0) {
            boolean antidoteUsed = WerewolfClientState.usedAntidote;
            boolean poisonUsed = WerewolfClientState.usedPoison;

            Component antidoteLabel = antidoteUsed
                    ? Component.translatable("werewolf.screen.action.antidote_used")
                    : Component.translatable("werewolf.screen.action.use_antidote");
            Button saveBtn = Button.builder(antidoteLabel, b -> {
                ClientPlayNetworking.send(new WerewolfActionC2SPacket(-1, (byte) 1));
                onClose();
            }).bounds(panelX + 10, startY, PANEL_WIDTH - 20, BTN_H).build();
            saveBtn.active = !antidoteUsed;
            addRenderableWidget(saveBtn);

            Component poisonLabel = poisonUsed
                    ? Component.translatable("werewolf.screen.action.poison_used")
                    : Component.translatable("werewolf.screen.action.use_poison");
            Button poisonBtn = Button.builder(poisonLabel, b -> {
                alchemistMode = 2;
                rebuildButtons();
            }).bounds(panelX + 10, startY + BTN_H + BTN_GAP, PANEL_WIDTH - 20, BTN_H).build();
            poisonBtn.active = !poisonUsed;
            addRenderableWidget(poisonBtn);

            addSkipButton(panelX, startY + 2 * (BTN_H + BTN_GAP), PANEL_WIDTH - 20);
            return;
        }

        // 普通目标选择（守护/查验/骑士/狼方/猎人/白狼王/炼药师选毒药目标）
        addSeatButtons(panelX, startY);
    }

    private void addSeatButtons(int panelX, int startY) {
        int btnW = (PANEL_WIDTH - 20 - (BTN_COLS - 1) * BTN_GAP) / BTN_COLS;
        int index = 0;
        for (Integer seat : WerewolfClientState.aliveSeats) {
            if (seat == WerewolfClientState.mySeat && !canTargetSelf()) {
                continue;
            }
            int col = index % BTN_COLS;
            int row = index / BTN_COLS;
            int x = panelX + 10 + col * (btnW + BTN_GAP);
            int y = startY + row * (BTN_H + BTN_GAP);
            final int targetSeat = seat;
            WerewolfSeatButton btn = new WerewolfSeatButton(x, y, btnW, BTN_H, targetSeat,
                    WerewolfClientState.getSeatName(targetSeat), b -> {
                        byte actionType = (byte) alchemistMode;
                        ClientPlayNetworking.send(new WerewolfActionC2SPacket(targetSeat, actionType));
                        onClose();
                    });
            addRenderableWidget(btn);
            index++;
        }

        // 跳过/不行动按钮
        int skipY = startY + ((index + BTN_COLS - 1) / BTN_COLS) * (BTN_H + BTN_GAP);
        addSkipButton(panelX, skipY, PANEL_WIDTH - 20);
    }

    private void addSkipButton(int panelX, int y, int w) {
        Button skipBtn = Button.builder(Component.translatable("werewolf.screen.action.skip"), b -> {
            ClientPlayNetworking.send(new WerewolfActionC2SPacket(-1, (byte) 0));
            onClose();
        }).bounds(panelX + 10, y, w, BTN_H).build();
        addRenderableWidget(skipBtn);
    }

    private boolean canTargetSelf() {
        // 狼方不能投票给自己；白狼王不能带自己（自己已被淘汰）；其余角色允许选择自己（如守护自己）
        return WerewolfClientState.phase != WerewolfPhase.NIGHT_WOLVES
                && WerewolfClientState.phase != WerewolfPhase.DAY_EXECUTE;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 全屏深色渐变背景
        graphics.fillGradient(0, 0, this.width, this.height, 0xE80A0A12, 0xE81A1A28);
        super.render(graphics, mouseX, mouseY, partialTick);

        int accent = accentColor();
        int centerX = this.width / 2;
        int panelX = centerX - PANEL_WIDTH / 2;
        int panelH = panelHeight();
        int panelY = (this.height - panelH) / 2;

        // === 面板容器 ===
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + panelH, 0xF0141420);
        // 边框：顶部强调色粗线 + 其余 1px 高光
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + 2, accent);
        graphics.fill(panelX, panelY + panelH - 1, panelX + PANEL_WIDTH, panelY + panelH, 0x55FFFFFF);
        graphics.fill(panelX, panelY, panelX + 1, panelY + panelH, 0x30FFFFFF);
        graphics.fill(panelX + PANEL_WIDTH - 1, panelY, panelX + PANEL_WIDTH, panelY + panelH, 0x30FFFFFF);

        // === 标题区 ===
        WerewolfPhase phase = WerewolfClientState.phase;
        // 阶段指示点
        graphics.fill(panelX + 12, panelY + 11, panelX + 17, panelY + 16, accent);
        // 阶段名（标题）
        graphics.drawString(this.font, Component.translatable(phase.translationKey),
                panelX + 23, panelY + 10, accent, true);
        // 说明文字
        graphics.drawString(this.font, getSubtitle(phase),
                panelX + 12, panelY + 26, 0xFFAAAAAA, true);
        // 炼药师：昨夜受害者提示行
        if (phase == WerewolfPhase.NIGHT_ALCHEMIST
                && WerewolfClientState.alchemistVictimSeat >= 0) {
            graphics.drawString(this.font,
                    Component.translatable("werewolf.msg.alchemist_victim",
                            WerewolfClientState.alchemistVictimSeat),
                    panelX + 12, panelY + 40, 0xFFDD88FF, true);
        }
        // 分隔线
        graphics.fill(panelX + 10, panelY + 42, panelX + PANEL_WIDTH - 10, panelY + 43, 0x30FFFFFF);

        // === 底部：倒计时进度条 ===
        var mc = net.minecraft.client.Minecraft.getInstance();
        long currentTick = mc.level != null ? mc.level.getGameTime() : 0;
        float remaining = WerewolfClientState.getRemainingSeconds(currentTick);
        int barY = panelY + panelH - 22;
        if (remaining >= 0) {
            int barW = PANEL_WIDTH - 70;
            float total = phase.durationTicks / 20.0f;
            float frac = total > 0 ? Math.min(1.0f, remaining / total) : 0;

            graphics.fill(panelX + 12, barY, panelX + 12 + barW, barY + 4, 0x90000000);
            int filledW = (int) (barW * frac);
            int barColor = remaining < 5 ? 0xFFFF5555 : accent;
            if (filledW > 0) {
                graphics.fill(panelX + 12, barY, panelX + 12 + filledW, barY + 4, barColor);
            }
            int timerColor = remaining < 5 ? 0xFFFF5555 : 0xFFFFFFFF;
            graphics.drawString(this.font, String.format("%.0fs", remaining),
                    panelX + PANEL_WIDTH - 50, barY - 3, timerColor, true);
        }

        // === 身份脚注（左下） ===
        if (WerewolfClientState.mySeat >= 0) {
            WerewolfRoleDef roleDef = WerewolfRoleDef.byId(WerewolfClientState.myRoleId);
            Component footer = Component.translatable("werewolf.screen.my_identity",
                    WerewolfClientState.mySeat,
                    Component.translatable(roleDef.getTranslationKey()));
            graphics.drawString(this.font, footer, 8, this.height - 14, 0xFF888888, true);
        }
    }

    private Component getSubtitle(WerewolfPhase phase) {
        return switch (phase) {
            case NIGHT_GUARDIAN -> Component.translatable("werewolf.screen.action.guardian_hint");
            case NIGHT_WOLVES -> Component.translatable("werewolf.screen.action.wolf_hint");
            case NIGHT_ALCHEMIST -> alchemistMode == 2
                    ? Component.translatable("werewolf.screen.action.poison_hint")
                    : Component.translatable("werewolf.screen.action.alchemist_hint");
            case NIGHT_PROPHET -> Component.translatable("werewolf.screen.action.prophet_hint");
            case NIGHT_KNIGHT -> Component.translatable("werewolf.screen.action.knight_hint");
            case DAY_HUNTER_SHOT -> Component.translatable("werewolf.screen.action.hunter_hint");
            case DAY_EXECUTE -> Component.translatable("werewolf.screen.action.wolfking_hint");
            case DAY_SPEECH -> Component.translatable("werewolf.screen.action.speech_hint");
            default -> Component.empty();
        };
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
