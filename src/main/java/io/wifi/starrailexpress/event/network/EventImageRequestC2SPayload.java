package io.wifi.starrailexpress.event.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 客户端→服务端：请求单张活动图片（本地缓存缺失时） */
public record EventImageRequestC2SPayload(String imageId) implements CustomPacketPayload {
    public static final Type<EventImageRequestC2SPayload> ID = new Type<>(SRE.id("sre_event_image_request"));
    public static final StreamCodec<FriendlyByteBuf, EventImageRequestC2SPayload> CODEC = StreamCodec.ofMember(
            (payload, buf) -> buf.writeUtf(payload.imageId, 128),
            buf -> new EventImageRequestC2SPayload(buf.readUtf(128)));

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}