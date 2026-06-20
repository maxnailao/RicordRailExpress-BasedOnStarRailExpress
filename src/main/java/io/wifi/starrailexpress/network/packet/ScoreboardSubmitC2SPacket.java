package io.wifi.starrailexpress.network.packet;

import io.wifi.starrailexpress.content.minigame.MinigameScoreboardData;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.Noellesroles;

/**
 * 积分榜分数提交 C2S 网络包
 * 客户端将游戏分数发送给服务端，由服务端验证并存储，防止客户端篡改
 */
public record ScoreboardSubmitC2SPacket(String minigameId, int score) implements CustomPacketPayload {

    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "scoreboard_submit");
    public static final Type<ScoreboardSubmitC2SPacket> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ScoreboardSubmitC2SPacket> CODEC =
            StreamCodec.ofMember(ScoreboardSubmitC2SPacket::write, ScoreboardSubmitC2SPacket::read);

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(minigameId, 64);
        buf.writeInt(score);
    }

    public static ScoreboardSubmitC2SPacket read(FriendlyByteBuf buf) {
        return new ScoreboardSubmitC2SPacket(buf.readUtf(64), buf.readInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ScoreboardSubmitC2SPacket packet, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        context.server().execute(() -> {
            String playerName = player.getName().getString();
            MinigameScoreboardData.addScore(packet.minigameId(), playerName, packet.score());
        });
    }
}
