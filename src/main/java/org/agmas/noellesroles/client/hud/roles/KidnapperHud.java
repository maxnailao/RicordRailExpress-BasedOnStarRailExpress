package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.game.roles.neutral.kidnapper.KidnapperPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

/**
 * 绑匪 HUD（屏幕左下角）：
 * - 当前绑架人数 X / 6
 * - 是否达到开启『审判阶段』的条件 / 审判进行中
 * - 审判阶段额外显示枪冷却缩减百分比
 */
public class KidnapperHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.KIDNAPPER_ID, (context, tickCounter) -> {
            Minecraft client = Minecraft.getInstance();
            if (SREClient.isPlayerSpectator())
                return;
            if (!SREClient.isPlayerAliveAndInSurvival())
                return;

            KidnapperPlayerComponent comp = KidnapperPlayerComponent.KEY.get(client.player);
            Font textRenderer = client.font;

            // 逃脱提示 - 屏幕最上方居中（有人质挣脱/被救/逃脱时短暂显示）
            if (comp.escapeNoticeTicks > 0) {
                Component escapedText = Component.translatable("hud.noellesroles.kidnapper.escaped")
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
                context.drawCenteredString(textRenderer, escapedText, context.guiWidth() / 2, 8, 0xFFFF5555);
            }

            // 渲染位置 - 左下角
            int screenHeight = client.getWindow().getGuiScaledHeight();
            int x = 10;
            int y = screenHeight - 70;

            // 标题
            Component title = Component.translatable("hud.noellesroles.kidnapper.title")
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
            context.drawString(textRenderer, title, x, y, 0xFFFFFF);
            y += 12;

            // 绑架人数
            int count = comp.kidnappedCount;
            int countColor = count >= KidnapperPlayerComponent.JUDGMENT_REQUIRED_COUNT
                    ? 0xFF5555
                    : 0xFFFFFF;
            Component countText = Component.translatable("hud.noellesroles.kidnapper.count",
                    count, KidnapperPlayerComponent.JUDGMENT_REQUIRED_COUNT);
            context.drawString(textRenderer, countText, x, y, countColor);
            y += 12;

            // 护盾层数
            int shield = SREArmorPlayerComponent.KEY.get(client.player).getArmor();
            Component shieldText = Component.translatable("hud.noellesroles.kidnapper.shield", shield);
            context.drawString(textRenderer, shieldText, x, y, shield > 0 ? 0x55FFFF : 0xFFFFFF);
            y += 12;

            // 绑架技能冷却（审判阶段捆绑技能已禁用；拖拽人质期间冷却冻结）
            if (comp.judgmentPhase) {
                Component disabledText = Component.translatable("hud.noellesroles.kidnapper.skill_disabled")
                        .withStyle(ChatFormatting.GRAY);
                context.drawString(textRenderer, disabledText, x, y, 0xFFFFFF);
            } else if (comp.draggingTarget != null) {
                Component draggingText = Component.translatable("hud.noellesroles.kidnapper.dragging")
                        .withStyle(ChatFormatting.YELLOW);
                context.drawString(textRenderer, draggingText, x, y, 0xFFFFFF);
            } else if (comp.kidnapCooldown > 0) {
                Component skillCdText = Component.translatable("hud.noellesroles.kidnapper.skill_cd",
                        (int) Math.ceil(comp.kidnapCooldown / 20.0));
                context.drawString(textRenderer, skillCdText, x, y, 0xFF5555);
            } else {
                Component skillReadyText = Component.translatable("hud.noellesroles.kidnapper.skill_ready");
                context.drawString(textRenderer, skillReadyText, x, y, 0x55FF55);
            }
            y += 12;

            // 审判阶段状态
            if (comp.judgmentPhase) {
                // 已进入审判阶段（显示剩余时间）
                Component activeText = Component.translatable("hud.noellesroles.kidnapper.judgment_active",
                                (int) Math.ceil(comp.judgmentRemainingTicks / 20.0))
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
                context.drawString(textRenderer, activeText, x, y, 0xFFFFFF);
                y += 12;
                int reduction = Math.round(
                        (1.0f - KidnapperPlayerComponent.gunCooldownMultiplier(comp.judgmentKidnapCount)) * 100);
                Component cdText = Component.translatable("hud.noellesroles.kidnapper.gun_cd", reduction);
                context.drawString(textRenderer, cdText, x, y, 0xFFFF55);
            } else if (count >= KidnapperPlayerComponent.JUDGMENT_REQUIRED_COUNT) {
                // 条件已达成，提示如何进入
                Component readyText = Component.translatable("hud.noellesroles.kidnapper.judgment_ready")
                        .withStyle(ChatFormatting.GREEN);
                context.drawString(textRenderer, readyText, x, y, 0xFFFFFF);
                y += 12;
                Component hintText = Component.translatable("hud.noellesroles.kidnapper.judgment_hint")
                        .withStyle(ChatFormatting.GRAY);
                context.drawString(textRenderer, hintText, x, y, 0xFFFFFF);
            } else {
                // 还差几个人
                int need = KidnapperPlayerComponent.JUDGMENT_REQUIRED_COUNT - count;
                Component needText = Component.translatable("hud.noellesroles.kidnapper.judgment_need", need);
                context.drawString(textRenderer, needText, x, y, 0xAAAAAA);
            }
        });
    }
}