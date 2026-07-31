package org.agmas.noellesroles.mixin.roles.jinghuazhe;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MilkBucketItem;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MilkBucketItem.class)
public class MilkBucketNoLeftoverMixin {

    @Inject(method = "finishUsingItem", at = @At("RETURN"), cancellable = true)
    private void noellesroles$purifierNoBucketLeftover(ItemStack stack, Level world, LivingEntity user, CallbackInfoReturnable<ItemStack> cir) {
        if (world.isClientSide()) return;
        if (!(user instanceof ServerPlayer player)) return;
        var gameComponent = SREGameWorldComponent.KEY.get(player.level());
        if (!gameComponent.isRole(player, ModRoles.JINGHUAZHE)) return;

        cir.setReturnValue(ItemStack.EMPTY);
    }
}
