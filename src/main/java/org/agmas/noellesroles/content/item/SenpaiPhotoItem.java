package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 前辈的照片
 * - 病娇商店专属道具
 * - 右键凝视照片，恢复 30 点 SAN 值后照片损毁
 */
public class SenpaiPhotoItem extends Item {

    /** 恢复的 SAN 值（0.30 = 30点） */
    public static final float SAN_RESTORE = 0.30f;

    public SenpaiPhotoItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level world, LivingEntity user) {
        super.finishUsingItem(stack, world, user);
        if (!world.isClientSide() && user instanceof Player player) {
            SREPlayerMoodComponent.KEY.get(player).addMood(SAN_RESTORE);
        }
        stack.shrink(1);
        return stack;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return 60;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public SoundEvent getEatingSound() {
        return SoundEvents.BOOK_PAGE_TURN;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        return ItemUtils.startUsingInstantly(world, user, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.LIGHT_PURPLE));
    }
}
