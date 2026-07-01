package org.agmas.noellesroles.client.hud.roles;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.game.roles.innocence.housekeeper.HousekeeperPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

/**
 * 管家 HUD
 * 右下角显示当前家具类型和花费提示
 */
public class HousekeeperHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.HOUSEKEEPER_ID, (context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null) return;

            HousekeeperPlayerComponent comp = HousekeeperPlayerComponent.KEY.get(client.player);

            Font textRenderer = client.font;
            int screenWidth = client.getWindow().getGuiScaledWidth();
            int screenHeight = client.getWindow().getGuiScaledHeight();
            int x = screenWidth - 10;
            int y = screenHeight - 12 * 3;

            // 显示当前家具类型
            Component typeText = Component.translatable(
                    "hud.noellesroles.housekeeper.type." + comp.getCurrentType().typeName);
            Component currentText = Component.translatable("hud.noellesroles.housekeeper.current", typeText);
            context.drawString(textRenderer, currentText,
                    x - textRenderer.width(currentText), y, CommonColors.WHITE);

            // 显示花费
            Component costText = Component.translatable("hud.noellesroles.housekeeper.cost",
                    HousekeeperPlayerComponent.PLACE_COST);
            context.drawString(textRenderer, costText,
                    x - textRenderer.width(costText), y + 12, CommonColors.GREEN);

            // 显示切换提示
            Component switchText = Component.translatable("hud.noellesroles.housekeeper.switch_hint",
                    "Shift+G");
            context.drawString(textRenderer, switchText,
                    x - textRenderer.width(switchText), y + 24, 0xAAAAAA);
        });
    }
}
