package org.agmas.noellesroles.cs2.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端→客户端：返回箱子奖池配置 JSON
 *
 * @param configJson 箱子配置 JSON 字符串（与箱子配置文件格式一致）
 */
public record BoxPreviewS2CPayload(String configJson)
        implements CustomPacketPayload {

    public static final Type<BoxPreviewS2CPayload> ID = new Type<>(SRE.id("cs2_box_preview"));

    public static final StreamCodec<FriendlyByteBuf, BoxPreviewS2CPayload> CODEC = StreamCodec.ofMember(
            (payload, buf) -> buf.writeUtf(payload.configJson, 65536),
            buf -> new BoxPreviewS2CPayload(buf.readUtf(65536)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
