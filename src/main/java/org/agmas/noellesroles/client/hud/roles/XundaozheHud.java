package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.roles.innocence.role.ModRoles;
import org.agmas.noellesroles.game.roles.innocence.xundaozhe.XundaozhePlayerComponent;

/**
 * 殉道者 HUD 渲染
 *
 * 显示当前状态：复活进度 / 就绪 / 金币不足 / 已使用
 */
public class XundaozheHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.XUNDAOZHE_ID, (context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null) return;
            if (SREClient.gameComponent == null) return;
            if (!SREClient.isPlayerAliveAndInSurvival()) return;

            XundaozhePlayerComponent comp = ModComponents.XUNDAOZHE.get(client.player);
            if (comp == null) return;

            int screenWidth = context.guiWidth();
            int screenHeight = context.guiHeight();
            int x = screenWidth - 10;
            int y = screenHeight - 20;
            var font = client.font;

            // 复活进度
            if (comp.isReviving) {
                int seconds = comp.revivalTicks / 20;
                int totalSeconds = XundaozhePlayerComponent.REVIVAL_DURATION / 20;
                Component progressText = Component.translatable(
                                "hud.noellesroles.xundaozhe.reviving",
                                comp.revivalTargetName,
                                seconds + "/" + totalSeconds + "s")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
                context.drawString(font, progressText, x - font.width(progressText), y, 0xFFFFFF);
                return;
            }

            // 已使用过
            if (comp.hasRevived) {
                Component usedText = Component.translatable("hud.noellesroles.xundaozhe.already_used")
                        .withStyle(ChatFormatting.GRAY, ChatFormatting.STRIKETHROUGH);
                context.drawString(font, usedText, x - font.width(usedText), y, 0xFFFFFF);
                return;
            }

            // 金币检查
            SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(client.player);
            if (shop.balance < XundaozhePlayerComponent.REVIVAL_COST) {
                Component noCoinsText = Component.translatable("hud.noellesroles.xundaozhe.no_coins")
                        .withStyle(ChatFormatting.RED);
                context.drawString(font, noCoinsText, x - font.width(noCoinsText), y, 0xFFFFFF);
                return;
            }

            // 就绪状态
            Component readyText = Component.translatable("hud.noellesroles.xundaozhe.ready")
                    .withStyle(ChatFormatting.GREEN);
            context.drawString(font, readyText, x - font.width(readyText), y, 0xFFFFFF);
        });
    }
}
