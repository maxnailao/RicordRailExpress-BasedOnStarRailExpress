package org.agmas.noellesroles.cs2.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端→客户端：同步所有箱子的名称配置
 * <p>
 * JSON 格式: {"boxNames":{"boxId":"boxName",...},"keyNames":{"boxId":"keyName",...}}
 * </p>
 */
public record BoxConfigSyncS2CPayload(String configJson)
        implements CustomPacketPayload {

    public static final Type<BoxConfigSyncS2CPayload> ID = new Type<>(SRE.id("cs2_box_config_sync"));

    public static final StreamCodec<FriendlyByteBuf, BoxConfigSyncS2CPayload> CODEC = StreamCodec.ofMember(
            (payload, buf) -> buf.writeUtf(payload.configJson, 65536),
            buf -> new BoxConfigSyncS2CPayload(buf.readUtf(65536)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
