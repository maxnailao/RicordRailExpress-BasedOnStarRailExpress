package org.agmas.noellesroles.mixin.client.roles.banyanzhe;

import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.gui.HudMoodRenderer;
import net.minecraft.client.Minecraft;
import org.agmas.noellesroles.game.roles.killer.banyanzhe.BanyanzhePlayerComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 扮演者任务提示伪装
 *
 * 原版左上角任务提示对杀手阵营显示"你可以假装去"（task.fake），对平民显示"你感觉想去"（task.feel）。
 * 扮演者未回忆前做的是真实平民任务，不能出现"假装"二字，因此将其判定为平民提示。
 */
@Mixin(HudMoodRenderer.TaskRenderer.class)
public class BanyanzheTaskHintMixin {

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lio/wifi/starrailexpress/client/SREClient;isKiller()Z"))
    private boolean banyanzhe$disguisedTaskNotFake() {
        if (!SREClient.isKiller())
            return false;
        var client = Minecraft.getInstance();
        if (client.player == null || SREClient.gameComponent == null)
            return true;
        var actualRole = SREClient.gameComponent.getRole(client.player);
        // 伪装中的扮演者展示为扮演的职业 -> 返回 false 使用平民任务文案；回忆后恢复杀手文案
        return BanyanzhePlayerComponent.getDisplayedRole(client.player, actualRole) == actualRole;
    }
}
