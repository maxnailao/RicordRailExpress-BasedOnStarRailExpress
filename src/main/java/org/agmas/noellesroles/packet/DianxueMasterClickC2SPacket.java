package org.agmas.noellesroles.packet;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.game.roles.vigilante.dianxue_master.DianxueMasterPlayerComponent;

public record DianxueMasterClickC2SPacket(int acupointIndex) implements CustomPacketPayload {

    public static final Type<DianxueMasterClickC2SPacket> ID = new Type<>(Noellesroles.id("dianxue_master_click"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DianxueMasterClickC2SPacket> CODEC =
            StreamCodec.ofMember(DianxueMasterClickC2SPacket::encode, DianxueMasterClickC2SPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(acupointIndex);
    }

    public static DianxueMasterClickC2SPacket decode(RegistryFriendlyByteBuf buf) {
        return new DianxueMasterClickC2SPacket(buf.readVarInt());
    }

    public static void handle(DianxueMasterClickC2SPacket payload, ServerPlayNetworking.Context context) {
        context.server().execute(() -> DianxueMasterPlayerComponent.KEY.get(context.player())
                .handleClientClick(context.player(), payload.acupointIndex()));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}