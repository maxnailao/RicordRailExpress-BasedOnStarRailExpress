package org.agmas.noellesroles.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

import java.util.UUID;

/**
 * 梦魇恐惧技能网络包
 * 客户端 -> 服务端
 *
 * 当玩家在背包界面选择目标玩家时发送，请求对目标施加"恐惧"
 */
public record MengyanC2SPacket(UUID targetUuid) implements CustomPacketPayload {

    public static final ResourceLocation MENGYAN_PAYLOAD_ID = ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "mengyan_fear");
    public static final Type<MengyanC2SPacket> ID = new Type<>(MENGYAN_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, MengyanC2SPacket> CODEC;

    public MengyanC2SPacket(UUID targetUuid) {
        this.targetUuid = targetUuid;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(this.targetUuid);
    }

    public static MengyanC2SPacket read(FriendlyByteBuf buf) {
        return new MengyanC2SPacket(buf.readUUID());
    }

    public UUID targetUuid() {
        return this.targetUuid;
    }

    static {
        CODEC = StreamCodec.ofMember(MengyanC2SPacket::write, MengyanC2SPacket::read);
    }
}
