package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.killer.ReconBotRoleData;

/**
 * 荡索者 HUD：钩爪使用过程中，在右下角显示「按技能键断开钩子」。
 * <p>技能冷却由通用 {@code UnifiedSkillHud} 显示；这里只补充使用中的操作提示。
 */
public final class ReconBotHud {

    private ReconBotHud() {
    }

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.RECON_BOT_ID, (guiGraphics, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || client.screen != null) {
                return;
            }
            if (SREClient.gameComponent == null || !SREClient.isPlayerAliveAndInSurvival()) {
                return;
            }

            ReconBotRoleData data = ReconBotRoleData.of(client.player);
            if (data == null || !data.isAttachedClient()) {
                return;
            }

            Component hint = Component.translatable("hud.noellesroles.recon_bot.detach_hint")
                    .withStyle(ChatFormatting.GRAY);
            int screenWidth = guiGraphics.guiWidth();
            int screenHeight = guiGraphics.guiHeight();
            // 画在通用技能 HUD 上方（技能卡片在 screenHeight - totalH - 20 附近）
            guiGraphics.drawString(client.font, hint,
                    screenWidth - 10 - client.font.width(hint), screenHeight - 52, 0xFFFFFF, false);
        });
    }
}
