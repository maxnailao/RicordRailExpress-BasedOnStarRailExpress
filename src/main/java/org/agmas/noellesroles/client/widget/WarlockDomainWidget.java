package org.agmas.noellesroles.client.widget;

import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import io.wifi.starrailexpress.util.ShopEntry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.game.roles.killer.warlock.WarlockPlayerComponent;
import org.agmas.noellesroles.packet.WarlockDomainC2SPacket;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;

/**
 * 咒术师·领域展开目标选择组件。
 * 显示「已被诅咒且存活」的候选玩家头像；点击即请求对其展开领域（60s 冷却）。
 * 实际校验（角色 / 冷却 / 目标状态）在服务端接收端完成。
 */
public class WarlockDomainWidget extends Button {
    public final LimitedInventoryScreen screen;
    public final PlayerInfo targetPlayer;

    public WarlockDomainWidget(LimitedInventoryScreen screen, int x, int y,
            @NotNull PlayerInfo targetPlayer) {
        super(x, y, 16, 16, Component.literal(targetPlayer.getProfile().getName()), (button) -> {
            AbstractClientPlayer player = Minecraft.getInstance().player;
            if (player != null && isReady(player)) {
                ClientPlayNetworking.send(new WarlockDomainC2SPacket(targetPlayer.getProfile().getId()));
            }
        }, DEFAULT_NARRATION);
        this.screen = screen;
        this.targetPlayer = targetPlayer;
    }

    private static boolean isReady(AbstractClientPlayer player) {
        WarlockPlayerComponent comp = WarlockPlayerComponent.KEY.maybeGet(player).orElse(null);
        return comp != null && comp.domainCooldown <= 0;
    }

    @Override
    protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
        AbstractClientPlayer player = Minecraft.getInstance().player;
        if (player == null)
            return;
        WarlockPlayerComponent comp = WarlockPlayerComponent.KEY.maybeGet(player).orElse(null);
        if (comp == null)
            return;
        boolean ready = comp.domainCooldown <= 0;

        super.renderWidget(context, mouseX, mouseY, delta);
        if (!ready) {
            context.setColor(0.25f, 0.25f, 0.25f, 0.5f);
        }
        context.blitSprite(ShopEntry.Type.WEAPON.getTexture(), this.getX() - 7, this.getY() - 7, 30, 30);
        PlayerFaceRenderer.draw(context, targetPlayer.getSkin().texture(), this.getX(), this.getY(), 16);

        if (this.isHovered()) {
            drawSlotHighlight(context, this.getX(), this.getY());
            context.renderTooltip(Minecraft.getInstance().font,
                    Component.nullToEmpty(targetPlayer.getProfile().getName()),
                    this.getX() - 4 - Minecraft.getInstance().font.width(targetPlayer.getProfile().getName()) / 2,
                    this.getY() - 9);
        }
        context.setColor(1f, 1f, 1f, 1f);

        if (!ready) {
            int cooldownSeconds = (comp.domainCooldown + 19) / 20;
            context.drawString(Minecraft.getInstance().font, cooldownSeconds + "s",
                    this.getX(), this.getY(), Color.RED.getRGB(), true);
        }
    }

    private void drawSlotHighlight(GuiGraphics context, int x, int y) {
        int color = -1862287543;
        context.fillGradient(RenderType.guiOverlay(), x, y, x + 16, y + 14, color, color, 0);
        context.fillGradient(RenderType.guiOverlay(), x, y + 14, x + 15, y + 15, color, color, 0);
        context.fillGradient(RenderType.guiOverlay(), x, y + 15, x + 14, y + 16, color, color, 0);
    }

    @Override
    public void renderString(GuiGraphics context, Font textRenderer, int color) {
        // 空实现：只画头像与冷却角标
    }
}
