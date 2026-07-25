package org.agmas.noellesroles.cs2.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端→客户端：同步商店配置（购买物品列表 + 出售价格表）
 * <p>
 * JSON 格式与 shopprice.json 一致
 * </p>
 */
public record ShopConfigSyncS2CPayload(String configJson)
        implements CustomPacketPayload {

    public static final Type<ShopConfigSyncS2CPayload> ID = new Type<>(SRE.id("cs2_shop_config_sync"));

    public static final StreamCodec<FriendlyByteBuf, ShopConfigSyncS2CPayload> CODEC = StreamCodec.ofMember(
            (payload, buf) -> buf.writeUtf(payload.configJson, 65536),
            buf -> new ShopConfigSyncS2CPayload(buf.readUtf(65536)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
