package org.agmas.noellesroles.content.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.init.ModItems;
import org.jetbrains.annotations.NotNull;

/**
 * 沙鹰弹匣
 * - 右键使用：装填沙漠之鹰（找到背包中的沙漠之鹰并回满弹药）
 * - 也可通过手持沙漠之鹰R键装填（由InputHandler处理）
 */
public class DesertEagleMagazineItem extends Item {
    public DesertEagleMagazineItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player user,
            @NotNull InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);

        if (tryReloadDesertEagle(user, stack)) {
            return InteractionResultHolder.consume(stack);
        }
        return InteractionResultHolder.pass(stack);
    }

    private boolean tryReloadDesertEagle(Player user, ItemStack magazineStack) {
        if (user.getCooldowns().isOnCooldown(this)) {
            return false;
        }
        ItemStack pistol = findDesertEagle(user);
        if (pistol.isEmpty()) {
            return false;
        }
        int currentAmmo = DesertEagleItem.getAmmoCount(pistol);
        if (currentAmmo >= DesertEagleItem.MAX_AMMO) {
            user.displayClientMessage(
                    Component.translatable("message.noellesroles.desert_eagle.ammo_full")
                            .withStyle(ChatFormatting.YELLOW),
                    true);
            return false;
        }

        DesertEagleItem.setAmmoCount(pistol, DesertEagleItem.MAX_AMMO);
        if (!user.isCreative()) {
            magazineStack.shrink(1);
        }
        // 2.5秒装填冷却（50 ticks）
        user.getCooldowns().addCooldown(this, 50);
        user.displayClientMessage(
                Component.translatable("message.noellesroles.desert_eagle.reloaded")
                        .withStyle(ChatFormatting.GREEN),
                true);
        return true;
    }

    private ItemStack findDesertEagle(Player user) {
        for (int i = 0; i < user.getInventory().getContainerSize(); i++) {
            ItemStack stack = user.getInventory().getItem(i);
            if (stack.is(ModItems.DESERT_EAGLE)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}
