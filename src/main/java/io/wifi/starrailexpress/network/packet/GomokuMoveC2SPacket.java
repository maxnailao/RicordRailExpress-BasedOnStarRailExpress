package io.wifi.starrailexpress.network.packet;

import io.wifi.starrailexpress.content.minigame.gomoku.GomokuSessionManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.Noellesroles;

/**
 * 五子棋落子 C2S 网络包
 * row/col: 0-14 棋盘坐标
 */
public record GomokuMoveC2SPacket(int row, int col) implements CustomPacketPayload {

    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "gomoku_move");
    public static final Type<GomokuMoveC2SPacket> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, GomokuMoveC2SPacket> CODEC =
            StreamCodec.ofMember(GomokuMoveC2SPacket::write, GomokuMoveC2SPacket::read);

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(row);
        buf.writeInt(col);
    }

    public static GomokuMoveC2SPacket read(FriendlyByteBuf buf) {
        return new GomokuMoveC2SPacket(buf.readInt(), buf.readInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(GomokuMoveC2SPacket packet, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        context.server().execute(() -> {
            GomokuSessionManager.INSTANCE.handleMove(player, packet.row(), packet.col());
        });
    }
}
