package org.agmas.noellesroles.client.widget;

import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import io.wifi.starrailexpress.util.ShopEntry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.roles.killer.mengyan.MengyanPlayerComponent;
import org.agmas.noellesroles.packet.MengyanC2SPacket;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * 梦魇恐惧目标选择组件
 * 在背包界面显示可选玩家头像，点击发送恐惧C2S包
 */
public class MengyanPlayerWidget extends Button {
    public final LimitedInventoryScreen screen;
    public final PlayerInfo targetPlayer;
    private Component displayText = Component.empty();
    private java.util.List<net.minecraft.util.FormattedCharSequence> cachedLines = new java.util.ArrayList<>();

    public MengyanPlayerWidget(LimitedInventoryScreen screen, int x, int y,
            @NotNull PlayerInfo targetPlayer) {
        super(x, y, 16, 16, Component.literal(targetPlayer.getProfile().getName()), (button) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player != null) {
                MengyanPlayerComponent comp = ModComponents.MENGYAN.get(client.player);
                if (!comp.fearActive) {
                    ClientPlayNetworking.send(new MengyanC2SPacket(targetPlayer.getProfile().getId()));
                }
            }
        }, DEFAULT_NARRATION);
        this.screen = screen;
        this.targetPlayer = targetPlayer;
    }

    @Override
    protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        MengyanPlayerComponent comp = ModComponents.MENGYAN.get(client.player);
        boolean canUse = !comp.fearActive;

        if (canUse) {
            super.renderWidget(context, mouseX, mouseY, delta);
            context.blitSprite(ShopEntry.Type.WEAPON.getTexture(), this.getX() - 7, this.getY() - 7, 30, 30);
            PlayerFaceRenderer.draw(context, targetPlayer.getSkin().texture(), this.getX(), this.getY(), 16);

            if (this.isHovered()) {
                this.drawShopSlotHighlight(context, this.getX(), this.getY(), 0);
                context.renderTooltip(client.font, Component.nullToEmpty(targetPlayer.getProfile().getName()),
                        this.getX() - 4 - client.font.width(targetPlayer.getProfile().getName()) / 2,
                        this.getY() - 9);
            }
        } else {
            super.renderWidget(context, mouseX, mouseY, delta);
            context.setColor(0.25f, 0.25f, 0.25f, 0.5f);
            context.blitSprite(ShopEntry.Type.WEAPON.getTexture(), this.getX() - 7, this.getY() - 7, 30, 30);
            PlayerFaceRenderer.draw(context, targetPlayer.getSkin().texture(), this.getX(), this.getY(), 16);

            if (this.isHovered()) {
                this.drawShopSlotHighlight(context, this.getX(), this.getY(), 0);
                context.renderTooltip(client.font, Component.nullToEmpty(targetPlayer.getProfile().getName()),
                        this.getX() - 4 - client.font.width(targetPlayer.getProfile().getName()) / 2,
                        this.getY() - 9);
            }

            context.setColor(1f, 1f, 1f, 1f);
        }

        renderDisplayText(context);
    }

    private void drawShopSlotHighlight(GuiGraphics context, int x, int y, int z) {
        int color = -1862287543;
        context.fillGradient(RenderType.guiOverlay(), x, y, x + 16, y + 14, color, color, 0);
        context.fillGradient(RenderType.guiOverlay(), x, y + 14, x + 15, y + 15, color, color, 0);
        context.fillGradient(RenderType.guiOverlay(), x, y + 15, x + 14, y + 16, color, color, 0);
    }

    public void setDisplayText(Component text) {
        this.displayText = text;
        this.cachedLines.clear();
    }

    private void renderDisplayText(GuiGraphics context) {
        if (displayText == null || displayText.getString().isEmpty()) {
            return;
        }

        Font font = Minecraft.getInstance().font;
        int maxWidth = 50;
        int lineHeight = font.lineHeight + 1;
        int yOffset = 4;

        if (cachedLines.isEmpty()) {
            cachedLines = font.split(displayText, maxWidth);
        }

        int startY = this.getY() + this.getHeight() + yOffset;

        for (int i = 0; i < cachedLines.size(); i++) {
            net.minecraft.util.FormattedCharSequence line = cachedLines.get(i);
            int lineWidth = font.width(line);
            int x = this.getX() + (this.getWidth() - lineWidth) / 2;
            int y = startY + (i * lineHeight);

            context.fill(x - 2, y - 1, x + lineWidth + 2, y + font.lineHeight + 1, 0x80000000);
            context.drawString(font, line, x, y, 0xFFFFFF, true);
        }
    }

    @Override
    public void renderString(GuiGraphics context, Font textRenderer, int color) {
        // 空实现，自定义文本渲染
    }
}
