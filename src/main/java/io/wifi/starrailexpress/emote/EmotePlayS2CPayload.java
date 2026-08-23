package io.wifi.starrailexpress.emote;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 表情播放广播包（服务端 -> 客户端）
 * 通知所有同维度玩家：指定实体开始播放表情
 *
 * @param entityId 播放表情的玩家实体 ID
 * @param emoteId  表情 ID（见 {@link EmoteType#id()}）
 */
public record EmotePlayS2CPayload(int entityId, String emoteId) implements CustomPacketPayload {

    public static final Type<EmotePlayS2CPayload> ID = new Type<>(SRE.id("emote_play_s2c"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EmotePlayS2CPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, EmotePlayS2CPayload::entityId,
            ByteBufCodecs.STRING_UTF8, EmotePlayS2CPayload::emoteId,
            EmotePlayS2CPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
