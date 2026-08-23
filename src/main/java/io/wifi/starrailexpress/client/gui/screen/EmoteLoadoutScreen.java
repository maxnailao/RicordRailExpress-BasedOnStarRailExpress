package io.wifi.starrailexpress.client.gui.screen;

import io.wifi.starrailexpress.client.emote.EmoteLoadout;
import io.wifi.starrailexpress.emote.EmoteType;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 表情装配设置界面：
 * <ul>
 *   <li>左键点击槽位选中，再点击下方表情即装配到该槽位；未选中时自动装配到第一个空槽</li>
 *   <li>右键点击槽位卸下表情</li>
 *   <li>关闭界面时自动保存到 config/emote_loadout.json</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public class EmoteLoadoutScreen extends Screen {

    private static final int SLOT_SIZE = 40;
    private static final int SLOT_GAP = 8;

    private static final int COLOR_SLOT_BG = 0xC82A2A33;
    private static final int COLOR_SLOT_SELECTED = 0xE04A4A5A;
    private static final int COLOR_BORDER = 0xFF888899;
    private static final int COLOR_BORDER_SELECTED = 0xFFFFD966;
    private static final int COLOR_ICON = 0xFFEEEEF4;

    /** 当前选中的装配槽位，-1 表示未选中 */
    private int selectedSlot = -1;
    /** 悬停目标：>=0 为槽位索引，100+ 为表情列表索引 */
    private int hoverTarget = -1;

    public EmoteLoadoutScreen() {
        super(Component.translatable("screen.starrailexpress.emote_loadout"));
    }

    @Override
    protected void init() {
        int buttonY = this.height - 32;
        this.addRenderableWidget(Button.builder(
                Component.translatable("screen.starrailexpress.emote_loadout.reset"),
                button -> {
                    EmoteLoadout.resetDefaults();
                    this.selectedSlot = -1;
                })
                .bounds(this.width / 2 - 126, buttonY, 120, 20)
                .build());
        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.done"),
                button -> this.onClose())
                .bounds(this.width / 2 + 6, buttonY, 120, 20)
                .build());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        EmoteLoadout.save();
        // 返回表情罗盘
        if (this.minecraft != null) {
            this.minecraft.setScreen(new EmoteCompassScreen());
            return;
        }
        super.onClose();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        guiGraphics.drawCenteredString(this.font, this.title, centerX, 16, 0xFFD8D8E8);
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("screen.starrailexpress.emote_loadout.hint"),
                centerX, 30, 0xAA9999AA);

        hoverTarget = hitTest(mouseX, mouseY);

        // 装配槽位区
        int slotsWidth = EmoteLoadout.SLOT_COUNT * (SLOT_SIZE + SLOT_GAP) - SLOT_GAP;
        int slotsX = centerX - slotsWidth / 2;
        int slotsY = 56;
        guiGraphics.drawString(this.font,
                Component.translatable("screen.starrailexpress.emote_loadout.slots"),
                slotsX, slotsY - 12, 0xFFBBBBCC, false);
        for (int i = 0; i < EmoteLoadout.SLOT_COUNT; i++) {
            int x = slotsX + i * (SLOT_SIZE + SLOT_GAP);
            boolean selected = selectedSlot == i;
            boolean hovered = hoverTarget == i;
            guiGraphics.fill(x, slotsY, x + SLOT_SIZE, slotsY + SLOT_SIZE,
                    selected ? COLOR_SLOT_SELECTED : (hovered ? 0xD8383845 : COLOR_SLOT_BG));
            guiGraphics.renderOutline(x, slotsY, SLOT_SIZE, SLOT_SIZE,
                    selected ? COLOR_BORDER_SELECTED : COLOR_BORDER);
            EmoteType emote = EmoteLoadout.get(i);
            if (emote != null) {
                String icon = iconOf(emote);
                guiGraphics.drawString(this.font, icon,
                        x + SLOT_SIZE / 2 - this.font.width(icon) / 2,
                        slotsY + SLOT_SIZE / 2 - 4, COLOR_ICON, false);
            }
            guiGraphics.drawString(this.font, String.valueOf(i + 1),
                    x + SLOT_SIZE - 7, slotsY + SLOT_SIZE - 10, 0x99AAAACC, false);
        }

        // 全部表情区（默认全部解锁）
        EmoteType[] all = EmoteType.values();
        int listWidth = all.length * (SLOT_SIZE + SLOT_GAP) - SLOT_GAP;
        int listX = centerX - listWidth / 2;
        int listY = slotsY + SLOT_SIZE + 44;
        guiGraphics.drawString(this.font,
                Component.translatable("screen.starrailexpress.emote_loadout.all"),
                listX, listY - 12, 0xFFBBBBCC, false);
        for (int i = 0; i < all.length; i++) {
            int x = listX + i * (SLOT_SIZE + SLOT_GAP);
            boolean hovered = hoverTarget == 100 + i;
            guiGraphics.fill(x, listY, x + SLOT_SIZE, listY + SLOT_SIZE,
                    hovered ? 0xD8383845 : COLOR_SLOT_BG);
            guiGraphics.renderOutline(x, listY, SLOT_SIZE, SLOT_SIZE, COLOR_BORDER);
            String icon = iconOf(all[i]);
            guiGraphics.drawString(this.font, icon,
                    x + SLOT_SIZE / 2 - this.font.width(icon) / 2,
                    listY + SLOT_SIZE / 2 - 4, COLOR_ICON, false);
            // 表情全名
            String name = Component.translatable(all[i].translationKey()).getString();
            guiGraphics.drawCenteredString(this.font, name, x + SLOT_SIZE / 2, listY + SLOT_SIZE + 4, 0xFFCCCCDD);
        }

        // 悬停提示
        if (hoverTarget >= 100) {
            EmoteType emote = all[hoverTarget - 100];
            guiGraphics.drawCenteredString(this.font,
                    Component.translatable("screen.starrailexpress.emote_loadout.assign",
                            Component.translatable(emote.translationKey())),
                    centerX, listY + SLOT_SIZE + 20, 0xFFD9B366);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int target = hitTest((int) mouseX, (int) mouseY);
        if (button == 0) {
            if (target >= 0 && target < EmoteLoadout.SLOT_COUNT) {
                // 选中槽位（再点一次取消选中）
                this.selectedSlot = this.selectedSlot == target ? -1 : target;
                return true;
            }
            if (target >= 100) {
                EmoteType emote = EmoteType.values()[target - 100];
                int slot = selectedSlot >= 0 ? selectedSlot : EmoteLoadout.firstEmptySlot();
                if (slot >= 0) {
                    EmoteLoadout.set(slot, emote);
                }
                return true;
            }
        } else if (button == 1 && target >= 0 && target < EmoteLoadout.SLOT_COUNT) {
            // 右键卸下
            EmoteLoadout.set(target, null);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private int hitTest(int mouseX, int mouseY) {
        int centerX = this.width / 2;
        int slotsWidth = EmoteLoadout.SLOT_COUNT * (SLOT_SIZE + SLOT_GAP) - SLOT_GAP;
        int slotsX = centerX - slotsWidth / 2;
        int slotsY = 56;
        for (int i = 0; i < EmoteLoadout.SLOT_COUNT; i++) {
            int x = slotsX + i * (SLOT_SIZE + SLOT_GAP);
            if (mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= slotsY && mouseY < slotsY + SLOT_SIZE) {
                return i;
            }
        }
        EmoteType[] all = EmoteType.values();
        int listWidth = all.length * (SLOT_SIZE + SLOT_GAP) - SLOT_GAP;
        int listX = centerX - listWidth / 2;
        int listY = slotsY + SLOT_SIZE + 44;
        for (int i = 0; i < all.length; i++) {
            int x = listX + i * (SLOT_SIZE + SLOT_GAP);
            if (mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= listY && mouseY < listY + SLOT_SIZE) {
                return 100 + i;
            }
        }
        return -1;
    }

    private static String iconOf(EmoteType emote) {
        String name = Component.translatable(emote.translationKey()).getString();
        return name.isEmpty() ? "?" : name.substring(0, Math.min(2, name.length()));
    }
}

