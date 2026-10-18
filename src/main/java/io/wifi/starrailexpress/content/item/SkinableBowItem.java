package io.wifi.starrailexpress.content.item;

import io.wifi.starrailexpress.content.entity.ShenfaArrowEntity;
import io.wifi.starrailexpress.index.TMMEntities;
import io.wifi.starrailexpress.util.ShenfaBowSkinHandler;
import io.wifi.starrailexpress.util.ItemSkinManager;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
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

    /** 松手：按蓄力时长发射箭矢（神罚皮肤 → 白拖尾箭；否则普通箭），不消耗箭矢物品 */
    @Override
    public void releaseUsing(@NotNull ItemStack stack, @NotNull Level world, @NotNull LivingEntity user,
            int remainingUseTicks) {
        if (!(user instanceof Player player) || world.isClientSide) {
            return;
        }
        int charge = getUseDuration(stack, user) - remainingUseTicks;
        float power = Mth.clamp(charge / 20.0F, 0.0F, 1.0F);
        if (power < 0.1F) {
            return;
        }
        Vec3 look = player.getLookAngle();
        Arrow arrow = ShenfaBowSkinHandler.hasShenfaSkinEquipped(player, stack)
                ? new ShenfaArrowEntity(TMMEntities.SHENFA_ARROW, world)
                : new Arrow(EntityType.ARROW, world);
        arrow.setPos(player.getX() + look.x * 0.6,
                player.getEyeY() - user.getBbHeight() * 0.1F + look.y * 0.6,
                player.getZ() + look.z * 0.6);
        arrow.setOwner(player);
        arrow.setBaseDamage(2.0 + power * power * 8.0);
        arrow.setDeltaMovement(look.x * power * 3.0, look.y * power * 3.0, look.z * power * 3.0);
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        world.addFreshEntity(arrow);
        world.playSound(null, player, SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0F,
                1.0F / (world.random.nextFloat() - world.random.nextFloat()) * 0.2F + 1.0F);
    }
}