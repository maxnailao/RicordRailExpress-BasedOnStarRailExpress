package io.wifi.starrailexpress.content.item;

import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class BlackoutItem extends Item {

    public BlackoutItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        var item = user.getItemInHand(hand);
        if (user.getCooldowns().isOnCooldown(this)) {
            InteractionResultHolder.pass(item);
        }
        if (!user.isSpectator())
            SREPlayerShopComponent.useBlackout(user);
        if (user.isCreative()) {
            return InteractionResultHolder.consume(item);
        }
        return InteractionResultHolder.consume(item.consumeAndReturn(1, user));
        // return super.use(world, user, hand);
    }
}
