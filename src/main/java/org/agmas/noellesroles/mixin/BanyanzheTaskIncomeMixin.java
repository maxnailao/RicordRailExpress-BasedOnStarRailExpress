package org.agmas.noellesroles.mixin;

import io.wifi.starrailexpress.api.RoleMethodDispatcher;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import org.agmas.noellesroles.game.roles.killer.banyanzhe.BanyanzhePlayerComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 扮演者被动金钱收入移除
 *
 * 任务派发（RoleMethodDispatcher.callOnFinishQuest）中"平民完成任务 -> 所有杀手获得
 * killerTaskIncome"的被动滴金会把伪装中的扮演者也计入杀手而发放。扮演者不应拥有
 * 被动金钱收入，因此在该方法内拦截发给伪装扮演者的加款。
 * 扮演者本人做任务的 +50 奖励走 role.onFinishQuest，不在本拦截范围内。
 */
@Mixin(RoleMethodDispatcher.class)
public class BanyanzheTaskIncomeMixin {

    @Redirect(method = "callOnFinishQuest(Lnet/minecraft/world/entity/player/Player;Ljava/lang/String;IZ)V", at = @At(value = "INVOKE", target = "Lio/wifi/starrailexpress/cca/SREPlayerShopComponent;addToBalance(I)V"))
    private static void banyanzhe$filterPassiveIncome(SREPlayerShopComponent shop, int amount) {
        if (BanyanzhePlayerComponent.shouldBlockPassiveIncome(shop.getPlayer()))
            return;
        shop.addToBalance(amount);
    }
}
