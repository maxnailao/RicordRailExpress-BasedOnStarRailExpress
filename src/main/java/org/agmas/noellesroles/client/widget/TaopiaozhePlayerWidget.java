package org.agmas.noellesroles.client.widget;

import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;

import java.awt.Color;
import java.util.UUID;

/**
 * 逃票者背包内玩家头像控件
 * 展示已知晓阵营归属的玩家头像，头像边框颜色代表阵营，悬停显示玩家名与阵营
 */
public class TaopiaozhePlayerWidget extends Button {
    public final LimitedInventoryScreen screen;
    public final UUID targetUUID;
    public final PlayerInfo targetPlayerEntry;
    /** 阵营编码（见 TaopiaozhePlayerComponent.CAMP_*） */
    public final byte camp;

    public TaopiaozhePlayerWidget(LimitedInventoryScreen screen, int x, int y, UUID targetUUID,
            PlayerInfo targetPlayerEntry, byte camp) {
        super(x, y, 16, 16, Component.literal(""), (a) -> {
        }, DEFAULT_NARRATION);
        this.screen = screen;
        this.targetPlayerEntry = targetPlayerEntry;
        this.targetUUID = targetUUID;
        this.camp = camp;
    }

    @Override
    protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.renderWidget(context, mouseX, mouseY, delta);
        final var player = Minecraft.getInstance().player;
        if (player == null) return;
        if (targetPlayerEntry == null) return;

        // 检查皮肤贴图是否存在，避免空指针异常
        var skinTextures = targetPlayerEntry.getSkin();
        if (skinTextures == null || skinTextures.texture() == null) return;

        final var textRenderer = Minecraft.getInstance().font;
        if (textRenderer == null) return;

        // 阵营色边框
        final int borderColor = campColor(camp) | 0xFF000000;
        context.fill(this.getX() - 2, this.getY() - 2, this.getX() + 18, this.getY() - 1, borderColor);
        context.fill(this.getX() - 2, this.getY() + 17, this.getX() + 18, this.getY() + 18, borderColor);
        context.fill(this.getX() - 2, this.getY() - 1, this.getX() - 1, this.getY() + 17, borderColor);
        context.fill(this.getX() + 17, this.getY() - 1, this.getX() + 18, this.getY() + 17, borderColor);

        PlayerFaceRenderer.draw(context, skinTextures.texture(), this.getX(), this.getY(), 16);

        if (this.isHovered()) {
            this.drawSlotHighlight(context, this.getX(), this.getY(), 0);
            final var displayName = targetPlayerEntry.getProfile().getName();
            if (displayName != null) {
                context.renderTooltip(textRenderer,
                        Component.nullToEmpty(displayName).copy()
                                .append("\n")
                                .append(campText(camp)),
                        this.getX() - 4 - 10, this.getY() - 9);
            }
        }
    }

    private void drawSlotHighlight(GuiGraphics context, int x, int y, int z) {
        int color = -1862287543;
        context.fillGradient(RenderType.guiOverlay(), x, y, x + 16, y + 14, color, color, z);
        context.fillGradient(RenderType.guiOverlay(), x, y + 14, x + 15, y + 15, color, color, z);
        context.fillGradient(RenderType.guiOverlay(), x, y + 15, x + 14, y + 16, color, color, z);
    }

    /**
     * 阵营编码 -> 显示颜色（ARGB 低24位）
     */
    public static int campColor(byte camp) {
        return switch (camp) {
            case 1 -> new Color(255, 85, 85).getRGB() & 0xFFFFFF; // 杀手阵营：红
            case 2 -> new Color(255, 255, 85).getRGB() & 0xFFFFFF; // 中立阵营：黄
            default -> new Color(85, 255, 85).getRGB() & 0xFFFFFF; // 平民阵营：绿
        };
    }

    /**
     * 阵营编码 -> 翻译文本
     */
    public static Component campText(byte camp) {
        return switch (camp) {
            case 1 -> Component.translatable("hud.noellesroles.taopiaozhe.camp.killer");
            case 2 -> Component.translatable("hud.noellesroles.taopiaozhe.camp.neutral");
            default -> Component.translatable("hud.noellesroles.taopiaozhe.camp.innocent");
        };
    }
}
