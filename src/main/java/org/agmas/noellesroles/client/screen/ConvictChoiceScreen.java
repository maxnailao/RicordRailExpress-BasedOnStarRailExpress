package org.agmas.noellesroles.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.packet.ConvictChoiceOpenS2CPacket;
import org.agmas.noellesroles.packet.ConvictChoiceSelectC2SPacket;

/**
 * 重刑犯「做出你的抉择」GUI（阶段 4）。
 *
 * <p>三个按钮：改过自新 / 毁灭一切 / 加入组织；顶部显示倒计时。点击后发送
 * {@link ConvictChoiceSelectC2SPacket} 并关闭界面；倒计时归零时服务端已默认「毁灭一切」，
 * 客户端亦自动关闭。界面不可用 ESC 关闭、不暂停游戏。</p>
 */
public class ConvictChoiceScreen extends Screen {

    private static final int PANEL_W = 360;
    private static final int PANEL_H = 200;
    private static final int BTN_W = 240;
    private static final int BTN_H = 24;
    private static final int BTN_GAP = 8;

    private final ConvictChoiceOpenS2CPacket payload;
    private int secondsLeft;
    private int tickCounter;
    private boolean submitted;

    public ConvictChoiceScreen(ConvictChoiceOpenS2CPacket payload) {
        super(Component.translatable("screen.noellesroles.convict_choice.title"));
        this.payload = payload;
        this.secondsLeft = payload.secondsLeft();
    }

    @Override
    protected void init() {
        int left = (width - PANEL_W) / 2;
        int top = (height - PANEL_H) / 2;
        int btnX = left + (PANEL_W - BTN_W) / 2;
        int y = top + 82;
        addRenderableWidget(choiceButton(btnX, y, 0, "screen.noellesroles.convict_choice.reform"));
        y += BTN_H + BTN_GAP;
        addRenderableWidget(choiceButton(btnX, y, 1, "screen.noellesroles.convict_choice.destroy"));
        y += BTN_H + BTN_GAP;
        addRenderableWidget(choiceButton(btnX, y, 2, "screen.noellesroles.convict_choice.join"));
    }

    private Button choiceButton(int x, int y, int index, String labelKey) {
        return Button.builder(Component.translatable(labelKey), b -> submit(index))
                .bounds(x, y, BTN_W, BTN_H).build();
    }

    private void submit(int index) {
        if (submitted) {
            return;
        }
        submitted = true;
        ClientPlayNetworking.send(new ConvictChoiceSelectC2SPacket(index));
        onClose();
    }

    @Override
    public void tick() {
        super.tick();
        tickCounter++;
        if (tickCounter % 20 == 0 && secondsLeft > 0) {
            secondsLeft--;
            // 倒计时归零：服务端已默认「毁灭一切」，客户端同步关闭界面
            if (secondsLeft <= 0 && !submitted) {
                submitted = true;
                onClose();
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int left = (width - PANEL_W) / 2;
        int top = (height - PANEL_H) / 2;
        int cx = left + PANEL_W / 2;

        // 面板底 + 描边
        g.fill(left, top, left + PANEL_W, top + PANEL_H, 0xEE140F1D);
        g.fill(left + 4, top + 4, left + PANEL_W - 4, top + PANEL_H - 4, 0xAA211626);
        g.renderOutline(left, top, PANEL_W, PANEL_H, 0xFF8B6914);

        // 标题 + 副标题
        g.drawCenteredString(font, title, cx, top + 20, 0xFFECCB79);
        g.drawCenteredString(font, Component.translatable("screen.noellesroles.convict_choice.subtitle"),
                cx, top + 38, 0xFFBBA86D);

        // 倒计时（临近结束闪烁变色，参照 RoleRotationScreen）
        int seconds = Math.max(0, secondsLeft);
        int color = seconds <= 10 ? (tickCounter % 20 < 10 ? 0xFFE06B65 : 0xFFFFA0A0)
                : seconds <= 30 ? 0xFFFFAA33 : 0xFF5EB7D8;
        Component timer = Component.translatable("screen.noellesroles.convict_choice.timer",
                String.format("%d:%02d", seconds / 60, seconds % 60));
        g.drawCenteredString(font, timer, cx, top + 58, color);

        // 最上层：按钮
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 不绘制全屏背景，交由 render 内的面板处理
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
