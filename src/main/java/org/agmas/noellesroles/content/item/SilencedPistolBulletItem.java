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
 * 消音手枪子弹
 * - 右键使用：装填消音手枪（找到背包中的消音手枪并装填1发）
 * - 也可通过手持消音手枪左键装填（由MouseHandlerMixin处理）
 */
public class SilencedPistolBulletItem extends Item {
    public SilencedPistolBulletItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player user,
            @NotNull InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);

        if (tryReloadSilencedPistol(user, stack)) {
            return InteractionResultHolder.consume(stack);
        }
        return InteractionResultHolder.pass(stack);
    }

    private boolean tryReloadSilencedPistol(Player user, ItemStack bulletStack) {
        if (user.getCooldowns().isOnCooldown(this)) {
            return false;
        }
        ItemStack pistol = findSilencedPistol(user);
        if (pistol.isEmpty()) {
            return false;
        }
        int currentAmmo = SilencedPistolItem.getAmmoCount(pistol);
        if (currentAmmo >= SilencedPistolItem.MAX_AMMO) {
            user.displayClientMessage(
                    Component.translatable("message.noellesroles.silenced_pistol.ammo_full")
                            .withStyle(ChatFormatting.YELLOW),
                    true);
            return false;
        }

        SilencedPistolItem.setAmmoCount(pistol, currentAmmo + 1);
        if (!user.isCreative()) {
            bulletStack.shrink(1);
        }
        // 2秒装填冷却
        user.getCooldowns().addCooldown(this, 40);
        user.displayClientMessage(
                Component.translatable("message.noellesroles.silenced_pistol.reloaded")
                        .withStyle(ChatFormatting.GREEN),
                true);
        return true;
    }

    private ItemStack findSilencedPistol(Player user) {
        for (int i = 0; i < user.getInventory().getContainerSize(); i++) {
            ItemStack stack = user.getInventory().getItem(i);
            if (stack.is(ModItems.SILENCED_PISTOL)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}
