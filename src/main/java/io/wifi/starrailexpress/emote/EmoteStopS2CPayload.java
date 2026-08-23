package io.wifi.starrailexpress.emote;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 表情停止广播包（服务端 -> 客户端）
 * 通知所有同维度玩家：指定实体结束表情播放（移动打断 / 播放到期 / 玩家异常）
 *
 * @param entityId 停止表情的玩家实体 ID
 */
public record EmoteStopS2CPayload(int entityId) implements CustomPacketPayload {

    public static final Type<EmoteStopS2CPayload> ID = new Type<>(SRE.id("emote_stop_s2c"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EmoteStopS2CPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, EmoteStopS2CPayload::entityId,
            EmoteStopS2CPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
