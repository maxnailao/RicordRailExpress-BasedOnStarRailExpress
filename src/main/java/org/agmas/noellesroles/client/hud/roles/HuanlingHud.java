package org.agmas.noellesroles.client.hud.roles;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.roles.innocence.huanling.HuanlingPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

/**
 * 幻灵 HUD —— 右下角显示附身/寻找倒计时。
 *
 * <p>
 * 附身期：宿主名字 + 距 3 分钟现身转换的剩余时间；
 * 寻找期：剩余寻找/宽限时间（红色，宽限期更醒目）。
 * 幻灵核心阶段处于旁观/冒险宽限，因此不做旁观早退判断。
 */
public final class HuanlingHud {
    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.HUANYING_ID, (context, tickCounter) -> {
            var player = Minecraft.getInstance().player;
            if (player == null)
                return;
            HuanlingPlayerComponent comp = ModComponents.HUANYING.get(player);
            if (comp == null)
                return;
            Font font = Minecraft.getInstance().font;
            int right = context.guiWidth() - 10;
            int bottom = context.guiHeight() - 20;

            if (comp.clientPossessTarget != null) {
                // 附身期：宿主名字 + 距现身转换剩余时间
                Player host = player.level().getPlayerByUUID(comp.clientPossessTarget);
                String hostName = host != null ? host.getName().getString() : "?";
                Component line1 = Component.translatable("hud.noellesroles.huanling.possessing", hostName);
                context.drawString(font, line1, right - font.width(line1), bottom - 11, 0xAAAAAA);
                if (comp.clientTransformTicks > 0) {
                    int seconds = (comp.clientTransformTicks + 19) / 20;
                    Component line2 = Component.translatable("hud.noellesroles.huanling.transform_time", seconds);
                    context.drawString(font, line2, right - font.width(line2), bottom, 0xC0C0C0);
                } else {
                    Component line2 = Component.translatable("hud.noellesroles.huanling.transform_now");
                    context.drawString(font, line2, right - font.width(line2), bottom, 0x55FF55);
                }
            } else if (comp.clientSearchTicks > 0) {
                // 寻找期：开局寻找或宽限倒计时
                int seconds = (comp.clientSearchTicks + 19) / 20;
                Component text = Component.translatable(comp.clientAdventureGrace
                        ? "hud.noellesroles.huanling.grace_time"
                        : "hud.noellesroles.huanling.search_time", seconds);
                context.drawString(font, text, right - font.width(text), bottom, 0xFF5555);
            }
        });
    }
}
