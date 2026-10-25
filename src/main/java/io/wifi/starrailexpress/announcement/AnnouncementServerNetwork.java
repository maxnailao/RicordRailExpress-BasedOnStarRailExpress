package io.wifi.starrailexpress.announcement;

import io.wifi.starrailexpress.announcement.network.AnnouncementRequestC2SPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/** 公告系统服务端网络：处理客户端列表请求 */
public final class AnnouncementServerNetwork {
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(AnnouncementRequestC2SPayload.ID, (payload, context) ->
                context.server().execute(() -> AnnouncementManager.getInstance().syncToPlayer(context.player())));
    }
}