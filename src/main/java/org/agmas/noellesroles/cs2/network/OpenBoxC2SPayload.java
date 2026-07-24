package org.agmas.noellesroles.cs2.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 客户端→服务端：请求开箱
 *
 * @param boxId 箱子 ID
 */
public record OpenBoxC2SPayload(String boxId) implements CustomPacketPayload {

    public static final Type<OpenBoxC2SPayload> ID = new Type<>(SRE.id("cs2_open_box"));
    public static final StreamCodec<FriendlyByteBuf, OpenBoxC2SPayload> CODEC = StreamCodec.ofMember(
            (payload, buf) -> buf.writeUtf(payload.boxId, 128),
            buf -> new OpenBoxC2SPayload(buf.readUtf(128)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
