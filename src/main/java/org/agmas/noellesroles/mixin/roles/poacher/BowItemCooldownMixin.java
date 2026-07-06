package org.agmas.noellesroles.mixin.roles.poacher;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.roles.killer.poacher.PoacherPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 盗猎者弓射击冷却Mixin
 * 监听弓的使用事件,在冷却期间阻止使用
 */
@Mixin(BowItem.class)
public class BowItemCooldownMixin {
    
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void noellesroles$onBowUse(Level world, Player user, InteractionHand hand, CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        // 只在服务端执行
        if (world.isClientSide || SRE.isLobby) {
            return;
        }
        
        if (!(user instanceof ServerPlayer sp)) {
            return;
        }
        
        // 检查是否是盗猎者
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(world);
        if (!gameWorld.isRole(user, ModRoles.POACHER)) {
            return;
        }
        
        // 获取组件
        PoacherPlayerComponent comp = ModComponents.POACHER.get(sp);
        
        // 检查是否在冷却中
        if (comp.bowShootCooldown > 0) {
            // 显示冷却提示
            sp.displayClientMessage(
                Component.translatable("message.noellesroles.poacher.bow_cooldown", 
                    (comp.bowShootCooldown + 19) / 20)
                    .withStyle(ChatFormatting.RED),
                true
            );
            
            // 取消使用
            cir.setReturnValue(InteractionResultHolder.fail(user.getItemInHand(hand)));
            cir.cancel();
        } else {
            // 不在冷却中，设置20秒冷却(400 tick)
            comp.bowShootCooldown = 400;
            comp.sync();
        }
    }
}
