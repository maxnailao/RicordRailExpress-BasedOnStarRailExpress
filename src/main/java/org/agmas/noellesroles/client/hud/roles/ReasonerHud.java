package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import io.wifi.starrailexpress.api.data.RoleData;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.neutral.ReasonerRoleData;

public final class ReasonerHud {
    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.REASONER_ID, (context, tickCounter) -> {
            if (SREClient.isPlayerSpectator()) return;
            var player = Minecraft.getInstance().player;
            var reasonerOpt = RoleData.getOptional(ReasonerRoleData.class, player);
            if (reasonerOpt.isEmpty()) return;
            ReasonerRoleData comp = reasonerOpt.get();

            int solved = comp.getSolvedCount();
            int x = context.guiWidth() - 100;
            int y = context.guiHeight() - 23;
            var font = Minecraft.getInstance().font;

            if (!comp.isCompassGiven()) {
                int remainTicks = comp.getCompassRemainingTicks();
                int secs = (int) Math.ceil(remainTicks / 20.0f);
                context.drawString(font,
                        Component.translatable("hud.noellesroles.reasoner.compass", secs),
                        x, y, 0xFF7FD4E0);
                y -= font.lineHeight + 2;
            }

            context.drawString(font,
                    Component.translatable("hud.noellesroles.reasoner.progress", solved, 5),
                    x, y, 0xFFD4B25C);
        });
    }
}
