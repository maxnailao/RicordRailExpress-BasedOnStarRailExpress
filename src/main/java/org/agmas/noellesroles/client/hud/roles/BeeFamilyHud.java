package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.util.SREClientUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.game.roles.neutral.beefamily.BeeFamilyComponent;
import org.agmas.noellesroles.game.roles.neutral.beefamily.BeeFamilyManager;
import org.agmas.noellesroles.game.roles.neutral.beefamily.BeeFamilyRole;
import org.agmas.noellesroles.role.BounsRoles;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.UUID;

/**
 * 蜜蜂家族的 HUD：三个职业都在左下角显示当前文字频道，
 * 工蜂额外显示剩余存活时间，蜂后额外显示召唤状态与继承者。
 */
public class BeeFamilyHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(BounsRoles.BEE_WASP.identifier(), (context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || SREClient.isPlayerSpectator())
                return;
            int x = 10;
            int y = context.guiHeight() - 20;
            drawChannel(client.font, context, x, y - 10);
        });

        RoleHudRenderCallback.EVENT.register(BounsRoles.BEE_WORKER.identifier(), (context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || SREClient.isPlayerSpectator())
                return;
            var cca = SREAbilityPlayerComponent.KEY.get(client.player);
            int x = 10;
            int y = context.guiHeight() - 20;
            Font font = client.font;

            if (cca.duration > 0) {
                String cdSeconds = String.format("%.1fs", cca.duration / 20f);
                Component cdText = Component
                        .translatable("hud.noellesroles.bee_worker.tip",
                                Component.literal(cdSeconds).withStyle(ChatFormatting.RED))
                        .withStyle(ChatFormatting.YELLOW);
                context.drawString(font, cdText, x, y, 0xffffffff);
            }
            drawChannel(font, context, x, y - 10);
        });

        RoleHudRenderCallback.EVENT.register(BounsRoles.BEE_QUEEN.identifier(), (context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null)
                return;
            var cca = SREAbilityPlayerComponent.KEY.get(client.player);
            var shopcca = SREPlayerShopComponent.KEY.get(client.player);
            int x = 10;
            int y = context.guiHeight() - 20;
            Font font = client.font;

            if (cca.hasCooldown()) {
                Component cdText = Component
                        .translatable("hud.noellesroles.bee_queen.spawn.cooldown", cca.getCooldownStr())
                        .withStyle(ChatFormatting.RED);
                context.drawString(font, cdText, x, y, 0xffffffff);
            } else if (shopcca.balance < BeeFamilyManager.REVIVE_COST_MONEY) {
                Component cdText = Component
                        .translatable("hud.noellesroles.bee_queen.spawn.cost", BeeFamilyManager.REVIVE_COST_MONEY)
                        .withStyle(ChatFormatting.RED);
                context.drawString(font, cdText, x, y, 0xffffffff);
            } else {
                Component cdText = Component.translatable("hud.noellesroles.bee_queen.spawn.ready")
                        .withStyle(ChatFormatting.GREEN);
                context.drawString(font, cdText, x, y, 0xffffffff);
            }

            // 下一次右键尸体会召唤出的职业：增强过是马蜂，否则是工蜂
            var reviveRole = cca.status > 0 ? BounsRoles.BEE_WASP : BounsRoles.BEE_WORKER;
            context.drawString(font,
                    Component.translatable("hud.noellesroles.bee_queen.tip",
                            RoleUtils.getRoleNameWithColor(reviveRole.identifier()))
                            .withStyle(ChatFormatting.GOLD),
                    x, y - 10, 0xffffffff);

            drawChannel(font, context, x, y - 20);

            BeeFamilyComponent data = BeeFamilyComponent.getNullable(client.player);
            if (data == null)
                return;
            UUID target = data.markTarget;
            if (target == null)
                return;
            var info = SREClientUtils.getPlayerInfoByUid(target);
            if (info == null)
                return;
            context.drawString(font,
                    Component.translatable("hud.noellesroles.bee_family.successor",
                            Component.literal(info.getProfile().getName()).withStyle(ChatFormatting.AQUA))
                            .withStyle(ChatFormatting.GOLD),
                    x, y - 30, 0xffffffff);
        });
    }

    /** 频道提示行，三个职业共用。 */
    private static void drawChannel(Font font, io.wifi.utils.client.betterrender.FakeGuiGraphics context, int x,
            int y) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null)
            return;
        context.drawString(font, BeeFamilyRole.getChannelText(client.player), x, y, 0xffffffff);
    }
}
