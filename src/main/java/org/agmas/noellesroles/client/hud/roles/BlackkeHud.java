package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.game.roles.innocence.blackke.BlackkePlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

/**
 * Hacker HUD
 * Displays skill cost and cooldown info in the bottom-right corner
 */
public class BlackkeHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.BLACKKE_ID, (context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null) return;

            SREAbilityPlayerComponent abilityComp = SREAbilityPlayerComponent.KEY.get(client.player);

            Font textRenderer = client.font;
            int screenWidth = client.getWindow().getGuiScaledWidth();
            int screenHeight = client.getWindow().getGuiScaledHeight();
            int x = screenWidth - 10;
            int y = screenHeight - 12 * 2;

            // Display skill cost
            Component costText = Component.translatable("hud.noellesroles.blackke.cost",
                    BlackkePlayerComponent.SKILL_COST);
            context.drawString(textRenderer, costText,
                    x - textRenderer.width(costText), y, 0x00C853);

            // Display cooldown time
            if (abilityComp.cooldown > 0) {
                int cooldownSeconds = (abilityComp.cooldown + 19) / 20;
                Component cooldownText = Component.translatable("hud.noellesroles.blackke.cooldown",
                        cooldownSeconds);
                context.drawString(textRenderer, cooldownText,
                        x - textRenderer.width(cooldownText), y + 12, 0xFF5555);
            } else {
                Component readyText = Component.translatable("hud.noellesroles.blackke.ready");
                context.drawString(textRenderer, readyText,
                        x - textRenderer.width(readyText), y + 12, 0x55FF55);
            }
        });
    }
}
