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
 * 麻将出牌 C2S 网络包
 */
public record MahjongDiscardC2SPacket(byte tileId) implements CustomPacketPayload {

    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "mahjong_discard");
    public static final Type<MahjongDiscardC2SPacket> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, MahjongDiscardC2SPacket> CODEC =
            StreamCodec.ofMember(MahjongDiscardC2SPacket::write, MahjongDiscardC2SPacket::read);

    public void write(FriendlyByteBuf buf) { buf.writeByte(tileId); }

    public static MahjongDiscardC2SPacket read(FriendlyByteBuf buf) {
        return new MahjongDiscardC2SPacket(buf.readByte());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(MahjongDiscardC2SPacket packet, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        context.server().execute(() ->
                MahjongSessionManager.INSTANCE.handleDiscard(player, Byte.toUnsignedInt(packet.tileId())));
    }
}
