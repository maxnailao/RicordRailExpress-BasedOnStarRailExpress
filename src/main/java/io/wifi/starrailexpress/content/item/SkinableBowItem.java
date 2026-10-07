package io.wifi.starrailexpress.content.item;

import io.wifi.starrailexpress.util.ItemSkinManager;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * 弓皮肤载体。
 * 现已实现"拉弓蓄力"状态：右键长按会进入 isUsingItem() 使用状态，
 * 从而触发 GeneralModel 中的 pulling_0/1/2 三档皮肤渲染。
 * 真正的发射箭矢逻辑可在 releaseUsing 中补充（弓皮肤特效钩子同样可在此扩展）。
 */
public class SkinableBowItem extends SkinableItem {

    public SkinableBowItem(Properties settings) {
        super(settings.stacksTo(1));
    }

    @Override
    public String getItemSkinType() {
        return ItemSkinManager.SkinTypes.BOW;
    }

    @Override
    public @NotNull UseAnim getUseAnimation(@NotNull ItemStack stack) {
        return UseAnim.BOW;
    }

    /** 右键开始拉弓蓄力：进入使用状态，拉弓三档皮肤才会显示 */
    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level world, @NotNull Player user,
            @NotNull InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        user.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    /** 蓄力时长给足，使 getTicksUsingItem() 能递增到 20+（满弓档） */
    @Override
    public int getUseDuration(@NotNull ItemStack stack, @NotNull LivingEntity user) {
        return 72000;
    }

    /** 松手：TODO 依据蓄力时长 getUseDuration(stack,user) - remainingUseTicks 发射箭矢 */
    @Override
    public void releaseUsing(@NotNull ItemStack stack, @NotNull Level world, @NotNull LivingEntity user,
            int remainingUseTicks) {
        // 预留：此处加入射箭 / 弓皮肤击杀特效逻辑
    }
}