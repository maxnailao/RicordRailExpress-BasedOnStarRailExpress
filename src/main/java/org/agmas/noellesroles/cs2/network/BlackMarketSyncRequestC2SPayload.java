package org.agmas.noellesroles.cs2.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 客户端→服务端：请求同步黑市数据
 */
public record BlackMarketSyncRequestC2SPayload()
        implements CustomPacketPayload {

    public static final Type<BlackMarketSyncRequestC2SPayload> ID = new Type<>(SRE.id("cs2_market_sync_request"));

    public static final StreamCodec<FriendlyByteBuf, BlackMarketSyncRequestC2SPayload> CODEC = StreamCodec.ofMember(
            (payload, buf) -> {},
            buf -> new BlackMarketSyncRequestC2SPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
