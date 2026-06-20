package io.wifi.starrailexpress.content.minigame.mahjong;

import io.wifi.starrailexpress.network.packet.MahjongStateS2CPacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

/**
 * 麻将会话管理器
 * 管理等待队列（最多4人）和活跃游戏
 */
public class MahjongSessionManager {

    public static final MahjongSessionManager INSTANCE = new MahjongSessionManager();

    private final List<ServerPlayer> waitingQueue = new ArrayList<>();
    private final Map<UUID, MahjongSession> activeGames = new HashMap<>();

    private MahjongSessionManager() {}

    public void handleJoin(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (activeGames.containsKey(uuid)) return;
        for (ServerPlayer w : waitingQueue) if (w.getUUID().equals(uuid)) return;

        waitingQueue.add(player);

        if (waitingQueue.size() >= 4) {
            ServerPlayer p0 = waitingQueue.remove(0);
            ServerPlayer p1 = waitingQueue.remove(0);
            ServerPlayer p2 = waitingQueue.remove(0);
            ServerPlayer p3 = waitingQueue.remove(0);
            startGame(p0, p1, p2, p3);
        } else {
            int playerIndex = waitingQueue.indexOf(player);
            String[] names = new String[4];
            for (int i = 0; i < waitingQueue.size(); i++)
                names[i] = waitingQueue.get(i).getName().getString();
            for (int i = waitingQueue.size(); i < 4; i++) names[i] = "";
            MahjongStateS2CPacket pkt = MahjongStateS2CPacket.waiting(playerIndex, names, waitingQueue.size());
            ServerPlayNetworking.send(player, pkt);
        }
    }

    public void handleLeave(ServerPlayer player) {
        UUID uuid = player.getUUID();
        waitingQueue.removeIf(w -> w.getUUID().equals(uuid));

        MahjongSession session = activeGames.get(uuid);
        if (session != null && !session.isFinished()) {
            session.handlePlayerLeave(player);
            cleanupSession(session);
        }
    }

    public void handleDiscard(ServerPlayer player, int tileId) {
        MahjongSession session = activeGames.get(player.getUUID());
        if (session == null || session.isFinished()) return;
        session.routeDiscard(player, tileId);
        if (session.isFinished()) cleanupSession(session);
    }

    public void handleAction(ServerPlayer player, byte actionType, byte tileType) {
        MahjongSession session = activeGames.get(player.getUUID());
        if (session == null || session.isFinished()) return;
        session.routeAction(player, actionType, tileType);
        if (session.isFinished()) cleanupSession(session);
    }

    private void startGame(ServerPlayer p0, ServerPlayer p1, ServerPlayer p2, ServerPlayer p3) {
        MahjongSession session = new MahjongSession(p0, p1, p2, p3);
        for (int i = 0; i < 4; i++) {
            if (session.getPlayer(i) != null)
                activeGames.put(session.getPlayer(i).getUUID(), session);
        }
        session.broadcastState();
    }

    private void cleanupSession(MahjongSession session) {
        for (int i = 0; i < 4; i++) {
            if (session.getPlayer(i) != null)
                activeGames.remove(session.getPlayer(i).getUUID());
        }
    }

    /** 由服务端 tick 事件调用，处理动作超时 */
    public void tick() {
        Set<MahjongSession> processed = new HashSet<>();
        for (MahjongSession session : activeGames.values()) {
            if (!session.isFinished() && processed.add(session)) {
                session.tickActionTimeout();
            }
        }
    }
}
