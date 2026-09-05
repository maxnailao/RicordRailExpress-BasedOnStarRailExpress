package io.wifi.starrailexpress.content.vote.client;

import io.wifi.starrailexpress.client.gui.screen.gamemode.role_rotation.RoleRotationScreen;
import io.wifi.starrailexpress.network.RoleRotationSyncS2CPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;

public class RoleRotationClientReceiver {
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(RoleRotationSyncS2CPacket.TYPE, (payload, context) -> {
            Minecraft mc = context.client();
            mc.execute(() -> {

                // 更新客户端缓存
                RoleRotationCache.updateFromPacket(payload);

                if (RoleRotationCache.isSelecting() || RoleRotationCache.getConfirmCountdown() > 0) {
                    if ((mc.screen == null) || mc.screen instanceof ChatScreen || mc.screen instanceof PauseScreen) {
                        mc.setScreen(new RoleRotationScreen());
                    }
                } else {
                    if (mc.screen instanceof RoleRotationScreen) {
                        mc.setScreen(null);
                    }
                }
            });
        });
    }
}
