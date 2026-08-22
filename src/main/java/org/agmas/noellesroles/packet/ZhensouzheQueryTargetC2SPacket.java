package org.agmas.noellesroles.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

import java.util.UUID;

/**
 * 侦搜者存活查询网络包
 * 客户端在物品栏界面点击目标玩家头像时发送到服务端
 */
public record ZhensouzheQueryTargetC2SPacket(UUID target) implements CustomPacketPayload {
    public static final ResourceLocation ZHENSOUZHE_QUERY_TARGET_PAYLOAD_ID =
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "zhensouzhe_query_target");
    public static final CustomPacketPayload.Type<ZhensouzheQueryTargetC2SPacket> ID =
            new CustomPacketPayload.Type<>(ZHENSOUZHE_QUERY_TARGET_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ZhensouzheQueryTargetC2SPacket> CODEC;

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(this.target);
    }

    public static ZhensouzheQueryTargetC2SPacket read(FriendlyByteBuf buf) {
        return new ZhensouzheQueryTargetC2SPacket(buf.readUUID());
    }

    static {
        CODEC = StreamCodec.ofMember(ZhensouzheQueryTargetC2SPacket::write, ZhensouzheQueryTargetC2SPacket::read);
    }
}
