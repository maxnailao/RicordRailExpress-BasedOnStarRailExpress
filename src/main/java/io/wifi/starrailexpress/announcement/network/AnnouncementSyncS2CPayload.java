package io.wifi.starrailexpress.announcement.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 服务端→客户端：公告完整列表（JSON 数组） */
public record AnnouncementSyncS2CPayload(String listJson) implements CustomPacketPayload {
    public static final Type<AnnouncementSyncS2CPayload> ID = new Type<>(SRE.id("sre_announcement_sync"));
    public static final StreamCodec<FriendlyByteBuf, AnnouncementSyncS2CPayload> CODEC = StreamCodec.ofMember(
            (payload, buf) -> buf.writeUtf(payload.listJson, 1048576),
            buf -> new AnnouncementSyncS2CPayload(buf.readUtf(1048576)));

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}