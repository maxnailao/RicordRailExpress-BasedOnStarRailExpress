package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

public record PlayerDataPartSyncPayload(UUID playerUuid, String part, String json, long updatedAt)
        implements CustomPacketPayload {
    public static final Type<PlayerDataPartSyncPayload> ID = new Type<>(SRE.id("player_data_part_sync"));
    public static final StreamCodec<FriendlyByteBuf, PlayerDataPartSyncPayload> CODEC =
            CustomPacketPayload.codec(PlayerDataPartSyncPayload::write, PlayerDataPartSyncPayload::new);

    /** 协议 VarInt 包长度上限约 2MB，留安全余量设为 500K 字符 */
    private static final int MAX_JSON_CHARS = 524_288;

    private PlayerDataPartSyncPayload(FriendlyByteBuf buffer) {
        this(buffer.readUUID(), buffer.readUtf(64), buffer.readUtf(MAX_JSON_CHARS), buffer.readVarLong());
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeUUID(playerUuid);
        buffer.writeUtf(part, 64);
        buffer.writeUtf(json, MAX_JSON_CHARS);
        buffer.writeVarLong(updatedAt);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
