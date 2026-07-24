package org.agmas.noellesroles.cs2.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 客户端→服务端：商店购买请求
 */
public record ShopBuyC2SPayload(String itemType, String itemId) implements CustomPacketPayload {
    public static final Type<ShopBuyC2SPayload> ID = new Type<>(SRE.id("cs2_shop_buy"));
    public static final StreamCodec<FriendlyByteBuf, ShopBuyC2SPayload> CODEC = StreamCodec.ofMember(
            (p, buf) -> { buf.writeUtf(p.itemType, 64); buf.writeUtf(p.itemId, 128); },
            buf -> new ShopBuyC2SPayload(buf.readUtf(64), buf.readUtf(128)));

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
