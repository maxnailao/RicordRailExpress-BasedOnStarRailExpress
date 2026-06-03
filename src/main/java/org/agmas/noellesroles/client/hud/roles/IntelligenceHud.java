package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.roles.innocent.intelligence.IntelligencePlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

public class IntelligenceHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.INTELLIGENCE_ID, (guiGraphics, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (SREClient.gameComponent == null) {
                return;
            }
            if (!SREClient.isPlayerAliveAndInSurvival()) {
                return;
            }

            SREAbilityPlayerComponent ability = SREAbilityPlayerComponent.KEY.get(client.player);
            if (ability == null) {
                return;
            }

            IntelligencePlayerComponent intelComp = ModComponents.INTELLIGENCE.get(client.player);
            if (intelComp == null) {
                return;
            }

            int remainingMonitors = 2 - intelComp.monitors.size();

            int screenWidth = client.getWindow().getGuiScaledWidth();
            int screenHeight = client.getWindow().getGuiScaledHeight();
            int x = screenWidth - 10;
            int y = screenHeight - 40;

            if (ability.cooldown > 0) {
                int seconds = (ability.cooldown + 19) / 20;
                Component cooldownText = Component.translatable(
                        "hud.noellesroles.intelligence.cooldown",
                        seconds, remainingMonitors)
                        .withStyle(ChatFormatting.RED);
                guiGraphics.drawString(client.font, cooldownText,
                        x - client.font.width(cooldownText), y, 0xFFFFFF);
            } else if (remainingMonitors > 0) {
                Component readyText = Component.translatable(
                        "hud.noellesroles.intelligence.ready",
                        remainingMonitors)
                        .withStyle(ChatFormatting.GREEN);
                guiGraphics.drawString(client.font, readyText,
                        x - client.font.width(readyText), y, 0xFFFFFF);
            }
        });
    }
}
