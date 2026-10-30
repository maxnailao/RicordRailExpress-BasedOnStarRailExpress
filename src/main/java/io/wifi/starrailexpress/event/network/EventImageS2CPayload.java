package io.wifi.starrailexpress.event.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 服务端→客户端：单张活动图片字节（PNG，客户端导入时已压缩到 ≤1024px） */
public record EventImageS2CPayload(String imageId, byte[] data) implements CustomPacketPayload {
    public static final Type<EventImageS2CPayload> ID = new Type<>(SRE.id("sre_event_image"));
    public static final StreamCodec<FriendlyByteBuf, EventImageS2CPayload> CODEC = StreamCodec.ofMember(
            (payload, buf) -> { buf.writeUtf(payload.imageId, 128); buf.writeByteArray(payload.data); },
            buf -> new EventImageS2CPayload(buf.readUtf(128), buf.readByteArray(2_000_000)));

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}