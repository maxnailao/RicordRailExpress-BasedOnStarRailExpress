package io.wifi.starrailexpress.mixin.server;

import io.wifi.starrailexpress.util.CustomMotdManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.network.protocol.status.ServerStatus.Version;
import net.minecraft.server.MinecraftServer;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftServer.class)
public abstract class MotdMixin {
    @Shadow
    private ServerStatus.Players buildPlayerStatus() {
        return null;
    }

    @Shadow
    @Nullable
    private ServerStatus.Favicon statusIcon;

    @SuppressWarnings("resource")
    @Inject(method = "buildServerStatus", at = @At("HEAD"), cancellable = true)
    private void buildServerStatus(CallbackInfoReturnable<ServerStatus> cir) {
        ServerStatus.Players players = this.buildPlayerStatus();
        MinecraftServer server = (MinecraftServer) (Object) this;
        if (players == null)
            return;// 出错啦
        Component motd = CustomMotdManager.getMotd();
        cir.setReturnValue(
                new ServerStatus(motd, Optional.of(players), Optional.of(Version.current()),
                        Optional.ofNullable(this.statusIcon), server.enforceSecureProfile()));
    }
}
