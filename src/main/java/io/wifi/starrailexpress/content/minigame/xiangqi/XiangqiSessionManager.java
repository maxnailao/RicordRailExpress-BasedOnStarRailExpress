package io.wifi.starrailexpress.content.minigame.xiangqi;

import io.wifi.starrailexpress.network.packet.XiangqiStateS2CPacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 象棋会话管理器
 * <p>
 * 负责玩家匹配队列和活跃游戏管理。单例模式。
 * 先等待者为红方（先行），后加入者为黑方。
 * </p>
 */
public class XiangqiSessionManager {

    public static final XiangqiSessionManager INSTANCE = new XiangqiSessionManager();

    private ServerPlayer waitingPlayer = null;
    private final Map<UUID, XiangqiSession> activeGames = new HashMap<>();

    private XiangqiSessionManager() {}

    public void handleJoin(ServerPlayer player) {
        UUID uuid = player.getUUID();
        // 已在游戏中：重发当前状态（客户端重连/重开界面时补发，避免卡在等待界面）
        XiangqiSession existing = activeGames.get(uuid);
        if (existing != null) {
            existing.resyncTo(player);
            return;
        }

        // 等待玩家已断线时清理，防止幽灵玩家被匹配
        if (waitingPlayer != null && (waitingPlayer.connection == null || waitingPlayer.hasDisconnected()))
            waitingPlayer = null;

        // 自己已在等待：重发等待状态
        if (waitingPlayer != null && waitingPlayer.getUUID().equals(uuid)) {
            ServerPlayNetworking.send(player, XiangqiStateS2CPacket.waiting());
            return;
        }

        if (waitingPlayer != null && !waitingPlayer.getUUID().equals(uuid)) {
            ServerPlayer redPlayer = waitingPlayer;  // 先等待 = 红方
            ServerPlayer blackPlayer = player;        // 后加入 = 黑方
            waitingPlayer = null;

            XiangqiSession session = new XiangqiSession(redPlayer, blackPlayer);
            activeGames.put(redPlayer.getUUID(), session);
            activeGames.put(blackPlayer.getUUID(), session);
            session.broadcastGameStart();
        } else {
            waitingPlayer = player;
            ServerPlayNetworking.send(player, XiangqiStateS2CPacket.waiting());
        }
    }

    public void handleMove(ServerPlayer player, int fr, int fc, int tr, int tc) {
        XiangqiSession session = activeGames.get(player.getUUID());
        if (session == null || session.isFinished()) return;
        session.movePiece(fr, fc, tr, tc, player);
        if (session.isFinished()) cleanupSession(session);
    }

    public void handleLeave(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (waitingPlayer != null && waitingPlayer.getUUID().equals(uuid)) {
            waitingPlayer = null;
            return;
        }
        XiangqiSession session = activeGames.get(uuid);
        if (session != null && !session.isFinished()) {
            session.handleOpponentLeft(player);
            cleanupSession(session);
        }
    }

    private void cleanupSession(XiangqiSession session) {
        activeGames.remove(session.getRedPlayer().getUUID());
        activeGames.remove(session.getBlackPlayer().getUUID());
    }
}
