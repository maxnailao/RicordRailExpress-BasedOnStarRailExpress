package org.agmas.noellesroles.cs2.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 客户端→服务端：黑市上架请求
 *
 * @param currency 计价货币：{@code "coin"}（金币）或 {@code "shard"}（神话碎片）。
 *                 合并时这个字段被丢掉了，导致新主菜单的 ShopScreen 编译不过，这里补回。
 */
public record BlackMarketListC2SPayload(String itemType, String itemId, int price, String currency)
        implements CustomPacketPayload {
    public static final Type<BlackMarketListC2SPayload> ID = new Type<>(SRE.id("cs2_market_list"));
    public static final StreamCodec<FriendlyByteBuf, BlackMarketListC2SPayload> CODEC = StreamCodec.ofMember(
            (p, buf) -> {
                buf.writeUtf(p.itemType, 64);
                buf.writeUtf(p.itemId, 128);
                buf.writeVarInt(p.price);
                buf.writeUtf(p.currency == null ? "coin" : p.currency, 16);
            },
            buf -> new BlackMarketListC2SPayload(buf.readUtf(64), buf.readUtf(128), buf.readVarInt(),
                    buf.readUtf(16)));

    /** 兼容旧调用：不传货币时按金币计价 */
    public BlackMarketListC2SPayload(String itemType, String itemId, int price) {
        this(itemType, itemId, price, "coin");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
