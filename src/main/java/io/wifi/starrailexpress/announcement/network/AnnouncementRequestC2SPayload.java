package io.wifi.starrailexpress.announcement.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 客户端→服务端：请求公告列表（打开公告界面时发送） */
public record AnnouncementRequestC2SPayload() implements CustomPacketPayload {
    public static final Type<AnnouncementRequestC2SPayload> ID = new Type<>(SRE.id("sre_announcement_request"));
    public static final StreamCodec<FriendlyByteBuf, AnnouncementRequestC2SPayload> CODEC = StreamCodec.ofMember(
            (payload, buf) -> {},
            buf -> new AnnouncementRequestC2SPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
