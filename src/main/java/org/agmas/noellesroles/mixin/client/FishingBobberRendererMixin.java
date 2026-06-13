package org.agmas.noellesroles.mixin.client;

import net.minecraft.client.renderer.entity.FishingHookRenderer;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 修复自定义钓竿（FisherRodItem）钓鱼线端点位于玩家左手的问题。
 * 原版 FishingHookRenderer.getPlayerHandPos 使用 ItemStack.is(Items.FISHING_ROD) 精确匹配，
 * 导致自定义 FisherRodItem 无法通过检查，线端点被错误地渲染在左手。
 * 此 Mixin 将精确匹配改为 instanceof FishingRodItem 检查。
 */
@Mixin(FishingHookRenderer.class)
public class FishingBobberRendererMixin {

    @Redirect(
        method = "getPlayerHandPos",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z")
    )
    private boolean noellesroles$checkCustomFishingRod(ItemStack stack, Item item) {
        return stack.getItem() instanceof FishingRodItem;
    }
}
