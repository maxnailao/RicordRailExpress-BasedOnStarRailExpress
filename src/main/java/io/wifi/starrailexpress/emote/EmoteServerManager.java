package io.wifi.starrailexpress.emote;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 表情系统服务端管理器。
 * <p>
 * 职责：
 * <ul>
 *   <li>校验并受理客户端的播放/停止请求</li>
 *   <li>每 tick 检测玩家是否移动，移动即打断表情</li>
 *   <li>表情到期自动结束</li>
 *   <li>向同维度所有玩家广播播放/停止状态</li>
 * </ul>
 */
public final class EmoteServerManager {

    /** 每 tick 允许的位置漂移阈值（方块），超过即视为移动打断 */
    private static final double MOVE_THRESHOLD_SQR = 0.03D * 0.03D;

    private static final Map<UUID, ActiveEmote> ACTIVE = new HashMap<>();

    private record ActiveEmote(EmoteType emote, long startGameTick, Vec3 lastPos) {
    }

    private EmoteServerManager() {
    }

    /**
     * 注册服务端接收器与 tick 事件（在 SREReceiverRegister 中调用）
     */
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(EmotePlayC2SPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> play(player, payload.emoteId()));
        });
        ServerPlayNetworking.registerGlobalReceiver(EmoteStopC2SPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> stop(player));
        });
        ServerTickEvents.END_SERVER_TICK.register(EmoteServerManager::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ACTIVE.remove(handler.getPlayer().getUUID()));
    }

    /**
     * 玩家请求播放表情
     */
    private static void play(ServerPlayer player, String emoteId) {
        if (player == null || !player.isAlive() || player.isSpectator()) {
            return;
        }
        EmoteType emote = EmoteType.byId(emoteId);
        if (emote == null) {
            return;
        }
        // 替换旧表情（允许连续切换）
        ACTIVE.put(player.getUUID(),
                new ActiveEmote(emote, player.level().getGameTime(), player.position()));
        EmotePlayS2CPayload payload = new EmotePlayS2CPayload(player.getId(), emote.id());
        for (ServerPlayer target : player.serverLevel().players()) {
            ServerPlayNetworking.send(target, payload);
        }
    }

    /**
     * 停止玩家的表情（若无激活表情则忽略）
     */
    private static void stop(ServerPlayer player) {
        if (player == null) {
            return;
        }
        if (ACTIVE.remove(player.getUUID()) == null) {
            return;
        }
        broadcastStop(player);
    }

    private static void broadcastStop(ServerPlayer player) {
        EmoteStopS2CPayload payload = new EmoteStopS2CPayload(player.getId());
        for (ServerPlayer target : player.serverLevel().players()) {
            ServerPlayNetworking.send(target, payload);
        }
    }

    /**
     * 每 tick 检查移动打断与到期结束
     */
    private static void tick(net.minecraft.server.MinecraftServer server) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        List<UUID> toStop = new ArrayList<>();
        for (Map.Entry<UUID, ActiveEmote> entry : ACTIVE.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            ActiveEmote active = entry.getValue();
            if (player == null || player.isRemoved() || !player.isAlive() || player.isSpectator()) {
                toStop.add(entry.getKey());
                continue;
            }
            long elapsed = player.level().getGameTime() - active.startGameTick();
            // 到期自然结束
            if (elapsed >= active.emote().durationTicks()) {
                toStop.add(entry.getKey());
                continue;
            }
            // 移动打断：只比较水平位移，忽略垂直方向的微小抖动
            Vec3 pos = player.position();
            double dx = pos.x - active.lastPos().x;
            double dz = pos.z - active.lastPos().z;
            if (dx * dx + dz * dz > MOVE_THRESHOLD_SQR) {
                toStop.add(entry.getKey());
            }
        }
        for (UUID uuid : toStop) {
            ActiveEmote removed = ACTIVE.remove(uuid);
            if (removed == null) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                broadcastStop(player);
            }
        }
    }
}
