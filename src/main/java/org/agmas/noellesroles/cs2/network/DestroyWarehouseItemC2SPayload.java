package org.agmas.noellesroles.cs2.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 客户端→服务端：从仓库**销毁**（永久删除）一件物品。
 *
 * <p>用于清理幽灵物品（例如因为历史 bug 留在仓库里、却已经没有任何对应定义的东西）。
 *
 * @param itemType 物品类型：box / key / skin / musicbox
 * @param itemId   物品 id（skin 为 {@code itemType/skinName} 形式）
 */
public record DestroyWarehouseItemC2SPayload(String itemType, String itemId)
        implements CustomPacketPayload {

    public static final Type<DestroyWarehouseItemC2SPayload> ID =
            new Type<>(SRE.id("cs2_destroy_item"));
    public static final StreamCodec<FriendlyByteBuf, DestroyWarehouseItemC2SPayload> CODEC =
            StreamCodec.ofMember(
                    (payload, buf) -> {
                        buf.writeUtf(payload.itemType, 32);
                        buf.writeUtf(payload.itemId, 128);
                    },
                    buf -> new DestroyWarehouseItemC2SPayload(buf.readUtf(32), buf.readUtf(128)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
