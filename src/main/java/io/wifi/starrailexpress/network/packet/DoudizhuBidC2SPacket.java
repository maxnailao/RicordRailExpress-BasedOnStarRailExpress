package io.wifi.starrailexpress.network.packet;

import io.wifi.starrailexpress.content.minigame.doudizhu.DoudizhuSessionManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.Noellesroles;

/**
 * 斗地主叫分 C2S 网络包
 * bidScore: 0=不叫, 1-3=叫分
 */
public record DoudizhuBidC2SPacket(int bidScore) implements CustomPacketPayload {

    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "doudizhu_bid");
    public static final Type<DoudizhuBidC2SPacket> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, DoudizhuBidC2SPacket> CODEC =
            StreamCodec.ofMember(DoudizhuBidC2SPacket::write, DoudizhuBidC2SPacket::read);

    public void write(FriendlyByteBuf buf) { buf.writeByte(bidScore); }

    public static DoudizhuBidC2SPacket read(FriendlyByteBuf buf) {
        return new DoudizhuBidC2SPacket(buf.readByte());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(DoudizhuBidC2SPacket packet, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        context.server().execute(() ->
                DoudizhuSessionManager.INSTANCE.handleBid(player, packet.bidScore()));
    }
}
