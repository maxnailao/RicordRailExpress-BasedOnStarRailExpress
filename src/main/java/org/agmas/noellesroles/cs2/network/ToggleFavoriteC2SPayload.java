package org.agmas.noellesroles.cs2.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 客户端→服务端：切换仓库物品的收藏状态。
 *
 * <p>收藏的物品会排在仓库前面，且在取消收藏前**无法销毁**。
 *
 * @param itemType 物品类型：box / key / skin / musicbox / card 等
 * @param itemId   物品 id
 */
public record ToggleFavoriteC2SPayload(String itemType, String itemId)
        implements CustomPacketPayload {

    public static final Type<ToggleFavoriteC2SPayload> ID =
            new Type<>(SRE.id("cs2_toggle_favorite"));
    public static final StreamCodec<FriendlyByteBuf, ToggleFavoriteC2SPayload> CODEC =
            StreamCodec.ofMember(
                    (payload, buf) -> {
                        buf.writeUtf(payload.itemType, 32);
                        buf.writeUtf(payload.itemId, 128);
                    },
                    buf -> new ToggleFavoriteC2SPayload(buf.readUtf(32), buf.readUtf(128)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
