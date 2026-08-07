package org.agmas.noellesroles.game.modes.werewolf.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/**
 * 狼人杀阶段同步 S2C 包
 * Author: jiale
 */
public record WerewolfPhaseS2CPacket(
        byte phaseId,            // 阶段 ID
        int currentActorSeat,    // 当前行动者座位（-1 表示无）
        long deadlineTick,       // 截止时间 tick
        int round,               // 当前轮次
        List<Integer> aliveSeats, // 存活玩家座位列表
        List<String> seatNames   // 座位 1~N 对应的玩家名（索引=座位-1），用于头像显示
) implements CustomPacketPayload {

    public static final Type<WerewolfPhaseS2CPacket> TYPE =
            new Type<>(SRE.jialeId("werewolf_phase"));

    public static final StreamCodec<FriendlyByteBuf, WerewolfPhaseS2CPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.BYTE, WerewolfPhaseS2CPacket::phaseId,
            ByteBufCodecs.INT, WerewolfPhaseS2CPacket::currentActorSeat,
            ByteBufCodecs.VAR_LONG, WerewolfPhaseS2CPacket::deadlineTick,
            ByteBufCodecs.INT, WerewolfPhaseS2CPacket::round,
            ByteBufCodecs.INT.apply(ByteBufCodecs.list(64)), WerewolfPhaseS2CPacket::aliveSeats,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(64)), WerewolfPhaseS2CPacket::seatNames,
            WerewolfPhaseS2CPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
