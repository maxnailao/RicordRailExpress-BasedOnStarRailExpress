package org.agmas.noellesroles.content.item.charge_item;

import io.wifi.starrailexpress.api.ChargeableItem;
import io.wifi.starrailexpress.client.StaminaRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.content.item.WolfKnifeItem;

/**
 * 狼刀的蓄力实现（举刀条）
 * 蓄力时长跟随状态变化：正常8刻 / 黑灯5刻（加快65%）/ 午夜狼嚎2刻（极快）。
 */
public class WolfKnifeChargeItem implements ChargeableItem {
    @Override
    public int getMaxChargeTime(ItemStack stack, Player player) {
        return WolfKnifeItem.getMinChargeTicks(player);
    }

    @Override
    public float getChargePercentage(ItemStack stack, Player player, int ticksUsingItem) {
        return Math.min((float) ticksUsingItem / getMaxChargeTime(stack, player), 1f);
    }

    @Override
    public float getMaxStamina(ItemStack stack, Player player) {
        return (float) getMaxChargeTime(stack, player);
    }

    @Override
    public boolean hasSpecialVisualEffects(ItemStack stack, Player player) {
        return true;
    }

    @Override
    public void onFullyCharged(ItemStack stack, Player player) {
        // 触发屏幕边缘效果（同普通刀）
        StaminaRenderer.triggerScreenEdgeEffect(java.awt.Color.WHITE.getRGB(), 300L, 0.5f);
    }
}
