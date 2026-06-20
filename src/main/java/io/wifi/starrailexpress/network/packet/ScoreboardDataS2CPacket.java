package io.wifi.starrailexpress.network.packet;

import io.wifi.starrailexpress.content.minigame.MinigameScoreboardData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 积分榜数据 S2C 网络包
 * 服务端将积分榜数据发送给客户端
 */
public record ScoreboardDataS2CPacket(
        String minigameId,
        List<Entry> entries
) implements CustomPacketPayload {

    public record Entry(String playerName, int score, long timestamp) {}

    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "scoreboard_data");
    public static final Type<ScoreboardDataS2CPacket> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ScoreboardDataS2CPacket> CODEC =
            StreamCodec.ofMember(ScoreboardDataS2CPacket::write, ScoreboardDataS2CPacket::read);

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(minigameId, 64);
        buf.writeInt(entries.size());
        for (Entry e : entries) {
            buf.writeUtf(e.playerName(), 64);
            buf.writeInt(e.score());
            buf.writeLong(e.timestamp());
        }
    }

    public static ScoreboardDataS2CPacket read(FriendlyByteBuf buf) {
        String id = buf.readUtf(64);
        int count = buf.readInt();
        List<Entry> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(new Entry(buf.readUtf(64), buf.readInt(), buf.readLong()));
        }
        return new ScoreboardDataS2CPacket(id, list);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    /** 从 ScoreEntry 列表构建 S2C 包 */
    public static ScoreboardDataS2CPacket fromEntries(String minigameId, List<MinigameScoreboardData.ScoreEntry> scores) {
        List<Entry> entries = new ArrayList<>(scores.size());
        for (MinigameScoreboardData.ScoreEntry se : scores) {
            entries.add(new Entry(se.playerName(), se.score(), se.timestamp()));
        }
        return new ScoreboardDataS2CPacket(minigameId, entries);
    }
}
