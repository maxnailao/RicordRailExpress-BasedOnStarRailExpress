package org.agmas.noellesroles.packet.Loot;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

public record LootDataRefreshS2CPacket(int coinNumber, int lootChance, int pityCount) implements CustomPacketPayload {
    public static ResourceLocation LOOT_DATA_REFRESH_PAYLOAD_ID =
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "loot_data_refresh");
    public static final Type<LootDataRefreshS2CPacket> ID = new Type<>(LOOT_DATA_REFRESH_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, LootDataRefreshS2CPacket> CODEC;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeInt(coinNumber);
        buf.writeInt(lootChance);
        buf.writeInt(pityCount);
    }

    public static LootDataRefreshS2CPacket read(RegistryFriendlyByteBuf buf) {
        return new LootDataRefreshS2CPacket(buf.readInt(), buf.readInt(), buf.readInt());
    }
    static {
        CODEC = StreamCodec.ofMember(LootDataRefreshS2CPacket::write, LootDataRefreshS2CPacket::read);
    }

}
