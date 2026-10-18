package io.wifi.starrailexpress.mixin.item;

import io.wifi.starrailexpress.content.entity.ShenfaArrowEntity;
import io.wifi.starrailexpress.index.TMMEntities;
import io.wifi.starrailexpress.util.ShenfaBowSkinHandler;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 装备神罚皮肤时接管原版弓（Items.BOW）：松手改射 ShenfaArrowEntity，
 * 从而获得天蓝拖尾 + 圣剑天降击杀特效（与自定义弓 SkinableBowItem 行为一致）。
 * 判定走 ShenfaBowSkinHandler.hasShenfaSkinEquipped：原版弓非 SkinableItem，
 * 回落到注册表 key "bow"，恰好命中神罚皮肤的装备键，因此无需额外改判定。
 */
@Mixin(BowItem.class)
public class ShenfaBowMixin {

    @Inject(method = "releaseUsing", at = @At("HEAD"), cancellable = true)
    private void shenfa$releaseUsing(ItemStack stack, Level world, LivingEntity user,
            int remainingUseTicks, CallbackInfo ci) {
        if (world.isClientSide || !(user instanceof Player player)) {
            return;
        }
        if (!ShenfaBowSkinHandler.hasShenfaSkinEquipped(player, stack)) {
            return;
        }
        // 命中神罚：接管本次发射，取消原版逻辑
        ci.cancel();
        BowItem self = (BowItem) (Object) this;
        int charge = self.getUseDuration(stack, user) - remainingUseTicks;
        float power = Mth.clamp(charge / 20.0F, 0.0F, 1.0F);
        if (power < 0.1F) {
            return;
        }
        Vec3 look = player.getLookAngle();
        ShenfaArrowEntity arrow = new ShenfaArrowEntity(TMMEntities.SHENFA_ARROW, world);
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