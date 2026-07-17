package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.roles.killer.snow_hunter.SnowHunterPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

public abstract class SnowHunterHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.SNOW_HUNTER_ID, (context, tickCounter) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null) return;
            if (SREClient.gameComponent == null) return;
            if (!SREClient.isPlayerAliveAndInSurvival()) return;

            SnowHunterPlayerComponent comp = ModComponents.SNOW_HUNTER.get(client.player);
            if (comp == null) return;

            Font font = client.font;
            int screenWidth = context.guiWidth();
            int screenHeight = context.guiHeight();
            int x = screenWidth - 10;
            int y = screenHeight - 20;

            Component statusText;
            int statusColor;
            if (comp.isSkillActive()) {
                int seconds = (comp.skillActiveTicks + 19) / 20;
                statusText = Component.translatable("hud.noellesroles.snow_hunter.skill_active", seconds)
                        .withStyle(ChatFormatting.AQUA);
                statusColor = 0x55FFFF;
            } else if (comp.skillCooldownTicks > 0) {
                int seconds = (comp.skillCooldownTicks + 19) / 20;
                statusText = Component.translatable("hud.noellesroles.snow_hunter.skill_cooldown", seconds)
                        .withStyle(ChatFormatting.RED);
                statusColor = 0xFF5555;
            } else {
                SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(client.player);
                if (shop.balance < SnowHunterPlayerComponent.SKILL_COST) {
                    statusText = Component.translatable("hud.noellesroles.snow_hunter.no_coins",
                            SnowHunterPlayerComponent.SKILL_COST)
                            .withStyle(ChatFormatting.YELLOW);
                    statusColor = 0xFFAA00;
                } else {
                    statusText = Component.translatable("hud.noellesroles.snow_hunter.skill_ready",
                            SnowHunterPlayerComponent.SKILL_COST)
                            .withStyle(ChatFormatting.GREEN);
                    statusColor = 0x55FF55;
                }
            }
            context.drawString(font, statusText, x - font.width(statusText), y, statusColor);
        });
    }
}
