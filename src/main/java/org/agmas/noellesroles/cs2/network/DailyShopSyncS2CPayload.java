package org.agmas.noellesroles.cs2.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 服务端→客户端：每日商店数据同步（JSON） */
public record DailyShopSyncS2CPayload(String shopJson) implements CustomPacketPayload {
    public static final Type<DailyShopSyncS2CPayload> ID = new Type<>(SRE.id("cs2_daily_shop_sync"));
    public static final StreamCodec<FriendlyByteBuf, DailyShopSyncS2CPayload> CODEC = StreamCodec.ofMember(
            (payload, buf) -> buf.writeUtf(payload.shopJson, 65536),
            buf -> new DailyShopSyncS2CPayload(buf.readUtf(65536)));

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}