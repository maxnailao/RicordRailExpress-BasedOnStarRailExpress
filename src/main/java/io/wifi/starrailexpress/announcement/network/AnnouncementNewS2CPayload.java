package io.wifi.starrailexpress.announcement.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 服务端→客户端：新公告横幅提示（仅标题） */
public record AnnouncementNewS2CPayload(String title) implements CustomPacketPayload {
    public static final Type<AnnouncementNewS2CPayload> ID = new Type<>(SRE.id("sre_announcement_new"));
    public static final StreamCodec<FriendlyByteBuf, AnnouncementNewS2CPayload> CODEC = StreamCodec.ofMember(
            (payload, buf) -> buf.writeUtf(payload.title, 65536),
            buf -> new AnnouncementNewS2CPayload(buf.readUtf(65536)));

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}