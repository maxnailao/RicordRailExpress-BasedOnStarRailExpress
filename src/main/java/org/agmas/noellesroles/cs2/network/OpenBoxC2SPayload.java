package org.agmas.noellesroles.cs2.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 客户端→服务端：请求开箱
 *
 * @param boxId 箱子 ID
 * @param count 本次开启的箱子数量（1~100，服务端会再按实际拥有的箱子/钥匙数裁剪）
 */
public record OpenBoxC2SPayload(String boxId, int count) implements CustomPacketPayload {

    public static final Type<OpenBoxC2SPayload> ID = new Type<>(SRE.id("cs2_open_box"));
    public static final StreamCodec<FriendlyByteBuf, OpenBoxC2SPayload> CODEC = StreamCodec.ofMember(
            (payload, buf) -> {
                buf.writeUtf(payload.boxId, 128);
                buf.writeVarInt(payload.count);
            },
            buf -> new OpenBoxC2SPayload(buf.readUtf(128), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
