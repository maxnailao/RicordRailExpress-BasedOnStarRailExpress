package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.CommonHudRenderCallback;
import org.agmas.noellesroles.game.roles.neutral.kidnapper.KidnappedCCA;

/**
 * 人质 HUD（屏幕左下角）：被绑架期间显示队友救援解锁倒计时。
 * 狼（杀手）被绑 90 秒解锁后，主手持撬锁器保持 5 秒可自行挣脱，此处显示撬锁倒计时；
 * 狼与平民统一在被绑 1 分钟后等待队友潜行右键救援。
 * 挂全局 CommonHudRenderCallback：人质可能是任何角色。
 */
public class KidnappedHud {

    public static void register() {
        CommonHudRenderCallback.EVENT.register((context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || SREClient.isPlayerSpectator())
                return;

            KidnappedCCA comp = KidnappedCCA.KEY.get(client.player);
            if (!comp.isKidnapped)
                return;

            Font textRenderer = client.font;
            int screenHeight = client.getWindow().getGuiScaledHeight();
            int x = 10;
            int y = screenHeight - 40;

            Component title = Component.translatable("hud.noellesroles.kidnapped.title")
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
            context.drawString(textRenderer, title, x, y, 0xFFFFFF);
            y += 12;

            if (comp.isKillerTarget && !comp.usedSelfEscape) {
                if (comp.escapeTicks >= KidnappedCCA.KILLER_ESCAPE_TICKS) {
                    // 时间已到：主手持撬锁器显示撬锁倒计时，否则提示拿出撬锁器
                    boolean holding = client.player.getMainHandItem()
                            .is(io.wifi.starrailexpress.index.TMMItems.LOCKPICK);
                    Component cd;
                    if (holding) {
                        int remain = (KidnappedCCA.LOCKPICK_ESCAPE_TICKS - comp.lockpickTicks + 19) / 20;
                        cd = Component.translatable("hud.noellesroles.kidnapped.escape_ready", remain)
                                .withStyle(ChatFormatting.GOLD);
                    } else {
                        cd = Component.translatable("hud.noellesroles.kidnapped.escape_need_lockpick")
                                .withStyle(ChatFormatting.RED);
                    }
                    context.drawString(textRenderer, cd, x, y, 0xFFFFFF);
                } else {
                    // 狼：显示自行挣脱倒计时
                    int remain = (KidnappedCCA.KILLER_ESCAPE_TICKS - comp.escapeTicks + 19) / 20;
                    Component cd = Component.translatable("hud.noellesroles.kidnapped.escape_cd", remain)
                            .withStyle(ChatFormatting.GOLD);
                    context.drawString(textRenderer, cd, x, y, 0xFFFFFF);
                }
            } else if (comp.canBeRescued()) {
                if (comp.rescuer != null) {
                    // 队友正在解绳：显示剩余秒数
                    int remain = (KidnappedCCA.RESCUE_DURATION_TICKS - comp.rescueTicks + 19) / 20;
                    Component progress = Component.translatable("hud.noellesroles.kidnapped.rescue_progress", remain)
                            .withStyle(ChatFormatting.GREEN);
                    context.drawString(textRenderer, progress, x, y, 0xFFFFFF);
                } else {
                    Component ready = Component.translatable("hud.noellesroles.kidnapped.rescue_ready")
                            .withStyle(ChatFormatting.GREEN);
                    context.drawString(textRenderer, ready, x, y, 0xFFFFFF);
                }
            } else {
                int remain = (KidnappedCCA.RESCUE_UNLOCK_TICKS - comp.kidnappedTicks + 19) / 20;
                Component cd = Component.translatable("hud.noellesroles.kidnapped.rescue_cd", remain);
                context.drawString(textRenderer, cd, x, y, 0xFFFF55);
            }
        });
    }
}