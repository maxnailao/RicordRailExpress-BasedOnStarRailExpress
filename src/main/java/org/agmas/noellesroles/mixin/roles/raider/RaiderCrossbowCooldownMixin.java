package org.agmas.noellesroles.mixin.roles.raider;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.roles.killer.raider.RaiderPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 掠夺者弩冷却Mixin
 * 掠夺者的弩在击杀玩家后进入30秒冷却，冷却期间无法使用弩
 * 疯魔期间无冷却限制
 */
@Mixin(CrossbowItem.class)
public class RaiderCrossbowCooldownMixin {

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void noellesroles$onCrossbowUse(Level world, Player user, InteractionHand hand,
            CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        if (world.isClientSide || SRE.isLobby) {
            return;
        }

        if (!(user instanceof ServerPlayer sp)) {
            return;
        }

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(world);
        if (!gameWorld.isRole(user, ModRoles.LUEDUOZHE)) {
            return;
        }

        RaiderPlayerComponent comp = ModComponents.RAIDER.get(sp);

        // 疯魔期间无冷却限制
        if (comp.inFrenzy) {
            return;
        }

        if (comp.crossbowKillCooldown > 0) {
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.raider.crossbow_cooldown",
                            (comp.crossbowKillCooldown + 19) / 20)
                            .withStyle(ChatFormatting.RED),
                    true);

            cir.setReturnValue(InteractionResultHolder.fail(user.getItemInHand(hand)));
            cir.cancel();
        }
    }
}
