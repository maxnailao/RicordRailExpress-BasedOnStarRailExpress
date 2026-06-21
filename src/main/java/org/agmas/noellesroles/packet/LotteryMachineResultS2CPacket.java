package org.agmas.noellesroles.packet;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.Noellesroles;

public record LotteryMachineResultS2CPacket(BlockPos blockPos, boolean success, String messageKey, ItemStack itemStack)
        implements CustomPacketPayload {
    public static final ResourceLocation LOTTERY_MACHINE_RESULT_S2C = ResourceLocation.fromNamespaceAndPath(
            Noellesroles.MOD_ID, "lottery_machine_result_s2c");
    public static final Type<LotteryMachineResultS2CPacket> ID = new Type<>(LOTTERY_MACHINE_RESULT_S2C);
    public static final StreamCodec<RegistryFriendlyByteBuf, LotteryMachineResultS2CPacket> CODEC =
            StreamCodec.ofMember(LotteryMachineResultS2CPacket::encode, LotteryMachineResultS2CPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(this.blockPos);
        buf.writeBoolean(this.success);
        buf.writeUtf(this.messageKey);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, this.itemStack);
    }

    public static LotteryMachineResultS2CPacket decode(RegistryFriendlyByteBuf buf) {
        return new LotteryMachineResultS2CPacket(
                buf.readBlockPos(),
                buf.readBoolean(),
                buf.readUtf(),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
    }
}
