package io.wifi.starrailexpress.client.gui.screen;

import io.wifi.starrailexpress.client.emote.EmoteClientHandler;
import io.wifi.starrailexpress.client.emote.EmoteLoadout;
import io.wifi.starrailexpress.emote.EmoteType;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 表情罗盘界面：8 个槽位环形排布，点击/数字键 1-8 播放对应表情。
 * 世界保持可见（半透明背景），播放后自动关闭；打开设置页可调整槽位装配。
 */
@Environment(EnvType.CLIENT)
public class EmoteCompassScreen extends Screen {

    /** 槽位环半径（像素） */
    private static final float RING_RADIUS = 74.0F;
    /** 槽位圆形半径（像素） */
    private static final int SLOT_RADIUS = 20;

    private static final int COLOR_HUB = 0xE0202028;
    private static final int COLOR_SLOT = 0xC82A2A33;
    private static final int COLOR_SLOT_HOVER = 0xE04A4A5A;
    private static final int COLOR_RING = 0xFFD8D8E8;
    private static final int COLOR_ICON = 0xFFEEEEF4;
    private static final int COLOR_ICON_EMPTY = 0x77888899;

    private int hoveredSlot = -1;

    public EmoteCompassScreen() {
        super(Component.translatable("screen.starrailexpress.emote_compass"));
    }

    @Override
    protected void init() {
        // 打开装配设置页
        this.addRenderableWidget(Button.builder(
                Component.translatable("screen.starrailexpress.emote_loadout"),
                button -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new EmoteLoadoutScreen());
                    }
                })
                .bounds(this.width / 2 - 60, this.height - 36, 120, 20)
                .build());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 罗盘为悬浮界面：保持世界可见，仅压暗
        this.renderTransparentBackground(guiGraphics);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        hoveredSlot = hitTestSlot(mouseX, mouseY);

        // 中心圆盘与标题
        fillCircle(guiGraphics, centerX, centerY, 26, COLOR_HUB);
        guiGraphics.drawCenteredString(this.font, this.title, centerX, centerY - 32 - 10, 0xFFD8D8E8);
        String centerText = hoveredSlot >= 0 && EmoteLoadout.get(hoveredSlot) != null
                ? Component.translatable(EmoteLoadout.get(hoveredSlot).translationKey()).getString()
                : Component.translatable("screen.starrailexpress.emote_compass.hint").getString();
        guiGraphics.drawCenteredString(this.font, centerText, centerX, centerY - 4, 0xFFEEEEF4);

        // 8 个槽位
        for (int i = 0; i < EmoteLoadout.SLOT_COUNT; i++) {
            int slotX = slotX(i);
            int slotY = slotY(i);
            EmoteType emote = EmoteLoadout.get(i);
            boolean hovered = hoveredSlot == i;

            if (hovered && emote != null) {
                fillCircle(guiGraphics, slotX, slotY, SLOT_RADIUS + 2, COLOR_RING);
            }
            fillCircle(guiGraphics, slotX, slotY, SLOT_RADIUS, hovered ? COLOR_SLOT_HOVER : COLOR_SLOT);

            // 图标：取本地化名称首字符，兼容任意语言
            if (emote != null) {
                String icon = iconOf(emote);
                guiGraphics.drawString(this.font, icon,
                        slotX - this.font.width(icon) / 2, slotY - 4, COLOR_ICON, false);
            } else {
                guiGraphics.drawCenteredString(this.font, "·", slotX, slotY - 4, COLOR_ICON_EMPTY);
            }
            // 槽位序号提示（数字键快捷播放）
            guiGraphics.drawString(this.font, String.valueOf(i + 1),
                    slotX + SLOT_RADIUS - 5, slotY + SLOT_RADIUS - 8, 0x99AAAACC, false);
        }

        // 底部操作提示
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("screen.starrailexpress.emote_compass.tip"),
                centerX, centerY + (int) RING_RADIUS + 26, 0xAA9999AA);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 先交给父类分发（否则底部“表情装配”按钮收不到点击）
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0) {
            int slot = hitTestSlot((int) mouseX, (int) mouseY);
            if (slot >= 0) {
                EmoteType emote = EmoteLoadout.get(slot);
                if (emote != null) {
                    EmoteClientHandler.requestPlay(emote);
                }
                this.onClose();
                return true;
            }
            // 点击罗盘外空白处关闭
            this.onClose();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 再按一次热键关闭
        if (EmoteClientHandler.emoteCompassKeybind != null
                && EmoteClientHandler.emoteCompassKeybind.matches(keyCode, scanCode)) {
            this.onClose();
            return true;
        }
        // 数字键 1-8 快捷播放
        if (keyCode >= org.lwjgl.glfw.GLFW.GLFW_KEY_1 && keyCode <= org.lwjgl.glfw.GLFW.GLFW_KEY_8) {
            int slot = keyCode - org.lwjgl.glfw.GLFW.GLFW_KEY_1;
            EmoteType emote = EmoteLoadout.get(slot);
            if (emote != null) {
                EmoteClientHandler.requestPlay(emote);
            }
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private int hitTestSlot(int mouseX, int mouseY) {
        for (int i = 0; i < EmoteLoadout.SLOT_COUNT; i++) {
            int dx = mouseX - slotX(i);
            int dy = mouseY - slotY(i);
            if (dx * dx + dy * dy <= (SLOT_RADIUS + 2) * (SLOT_RADIUS + 2)) {
                return i;
            }
        }
        return -1;
    }

    private int slotX(int i) {
        return this.width / 2 + (int) (Math.cos(slotAngle(i)) * RING_RADIUS);
    }

    private int slotY(int i) {
        return this.height / 2 + (int) (Math.sin(slotAngle(i)) * RING_RADIUS);
    }

    /** 槽位角度：第一个槽位位于正上方，顺时针分布 */
    private static double slotAngle(int i) {
        return -Math.PI / 2 + i * (Math.PI * 2 / EmoteLoadout.SLOT_COUNT);
    }

    private static String iconOf(EmoteType emote) {
        String name = Component.translatable(emote.translationKey()).getString();
        return name.isEmpty() ? "?" : name.substring(0, Math.min(2, name.length()));
    }

    /**
     * 扫描线法绘制实心圆（每行一次 fill，避免逐像素绘制）
     */
    private static void fillCircle(GuiGraphics guiGraphics, int cx, int cy, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int dx = (int) Math.sqrt((double) radius * radius - (double) dy * dy);
            guiGraphics.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }
}
