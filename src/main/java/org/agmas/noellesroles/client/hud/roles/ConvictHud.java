package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.game.roles.neutral.convict.ConvictPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

/**
 * 重刑犯 HUD：右下角显示当前选择的道路。
 *
 * <p>改过自新（帮助乘客方）/ 毁灭一切（独自获胜）/ 加入组织（帮助杀手方）；
 * 尚未抉择时显示剩余秒数（超时默认毁灭一切）。</p>
 *
 * <p>由 {@link RoleHudRenderCallback} 按当前角色分发，因此只对重刑犯本人渲染。
 * 重刑犯没有 RoleSkill / RolePassive，{@code UnifiedSkillHud} 会在两者皆空时提前返回，
 * 故右下角不会被占用。</p>
 */
public final class ConvictHud {

    /** 距屏幕右 / 下边缘的留白 */
    private static final int MARGIN_RIGHT = 6;
    private static final int MARGIN_BOTTOM = 20;
    /** 文本周围的半透明底板内边距 */
    private static final int PAD_X = 4;
    private static final int PAD_Y = 2;
    private static final int BG_COLOR = 0x60000000;

    /** 改过自新 - 绿 */
    private static final int COLOR_REFORM = 0x55FF55;
    /** 毁灭一切 - 红 */
    private static final int COLOR_DESTROY = 0xFF5555;
    /** 加入组织 - 金 */
    private static final int COLOR_JOIN = 0xFFAA00;
    /** 尚未抉择 - 灰 */
    private static final int COLOR_NONE = 0xAAAAAA;

    private ConvictHud() {
    }

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.CONVICT_ID, (context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || SREClient.isPlayerSpectator()) {
                return;
            }
            ConvictPlayerComponent comp = ConvictPlayerComponent.KEY.maybeGet(client.player).orElse(null);
            if (comp == null) {
                return;
            }
            Component line;
            int color;
            switch (comp.choice) {
                case REFORM -> {
                    line = Component.translatable("hud.noellesroles.convict.path.reform");
                    color = COLOR_REFORM;
                }
                case DESTROY -> {
                    line = Component.translatable("hud.noellesroles.convict.path.destroy");
                    color = COLOR_DESTROY;
                }
                case JOIN -> {
                    line = Component.translatable("hud.noellesroles.convict.path.join");
                    color = COLOR_JOIN;
                }
                default -> {
                    // NONE：尚未抉择，显示倒计时（向上取整到秒）
                    line = Component.translatable("hud.noellesroles.convict.path.none",
                            (comp.choiceTimeLeftTicks + 19) / 20);
                    color = COLOR_NONE;
                }
            }
            Font font = client.font;
            int width = font.width(line);
            int x = context.guiWidth() - width - MARGIN_RIGHT;
            int y = context.guiHeight() - MARGIN_BOTTOM;
            context.fill(x - PAD_X, y - PAD_Y, x + width + PAD_X, y + font.lineHeight + PAD_Y, BG_COLOR);
            context.drawString(font, line, x, y, color, false);
        });
    }
}
