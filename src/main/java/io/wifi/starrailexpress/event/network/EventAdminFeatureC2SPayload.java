package io.wifi.starrailexpress.event.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 管理员→服务端：指定大活动（空串 = 取消所有置顶） */
public record EventAdminFeatureC2SPayload(String eventId) implements CustomPacketPayload {
    public static final Type<EventAdminFeatureC2SPayload> ID = new Type<>(SRE.id("sre_event_admin_feature"));
    public static final StreamCodec<FriendlyByteBuf, EventAdminFeatureC2SPayload> CODEC = StreamCodec.ofMember(
            (payload, buf) -> buf.writeUtf(payload.eventId, 128),
            buf -> new EventAdminFeatureC2SPayload(buf.readUtf(128)));

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}