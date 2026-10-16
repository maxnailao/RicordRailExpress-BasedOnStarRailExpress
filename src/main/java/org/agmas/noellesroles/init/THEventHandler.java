package org.agmas.noellesroles.init;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameConstants.DeathReasons;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.role.touhou.RedHouseRoles;

import java.util.ArrayList;
import java.util.Collections;

public class THEventHandler {
    public static void registerEvents() {
        AllowPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            if (killer == null)
                return true;
            var gameWorldComponent = SREGameWorldComponent.KEY.get(victim.level());
            if (gameWorldComponent.isRole(killer, RedHouseRoles.FURANDORU)) {
                if (gameWorldComponent.isRole(victim, RedHouseRoles.REMILIA)
                        || gameWorldComponent.isRole(victim, RedHouseRoles.PACHURI)
                        || gameWorldComponent.isRole(victim, RedHouseRoles.MAID_SAKUYA))
                    return false;
            } else if (gameWorldComponent.isRole(killer, RedHouseRoles.REMILIA)) {
                if (gameWorldComponent.isRole(victim, RedHouseRoles.FURANDORU))
                    return false;
            } else if (gameWorldComponent.isRole(killer, RedHouseRoles.MAID_SAKUYA)) {
                if (gameWorldComponent.isRole(victim, RedHouseRoles.FURANDORU)
                        || gameWorldComponent.isRole(victim, RedHouseRoles.REMILIA))
                    return false;
            } else if (gameWorldComponent.isRole(killer, RedHouseRoles.PACHURI)) {
                if (gameWorldComponent.isRole(victim, RedHouseRoles.FURANDORU))
                    return false;
            }
            return true;
        });
    }

    public static SRERole getRandomRole() {
        var roles = Noellesroles.getEnableAndAvailableRoles(true);
        if (roles.isEmpty())
            return TMMRoles.KILLER;
        Collections.shuffle(roles);
        return roles.getFirst();
    }

    public static String getRandomDeathReason() {
        // 移植自 SRE THRemiliaRole.getRandomDeathReason：使用完整的死亡原因集合，
        // 而不是原来那 7 个硬编码的原因。
        ArrayList<ResourceLocation> list = new ArrayList<>(
                io.wifi.starrailexpress.game.GameConstants.DeathReasons.getAllDeathReasons());
        if (list.isEmpty()) {
            return DeathReasons.GENERIC.toString();
        }
        Collections.shuffle(list);
        return list.getFirst().toString();
    }
}
