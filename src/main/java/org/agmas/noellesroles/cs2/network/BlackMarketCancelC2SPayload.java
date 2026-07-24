package org.agmas.noellesroles.cs2.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 客户端→服务端：黑市取消上架
 */
public record BlackMarketCancelC2SPayload(String listingId) implements CustomPacketPayload {
    public static final Type<BlackMarketCancelC2SPayload> ID = new Type<>(SRE.id("cs2_market_cancel"));
    public static final StreamCodec<FriendlyByteBuf, BlackMarketCancelC2SPayload> CODEC = StreamCodec.ofMember(
            (p, buf) -> buf.writeUtf(p.listingId, 64),
            buf -> new BlackMarketCancelC2SPayload(buf.readUtf(64)));

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
