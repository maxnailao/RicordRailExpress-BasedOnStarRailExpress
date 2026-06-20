package io.wifi.starrailexpress.content.minigame.doudizhu;

import io.wifi.starrailexpress.network.packet.DoudizhuStateS2CPacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

/**
 * 斗地主会话管理器
 * <p>
 * 管理等待队列（最多2人）和活跃游戏。
 * 第3人加入时自动开始游戏。
 * 首位等待者超过10秒可请求AI补位。
 * </p>
 */
public class DoudizhuSessionManager {

    public static final DoudizhuSessionManager INSTANCE = new DoudizhuSessionManager();

    private final List<ServerPlayer> waitingQueue = new ArrayList<>();
    private final Map<UUID, DoudizhuSession> activeGames = new HashMap<>();
    private long firstWaitTime = 0;

    private DoudizhuSessionManager() {}

    // ── 加入/离开 ──

    public void handleJoin(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (activeGames.containsKey(uuid)) return;
        for (ServerPlayer w : waitingQueue) if (w.getUUID().equals(uuid)) return;

        waitingQueue.add(player);

        if (waitingQueue.size() >= 3) {
            ServerPlayer p0 = waitingQueue.remove(0);
            ServerPlayer p1 = waitingQueue.remove(0);
            ServerPlayer p2 = waitingQueue.remove(0);
            firstWaitTime = 0;
            startGame(p0, p1, p2);
        } else {
            if (waitingQueue.size() == 1) firstWaitTime = System.currentTimeMillis();
            // 发送等待状态
            int playerIndex = waitingQueue.indexOf(player);
            String[] names = new String[3];
            for (int i = 0; i < waitingQueue.size(); i++)
                names[i] = waitingQueue.get(i).getName().getString();
            for (int i = waitingQueue.size(); i < 3; i++) names[i] = "";
            DoudizhuStateS2CPacket pkt = DoudizhuStateS2CPacket.waiting(playerIndex, names, waitingQueue.size());
            ServerPlayNetworking.send(player, pkt);
        }
    }

    public void handleLeave(ServerPlayer player) {
        UUID uuid = player.getUUID();
        waitingQueue.removeIf(w -> w.getUUID().equals(uuid));

        DoudizhuSession session = activeGames.get(uuid);
        if (session != null && !session.isFinished()) {
            session.handleOpponentLeft(player);
            cleanupSession(session);
        }
    }

    // ── AI补位 ──

    public void handleFillAI(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (activeGames.containsKey(uuid)) return;
        int idx = -1;
        for (int i = 0; i < waitingQueue.size(); i++)
            if (waitingQueue.get(i).getUUID().equals(uuid)) { idx = i; break; }
        if (idx < 0) return;

        // 用AI补齐剩余位置
        while (waitingQueue.size() < 3) waitingQueue.add(null);
        ServerPlayer p0 = waitingQueue.remove(0);
        ServerPlayer p1 = waitingQueue.remove(0);
        ServerPlayer p2 = waitingQueue.remove(0);
        firstWaitTime = 0;
        startGame(p0, p1, p2);
    }

    // ── 叫分/出牌 ──

    public void handleBid(ServerPlayer player, int bidScore) {
        DoudizhuSession session = activeGames.get(player.getUUID());
        if (session == null || session.isFinished()) return;
        int pi = session.getPlayerIndex(player);
        if (pi < 0) return;
        session.handleBid(pi, bidScore);
        if (session.isFinished()) cleanupSession(session);
    }

    public void handlePlay(ServerPlayer player, int[] cardIds) {
        DoudizhuSession session = activeGames.get(player.getUUID());
        if (session == null || session.isFinished()) return;
        int pi = session.getPlayerIndex(player);
        if (pi < 0) return;
        session.handlePlay(pi, cardIds);
        if (session.isFinished()) cleanupSession(session);
    }

    // ── 内部方法 ──

    private void startGame(ServerPlayer p0, ServerPlayer p1, ServerPlayer p2) {
        DoudizhuSession session = new DoudizhuSession(p0, p1, p2);
        for (int i = 0; i < 3; i++) {
            if (!session.isAI(i) && session.getPlayer(i) != null)
                activeGames.put(session.getPlayer(i).getUUID(), session);
        }
        session.broadcastState();
    }

    private void cleanupSession(DoudizhuSession session) {
        for (int i = 0; i < 3; i++) {
            if (!session.isAI(i) && session.getPlayer(i) != null)
                activeGames.remove(session.getPlayer(i).getUUID());
        }
    }

    /** 由服务端 tick 事件调用，驱动 AI 行动 */
    public void tick() {
        for (DoudizhuSession session : activeGames.values()) {
            if (!session.isFinished() && session.hasAIPlayers()) {
                session.tickAI();
            }
        }
    }

    public long getFirstWaitTime() { return firstWaitTime; }
}
