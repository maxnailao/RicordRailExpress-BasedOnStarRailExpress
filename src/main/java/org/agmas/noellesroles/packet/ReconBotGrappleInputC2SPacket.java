package org.agmas.noellesroles.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/**
 * 荡索者·钩爪输入包：拉取期间客户端每 tick 上报移动/跳跃/潜行位掩码，
 * 服务端据此实现「跳跃上甩 / 方向助力 / 潜行断开保动能」。
 * 位定义：1=前进, 2=后退, 4=左, 8=右, 16=跳跃, 32=潜行。
 */
public record ReconBotGrappleInputC2SPacket(int movementBits) implements CustomPacketPayload {

    public static final int BIT_FORWARD = 1;
    public static final int BIT_BACK = 2;
    public static final int BIT_LEFT = 4;
    public static final int BIT_RIGHT = 8;
    public static final int BIT_JUMP = 16;
    public static final int BIT_SNEAK = 32;

    public static final ResourceLocation PAYLOAD_ID =
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "recon_bot_grapple_input");
    public static final CustomPacketPayload.Type<ReconBotGrappleInputC2SPacket> ID =
            new CustomPacketPayload.Type<>(PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ReconBotGrappleInputC2SPacket> CODEC =
            StreamCodec.ofMember(ReconBotGrappleInputC2SPacket::write, ReconBotGrappleInputC2SPacket::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.movementBits);
    }

    public static ReconBotGrappleInputC2SPacket read(FriendlyByteBuf buf) {
        return new ReconBotGrappleInputC2SPacket(buf.readVarInt());
    }
}