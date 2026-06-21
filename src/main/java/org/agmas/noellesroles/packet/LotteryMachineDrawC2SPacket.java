package org.agmas.noellesroles.packet;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

public record LotteryMachineDrawC2SPacket(BlockPos blockPos) implements CustomPacketPayload {
    public static final ResourceLocation LOTTERY_MACHINE_DRAW_C2S = ResourceLocation.fromNamespaceAndPath(
            Noellesroles.MOD_ID, "lottery_machine_draw_c2s");
    public static final Type<LotteryMachineDrawC2SPacket> TYPE = new Type<>(LOTTERY_MACHINE_DRAW_C2S);
    public static final StreamCodec<RegistryFriendlyByteBuf, LotteryMachineDrawC2SPacket> CODEC =
            StreamCodec.ofMember(LotteryMachineDrawC2SPacket::encode, LotteryMachineDrawC2SPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(this.blockPos);
    }

    public static LotteryMachineDrawC2SPacket decode(RegistryFriendlyByteBuf buf) {
        return new LotteryMachineDrawC2SPacket(buf.readBlockPos());
    }
}
