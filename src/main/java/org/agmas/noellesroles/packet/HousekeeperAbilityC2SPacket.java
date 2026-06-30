package org.agmas.noellesroles.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/**
 * 管家技能网络包（客户端 -> 服务端）
 * 当玩家按下技能键时发送
 */
public record HousekeeperAbilityC2SPacket(boolean shiftDown) implements CustomPacketPayload {

    public static final Type<HousekeeperAbilityC2SPacket> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "housekeeper_ability"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HousekeeperAbilityC2SPacket> CODEC = StreamCodec.ofMember(
            HousekeeperAbilityC2SPacket::write, HousekeeperAbilityC2SPacket::read);

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(shiftDown);
    }

    public static HousekeeperAbilityC2SPacket read(FriendlyByteBuf buf) {
        return new HousekeeperAbilityC2SPacket(buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
