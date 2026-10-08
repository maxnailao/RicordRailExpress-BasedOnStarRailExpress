package io.wifi.starrailexpress.content.title.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 客户端 → 服务端：装备 / 卸下称号。
 *
 * <p>空字符串表示"卸下当前称号"。
 */
public record EquipTitleC2SPayload(String titleId) implements CustomPacketPayload {

    public static final Type<EquipTitleC2SPayload> ID = new Type<>(SRE.id("equip_title"));
    public static final StreamCodec<FriendlyByteBuf, EquipTitleC2SPayload> CODEC =
            StreamCodec.ofMember(
                    (payload, buf) -> buf.writeUtf(payload.titleId(), 64),
                    buf -> new EquipTitleC2SPayload(buf.readUtf(64)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
