package org.agmas.noellesroles.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.packet.PlushMakerCraftC2SPacket;

/**
 * 玩偶制作器界面。
 *
 * <p>输入「玩家名或 UUID」后点「制作」，客户端把输入发给服务端，
 * 由 {@code PlushMakerBlock} 对应的服务端逻辑解析该玩家档案并产出一个带
 * {@code DataComponents.PROFILE} 的自定义玩家玩偶（复用 {@code CustomPlayerPlushBlock}）。</p>
 */
public class PlushMakerScreen extends Screen {

    private EditBox idField;

    public PlushMakerScreen() {
        super(Component.translatable("screen.noellesroles.plush_maker.title"));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int fieldWidth = 200;

        this.idField = new EditBox(this.font, centerX - fieldWidth / 2, this.height / 2 - 24, fieldWidth, 20,
                Component.translatable("screen.noellesroles.plush_maker.hint"));
        this.idField.setMaxLength(PlushMakerCraftC2SPacket.MAX_INPUT_LENGTH);
        this.idField.setHint(Component.translatable("screen.noellesroles.plush_maker.hint"));
        this.addRenderableWidget(this.idField);

        this.addRenderableWidget(Button.builder(Component.translatable("screen.noellesroles.plush_maker.craft"),
                button -> this.submit())
                .bounds(centerX - fieldWidth / 2, this.height / 2 + 4, fieldWidth, 20)
                .build());

        this.setInitialFocus(this.idField);
    }

    /** 把输入发给服务端并关闭界面；空白输入直接忽略，不关闭界面。 */
    private void submit() {
        String value = this.idField == null ? "" : this.idField.getValue().trim();
        if (value.isEmpty()) {
            return;
        }
        ClientPlayNetworking.send(new PlushMakerCraftC2SPacket(value));
        this.onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 回车 = 制作，方便连续使用
        if (keyCode == 257 || keyCode == 335) { // GLFW_KEY_ENTER / GLFW_KEY_KP_ENTER
            this.submit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 56, 0xFFFFFF);
        graphics.drawCenteredString(this.font,
                Component.translatable("screen.noellesroles.plush_maker.tip"),
                this.width / 2, this.height / 2 - 42, 0xA0A0A0);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
