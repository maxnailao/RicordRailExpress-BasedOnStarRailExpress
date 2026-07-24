package org.agmas.noellesroles.cs2.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端→客户端：游戏结束后箱子/货币掉落通知
 *
 * @param droppedBoxId 掉落的箱子 ID（null/空表示未掉落）
 * @param currencyGained 获得的货币数量
 * @param isMvp 是否为 MVP
 */
public record BoxDropS2CPayload(String droppedBoxId, int currencyGained, boolean isMvp)
        implements CustomPacketPayload {

    public static final Type<BoxDropS2CPayload> ID = new Type<>(SRE.id("cs2_box_drop"));
    public static final StreamCodec<FriendlyByteBuf, BoxDropS2CPayload> CODEC = StreamCodec.ofMember(
            (payload, buf) -> {
                buf.writeUtf(payload.droppedBoxId != null ? payload.droppedBoxId : "", 128);
                buf.writeVarInt(payload.currencyGained);
                buf.writeBoolean(payload.isMvp);
            },
            buf -> new BoxDropS2CPayload(buf.readUtf(128), buf.readVarInt(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
