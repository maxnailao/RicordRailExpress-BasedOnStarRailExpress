package org.agmas.noellesroles.cs2.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 客户端→服务端：请求领取黑市离线收入
 */
public record BlackMarketClaimC2SPayload()
        implements CustomPacketPayload {

    public static final Type<BlackMarketClaimC2SPayload> ID = new Type<>(SRE.id("cs2_market_claim"));

    public static final StreamCodec<FriendlyByteBuf, BlackMarketClaimC2SPayload> CODEC = StreamCodec.ofMember(
            (payload, buf) -> {},
            buf -> new BlackMarketClaimC2SPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
