package org.agmas.noellesroles.client.widget;

import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
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
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.game.roles.neutral.munaiyi_desert.MunaiyiDesertPlayerComponent;
import org.agmas.noellesroles.packet.MunaiyiCurseSelectC2SPacket;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * 木乃伊玩家选择组件（技能1「木乃伊的诅咒」）
 * 背包界面中点击存活玩家头像对其施加一层诅咒；头像下方显示当前诅咒层数
 */
public class MunaiyiPlayerWidget extends Button {
    public final LimitedInventoryScreen screen;
    public final PlayerInfo targetPlayer;
    private Component displayText = Component.empty();
    private java.util.List<net.minecraft.util.FormattedCharSequence> cachedLines = new java.util.ArrayList<>();

    public MunaiyiPlayerWidget(LimitedInventoryScreen screen, int x, int y,
            @NotNull PlayerInfo targetPlayer) {
        super(x, y, 16, 16, Component.literal(targetPlayer.getProfile().getName()), (button) -> {
            AbstractClientPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                // 实际校验（角色/冷却/层数）在服务端接收端完成
                ClientPlayNetworking.send(new MunaiyiCurseSelectC2SPacket(targetPlayer.getProfile().getId()));
            }
        }, DEFAULT_NARRATION);
        this.screen = screen;
        this.targetPlayer = targetPlayer;
    }

    @Override
    protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
        AbstractClientPlayer player = Minecraft.getInstance().player;
        if (player == null)
            return;

        MunaiyiDesertPlayerComponent mummyComp = MunaiyiDesertPlayerComponent.KEY.maybeGet(player).orElse(null);
        SREAbilityPlayerComponent abilityComp = SREAbilityPlayerComponent.KEY.get(player);

        int stacks = mummyComp != null
                ? mummyComp.curseStacks.getOrDefault(targetPlayer.getProfile().getId(), 0)
                : 0;
        boolean skillReady = abilityComp.canUseSkill(MunaiyiDesertPlayerComponent.SKILL_CURSE);
        boolean canCurse = mummyComp != null && skillReady && stacks < MunaiyiDesertPlayerComponent.MAX_CURSE_STACKS;

        super.renderWidget(context, mouseX, mouseY, delta);
        if (!canCurse) {
            context.setColor(0.25f, 0.25f, 0.25f, 0.5f);
        }
        context.blitSprite(ShopEntry.Type.WEAPON.getTexture(), this.getX() - 7, this.getY() - 7, 30, 30);
        PlayerFaceRenderer.draw(context, targetPlayer.getSkin().texture(), this.getX(), this.getY(), 16);

        if (this.isHovered()) {
            this.drawSlotHighlight(context, this.getX(), this.getY(), 0);
            context.renderTooltip(Minecraft.getInstance().font,
                    Component.nullToEmpty(targetPlayer.getProfile().getName()),
                    this.getX() - 4 - Minecraft.getInstance().font.width(targetPlayer.getProfile().getName()) / 2,
                    this.getY() - 9);
        }
        context.setColor(1f, 1f, 1f, 1f);

        // 诅咒层数角标
        if (stacks > 0) {
            context.drawString(Minecraft.getInstance().font,
                    stacks >= MunaiyiDesertPlayerComponent.MAX_CURSE_STACKS ? "MAX" : String.valueOf(stacks),
                    this.getX() + 8, this.getY() - 4,
                    stacks >= MunaiyiDesertPlayerComponent.MAX_CURSE_STACKS ? 0xFF5555 : 0xFFAA00, true);
        }
        // 技能冷却中显示秒数
        if (!skillReady) {
            int cooldownTicks = abilityComp.getSkillState(MunaiyiDesertPlayerComponent.SKILL_CURSE).cooldown;
            int cooldownSeconds = (cooldownTicks + 19) / 20;
            context.drawString(Minecraft.getInstance().font, cooldownSeconds + "s",
                    this.getX(), this.getY(), Color.RED.getRGB(), true);
        }

        // 层数提示文本
        if (stacks >= MunaiyiDesertPlayerComponent.MAX_CURSE_STACKS) {
            setDisplayText(Component.translatable("hud.munaiyi.curse_full").withStyle(ChatFormatting.DARK_RED));
        } else if (stacks > 0) {
            setDisplayText(Component.translatable("hud.munaiyi.curse_stacks", stacks)
                    .withStyle(ChatFormatting.YELLOW));
        }
        renderDisplayText(context);
    }

    private void drawSlotHighlight(GuiGraphics context, int x, int y, int z) {
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
        int maxWidth = 60;
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
        // 空实现：文本由 renderDisplayText 渲染
    }
}
