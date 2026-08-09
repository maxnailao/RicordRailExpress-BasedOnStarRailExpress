package io.wifi.starrailexpress.content.minigame.gomoku;

import io.wifi.starrailexpress.network.packet.GomokuStateS2CPacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 五子棋会话管理器
 * <p>
 * 负责玩家匹配队列和活跃游戏管理。
 * 单例模式，通过 INSTANCE 访问。
 * </p>
 */
public class GomokuSessionManager {

    public static final GomokuSessionManager INSTANCE = new GomokuSessionManager();

    /** 队列中等待的玩家 */
    private ServerPlayer waitingPlayer = null;

    /** 活跃游戏：玩家UUID -> 所在会话 */
    private final Map<UUID, GomokuSession> activeGames = new HashMap<>();

    private GomokuSessionManager() {}

    /**
     * 玩家加入匹配队列
     * - 若已有等待玩家（且不是自己），创建会话并开始游戏
     * - 否则存入等待队列，发送等待包
     */
    public void handleJoin(ServerPlayer player) {
        UUID uuid = player.getUUID();

        // 已在游戏中：重发当前状态（客户端重连/重开界面时补发，避免卡在等待界面）
        GomokuSession existing = activeGames.get(uuid);
        if (existing != null) {
            existing.resyncTo(player);
            return;
        }

        // 等待玩家已断线时清理，防止幽灵玩家被匹配
        if (waitingPlayer != null && (waitingPlayer.connection == null || waitingPlayer.hasDisconnected()))
            waitingPlayer = null;

        // 自己已在等待：重发等待状态
        if (waitingPlayer != null && waitingPlayer.getUUID().equals(uuid)) {
            ServerPlayNetworking.send(player, GomokuStateS2CPacket.waiting());
            return;
        }

        if (waitingPlayer != null && !waitingPlayer.getUUID().equals(uuid)) {
            // 匹配成功，创建会话
            ServerPlayer blackPlayer = waitingPlayer;  // 先等待的 = 黑棋（先手）
            ServerPlayer whitePlayer = player;         // 后加入的 = 白棋
            waitingPlayer = null;

            GomokuSession session = new GomokuSession(blackPlayer, whitePlayer);
            activeGames.put(blackPlayer.getUUID(), session);
            activeGames.put(whitePlayer.getUUID(), session);

            // 双方发送 game_start
            session.broadcastGameStart();
        } else {
            // 进入等待队列
            waitingPlayer = player;
            ServerPlayNetworking.send(player, GomokuStateS2CPacket.waiting());
        }
    }

    /**
     * 玩家落子
     */
    public void handleMove(ServerPlayer player, int row, int col) {
        GomokuSession session = activeGames.get(player.getUUID());
        if (session == null || session.isFinished()) return;

        session.placeStone(row, col, player);

        // 如果游戏结束，清理会话
        if (session.isFinished()) {
            cleanupSession(session);
        }
    }

    /**
     * 玩家离开（关闭界面或断开连接）
     */
    public void handleLeave(ServerPlayer player) {
        UUID uuid = player.getUUID();

        // 如果在等待队列中，移除
        if (waitingPlayer != null && waitingPlayer.getUUID().equals(uuid)) {
            waitingPlayer = null;
            return;
        }

        // 如果在游戏中，通知对手
        GomokuSession session = activeGames.get(uuid);
        if (session != null && !session.isFinished()) {
            session.handleOpponentLeft(player);
            cleanupSession(session);
        }
    }

    /**
     * 清理已结束的会话
     */
    private void cleanupSession(GomokuSession session) {
        activeGames.remove(session.getBlackPlayer().getUUID());
        activeGames.remove(session.getWhitePlayer().getUUID());
    }
}
