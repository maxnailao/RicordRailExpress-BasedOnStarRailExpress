package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

import java.util.UUID;

public record DianxueMasterSyncS2CPacket(
        int action, int phase, int activeIndex, UUID targetId, String targetName,
        int remainingTicks, int maxTicks,
        float[] dotHeightFracs, float[] dotAzimuths, float dotRadius
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
        buf.writeVarInt(dotHeightFracs.length);
        for (float f : dotHeightFracs) buf.writeFloat(f);
        buf.writeVarInt(dotAzimuths.length);
        for (float f : dotAzimuths) buf.writeFloat(f);
        buf.writeFloat(dotRadius);
    }

    public static DianxueMasterSyncS2CPacket decode(RegistryFriendlyByteBuf buf) {
        int action = buf.readVarInt();
        int phase = buf.readVarInt();
        int activeIndex = buf.readVarInt();
        UUID targetId = buf.readUUID();
        String targetName = buf.readUtf(64);
        int remainingTicks = buf.readVarInt();
        int maxTicks = buf.readVarInt();
        int hn = buf.readVarInt();
        float[] dotHeightFracs = new float[hn];
        for (int i = 0; i < hn; i++) dotHeightFracs[i] = buf.readFloat();
        int an = buf.readVarInt();
        float[] dotAzimuths = new float[an];
        for (int i = 0; i < an; i++) dotAzimuths[i] = buf.readFloat();
        float dotRadius = buf.readFloat();
        return new DianxueMasterSyncS2CPacket(action, phase, activeIndex, targetId, targetName,
                remainingTicks, maxTicks, dotHeightFracs, dotAzimuths, dotRadius);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}