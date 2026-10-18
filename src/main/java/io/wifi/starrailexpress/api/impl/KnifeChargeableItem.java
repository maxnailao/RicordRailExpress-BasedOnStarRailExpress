package io.wifi.starrailexpress.api.impl;

import java.awt.Color;

import io.wifi.starrailexpress.api.ChargeableItem;
import io.wifi.starrailexpress.client.StaminaRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 刀的蓄力实现
 */
public class KnifeChargeableItem implements ChargeableItem {
    @Override
    public int getMaxChargeTime(ItemStack stack, Player player) {
        return 8; // 10刻（0.5秒）蓄力时间
    }

    @Override
    public float getChargePercentage(ItemStack stack, Player player, int ticksUsingItem) {
        return Math.min((float) ticksUsingItem / getMaxChargeTime(stack, player), 1f);
    }

    @Override
    public float getMaxStamina(ItemStack stack, Player player) {
        return 8.0f;
    }

    @Override
    public boolean hasSpecialVisualEffects(ItemStack stack, Player player) {
        return true;
    }

    @Override
    public void onFullyCharged(ItemStack stack, Player player) {
        // 恶魔之刃→粉紫色闪光；星光→黑色闪光；其余→白色闪光
        int color;
        if (io.wifi.starrailexpress.util.EmozhidaoSkinHandler.hasEmozhidaoSkinEquipped(player, stack)) {
            color = 0xE066FF;
        } else if (io.wifi.starrailexpress.client.XingguangClientEffects.SKIN_ID
                .equals(stack.get(io.wifi.starrailexpress.index.SREDataComponentTypes.SKIN))) {
            color = 0x000000;
        } else {
            color = Color.WHITE.getRGB();
        }
        StaminaRenderer.triggerScreenEdgeEffect(color, 300L, 0.5f);
        // 默认会触发
    }
}