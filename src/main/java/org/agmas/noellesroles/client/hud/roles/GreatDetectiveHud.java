package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.api.data.RoleData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.innocence.GreatDetectiveRoleData;

/**
 * 大侦探 HUD：勘察倒计时 / 冷却 / 就绪。
 */
public class GreatDetectiveHud {
    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.GREAT_DETECTIVE_ID, (context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();

            var detectiveOpt = RoleData.getOptional(GreatDetectiveRoleData.class, client.player);
            if (detectiveOpt.isEmpty())
                return;
            GreatDetectiveRoleData detectiveComponent = detectiveOpt.get();

            // 渲染位置 - 右下角
            int screenWidth = client.getWindow().getGuiScaledWidth();
            int screenHeight = client.getWindow().getGuiScaledHeight();
            int x = screenWidth - 10; // 距离右边缘
            int y = screenHeight - 20; // 距离底部

            Font font = client.font;

            if (detectiveComponent.isChanneling()) {
                // 引导中：优先显示剩余秒数
                float left = detectiveComponent.getChannelLeftTime() / 20f;
                Component text = Component.translatable("hud.noellesroles.great_detective.channeling",
                        String.format("%.1f", left));
                context.drawString(font, text, x - font.width(text), y, 0xFFEEDDAA);
            } else if (detectiveComponent.isInCooldown()) {
                float cdSeconds = detectiveComponent.getCooldownLeftTime() / 20f;
                Component cdText = Component.translatable("hud.noellesroles.great_detective.cooldown",
                        String.format("%.1f", cdSeconds));
                // 红色文字表示冷却中
                context.drawString(font, cdText, x - font.width(cdText), y, CommonColors.RED);
            } else {
                // 技能可用
                Component readyText = Component.translatable("hud.noellesroles.great_detective.ready");
                context.drawString(font, readyText, x - font.width(readyText), y, CommonColors.GREEN);
            }
        });
    }
}
