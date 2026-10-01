package org.agmas.noellesroles.mixin.roles.kidnapper;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.agmas.noellesroles.game.roles.neutral.kidnapper.KidnappedCCA;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public class KidnapperChatMixin {

    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleChat", at = @At("HEAD"), cancellable = true)
    private void blockKidnappedChat(ServerboundChatPacket packet, CallbackInfo ci) {
        if (KidnappedCCA.get(player).isKidnapped) {
            player.sendSystemMessage(Component.translatable("message.noellesroles.kidnapped.muted")
                    .withStyle(ChatFormatting.RED));
            ci.cancel();
        }
    }
}