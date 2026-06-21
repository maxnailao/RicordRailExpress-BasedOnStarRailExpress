package org.agmas.noellesroles.packet;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

public record OpenLotteryMachineScreenS2CPacket(BlockPos blockPos) implements CustomPacketPayload {
    public static final ResourceLocation OPEN_LOTTERY_MACHINE_SCREEN_S2C = ResourceLocation.fromNamespaceAndPath(
            Noellesroles.MOD_ID, "open_lottery_machine_screen_s2c");
    public static final Type<OpenLotteryMachineScreenS2CPacket> ID = new Type<>(OPEN_LOTTERY_MACHINE_SCREEN_S2C);
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenLotteryMachineScreenS2CPacket> CODEC =
            StreamCodec.ofMember(OpenLotteryMachineScreenS2CPacket::encode, OpenLotteryMachineScreenS2CPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(this.blockPos);
    }

    public static OpenLotteryMachineScreenS2CPacket decode(RegistryFriendlyByteBuf buf) {
        return new OpenLotteryMachineScreenS2CPacket(buf.readBlockPos());
    }
}
