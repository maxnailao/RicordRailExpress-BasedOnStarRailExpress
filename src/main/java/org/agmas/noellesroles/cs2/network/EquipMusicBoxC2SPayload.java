package org.agmas.noellesroles.cs2.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 客户端→服务端：装备/卸下音乐盒
 *
 * @param musicBoxId 音乐盒 ID，空字符串表示卸下
 */
public record EquipMusicBoxC2SPayload(String musicBoxId) implements CustomPacketPayload {

    public static final Type<EquipMusicBoxC2SPayload> ID = new Type<>(SRE.id("cs2_equip_musicbox"));
    public static final StreamCodec<FriendlyByteBuf, EquipMusicBoxC2SPayload> CODEC = StreamCodec.ofMember(
            (payload, buf) -> buf.writeUtf(payload.musicBoxId, 128),
            buf -> new EquipMusicBoxC2SPayload(buf.readUtf(128)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
