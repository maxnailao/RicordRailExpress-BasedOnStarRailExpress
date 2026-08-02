package org.agmas.noellesroles.game.modes.werewolf.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.game.modes.werewolf.*;

/**
 * 狼人杀夜晚行动 C2S 包
 * 玩家提交夜晚操作（目标编号 + 操作类型）
 * Author: jiale
 */
public record WerewolfActionC2SPacket(
        int targetSeat,      // 目标座位编号（-1 表示跳过/不行动）
        byte actionType      // 操作类型：0=普通选择, 1=炼药师使用解药, 2=炼药师使用毒药
) implements CustomPacketPayload {

    public static final Type<WerewolfActionC2SPacket> TYPE =
            new Type<>(SRE.jialeId("werewolf_action"));

    public static final StreamCodec<FriendlyByteBuf, WerewolfActionC2SPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, WerewolfActionC2SPacket::targetSeat,
            ByteBufCodecs.BYTE, WerewolfActionC2SPacket::actionType,
            WerewolfActionC2SPacket::new);

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

        // 验证是否是当前行动者
        if (!player.getUUID().equals(state.currentActor) && state.phase != WerewolfPhase.NIGHT_WOLVES) {
            return;
        }

        // 获取目标 UUID
        java.util.UUID targetUuid = targetSeat >= 0 ? state.getPlayerBySeat(targetSeat) : null;

        switch (state.phase) {
            case NIGHT_GUARDIAN -> {
                // 守护者不能连续守护同一人
                var comp = org.agmas.noellesroles.component.ModComponents.WEREWOLF.get(player);
                if (targetSeat == comp.lastGuardTarget && targetSeat >= 0) {
                    player.displayClientMessage(
                            net.minecraft.network.chat.Component.translatable("werewolf.msg.guardian_same_target")
                                    .withStyle(net.minecraft.ChatFormatting.RED),
                            true);
                    return; // 不允许，等待重新选择
                }
                state.guardianTarget = targetUuid;
                comp.lastGuardTarget = targetSeat;
                comp.sync();
                advanceToNextPhase(level, state);
            }
            case NIGHT_WOLVES -> {
                // 验证玩家是否是狼方
                var wolfComp = org.agmas.noellesroles.component.ModComponents.WEREWOLF.get(player);
                if (!wolfComp.isWolf()) {
                    return; // 非狼方不能投票
                }
                // 狼方投票
                if (targetUuid != null) {
                    state.wolfVotes.put(player.getUUID(), targetUuid);
                }
                // 检查是否所有狼人都已投票
                var wolves = state.getAlivePlayersByFaction(level, WerewolfRoleDef.Faction.WOLF);
                if (state.wolfVotes.size() >= wolves.size()) {
                    WerewolfNightManager.resolveWolfVotes(level, state);
                    advanceToNextPhase(level, state);
                }
            }
            case NIGHT_ALCHEMIST -> {
                var alchComp = org.agmas.noellesroles.component.ModComponents.WEREWOLF.get(player);
                if (actionType == 1) {
                    // 使用解药 - 检查是否已使用
                    if (alchComp.usedAntidote) {
                        player.displayClientMessage(
                                net.minecraft.network.chat.Component.translatable("werewolf.msg.alchemist_no_antidote")
                                        .withStyle(net.minecraft.ChatFormatting.RED),
                                true);
                        return;
                    }
                    state.alchemistSaveTarget = state.wolfTarget; // 救被狼杀的人
                } else if (actionType == 2 && targetUuid != null) {
                    // 使用毒药 - 检查是否已使用
                    if (alchComp.usedPoison) {
                        player.displayClientMessage(
                                net.minecraft.network.chat.Component.translatable("werewolf.msg.alchemist_no_poison")
                                        .withStyle(net.minecraft.ChatFormatting.RED),
                                true);
                        return;
                    }
                    state.alchemistPoisonTarget = targetUuid;
                }
                advanceToNextPhase(level, state);
            }
            case NIGHT_PROPHET -> {
                state.prophetTarget = targetUuid;
                // 发送查验结果给预言家
                if (targetUuid != null) {
                    boolean isTargetWolf = state.isWolf(level, targetUuid);
                    int targetSeatNum = state.getSeatNumber(targetUuid);
                    String resultKey = isTargetWolf ? "werewolf.msg.prophet_result_wolf" : "werewolf.msg.prophet_result_good";
                    player.displayClientMessage(
                            net.minecraft.network.chat.Component.translatable(resultKey, targetSeatNum)
                                    .withStyle(isTargetWolf ? net.minecraft.ChatFormatting.RED : net.minecraft.ChatFormatting.GREEN),
                            false);
                }
                advanceToNextPhase(level, state);
            }
            case NIGHT_KNIGHT -> {
                state.knightTarget = targetUuid;
                advanceToNextPhase(level, state);
            }
            case DAY_HUNTER_SHOT -> {
                WerewolfNightManager.handleHunterShot(level, state, targetUuid);
            }
            case DAY_EXECUTE -> {
                WerewolfDayManager.handleWolfKingShot(level, state, targetUuid);
            }
            default -> {}
        }
    }

    private void advanceToNextPhase(net.minecraft.server.level.ServerLevel level, WerewolfGameState state) {
        // 委托给 WerewolfGameMode 以正确广播和通知
        var gameMode = io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(level).getGameMode();
        if (gameMode instanceof WerewolfGameMode wwMode) {
            wwMode.advanceNightPhasePublic(level, state);
        }
    }
}
