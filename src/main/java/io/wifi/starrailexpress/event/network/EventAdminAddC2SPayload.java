package io.wifi.starrailexpress.event.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 管理员→服务端：添加活动（含可选图片字节；置顶走 EventAdminFeatureC2SPayload） */
public record EventAdminAddC2SPayload(String title, String content, String rewards,
                                      String startDate, String endDate, String condition,
                                      String imageId, byte[] imageData) implements CustomPacketPayload {
    public static final Type<EventAdminAddC2SPayload> ID = new Type<>(SRE.id("sre_event_admin_add"));
    public static final StreamCodec<FriendlyByteBuf, EventAdminAddC2SPayload> CODEC = StreamCodec.ofMember(
            (payload, buf) -> {
                buf.writeUtf(payload.title, 128);
                buf.writeUtf(payload.content, 1024);
                buf.writeUtf(payload.rewards, 2048);
                buf.writeUtf(payload.startDate, 32);
                buf.writeUtf(payload.endDate, 32);
                buf.writeUtf(payload.condition, 1024);
                buf.writeUtf(payload.imageId, 128);
                buf.writeByteArray(payload.imageData);
            },
            buf -> new EventAdminAddC2SPayload(buf.readUtf(128), buf.readUtf(1024), buf.readUtf(2048),
                    buf.readUtf(32), buf.readUtf(32), buf.readUtf(1024),
                    buf.readUtf(128), buf.readByteArray(2_000_000)));

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}