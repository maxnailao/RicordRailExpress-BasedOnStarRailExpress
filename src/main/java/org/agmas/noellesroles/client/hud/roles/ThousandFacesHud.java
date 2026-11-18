package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.CommonHudRenderCallback;
import org.agmas.noellesroles.game.roles.neutral.thousand_faces.ThousandFacesPlayerComponent;
import org.agmas.noellesroles.utils.RoleUtils;

/**
 * 千面人 HUD：显示剩余可用次数；已伪装时额外显示当前伪装职业。
 * 用通用 HUD 回调而不是按职业 ID 注册，因为伪装后玩家的真实职业已经变成目标职业。
 */
public final class ThousandFacesHud {
    private ThousandFacesHud() {
    }

    public static void register() {
        CommonHudRenderCallback.EVENT.register((graphics, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || client.screen != null) {
                return;
            }
            if (SREClient.gameComponent == null || !SREClient.gameComponent.isRunning()) {
                return;
            }
            if (SREClient.isPlayerSpectator()) {
                return;
            }
            ThousandFacesPlayerComponent comp = ThousandFacesPlayerComponent.KEY
                    .maybeGet(client.player).orElse(null);
            if (comp == null || !comp.isActive()) {
                return;
            }

            int x = graphics.guiWidth() - 180;
            int y = graphics.guiHeight() - 57;
            int usesColor = comp.getUses() > 0 ? 0xFFD8A0FF : 0xFF888888;
            graphics.drawString(client.font,
                    Component.translatable("hud.noellesroles.thousand_faces.uses",
                            String.valueOf(comp.getUses())),
                    x, y, usesColor);

            if (comp.isDisguised()) {
                var currentRole = SREClient.gameComponent.getRole(client.player);
                if (currentRole != null) {
                    graphics.drawString(client.font,
                            Component.translatable("hud.noellesroles.thousand_faces.disguised",
                                    RoleUtils.getRoleName(currentRole)),
                            x, y + 11, 0xFFB07FE0);
                }
            }
        });
    }
}
