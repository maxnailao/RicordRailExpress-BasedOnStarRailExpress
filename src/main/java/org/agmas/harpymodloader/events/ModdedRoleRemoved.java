package org.agmas.harpymodloader.events;


import io.wifi.starrailexpress.api.RoleMethodDispatcher;
import io.wifi.starrailexpress.api.SRERole;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

public interface ModdedRoleRemoved {

    Event<ModdedRoleRemoved> EVENT = createArrayBacked(ModdedRoleRemoved.class, listeners -> (player, role) -> {
        if (role == null) {
            return;
        }
        // 先清理职业数据 / 调用 role.onRemove，再通知各监听器
        if (player instanceof ServerPlayer serverPlayer) {
            RoleMethodDispatcher.onRemoveRole(role, serverPlayer.getServer(), serverPlayer);
        }

        for (ModdedRoleRemoved listener : listeners) {
            listener.removeModdedRole(player, role);
        }
    });

    void removeModdedRole(Player player, SRERole role);
}