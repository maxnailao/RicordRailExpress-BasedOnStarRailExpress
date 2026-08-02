package org.agmas.noellesroles.game.modes.werewolf.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.game.modes.werewolf.*;

/**
 * 狼人杀投票 C2S 包
 * Author: jiale
 */
public record WerewolfVoteC2SPacket(
        int targetSeat      // 投票目标座位编号（-1 表示弃票）
) implements CustomPacketPayload {

    public static final Type<WerewolfVoteC2SPacket> TYPE =
            new Type<>(SRE.jialeId("werewolf_vote"));

    public static final StreamCodec<FriendlyByteBuf, WerewolfVoteC2SPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, WerewolfVoteC2SPacket::targetSeat,
            WerewolfVoteC2SPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 服务端处理
     */
    public void handle(ServerPlayer player) {
        var level = player.serverLevel();
        WerewolfGameState state = WerewolfGameState.get(level);
        
        if (!state.active) {
            return;
        }

        // 只在投票阶段处理
        if (state.phase != WerewolfPhase.DAY_VOTE && state.phase != WerewolfPhase.DAY_VOTE_PK_RESULT) {
            return;
        }

        // 获取目标 UUID
        java.util.UUID targetUuid = targetSeat >= 0 ? state.getPlayerBySeat(targetSeat) : null;

        // PK 投票时只能投给 PK 玩家
        if (state.phase == WerewolfPhase.DAY_VOTE_PK_RESULT && targetUuid != null) {
            if (!state.pkPlayers.contains(targetUuid)) {
                return; // 无效目标
            }
        }

        WerewolfDayManager.handleVote(level, state, player.getUUID(), targetUuid);
    }
}
