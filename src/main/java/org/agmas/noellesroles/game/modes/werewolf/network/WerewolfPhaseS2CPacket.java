package org.agmas.noellesroles.game.modes.werewolf.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 狼人杀阶段同步 S2C 包
 * Author: jiale
 */
public record WerewolfPhaseS2CPacket(
        byte phaseId,           // 阶段 ID
        int currentActorSeat,   // 当前行动者座位（-1 表示无）
        long deadlineTick,      // 截止时间 tick
        int round               // 当前轮次
) implements CustomPacketPayload {

    public static final Type<WerewolfPhaseS2CPacket> TYPE =
            new Type<>(SRE.jialeId("werewolf_phase"));

    public static final StreamCodec<FriendlyByteBuf, WerewolfPhaseS2CPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.BYTE, WerewolfPhaseS2CPacket::phaseId,
            ByteBufCodecs.INT, WerewolfPhaseS2CPacket::currentActorSeat,
            ByteBufCodecs.VAR_LONG, WerewolfPhaseS2CPacket::deadlineTick,
            ByteBufCodecs.INT, WerewolfPhaseS2CPacket::round,
            WerewolfPhaseS2CPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
