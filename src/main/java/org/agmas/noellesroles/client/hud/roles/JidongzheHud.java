package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.agmas.noellesroles.client.event.MutableComponentResult;
import org.agmas.noellesroles.client.event.OnMessageBelowMoneyRenderer;
import org.agmas.noellesroles.game.roles.innocence.jidongzhe.JidongzheRoleData;
import org.agmas.noellesroles.role.ModRoles;

import java.util.Locale;

/**
 * 机动者 HUD：超载进行中显示剩余时间。
 *
 * <p>走 {@link OnMessageBelowMoneyRenderer}（与雪怪暴风雪倒计时同一条通道），剩余刻数由 RoleData
 * 同步过来后在客户端本地倒数，不需要服务端每刻发包。</p>
 */
public final class JidongzheHud {

    public static void register() {
        OnMessageBelowMoneyRenderer.EVENT.register((client, guiGraphics, deltaTracker) -> {
            if (client.player == null || SREClient.gameComponent == null || !SREClient.gameComponent.isRunning()) {
                return null;
            }
            if (!SREClient.isPlayerAliveAndInSurvival()) {
                return null;
            }
            if (!SREClient.gameComponent.isRole(client.player, ModRoles.JIDONGZHE)) {
                return null;
            }
            JidongzheRoleData data = RoleData.getNullable(JidongzheRoleData.class, client.player);
            if (data == null || !data.isOverloaded()) {
                return null;
            }
            long ticks = data.overloadRemainingTicks();
            String seconds = String.format(Locale.ROOT, "%.1f", ticks / 20.0D);
            MutableComponent text = Component.translatable("hud.noellesroles.jidongzhe.overload", seconds)
                    .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
            return new MutableComponentResult(text);
        });
    }
}
