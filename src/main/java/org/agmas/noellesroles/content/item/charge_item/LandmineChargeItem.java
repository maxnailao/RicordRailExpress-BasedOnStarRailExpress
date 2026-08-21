package org.agmas.noellesroles.content.item.charge_item;

import io.wifi.starrailexpress.api.ChargeableItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.content.item.LandmineItem;

/**
 * 反人员地雷的蓄力实现
 * - 长按右键 3 秒完成布设
 */
public class LandmineChargeItem implements ChargeableItem {
    @Override
    public int getMaxChargeTime(ItemStack stack, Player player) {
        return LandmineItem.PLACE_TICKS;
    }

    @Override
    public float getChargePercentage(ItemStack stack, Player player, int ticksUsingItem) {
        return Math.min((float) ticksUsingItem / getMaxChargeTime(stack, player), 1f);
    }

    @Override
    public float getMaxStamina(ItemStack stack, Player player) {
        return LandmineItem.PLACE_TICKS;
    }
}
