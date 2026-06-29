package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.roles.innocence.guardian.GuardianPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.role.ModRoles;

public class GuardianHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.GUARDIAN_ID, (guiGraphics, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (SREClient.gameComponent == null) return;
            if (!SREClient.isPlayerAliveAndInSurvival()) return;

            GuardianPlayerComponent comp = ModComponents.GUARDIAN.get(client.player);
            if (comp == null) return;

            int screenWidth = client.getWindow().getGuiScaledWidth();
            int screenHeight = client.getWindow().getGuiScaledHeight();
            int x = screenWidth - 10;
            int y = screenHeight - 20;

            // 显示智力障碍患者的免疫剩余时间（仅监护人可见，通过GuardianPlayerComponent同步）
            if (comp.zhizhangImmunityTicks > 0) {
                int seconds = (comp.zhizhangImmunityTicks + 19) / 20;
                Component immunityText = Component.translatable(
                        "hud.noellesroles.guardian.immunity_remaining", seconds)
                        .withStyle(ChatFormatting.AQUA);
                guiGraphics.drawString(client.font, immunityText,
                        x - client.font.width(immunityText), y, 0xFFFFFF);
                y -= client.font.lineHeight + 2;
            }

            // 技能状态
            Component skillText;
            if (comp.skillCooldown > 0) {
                int seconds = (comp.skillCooldown + 19) / 20;
                skillText = Component.translatable("tip.noellesroles.cooldown", seconds)
                        .withStyle(ChatFormatting.RED);
            } else {
                skillText = Component.translatable("hud.noellesroles.guardian.skill_ready",
                        GuardianPlayerComponent.SKILL_COST)
                        .withStyle(ChatFormatting.GREEN);
            }
            guiGraphics.drawString(client.font, skillText,
                    x - client.font.width(skillText), y, 0xFFFFFF);
        });
    }
}
