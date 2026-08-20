package org.agmas.noellesroles.client.hud.roles;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;
import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.game.roles.innocence.avenger.AvengerPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;
import pro.fazeclan.river.stupid_express.modifier.refugee.cca.RefugeeComponent;

/**
 * 复仇者 HUD Mixin
 * 在屏幕上显示绑定目标和激活状态
 */
public class AvengerHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.AVENGER_ID, (context, tickCounter) -> {
            Minecraft client = Minecraft.getInstance();
            final Font renderer = client.font;
            final LocalPlayer player = client.player;
            AvengerPlayerComponent avengerComponent = AvengerPlayerComponent.KEY.get(player);

            context.pose().pushPose();

            int screenWidth = context.guiWidth();
            int screenHeight = context.guiHeight();
            int yOffset = screenHeight - 28; // 右下角
            int xOffset = screenWidth - 180; // 距离右边缘
            var refugeeC = RefugeeComponent.KEY.get(player.level());
            boolean isRefugeeAlive = false;
            if (refugeeC.isAnyRevivals) {
                isRefugeeAlive = true;
            }
            if (isRefugeeAlive) {
                Component waitingText = Component.translatable("tip.noellesroles.avenger.refugee_mode")
                        .withStyle(ChatFormatting.GRAY);
                context.drawString(renderer, waitingText, screenWidth - 18 - renderer.width(waitingText), yOffset,
                        CommonColors.GRAY);
            } else if (avengerComponent.activated) {
                if (avengerComponent.rushActive) {
                    // 复仇心切生效中 - 显示倒计时
                    int seconds = Math.max(0, (avengerComponent.rushTicksRemaining + 19) / 20);
                    Component rushText = Component.translatable("tip.noellesroles.avenger.rush_active", seconds)
                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
                    context.drawString(renderer, rushText, xOffset, yOffset, CommonColors.RED);
                } else if (avengerComponent.rushSucceeded) {
                    // 复仇已完成
                    Component doneText = Component.translatable("tip.noellesroles.avenger.rush_done")
                            .withStyle(ChatFormatting.GOLD);
                    context.drawString(renderer, doneText, xOffset, yOffset, 0xFFAA00);
                } else {
                    // 复仇已激活 - 显示凶手信息
                    Component statusText = Component.translatable("tip.noellesroles.avenger.activated",
                            avengerComponent.getKillerName().isEmpty() ? "???" : avengerComponent.getKillerName())
                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);

                    context.drawString(renderer, statusText, xOffset, yOffset, CommonColors.RED);

                    // 如果知道凶手，显示凶手头像
                    if (avengerComponent.killerUuid != null) {
                        final var playerInfo = client.player.connection.getPlayerInfo(avengerComponent.killerUuid);
                        if (playerInfo != null) {
                            context.drawPlayerFace(playerInfo.getSkin().texture(),
                                    xOffset, yOffset - 14, 12);

                            Component killerName = Component.literal(avengerComponent.getKillerName())
                                    .withStyle(ChatFormatting.RED);
                            context.drawString(renderer, killerName, xOffset + 16, yOffset - 12, CommonColors.RED);
                        }
                    }

                    // 技能就绪时显示释放提示
                    if (!avengerComponent.rushUsed && avengerComponent.isKillerAlive()) {
                        Component rushReady = Component.translatable("tip.noellesroles.avenger.rush_ready",
                                NoellesrolesClient.abilityBind.getTranslatedKeyMessage())
                                .withStyle(ChatFormatting.GOLD);
                        context.drawString(renderer, rushReady, xOffset, yOffset - 26, 0xFFAA00);
                    }
                }
            } else if (avengerComponent.bound && avengerComponent.targetPlayer != null) {
                // 已绑定目标 - 显示保护目标
                var target = client.player.connection.getPlayerInfo(avengerComponent.targetPlayer);
                if (target != null) {
                    // 显示目标头像
                    context.drawPlayerFace(target.getSkin().texture(),
                            xOffset, yOffset, 12);

                    Component targetText = Component.translatable("tip.noellesroles.avenger.target",
                            avengerComponent.targetName).withStyle(ChatFormatting.GOLD);
                    context.drawString(renderer, targetText, xOffset + 16, yOffset + 2, 0xFFAA00);
                }
            } else {
                // 等待绑定目标
                Component waitingText = Component.translatable("tip.noellesroles.avenger.waiting")
                        .withStyle(ChatFormatting.GRAY);
                context.drawString(renderer, waitingText, xOffset, yOffset, CommonColors.GRAY);
            }

            context.pose().popPose();
        });
    }
}