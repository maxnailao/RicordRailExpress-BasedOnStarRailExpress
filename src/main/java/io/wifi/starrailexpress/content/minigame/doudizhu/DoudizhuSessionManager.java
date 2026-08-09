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
        // 已在游戏中：重发当前状态（客户端重连/重开界面时补发，避免卡在等待界面）
        DoudizhuSession existing = activeGames.get(uuid);
        if (existing != null) {
            existing.resyncTo(player);
            return;
        }
        // 清理已断开连接的队列玩家，防止幽灵玩家占用座位
        waitingQueue.removeIf(w -> w.connection == null || w.hasDisconnected());
        // 自己已在等待队列：重发等待状态
        for (ServerPlayer w : waitingQueue) if (w.getUUID().equals(uuid)) {
            broadcastWaitingState();
            return;
        }

        waitingQueue.add(player);

        // 循环开局，支持多桌同时进行
        if (tryStartGames()) {
            firstWaitTime = 0;
            // 若队列中仍有等待玩家，同步最新队列状态
            broadcastWaitingState();
        } else {
            if (waitingQueue.size() == 1) firstWaitTime = System.currentTimeMillis();
            // 向所有等待玩家广播最新队列状态
            broadcastWaitingState();
        }
    }

    public void handleLeave(ServerPlayer player) {
        UUID uuid = player.getUUID();
        boolean wasInWaitingQueue = waitingQueue.removeIf(w -> w.getUUID().equals(uuid));
        // 有玩家离开队列时，向剩余等待玩家同步最新队列状态
        if (wasInWaitingQueue) {
            if (!waitingQueue.isEmpty()) firstWaitTime = System.currentTimeMillis();
            broadcastWaitingState();
        }

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

        // 用AI补齐剩余位置（仅在队列凑不满一桌时才需要AI补位）
        if (!tryStartGames()) {
            while (waitingQueue.size() < 3) waitingQueue.add(null);
            ServerPlayer p0 = waitingQueue.remove(0);
            ServerPlayer p1 = waitingQueue.remove(0);
            ServerPlayer p2 = waitingQueue.remove(0);
            startGame(p0, p1, p2);
        }
        firstWaitTime = 0;
        broadcastWaitingState();
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

    /**
     * 尽可能从队列中凑满并开始多桌游戏（支持多组玩家同时游玩）
     *
     * @return 是否至少开始了一桌
     */
    private boolean tryStartGames() {
        boolean started = false;
        while (waitingQueue.size() >= 3) {
            ServerPlayer p0 = waitingQueue.remove(0);
            ServerPlayer p1 = waitingQueue.remove(0);
            ServerPlayer p2 = waitingQueue.remove(0);
            startGame(p0, p1, p2);
            started = true;
        }
        return started;
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

    /** 向队列中所有等待玩家广播最新的等待状态（各自携带正确的座位索引） */
    private void broadcastWaitingState() {
        if (waitingQueue.isEmpty()) return;
        String[] names = new String[3];
        for (int i = 0; i < waitingQueue.size(); i++)
            names[i] = waitingQueue.get(i).getName().getString();
        for (int i = waitingQueue.size(); i < 3; i++) names[i] = "";
        for (int i = 0; i < waitingQueue.size(); i++) {
            ServerPlayer w = waitingQueue.get(i);
            ServerPlayNetworking.send(w, DoudizhuStateS2CPacket.waiting(i, names, waitingQueue.size()));
        }
    }

    /** 由服务端 tick 事件调用，驱动 AI 行动 */
    public void tick() {
        // activeGames 中每个会话被多名玩家共享，需去重避免同一会话被多次 tick
        Set<DoudizhuSession> processed = new HashSet<>();
        for (DoudizhuSession session : activeGames.values()) {
            if (!session.isFinished() && session.hasAIPlayers() && processed.add(session)) {
                session.tickAI();
            }
        }
    }

    public long getFirstWaitTime() { return firstWaitTime; }
}
