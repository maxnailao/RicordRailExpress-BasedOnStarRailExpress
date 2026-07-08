package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

public record PlayerStatsSyncPayload(UUID playerUuid, String json) implements CustomPacketPayload {
    public static final Type<PlayerStatsSyncPayload> ID = new Type<>(SRE.id("player_stats_sync"));
    public static final StreamCodec<FriendlyByteBuf, PlayerStatsSyncPayload> CODEC =
            CustomPacketPayload.codec(PlayerStatsSyncPayload::write, PlayerStatsSyncPayload::new);

    /** 协议 VarInt 包长度上限约 2MB，留安全余量设为 500K 字符 */
    private static final int MAX_JSON_CHARS = 524_288;

    private PlayerStatsSyncPayload(FriendlyByteBuf buffer) {
        this(buffer.readUUID(), buffer.readUtf(MAX_JSON_CHARS));
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeUUID(playerUuid);
        buffer.writeUtf(json, MAX_JSON_CHARS);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
