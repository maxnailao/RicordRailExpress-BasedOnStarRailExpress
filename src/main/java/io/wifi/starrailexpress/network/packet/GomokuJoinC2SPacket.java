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
 * 五子棋加入/离开队列 C2S 网络包
 * action: 0=join, 1=leave
 */
public record GomokuJoinC2SPacket(byte action) implements CustomPacketPayload {

    public static final byte ACTION_JOIN = 0;
    public static final byte ACTION_LEAVE = 1;

    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "gomoku_join");
    public static final Type<GomokuJoinC2SPacket> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, GomokuJoinC2SPacket> CODEC =
            StreamCodec.ofMember(GomokuJoinC2SPacket::write, GomokuJoinC2SPacket::read);

    public void write(FriendlyByteBuf buf) {
        buf.writeByte(action);
    }

    public static GomokuJoinC2SPacket read(FriendlyByteBuf buf) {
        return new GomokuJoinC2SPacket(buf.readByte());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(GomokuJoinC2SPacket packet, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        context.server().execute(() -> {
            if (packet.action() == ACTION_JOIN) {
                GomokuSessionManager.INSTANCE.handleJoin(player);
            } else if (packet.action() == ACTION_LEAVE) {
                GomokuSessionManager.INSTANCE.handleLeave(player);
            }
        });
    }
}
