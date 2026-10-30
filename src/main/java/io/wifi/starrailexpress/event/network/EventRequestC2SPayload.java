package io.wifi.starrailexpress.event.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 客户端→服务端：请求活动列表（打开活动页 / 登录时） */
public record EventRequestC2SPayload() implements CustomPacketPayload {
    public static final Type<EventRequestC2SPayload> ID = new Type<>(SRE.id("sre_event_request"));
    public static final StreamCodec<FriendlyByteBuf, EventRequestC2SPayload> CODEC = StreamCodec.ofMember(
            (payload, buf) -> { },
            buf -> new EventRequestC2SPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}