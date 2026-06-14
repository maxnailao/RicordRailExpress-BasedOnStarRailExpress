package org.agmas.noellesroles.content.item.charge_item;

import io.wifi.starrailexpress.api.ChargeableItem;
import io.wifi.starrailexpress.client.StaminaRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 海盗弯刀的蓄力实现
 * - 最大蓄力时间3tick
 * - 满蓄力时触发屏幕边缘红色效果
 */
public class PirateCutlassChargeItem implements ChargeableItem {
    @Override
    public int getMaxChargeTime(ItemStack stack, Player player) {
        return 3; // 3tick蓄力
    }

    @Override
    public float getChargePercentage(ItemStack stack, Player player, int ticksUsingItem) {
        return Math.min((float) ticksUsingItem / getMaxChargeTime(stack, player), 1f);
    }

    @Override
    public float getMaxStamina(ItemStack stack, Player player) {
        return 3.0f;
    }

    @Override
    public boolean hasSpecialVisualEffects(ItemStack stack, Player player) {
        return true;
    }

    @Override
    public void onFullyCharged(ItemStack stack, Player player) {
        // 满蓄力时触发红色屏幕边缘效果
        StaminaRenderer.triggerScreenEdgeEffect(0xFF4400, 300L, 0.5f);
    }
}
