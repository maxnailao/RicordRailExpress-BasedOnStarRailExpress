package org.agmas.noellesroles.cs2.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 客户端→服务端：请求查看箱子奖池内容
 *
 * @param boxId 箱子 ID
 */
public record BoxPreviewRequestC2SPayload(String boxId)
        implements CustomPacketPayload {

    public static final Type<BoxPreviewRequestC2SPayload> ID = new Type<>(SRE.id("cs2_box_preview_request"));

    public static final StreamCodec<FriendlyByteBuf, BoxPreviewRequestC2SPayload> CODEC = StreamCodec.ofMember(
            (payload, buf) -> buf.writeUtf(payload.boxId, 256),
            buf -> new BoxPreviewRequestC2SPayload(buf.readUtf(256)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
