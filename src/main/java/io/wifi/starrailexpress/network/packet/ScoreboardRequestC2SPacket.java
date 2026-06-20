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

import java.util.List;

/**
 * 积分榜数据请求 C2S 网络包
 * 客户端向服务端请求指定小游戏的排行榜数据
 */
public record ScoreboardRequestC2SPacket(String minigameId) implements CustomPacketPayload {

    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "scoreboard_request");
    public static final Type<ScoreboardRequestC2SPacket> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ScoreboardRequestC2SPacket> CODEC =
            StreamCodec.ofMember(ScoreboardRequestC2SPacket::write, ScoreboardRequestC2SPacket::read);

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(minigameId, 64);
    }

    public static ScoreboardRequestC2SPacket read(FriendlyByteBuf buf) {
        return new ScoreboardRequestC2SPacket(buf.readUtf(64));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ScoreboardRequestC2SPacket packet, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        context.server().execute(() -> {
            List<MinigameScoreboardData.ScoreEntry> scores = MinigameScoreboardData.getScores(packet.minigameId());
            // 发送积分榜数据回客户端
            ServerPlayNetworking.send(player,
                    ScoreboardDataS2CPacket.fromEntries(packet.minigameId(), scores));
        });
    }
}
