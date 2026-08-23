package io.wifi.starrailexpress.emote;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 表情播放请求包（客户端 -> 服务端）
 * 玩家在表情罗盘中选择表情后发送，由服务端校验并广播给所有同维度玩家
 *
 * @param emoteId 表情 ID（见 {@link EmoteType#id()}）
 */
public record EmotePlayC2SPayload(String emoteId) implements CustomPacketPayload {

    public static final Type<EmotePlayC2SPayload> ID = new Type<>(SRE.id("emote_play"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EmotePlayC2SPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, EmotePlayC2SPayload::emoteId,
            EmotePlayC2SPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
