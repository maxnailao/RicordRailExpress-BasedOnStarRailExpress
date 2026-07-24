package org.agmas.noellesroles.cs2.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 客户端→服务端：黑市购买请求
 */
public record BlackMarketBuyC2SPayload(String listingId) implements CustomPacketPayload {
    public static final Type<BlackMarketBuyC2SPayload> ID = new Type<>(SRE.id("cs2_market_buy"));
    public static final StreamCodec<FriendlyByteBuf, BlackMarketBuyC2SPayload> CODEC = StreamCodec.ofMember(
            (p, buf) -> buf.writeUtf(p.listingId, 64),
            buf -> new BlackMarketBuyC2SPayload(buf.readUtf(64)));

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
