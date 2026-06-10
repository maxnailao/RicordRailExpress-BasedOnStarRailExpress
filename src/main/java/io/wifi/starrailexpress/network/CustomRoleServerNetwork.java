package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.SRE;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自定义职业服务端网络处理
 * 服务端发送自定义职业 JSON 给客户端，按玩家跟踪 hash 避免重复发送
 */
public class CustomRoleServerNetwork {

    private static String cachedJsonContent = null;
    private static int cachedHash = 0;
    private static boolean hasCustomRoles = false;
    // 记录每个玩家最后一次收到的 hash，避免重复发送相同内容
    private static final Map<UUID, Integer> playerHashCache = new ConcurrentHashMap<>();

    /**
     * 同步给所有在线玩家（仅在配置变更后调用）
     */
    public static void syncToAllPlayers(MinecraftServer server) {
        loadConfigContent(server);
        if (!hasCustomRoles) return;

        int currentHash = cachedHash;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncToPlayerWithHash(player, currentHash);
        }
    }

    /**
     * 发送给新加入的玩家
     */
    public static void syncToPlayer(MinecraftServer server, ServerPlayer player) {
        loadConfigContent(server);
        if (!hasCustomRoles) return;

        syncToPlayerWithHash(player, cachedHash);
    }

    /**
     * 带 hash 对比的发送：如果玩家已有相同的 hash 则跳过
     */
    private static void syncToPlayerWithHash(ServerPlayer player, int currentHash) {
        Integer lastHash = playerHashCache.get(player.getUUID());
        if (lastHash != null && lastHash == currentHash) {
            return; // hash 相同，无需重发
        }
        ServerPlayNetworking.send(player, new CustomRoleSyncPayload(currentHash, cachedJsonContent));
        playerHashCache.put(player.getUUID(), currentHash);
    }

    /**
     * 玩家断开时清理记录
     */
    public static void onPlayerDisconnect(UUID playerId) {
        playerHashCache.remove(playerId);
    }

    /**
     * 读取服务端存档中的 sre_custom_roles.json，计算 hash
     */
    private static void loadConfigContent(MinecraftServer server) {
        if (cachedJsonContent != null) return;
        try {
            Path worldPath = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
            Path configPath = worldPath.resolve("sre_custom_roles.json");
            if (Files.exists(configPath)) {
                String content = Files.readString(configPath, StandardCharsets.UTF_8);
                String trimmed = content.trim();
                if (!trimmed.isEmpty() && !trimmed.equals("{}") && !trimmed.equals("{\"roles\":[]}")) {
                    cachedJsonContent = content;
                    cachedHash = content.hashCode();
                    hasCustomRoles = true;
                    return;
                }
            }
        } catch (IOException e) {
            SRE.LOGGER.error("[CustomRole] Failed to read sre_custom_roles.json for sync", e);
        }
        hasCustomRoles = false;
    }

    /**
     * 清除所有缓存（重载角色配置后调用）
     */
    public static void clearCache() {
        cachedJsonContent = null;
        cachedHash = 0;
        hasCustomRoles = false;
        playerHashCache.clear();
    }
}
