package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.CommonHudRenderCallback;
import org.agmas.noellesroles.content.item.ConvictHandcuffsItem;
import org.agmas.noellesroles.role.ModRoles;

/**
 * 重刑犯靠近提示：杀手 / 杀手中立 / 警长靠近被铐住的重刑犯时，显示解铐操作提示。
 * 不同阵营显示不同文案（警长：改过自新；杀手侧：加入组织）。
 *
 * <p>用 {@link CommonHudRenderCallback}（对所有玩家渲染），而非重刑犯本人右下角的路径 HUD。</p>
 */
public final class ConvictHud {

    /** 提示触发距离（格） */
    private static final double HINT_DISTANCE = 5.0;

    private ConvictHud() {
    }

    public static void register() {
        CommonHudRenderCallback.EVENT.register((guiGraphics, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || client.level == null || client.screen != null) {
                return;
            }
            if (!GameUtils.isPlayerAliveAndSurvival(client.player)) {
                return;
            }
            if (SREClient.gameComponent == null || !SREClient.gameComponent.isRunning()) {
                return;
            }

            SRERole role = SREClient.gameComponent.getRole(client.player);
            if (role == null) {
                return;
            }
            boolean sheriff = role.isVigilanteTeam();
            boolean killerSide = role.isKillerTeam();
            if (!sheriff && !killerSide) {
                return;
            }

            for (var target : client.level.players()) {
                if (target == client.player) {
                    continue;
                }
                if (!SREClient.gameComponent.isRole(target, ModRoles.CONVICT)) {
                    continue;
                }
                if (client.player.distanceTo(target) > HINT_DISTANCE) {
                    continue;
                }
                // 已解铐则不再提示
                if (!ConvictHandcuffsItem.hasConvictHandCuff(target)) {
                    continue;
                }

                Component hint = sheriff
                        ? Component.translatable("hud.noellesroles.convict.uncuff_hint_sheriff")
                        : Component.translatable("hud.noellesroles.convict.uncuff_hint_killer");
                guiGraphics.drawCenteredString(client.font, hint,
                        guiGraphics.guiWidth() / 2, guiGraphics.guiHeight() / 2 + 30, 0xFFFFAA00);
                return;
            }
        });
    }
}
