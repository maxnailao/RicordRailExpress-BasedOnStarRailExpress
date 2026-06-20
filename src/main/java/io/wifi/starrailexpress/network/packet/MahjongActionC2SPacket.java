package io.wifi.starrailexpress.network.packet;

import io.wifi.starrailexpress.content.minigame.mahjong.MahjongSessionManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.Noellesroles;

/**
 * 麻将动作 C2S 网络包
 * actionType: 0=none, 1=chi, 2=pong, 3=kong, 4=hu, 5=pass, 6=draw_win(自摸)
 * tileType: 相关牌型 (0-33)
 */
public record MahjongActionC2SPacket(byte actionType, byte tileType) implements CustomPacketPayload {

    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "mahjong_action");
    public static final Type<MahjongActionC2SPacket> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, MahjongActionC2SPacket> CODEC =
            StreamCodec.ofMember(MahjongActionC2SPacket::write, MahjongActionC2SPacket::read);

    public void write(FriendlyByteBuf buf) {
        buf.writeByte(actionType);
        buf.writeByte(tileType);
    }

    public static MahjongActionC2SPacket read(FriendlyByteBuf buf) {
        return new MahjongActionC2SPacket(buf.readByte(), buf.readByte());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(MahjongActionC2SPacket packet, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        context.server().execute(() ->
                MahjongSessionManager.INSTANCE.handleAction(player, packet.actionType(), packet.tileType()));
    }
}
