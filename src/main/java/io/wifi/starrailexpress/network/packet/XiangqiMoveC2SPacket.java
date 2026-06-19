package io.wifi.starrailexpress.network.packet;

import io.wifi.starrailexpress.content.minigame.xiangqi.XiangqiSessionManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.Noellesroles;

/**
 * 象棋移动 C2S 网络包
 * fromRow/fromCol: 起点坐标 (0-9, 0-8)
 * toRow/toCol: 终点坐标 (0-9, 0-8)
 */
public record XiangqiMoveC2SPacket(int fromRow, int fromCol, int toRow, int toCol) implements CustomPacketPayload {

    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "xiangqi_move");
    public static final Type<XiangqiMoveC2SPacket> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, XiangqiMoveC2SPacket> CODEC =
            StreamCodec.ofMember(XiangqiMoveC2SPacket::write, XiangqiMoveC2SPacket::read);

    public void write(FriendlyByteBuf buf) {
        buf.writeByte(fromRow); buf.writeByte(fromCol);
        buf.writeByte(toRow); buf.writeByte(toCol);
    }

    public static XiangqiMoveC2SPacket read(FriendlyByteBuf buf) {
        return new XiangqiMoveC2SPacket(buf.readByte(), buf.readByte(), buf.readByte(), buf.readByte());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(XiangqiMoveC2SPacket packet, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        context.server().execute(() ->
                XiangqiSessionManager.INSTANCE.handleMove(player,
                        packet.fromRow(), packet.fromCol(), packet.toRow(), packet.toCol()));
    }
}
