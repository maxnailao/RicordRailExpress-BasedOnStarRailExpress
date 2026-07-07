package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role.ModRoles;

public class JinghuazheHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.JINGHUAZHE_ID, (guiGraphics, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (SREClient.gameComponent == null) return;
            if (!SREClient.isPlayerAliveAndInSurvival()) return;

            int screenWidth = client.getWindow().getGuiScaledWidth();
            int screenHeight = client.getWindow().getGuiScaledHeight();
            int x = screenWidth - 10;
            int y = screenHeight - 20;

            // 显示技能冷却状态
            var ability = SREAbilityPlayerComponent.KEY.get(client.player);
            if (ability != null && ability.cooldown > 0) {
                int seconds = (ability.cooldown + 19) / 20;
                Component cooldownText = Component.translatable("hud.noellesroles.jinghuazhe.skill_cooldown", seconds)
                        .withStyle(ChatFormatting.RED);
                guiGraphics.drawString(client.font, cooldownText,
                        x - client.font.width(cooldownText), y, 0xFFFFFF);
            } else {
                Component readyText = Component.translatable("hud.noellesroles.jinghuazhe.skill_ready")
                        .withStyle(ChatFormatting.GREEN);
                guiGraphics.drawString(client.font, readyText,
                        x - client.font.width(readyText), y, 0xFFFFFF);
            }
        });
    }
}
