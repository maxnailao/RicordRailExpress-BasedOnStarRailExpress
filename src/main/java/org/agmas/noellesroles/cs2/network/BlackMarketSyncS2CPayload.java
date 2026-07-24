package org.agmas.noellesroles.cs2.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端→客户端：黑市挂单列表同步
 *
 * @param listingsJson JSON 格式的挂单列表（MarketListing 数组序列化）
 * @param myPendingCoins 当前玩家待领取的黑市货币（默认 0）
 */
public record BlackMarketSyncS2CPayload(String listingsJson, int myPendingCoins)
        implements CustomPacketPayload {

    public static final Type<BlackMarketSyncS2CPayload> ID = new Type<>(SRE.id("cs2_market_sync"));

    public static final StreamCodec<FriendlyByteBuf, BlackMarketSyncS2CPayload> CODEC = StreamCodec.ofMember(
            (payload, buf) -> { buf.writeUtf(payload.listingsJson, 65536); buf.writeInt(payload.myPendingCoins); },
            buf -> new BlackMarketSyncS2CPayload(buf.readUtf(65536), buf.readInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
