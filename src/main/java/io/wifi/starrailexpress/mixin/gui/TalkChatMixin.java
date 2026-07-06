package io.wifi.starrailexpress.mixin.gui;

import com.kreezcraft.localizedchat.commands.TalkChat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TalkChat.class)
public class TalkChatMixin {
    @Inject(method = "isPlayerOpped", at = @At("HEAD"), cancellable = true)
    private static void execute(MinecraftServer server, ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(player.hasPermissions(2));
    }
}
