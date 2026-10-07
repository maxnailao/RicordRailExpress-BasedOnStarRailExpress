package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

import java.util.UUID;

public record DianxueMasterSyncS2CPacket(
        int action, int phase, int activeIndex, UUID targetId, String targetName,
        int remainingTicks, int maxTicks
) implements CustomPacketPayload {

    public static final Type<DianxueMasterSyncS2CPacket> ID = new Type<>(Noellesroles.id("dianxue_master_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DianxueMasterSyncS2CPacket> CODEC =
            StreamCodec.ofMember(DianxueMasterSyncS2CPacket::encode, DianxueMasterSyncS2CPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(action);
        buf.writeVarInt(phase);
        buf.writeVarInt(activeIndex);
        buf.writeUUID(targetId);
        buf.writeUtf(targetName, 64);
        buf.writeVarInt(remainingTicks);
        buf.writeVarInt(maxTicks);
    }

    public static DianxueMasterSyncS2CPacket decode(RegistryFriendlyByteBuf buf) {
        return new DianxueMasterSyncS2CPacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readUUID(), buf.readUtf(64), buf.readVarInt(), buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}