package org.agmas.noellesroles.content.item;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.content.entity.DurabilityBoatEntity;
import org.agmas.noellesroles.init.ModEntities;
import org.agmas.noellesroles.init.ModItems;

public class DurabilityBoatItem extends Item {
    public DurabilityBoatItem() {
        super(new Item.Properties().stacksTo(1).durability(90));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(itemStack);
        }

        // 创建船实体
        DurabilityBoatEntity boat = new DurabilityBoatEntity(ModEntities.DURABILITY_BOAT, level);
        boat.setPos(player.getX(), player.getY(), player.getZ());
        boat.setYRot(player.getYRot());
        // 传入剩余耐久
        boat.durability = itemStack.getMaxDamage() - itemStack.getDamageValue();
        level.addFreshEntity(boat);

        // 消耗物品
        itemStack.consume(1, player);
        player.awardStat(Stats.ITEM_USED.get(this));

        // 自动骑乘
        player.startRiding(boat);

        return InteractionResultHolder.consume(itemStack);
    }
}
