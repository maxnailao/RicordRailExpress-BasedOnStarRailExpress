package io.wifi.starrailexpress.emote;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 表情停止请求包（客户端 -> 服务端）
 * 本地玩家在播放表情期间按下移动按键时立即发送，保证打断的即时响应
 */
public record EmoteStopC2SPayload() implements CustomPacketPayload {

    public static final Type<EmoteStopC2SPayload> ID = new Type<>(SRE.id("emote_stop"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EmoteStopC2SPayload> CODEC =
            StreamCodec.ofMember(EmoteStopC2SPayload::write, EmoteStopC2SPayload::read);

    public void write(FriendlyByteBuf buf) {
        // 无内容：发送者身份即玩家
    }

    public static EmoteStopC2SPayload read(FriendlyByteBuf buf) {
        return new EmoteStopC2SPayload();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
