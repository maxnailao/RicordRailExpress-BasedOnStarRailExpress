package org.agmas.noellesroles.content.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;

/**
 * 皮革嘎的的铁剑
 * - 贴图为原版铁剑
 * - 仅对坠木角色玩家生效
 * - 一局内左键打中坠木三次即可击杀坠木
 * - 对其他玩家无伤害效果
 */
public class PigeSwordItem extends SwordItem {
    public PigeSwordItem() {
        super(Tiers.IRON, new Item.Properties()
                .stacksTo(1)
                .attributes(createAttributes(Tiers.IRON, 0, -2.4f)));
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }
}
