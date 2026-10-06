package org.agmas.noellesroles.cs2.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 客户端→服务端：神话商店购买指定皮肤（消耗神话碎片） */
public record MythicShopBuyC2SPayload(String skinId) implements CustomPacketPayload {
    public static final Type<MythicShopBuyC2SPayload> ID = new Type<>(SRE.id("cs2_mythic_shop_buy"));
    public static final StreamCodec<FriendlyByteBuf, MythicShopBuyC2SPayload> CODEC = StreamCodec.ofMember(
            (p, buf) -> buf.writeUtf(p.skinId, 128),
            buf -> new MythicShopBuyC2SPayload(buf.readUtf(128)));

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}