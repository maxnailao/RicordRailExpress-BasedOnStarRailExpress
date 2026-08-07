package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.client.event.CommonHudRenderCallback;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.game.roles.killer.mengyan.MengyanPlayerComponent;

/**
 * 梦魇 HUD 渲染
 *
 * 梦魇视角：显示恐惧目标、倒计时、睡眠进度
 * 目标视角：显示恐惧倒计时提示
 */
public class MengyanHud {

    private static final int FEAR_DURATION_TICKS = 400;
    private static final int REQUIRED_SLEEP_TICKS = 200;

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.MENGYAN_ID, (context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null) return;
            if (SREClient.gameComponent == null) return;
            if (!SREClient.isPlayerAliveAndInSurvival()) return;

            MengyanPlayerComponent comp = ModComponents.MENGYAN.get(client.player);
            if (comp == null) return;

            int screenWidth = context.guiWidth();
            int screenHeight = context.guiHeight();
            int x = screenWidth - 10;
            int y = screenHeight - 20;
            var font = client.font;

            // 梦魇侧 HUD：显示恐惧目标状态
            if (comp.fearActive && comp.fearTarget != null) {
                // 获取目标名称
                String targetName = "???";
                if (client.level != null) {
                    var targetPlayer = client.level.getPlayerByUUID(comp.fearTarget);
                    if (targetPlayer != null) {
                        targetName = targetPlayer.getName().getString();
                    }
                }

                // 显示恐惧目标
                Component targetText = Component.translatable("hud.noellesroles.mengyan.fear_target", targetName)
                        .withStyle(ChatFormatting.DARK_PURPLE);
                context.drawString(font, targetText, x - font.width(targetText), y, 0xFFFFFF);
                y -= font.lineHeight + 2;

                // 显示倒计时（秒）
                int remainingSeconds = comp.fearRemainingTicks / 20;
                Component timerText = Component.translatable("hud.noellesroles.mengyan.fear_timer", remainingSeconds)
                        .withStyle(remainingSeconds <= 5 ? ChatFormatting.RED : ChatFormatting.YELLOW);
                context.drawString(font, timerText, x - font.width(timerText), y, 0xFFFFFF);
                y -= font.lineHeight + 2;

                // 显示睡眠进度（秒）
                int sleepSeconds = comp.fearSleepAccumulated / 20;
                Component sleepText = Component.translatable("hud.noellesroles.mengyan.sleep_progress", sleepSeconds)
                        .withStyle(ChatFormatting.AQUA);
                context.drawString(font, sleepText, x - font.width(sleepText), y, 0xFFFFFF);
            }

            // 目标侧 HUD 不在这里渲染（已移到 CommonHudRenderCallback）
        });

        // 目标侧 HUD：对所有玩家渲染，检查是否被施加恐惧
        CommonHudRenderCallback.EVENT.register((context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null) return;
            if (SREClient.gameComponent == null) return;
            if (!SREClient.isPlayerAliveAndInSurvival()) return;

            MengyanPlayerComponent comp = ModComponents.MENGYAN.get(client.player);
            if (comp == null || !comp.isUnderFear) return;

            long gameTime = client.level != null ? client.level.getGameTime() : 0;
            int remaining = (int) Math.max(0, comp.fearEndTime - gameTime);
            int remainingSeconds = remaining / 20;

            int screenWidth = context.guiWidth();
            int screenHeight = context.guiHeight();
            int x = screenWidth - 10;
            int textY = screenHeight - 20;
            var font = client.font;

            boolean isSleeping = client.player.isSleeping();

            Component fearedText;
            if (isSleeping) {
                fearedText = Component.translatable("hud.noellesroles.mengyan.sleeping", remainingSeconds)
                        .withStyle(ChatFormatting.GREEN);
            } else {
                fearedText = Component.translatable("hud.noellesroles.mengyan.feared_timer", remainingSeconds)
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
            }
            context.drawString(font, fearedText, x - font.width(fearedText), textY, 0xFFFFFF);
        });
    }
}
