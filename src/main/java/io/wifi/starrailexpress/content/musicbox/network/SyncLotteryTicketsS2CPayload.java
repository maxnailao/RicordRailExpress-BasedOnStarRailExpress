package io.wifi.starrailexpress.content.musicbox.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端→客户端：强制同步玩家的音乐盒抽奖次数。
 * <p>用于绕过 CCA AutoSyncedComponent 在特定时序下同步不可靠的问题。</p>
 *
 * @param tickets 当前抽奖次数
 */
public record SyncLotteryTicketsS2CPayload(int tickets) implements CustomPacketPayload {

    public static final Type<SyncLotteryTicketsS2CPayload> ID = new Type<>(SRE.id("sync_lottery_tickets"));
    public static final StreamCodec<FriendlyByteBuf, SyncLotteryTicketsS2CPayload> CODEC = StreamCodec.ofMember(
            (payload, buf) -> buf.writeVarInt(payload.tickets),
            buf -> new SyncLotteryTicketsS2CPayload(buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
