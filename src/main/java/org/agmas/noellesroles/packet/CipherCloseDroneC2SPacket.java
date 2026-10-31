package org.agmas.noellesroles.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/** 拼写 finish 完成后，客户端请求关闭无人机（进入冷却）。 */
public record CipherCloseDroneC2SPacket() implements CustomPacketPayload {

    public static final ResourceLocation PAYLOAD_ID =
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "cipher_close_drone");
    public static final CustomPacketPayload.Type<CipherCloseDroneC2SPacket> ID =
            new CustomPacketPayload.Type<>(PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, CipherCloseDroneC2SPacket> CODEC =
            StreamCodec.unit(new CipherCloseDroneC2SPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}