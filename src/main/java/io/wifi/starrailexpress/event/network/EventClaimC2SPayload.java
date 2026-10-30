package io.wifi.starrailexpress.event.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 玩家→服务端：领取活动任务包的无条件奖励（packKey = eventId#包下标） */
public record EventClaimC2SPayload(String packKey) implements CustomPacketPayload {
    public static final Type<EventClaimC2SPayload> ID = new Type<>(SRE.id("sre_event_claim"));
    public static final StreamCodec<FriendlyByteBuf, EventClaimC2SPayload> CODEC = StreamCodec.ofMember(
            (payload, buf) -> buf.writeUtf(payload.packKey, 160),
            buf -> new EventClaimC2SPayload(buf.readUtf(160)));

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}