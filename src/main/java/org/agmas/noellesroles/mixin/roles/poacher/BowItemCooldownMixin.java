package org.agmas.noellesroles.mixin.roles.poacher;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.roles.killer.poacher.PoacherPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BowItem.class)
public class BowItemCooldownMixin {
    
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void noellesroles$onBowUse(Level world, Player user, InteractionHand hand, CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        if (world.isClientSide || SRE.isLobby) {
            return;
        }
        
        if (!(user instanceof ServerPlayer sp)) {
            return;
        }
        
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(world);
        if (!gameWorld.isRole(user, ModRoles.POACHER)) {
            return;
        }
        
        PoacherPlayerComponent comp = ModComponents.POACHER.get(sp);
        
        if (comp.bowShootCooldown > 0) {
            sp.displayClientMessage(
                Component.translatable("message.noellesroles.poacher.bow_cooldown", 
                    (comp.bowShootCooldown + 19) / 20)
                    .withStyle(ChatFormatting.RED),
                true
            );
            
            cir.setReturnValue(InteractionResultHolder.fail(user.getItemInHand(hand)));
            cir.cancel();
        }
    }

    @Inject(method = "releaseUsing", at = @At("RETURN"))
    private void noellesroles$onBowRelease(ItemStack stack, Level level, LivingEntity entity, int timeCharged, CallbackInfo ci) {
        if (level.isClientSide || SRE.isLobby) {
            return;
        }

        if (!(entity instanceof ServerPlayer sp)) {
            return;
        }

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(level);
        if (!gameWorld.isRole(sp, ModRoles.POACHER)) {
            return;
        }

        PoacherPlayerComponent comp = ModComponents.POACHER.get(sp);
        if (comp.bowShootCooldown > 0) {
            return;
        }

        int duration = 72000 - timeCharged;
        float charge = BowItem.getPowerForTime(duration);
        if (charge < 0.1f) {
            return;
        }

        comp.bowShootCooldown = 400;
        comp.sync();
        sp.getCooldowns().addCooldown(Items.BOW, 400);
    }
}
