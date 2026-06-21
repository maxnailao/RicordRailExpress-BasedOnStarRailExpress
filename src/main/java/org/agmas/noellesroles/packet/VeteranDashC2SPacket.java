package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

public record VeteranDashC2SPacket() implements CustomPacketPayload {

    public static final Type<VeteranDashC2SPacket> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "veteran_dash"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VeteranDashC2SPacket> CODEC =
            StreamCodec.unit(new VeteranDashC2SPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
