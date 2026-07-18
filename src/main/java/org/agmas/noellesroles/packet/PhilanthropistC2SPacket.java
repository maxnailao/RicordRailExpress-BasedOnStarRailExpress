package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

import java.util.UUID;

public record PhilanthropistC2SPacket(UUID targetPlayer) implements CustomPacketPayload {
    public static final ResourceLocation PHILANTHROPIST_PAYLOAD_ID = ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "philanthropist_donate");
    public static final Type<PhilanthropistC2SPacket> ID = new Type<>(PHILANTHROPIST_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, PhilanthropistC2SPacket> CODEC;

    public PhilanthropistC2SPacket(UUID targetPlayer) {
        this.targetPlayer = targetPlayer;
    }

    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(this.targetPlayer);
    }

    public static PhilanthropistC2SPacket read(RegistryFriendlyByteBuf buf) {
        return new PhilanthropistC2SPacket(buf.readUUID());
    }

    static {
        CODEC = StreamCodec.ofMember(PhilanthropistC2SPacket::write, PhilanthropistC2SPacket::read);
    }
}
