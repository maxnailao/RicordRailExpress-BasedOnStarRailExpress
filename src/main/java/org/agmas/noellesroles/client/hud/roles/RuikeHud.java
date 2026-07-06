package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.game.roles.innocence.ruike.RuikePlayerComponent;

/**
 * 时空旅者 HUD 渲染
 *
 * 显示当前传送门数量和技能状态
 */
public class RuikeHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.RUIKE_ID, (context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null) return;
            if (SREClient.gameComponent == null) return;
            if (!SREClient.isPlayerAliveAndInSurvival()) return;

            RuikePlayerComponent comp = ModComponents.RUIKE.get(client.player);
            if (comp == null) return;

            int screenWidth = context.guiWidth();
            int screenHeight = context.guiHeight();
            int x = screenWidth - 10;
            int y = screenHeight - 20;
            var font = client.font;

            // 显示传送门数量
            int portalCount = comp.getPortalCount();
            Component portalText = Component.translatable("hud.noellesroles.ruike.portals", portalCount, 2)
                    .withStyle(ChatFormatting.LIGHT_PURPLE);
            context.drawString(font, portalText, x - font.width(portalText), y, 0xFFFFFF);

            // 显示技能冷却 / 就绪
            y -= font.lineHeight + 2;
            var ability = SREAbilityPlayerComponent.KEY.get(client.player);
            if (ability != null && ability.cooldown > 0) {
                int seconds = (ability.cooldown + 19) / 20;
                Component cooldownText = Component.translatable("hud.noellesroles.ruike.skill_cooldown", seconds)
                        .withStyle(ChatFormatting.RED);
                context.drawString(font, cooldownText, x - font.width(cooldownText), y, 0xFFFFFF);
            } else {
                Component readyText = Component.translatable("hud.noellesroles.ruike.skill_ready")
                        .withStyle(ChatFormatting.GREEN);
                context.drawString(font, readyText, x - font.width(readyText), y, 0xFFFFFF);
            }
        });
    }
}
