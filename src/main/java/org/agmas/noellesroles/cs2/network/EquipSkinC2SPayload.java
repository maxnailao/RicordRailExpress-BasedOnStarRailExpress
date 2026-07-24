package org.agmas.noellesroles.cs2.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 客户端→服务端：装备/卸下皮肤
 *
 * @param itemType 物品类型（knife, revolver, bat, grenade, hat）
 * @param skinName 皮肤名称，"default" 或空字符串表示卸下
 */
public record EquipSkinC2SPayload(String itemType, String skinName) implements CustomPacketPayload {

    public static final Type<EquipSkinC2SPayload> ID = new Type<>(SRE.id("cs2_equip_skin"));
    public static final StreamCodec<FriendlyByteBuf, EquipSkinC2SPayload> CODEC = StreamCodec.ofMember(
            (payload, buf) -> {
                buf.writeUtf(payload.itemType, 64);
                buf.writeUtf(payload.skinName, 128);
            },
            buf -> new EquipSkinC2SPayload(buf.readUtf(64), buf.readUtf(128)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
