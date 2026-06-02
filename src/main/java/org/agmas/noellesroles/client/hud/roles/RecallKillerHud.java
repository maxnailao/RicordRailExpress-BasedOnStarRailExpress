package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.roles.killer.recall_killer.RecallKillerPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

/**
 * 召回杀手 HUD
 * - 显示：放置/传送提示 + 冷却提示
 * - 不显示金币（召回杀手不花钱）
 */
public class RecallKillerHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.RECALL_KILLER_ID, (context, tickCounter) -> {
            Minecraft client = Minecraft.getInstance();
            if (SREClient.isPlayerSpectator())
                return;
            if (client.player == null)
                return;

            SREAbilityPlayerComponent abilityPlayerComponent = SREAbilityPlayerComponent.KEY.get(client.player);

            // 注意：召回杀手组件的 Key 在 ModComponents 里集中定义
            RecallKillerPlayerComponent comp = ModComponents.RECALL_KILLER.get(client.player);

            int drawY = context.guiHeight();

            Component line;
            if (!comp.placed) {
                line = Component.translatable(
                        "tip.recall_killer.place",
                        NoellesrolesClient.abilityBind.getTranslatedKeyMessage()
                );
            } else {
                line = Component.translatable(
                        "tip.recall_killer.teleport",
                        NoellesrolesClient.abilityBind.getTranslatedKeyMessage()
                );
            }

            if (abilityPlayerComponent.cooldown > 0) {
                line = Component.translatable("tip.noellesroles.cooldown",
                        abilityPlayerComponent.cooldown / 20);
            }

            drawY -= client.font.wordWrapHeight(line, 999999);
            context.drawString(client.font, line,
                    context.guiWidth() - client.font.width(line),
                    drawY,
                    ModRoles.RECALL_KILLER.color()
            );
        });
    }
}