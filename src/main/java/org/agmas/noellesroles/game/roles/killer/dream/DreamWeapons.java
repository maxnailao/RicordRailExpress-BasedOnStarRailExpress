package org.agmas.noellesroles.game.roles.killer.dream;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.role.ModRoles;

/**
 * Dream（Dream）系列虚拟血量武器的公共判定。
 *
 * <p>
 * 门禁对齐上游：{@code SRERole#canUseSpVanillaWeapon()}（「能用特殊原版武器削减他人虚拟血量」）。
 * Dream 的三把武器（铁斧 / 钻石剑 / 重锤）在商店里只卖给 Dream 本人。
 * 客户端与服务端都要能判定，所以走各自的组件读取路径。
 * </p>
 */
public final class DreamWeapons {

    private DreamWeapons() {
    }

    /** 该玩家是否可以使用 Dream 的虚拟血量武器（两端通用）。 */
    public static boolean canUse(Player player) {
        if (player == null) {
            return false;
        }
        Level level = player.level();
        if (level == null) {
            return false;
        }
        if (level.isClientSide()) {
            // 客户端：职业信息来自已同步的 gameComponent
            var gameComponent = io.wifi.starrailexpress.client.SREClient.gameComponent;
            if (gameComponent == null) {
                return false;
            }
            var role = gameComponent.getRole(player);
            return role != null && role.canUseSpVanillaWeapon();
        }
        // 服务端：直接查世界组件
        var gameWorld = io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(level);
        if (gameWorld == null) {
            return false;
        }
        var role = gameWorld.getRole(player);
        if (role != null && role.canUseSpVanillaWeapon()) {
            return true;
        }
        // 兜底：虚拟血量武器只在 Dream 的商店出售，旗标异常时仍放行本人
        return gameWorld.isRole(player, ModRoles.DREAM);
    }
}
