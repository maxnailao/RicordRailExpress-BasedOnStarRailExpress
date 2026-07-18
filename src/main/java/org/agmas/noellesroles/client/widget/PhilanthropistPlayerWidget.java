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
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.game.roles.innocence.philanthropist.PhilanthropistPlayerComponent;
import org.agmas.noellesroles.packet.PhilanthropistC2SPacket;

import java.awt.*;
import java.util.UUID;

public class PhilanthropistPlayerWidget extends Button {
    public final LimitedInventoryScreen screen;
    public final UUID targetUUID;
    public final PlayerInfo targetPlayerEntry;

    public PhilanthropistPlayerWidget(LimitedInventoryScreen screen, int x, int y, UUID targetUUID,
            PlayerInfo targetPlayerEntry) {
        super(x, y, 16, 16, Component.literal(""), (a) -> {
            ClientPlayNetworking.send(new PhilanthropistC2SPacket(targetUUID));
        }, DEFAULT_NARRATION);
        this.screen = screen;
        this.targetPlayerEntry = targetPlayerEntry;
        this.targetUUID = targetUUID;
    }

    @Override
    protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.renderWidget(context, mouseX, mouseY, delta);
        final var player = Minecraft.getInstance().player;
        if (player == null) return;
        if (targetPlayerEntry == null) return;

        final var philanthropistComp = PhilanthropistPlayerComponent.KEY.get(player);
        if (philanthropistComp == null) return;

        var skinTextures = targetPlayerEntry.getSkin();
        if (skinTextures == null || skinTextures.texture() == null) return;

        final var textRenderer = Minecraft.getInstance().font;
        if (textRenderer == null) return;

        if (philanthropistComp.getSkillCooldown() == 0) {
            context.blitSprite(ShopEntry.Type.POISON.getTexture(), this.getX() - 7, this.getY() - 7, 30, 30);
            PlayerFaceRenderer.draw(context, skinTextures.texture(), this.getX(), this.getY(), 16);
            if (this.isHovered()) {
                this.drawShopSlotHighlight(context, this.getX(), this.getY(), 0);
                final var displayName = targetPlayerEntry.getProfile().getName();
                if (displayName != null) {
                    context.renderTooltip(textRenderer, Component.nullToEmpty(displayName),
                            this.getX() - 4 - 10, this.getY() - 9);
                }
            }
        }

        if (philanthropistComp.getSkillCooldown() > 0) {
            context.setColor(0.25f, 0.25f, 0.25f, 0.5f);
            context.blitSprite(ShopEntry.Type.POISON.getTexture(), this.getX() - 7, this.getY() - 7, 30, 30);
            PlayerFaceRenderer.draw(context, skinTextures.texture(), this.getX(), this.getY(), 16);
            if (this.isHovered()) {
                this.drawShopSlotHighlight(context, this.getX(), this.getY(), 0);
            }
            context.setColor(1f, 1f, 1f, 1f);
            context.drawString(textRenderer, philanthropistComp.getSkillCooldown() / 20 + "",
                    this.getX(), this.getY(), Color.RED.getRGB(), true);
        }
    }

    private void drawShopSlotHighlight(GuiGraphics context, int x, int y, int z) {
        int color = -1862287543;
        context.fillGradient(RenderType.guiOverlay(), x, y, x + 16, y + 14, color, color, z);
        context.fillGradient(RenderType.guiOverlay(), x, y + 14, x + 15, y + 15, color, color, z);
        context.fillGradient(RenderType.guiOverlay(), x, y + 15, x + 14, y + 16, color, color, z);
    }

    public void renderString(GuiGraphics context, Font textRenderer, int color) {
    }
}
