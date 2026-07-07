package io.wifi.starrailexpress.client.render.hud.stamina.utils;

import org.agmas.noellesroles.init.ModEffects;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.PlayerStaminaGetter;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
/**
 * 客户端使用
 * StaminaProvider
 */
public class StaminaProvider {
    public float getCurrentStamina(Player clientPlayerEntity) {
        if (!clientPlayerEntity.level().isClientSide
                || !(clientPlayerEntity instanceof PlayerStaminaGetter provider))
            return 0;
        if (!GameUtils.isGameRunning(clientPlayerEntity))
            return 0;
        if (!GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(clientPlayerEntity))
            return 0;
        return provider.starrailexpress$getStamina();
    }

    public float getMaxStamina(Player player) {
        if (!SREClient.gameComponent.isRunning()) {
            return 0;
        }
        SREGameWorldComponent gameComponent = SREGameWorldComponent.getInstance(player);
        if (GameUtils.isPlayerAliveAndSurvival(player) && gameComponent != null) {
            SRERole role = gameComponent.getRole(player);
            if (role == null) {
                return 0;
            }
            float baseMaxStamina = role.getMaxSprintTime(player);
            if (baseMaxStamina == Integer.MAX_VALUE || ModEffects.hasInfiniteStamina(player)) {
                return Integer.MAX_VALUE;
            }
            return baseMaxStamina * ModEffects.getStaminaCapacityMultiplier(player);
        }
        return 0;
    }

    public float getStaminaPercentage(Player clientPlayerEntity) {
        float max = getMaxStamina(clientPlayerEntity);
        if (max <= 0 || max == Integer.MAX_VALUE) {
            return 1f;
        }
        return Mth.clamp(getCurrentStamina(clientPlayerEntity) / max, 0f, 1f);
    }
}
