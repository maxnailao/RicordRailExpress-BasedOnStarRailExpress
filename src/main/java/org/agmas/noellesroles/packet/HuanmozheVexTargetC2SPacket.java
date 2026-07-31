package org.agmas.noellesroles.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

import java.util.UUID;

/**
 * 幻魔者恼鬼召唤目标选择网络包
 * 客户端在背包界面点选玩家头像后发送，请求在目标周围召唤恼鬼
 */
public record HuanmozheVexTargetC2SPacket(UUID target) implements CustomPacketPayload {
    public static final ResourceLocation PAYLOAD_ID =
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "huanmozhe_vex_target");
    public static final CustomPacketPayload.Type<HuanmozheVexTargetC2SPacket> ID =
            new CustomPacketPayload.Type<>(PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, HuanmozheVexTargetC2SPacket> CODEC;

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(this.target);
    }

    public static HuanmozheVexTargetC2SPacket read(FriendlyByteBuf buf) {
        return new HuanmozheVexTargetC2SPacket(buf.readUUID());
    }

    static {
        CODEC = StreamCodec.ofMember(HuanmozheVexTargetC2SPacket::write, HuanmozheVexTargetC2SPacket::read);
    }
}
