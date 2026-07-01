package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.role.ModRoles;

public class GhostofanyingHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.GHOSTOFANYING_ID, (guiGraphics, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (SREClient.gameComponent == null) return;
            if (!SREClient.isPlayerAliveAndInSurvival()) return;

            var comp = ModComponents.GHOSTOFANYING.get(client.player);
            if (comp == null) return;

            int screenWidth = client.getWindow().getGuiScaledWidth();
            int screenHeight = client.getWindow().getGuiScaledHeight();
            int x = screenWidth - 10;
            int y = screenHeight - 20;

            // 暗影步状态显示
            if (comp.isDashing) {
                Component dashText = Component.translatable("hud.noellesroles.ghostofanying.dashing")
                        .withStyle(ChatFormatting.DARK_PURPLE);
                guiGraphics.drawString(client.font, dashText,
                        x - client.font.width(dashText), y, 0xFFFFFF);
                y -= client.font.lineHeight + 2;
            }

            // 次数和冷却显示
            Component statusText;
            if (comp.charges >= 3) {
                // 满次数
                statusText = Component.translatable("hud.noellesroles.ghostofanying.charges_full",
                                comp.charges)
                        .withStyle(ChatFormatting.GREEN);
            } else if (comp.rechargeCooldown > 0) {
                // 冷却中
                int seconds = (comp.rechargeCooldown + 19) / 20;
                statusText = Component.translatable("hud.noellesroles.ghostofanying.charges_cooldown",
                                comp.charges, seconds)
                        .withStyle(ChatFormatting.YELLOW);
            } else {
                // 可用
                statusText = Component.translatable("hud.noellesroles.ghostofanying.charges_ready",
                                comp.charges)
                        .withStyle(ChatFormatting.GREEN);
            }
            guiGraphics.drawString(client.font, statusText,
                    x - client.font.width(statusText), y, 0xFFFFFF);
        });
    }
}
