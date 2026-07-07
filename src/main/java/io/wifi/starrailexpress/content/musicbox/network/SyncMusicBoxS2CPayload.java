package io.wifi.starrailexpress.content.musicbox.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端→客户端：同步玩家拥有的音乐盒列表与当前装备。
 *
 * @param ownedBoxes  拥有的音乐盒 ID 列表
 * @param equippedBox 当前装备的音乐盒 ID（空字符串表示无装备）
 */
public record SyncMusicBoxS2CPayload(List<String> ownedBoxes, String equippedBox) implements CustomPacketPayload {

    public static final Type<SyncMusicBoxS2CPayload> ID = new Type<>(SRE.id("sync_music_box"));
    public static final StreamCodec<FriendlyByteBuf, SyncMusicBoxS2CPayload> CODEC = StreamCodec.ofMember(
            (payload, buf) -> {
                buf.writeVarInt(payload.ownedBoxes.size());
                for (String id : payload.ownedBoxes) {
                    buf.writeUtf(id);
                }
                buf.writeUtf(payload.equippedBox);
            },
            buf -> {
                int size = buf.readVarInt();
                List<String> list = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    list.add(buf.readUtf());
                }
                String equipped = buf.readUtf();
                return new SyncMusicBoxS2CPayload(list, equipped);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
