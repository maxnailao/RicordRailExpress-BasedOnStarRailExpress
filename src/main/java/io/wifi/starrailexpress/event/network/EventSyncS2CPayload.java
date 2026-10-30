package io.wifi.starrailexpress.event.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 服务端→客户端：活动完整列表（JSON 数组） */
public record EventSyncS2CPayload(String listJson) implements CustomPacketPayload {
    public static final Type<EventSyncS2CPayload> ID = new Type<>(SRE.id("sre_event_sync"));
    public static final StreamCodec<FriendlyByteBuf, EventSyncS2CPayload> CODEC = StreamCodec.ofMember(
            (payload, buf) -> buf.writeUtf(payload.listJson, 1048576),
            buf -> new EventSyncS2CPayload(buf.readUtf(1048576)));

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}