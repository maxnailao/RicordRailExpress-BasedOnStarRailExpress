package io.wifi.starrailexpress.content.musicbox.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端→客户端：通知全服播放某位玩家的凯旋音乐。
 *
 * @param musicBoxId  音乐盒 ID
 * @param playerName  玩家显示名
 */
public record PlayMusicBoxS2CPayload(String musicBoxId, String playerName) implements CustomPacketPayload {

    public static final Type<PlayMusicBoxS2CPayload> ID = new Type<>(SRE.id("play_music_box"));
    public static final StreamCodec<FriendlyByteBuf, PlayMusicBoxS2CPayload> CODEC = StreamCodec.ofMember(
            (payload, buf) -> {
                buf.writeUtf(payload.musicBoxId, 128);
                buf.writeUtf(payload.playerName, 64);
            },
            buf -> new PlayMusicBoxS2CPayload(buf.readUtf(128), buf.readUtf(64)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
