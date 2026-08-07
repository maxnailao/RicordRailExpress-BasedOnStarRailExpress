package org.agmas.noellesroles.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/**
 * 退伍军人冲刺翻滚通知包（服务端 -> 客户端）
 * 通知客户端对指定实体播放前滚翻动画
 *
 * @param entityId 翻滚玩家实体 ID
 * @param dirX     冲刺方向 X（水平单位向量）
 * @param dirZ     冲刺方向 Z（水平单位向量）
 */
public record VeteranRollS2CPacket(int entityId, float dirX, float dirZ) implements CustomPacketPayload {

    public static final Type<VeteranRollS2CPacket> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "veteran_roll"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VeteranRollS2CPacket> CODEC =
            StreamCodec.ofMember(VeteranRollS2CPacket::write, VeteranRollS2CPacket::read);

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeFloat(dirX);
        buf.writeFloat(dirZ);
    }

    public static VeteranRollS2CPacket read(FriendlyByteBuf buf) {
        return new VeteranRollS2CPacket(buf.readVarInt(), buf.readFloat(), buf.readFloat());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
