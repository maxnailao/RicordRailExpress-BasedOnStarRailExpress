package org.agmas.noellesroles.content.item;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.content.entity.CustomFishingHookEntity;

/**
 * 钓鱼佬钓竿 - 外观和功能与原版钓竿一致，但使用自定义奖励池
 */
public class FisherRodItem extends FishingRodItem {

    public FisherRodItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);

        if (player.fishing != null) {
            // 收回鱼钩
            int damage = player.fishing.retrieve(itemStack);
            itemStack.hurtAndBreak(damage, player, hand == InteractionHand.MAIN_HAND
                    ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS,
                    1.0F, 0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
        } else {
            // 抛出鱼钩
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.FISHING_BOBBER_THROW, SoundSource.NEUTRAL,
                    0.5F, 0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
            if (!level.isClientSide) {
                ServerLevel sl = (ServerLevel) level;
                int luckLevel = EnchantmentHelper.getFishingLuckBonus(sl, itemStack, player);
                int lureLevel = (int) (EnchantmentHelper.getFishingTimeReduction(sl, itemStack, player) * 20.0f);
                CustomFishingHookEntity hook = new CustomFishingHookEntity(
                        player, level, luckLevel, lureLevel);
                hook.setYRot(player.getYRot());
                hook.setXRot(player.getXRot());
                level.addFreshEntity(hook);
            }
        }

        return InteractionResultHolder.sidedSuccess(itemStack, level.isClientSide());
    }

    @Override
    public int getEnchantmentValue() {
        return 1;
    }
}
