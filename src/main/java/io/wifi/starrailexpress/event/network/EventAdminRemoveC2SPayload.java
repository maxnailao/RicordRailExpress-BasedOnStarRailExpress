package io.wifi.starrailexpress.event.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 管理员→服务端：删除指定 id 的活动 */
public record EventAdminRemoveC2SPayload(String eventId) implements CustomPacketPayload {
    public static final Type<EventAdminRemoveC2SPayload> ID = new Type<>(SRE.id("sre_event_admin_remove"));
    public static final StreamCodec<FriendlyByteBuf, EventAdminRemoveC2SPayload> CODEC = StreamCodec.ofMember(
            (payload, buf) -> buf.writeUtf(payload.eventId, 128),
            buf -> new EventAdminRemoveC2SPayload(buf.readUtf(128)));

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}