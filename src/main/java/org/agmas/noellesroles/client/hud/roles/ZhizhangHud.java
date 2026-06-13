package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.roles.innocent.zhizhang.ZhizhangPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

public class ZhizhangHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.ZHIZHANG_ID, (guiGraphics, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (SREClient.gameComponent == null) return;
            if (!SREClient.isPlayerAliveAndInSurvival()) return;

            ZhizhangPlayerComponent comp = ModComponents.ZHIZHANG.get(client.player);
            if (comp == null) return;

            int screenWidth = client.getWindow().getGuiScaledWidth();
            int screenHeight = client.getWindow().getGuiScaledHeight();
            int x = screenWidth - 10;
            int y = screenHeight - 20;

            // 监护人保护状态提示（不显示剩余时间，仅提示当前受保护）
            if (comp.guardianImmunityTicks > 0) {
                Component protectedText = Component.translatable("hud.noellesroles.zhizhang.protected")
                        .withStyle(ChatFormatting.AQUA);
                guiGraphics.drawString(client.font, protectedText,
                        x - client.font.width(protectedText), y, 0xFFFFFF);
                y -= client.font.lineHeight + 2;
            }

            // 技能状态
            Component skillText;
            if (comp.skillCooldown > 0) {
                int seconds = (comp.skillCooldown + 19) / 20;
                skillText = Component.translatable("tip.noellesroles.cooldown", seconds)
                        .withStyle(ChatFormatting.RED);
            } else if (comp.pendingDetectTimer > 0) {
                int seconds = (comp.pendingDetectTimer + 19) / 20;
                skillText = Component.translatable("hud.noellesroles.zhizhang.detecting", seconds)
                        .withStyle(ChatFormatting.YELLOW);
            } else if (!comp.highlightedPlayers.isEmpty() && comp.highlightTicks > 0) {
                skillText = Component.translatable("hud.noellesroles.zhizhang.highlighting",
                        comp.highlightedPlayers.size(), (comp.highlightTicks + 19) / 20)
                        .withStyle(ChatFormatting.RED);
            } else {
                skillText = Component.translatable("hud.noellesroles.zhizhang.skill_ready")
                        .withStyle(ChatFormatting.GREEN);
            }
            guiGraphics.drawString(client.font, skillText,
                    x - client.font.width(skillText), y, 0xFFFFFF);
        });
    }
}
