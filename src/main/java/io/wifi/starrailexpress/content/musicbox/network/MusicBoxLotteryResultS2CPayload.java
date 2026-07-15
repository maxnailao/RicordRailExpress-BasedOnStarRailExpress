package io.wifi.starrailexpress.content.musicbox.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端→客户端：音乐盒抽奖结果。
 *
 * @param won           是否抽中
 * @param musicBoxName  抽中的音乐盒显示名称（未抽中时为空字符串）
 * @param remainingTickets 剩余抽奖次数
 */
public record MusicBoxLotteryResultS2CPayload(boolean won, String musicBoxName, int remainingTickets)
        implements CustomPacketPayload {

    public static final Type<MusicBoxLotteryResultS2CPayload> ID = new Type<>(SRE.id("musicbox_lottery_result"));
    public static final StreamCodec<FriendlyByteBuf, MusicBoxLotteryResultS2CPayload> CODEC = StreamCodec.ofMember(
            (payload, buf) -> {
                buf.writeBoolean(payload.won);
                buf.writeUtf(payload.musicBoxName, 128);
                buf.writeVarInt(payload.remainingTickets);
            },
            buf -> new MusicBoxLotteryResultS2CPayload(buf.readBoolean(), buf.readUtf(128), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
