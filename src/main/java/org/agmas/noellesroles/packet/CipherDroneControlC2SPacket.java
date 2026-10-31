package org.agmas.noellesroles.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/**
 * 密客无人机远程驱动包。
 *
 * <p>本体留在原地，客户端把相机绑到无人机后，读取本地玩家的移动键与朝向发到此包，
 * 服务端据此驱动无人机飞行（本体已 MOVE_BANED，不会与远程驱动争抢位置）。
 *
 * <p>movementBits：1=前进 2=后退 4=左 8=右 16=上升 32=下降 64=交互(开门/按按钮)。
 */
public record CipherDroneControlC2SPacket(int movementBits, float yaw, float pitch)
        implements CustomPacketPayload {

    public static final int BIT_FORWARD = 1;
    public static final int BIT_BACK = 2;
    public static final int BIT_LEFT = 4;
    public static final int BIT_RIGHT = 8;
    public static final int BIT_ASCEND = 16;
    public static final int BIT_DESCEND = 32;
    public static final int BIT_USE = 64;

    public static final ResourceLocation PAYLOAD_ID =
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "cipher_drone_control");
    public static final CustomPacketPayload.Type<CipherDroneControlC2SPacket> ID =
            new CustomPacketPayload.Type<>(PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, CipherDroneControlC2SPacket> CODEC =
            StreamCodec.ofMember(CipherDroneControlC2SPacket::write, CipherDroneControlC2SPacket::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.movementBits);
        buf.writeFloat(this.yaw);
        buf.writeFloat(this.pitch);
    }

    public static CipherDroneControlC2SPacket read(FriendlyByteBuf buf) {
        return new CipherDroneControlC2SPacket(buf.readVarInt(), buf.readFloat(), buf.readFloat());
    }
}