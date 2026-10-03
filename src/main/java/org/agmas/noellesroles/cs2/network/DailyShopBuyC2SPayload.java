package org.agmas.noellesroles.cs2.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 客户端→服务端：购买每日商店指定槽位 */
public record DailyShopBuyC2SPayload(int slot) implements CustomPacketPayload {
    public static final Type<DailyShopBuyC2SPayload> ID = new Type<>(SRE.id("cs2_daily_shop_buy"));
    public static final StreamCodec<FriendlyByteBuf, DailyShopBuyC2SPayload> CODEC = StreamCodec.ofMember(
            (payload, buf) -> buf.writeInt(payload.slot),
            buf -> new DailyShopBuyC2SPayload(buf.readInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}