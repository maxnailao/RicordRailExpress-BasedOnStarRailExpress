package org.agmas.noellesroles.mixin;

import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 修复自定义钓竿（FisherRodItem）甩杆后鱼钩立即消失的问题。
 * 原版 FishingHook.shouldStopFishing 使用 ItemStack.is(Items.FISHING_ROD) 精确匹配，
 * 导致自定义 FisherRodItem 无法通过检查，鱼钩在第一帧就被 discard。
 * 此 Mixin 将精确匹配改为 instanceof FishingRodItem 检查。
 */
@Mixin(FishingHook.class)
public class FishingHookShouldStopMixin {

    @Redirect(
        method = "shouldStopFishing",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z")
    )
    private boolean noellesroles$checkFishingRod(ItemStack stack, Item item) {
        return stack.getItem() instanceof FishingRodItem;
    }
}
