package org.agmas.noellesroles.cs2.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 客户端→服务端：黑市上架请求
 */
public record BlackMarketListC2SPayload(String itemType, String itemId, int price) implements CustomPacketPayload {
    public static final Type<BlackMarketListC2SPayload> ID = new Type<>(SRE.id("cs2_market_list"));
    public static final StreamCodec<FriendlyByteBuf, BlackMarketListC2SPayload> CODEC = StreamCodec.ofMember(
            (p, buf) -> { buf.writeUtf(p.itemType, 64); buf.writeUtf(p.itemId, 128); buf.writeVarInt(p.price); },
            buf -> new BlackMarketListC2SPayload(buf.readUtf(64), buf.readUtf(128), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
