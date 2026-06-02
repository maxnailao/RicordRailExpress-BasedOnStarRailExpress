package org.agmas.noellesroles.mixin.roles.betterkillerghost;

import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.network.original.KnifeStabPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.content.entity.GhostPhantomEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 允许刀攻击鬼魅幻影实体
 */
@Mixin(KnifeStabPayload.Receiver.class)
public class GhostPhantomKnifePayloadMixin {
    
    @Inject(method = "receive", at = @At("HEAD"), cancellable = true)
    private void handleGhostPhantomTarget(KnifeStabPayload payload, ServerPlayNetworking.Context context, CallbackInfo ci) {
        ServerPlayer player = context.player();
        
        // 检查目标是否是鬼魅幻影实体
        if (player.serverLevel().getEntity(payload.target()) instanceof GhostPhantomEntity phantomEntity) {
            if (phantomEntity.distanceTo(player) > 4.0) return;
            
            // 对幻影造成致命伤害（参照傀儡师机制）
            phantomEntity.playerHurt(player, Noellesroles.id("knife_ghost_phantom"));
            
            phantomEntity.playSound(TMMSounds.ITEM_KNIFE_STAB, 1.0f, 1.0f);
            player.swing(InteractionHand.MAIN_HAND);
            
            if (!player.isCreative()) {
                player.getCooldowns().addCooldown(TMMItems.KNIFE, GameConstants.ITEM_COOLDOWNS.getOrDefault(TMMItems.KNIFE, 600));
            }
            
            ci.cancel();
        }
    }
}
