package io.wifi.starrailexpress.content.musicbox.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record MusicBoxLotteryResultS2CPayload(boolean won, String musicBoxName, int remainingTickets) implements CustomPacketPayload {

    public static final Type<MusicBoxLotteryResultS2CPayload> ID = new Type<>(SRE.id("music_box_lottery_result"));
    public static final StreamCodec<FriendlyByteBuf, MusicBoxLotteryResultS2CPayload> CODEC = StreamCodec.ofMember(
            (payload, buf) -> {
                buf.writeBoolean(payload.won);
                buf.writeUtf(payload.musicBoxName);
                buf.writeVarInt(payload.remainingTickets);
            },
            buf -> new MusicBoxLotteryResultS2CPayload(
                    buf.readBoolean(),
                    buf.readUtf(),
                    buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}