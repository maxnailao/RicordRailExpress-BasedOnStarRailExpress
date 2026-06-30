package org.agmas.noellesroles.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

import java.util.UUID;

/**
 * Hacker target selection network packet
 * Sent from client to server when the player clicks a target avatar in the inventory screen
 */
public record BlackkeSelectTargetC2SPacket(UUID target) implements CustomPacketPayload {
    public static final ResourceLocation BLACKKE_SELECT_TARGET_PAYLOAD_ID =
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "blackke_select_target");
    public static final CustomPacketPayload.Type<BlackkeSelectTargetC2SPacket> ID =
            new CustomPacketPayload.Type<>(BLACKKE_SELECT_TARGET_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, BlackkeSelectTargetC2SPacket> CODEC;

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(this.target);
    }

    public static BlackkeSelectTargetC2SPacket read(FriendlyByteBuf buf) {
        return new BlackkeSelectTargetC2SPacket(buf.readUUID());
    }

    static {
        CODEC = StreamCodec.ofMember(BlackkeSelectTargetC2SPacket::write, BlackkeSelectTargetC2SPacket::read);
    }
}
