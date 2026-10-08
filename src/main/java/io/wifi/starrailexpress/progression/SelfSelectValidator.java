package io.wifi.starrailexpress.progression;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.backpack.BackpackManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.harpymodloader.Harpymodloader;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 自选职业卡的「开始前复查」。
 *
 * <p>玩家用自选卡选职业发生在开局之前，而两件事可能在那之后才确定/变化：
 * <ol>
 * <li><b>地图</b>：特殊地图职业（雪原猎手、重刑犯…）只在特定地图刷新。
 * 选卡后地图若被换掉，这个选择就不该再生效，自选卡要退还；</li>
 * <li><b>人数</b>：开局瞬间的人数才是最终值，可能和选卡时不同。</li>
 * </ol>
 *
 * <p>因此在真正分配角色之前统一复查一遍：不满足就**撤销占位 + 退还自选卡 + 广播说明**。
 * 这样无论"选卡时判断"还是"开局时判断"，最终结果都由实际地图/人数决定。
 */
public final class SelfSelectValidator {

    private SelfSelectValidator() {
    }

    /**
     * 复查并撤销无效的自选占位，同时退还自选卡。
     *
     * @param serverLevel 本局所在世界（用于取地图）
     * @param currentMap  本局实际地图名
     * @param players     本局玩家
     * @return 被撤销的占位数量
     */
    public static int revalidate(ServerLevel serverLevel, @Nullable String currentMap,
            List<ServerPlayer> players) {
        if (players == null || players.isEmpty()) {
            return 0;
        }
        // 复制一份遍历：下面会改动 FORCED_MODDED_ROLE_FLIP
        var forced = new ArrayList<>(Harpymodloader.FORCED_MODDED_ROLE_FLIP.entrySet());
        int revoked = 0;
        for (var entry : forced) {
            UUID uuid = entry.getKey();
            SRERole role = entry.getValue();
            if (role == null) {
                continue;
            }
            SelfSelectGate.Deny deny = SelfSelectGate.checkForRound(role, players, currentMap);
            if (deny == SelfSelectGate.Deny.NONE) {
                continue;
            }
            Harpymodloader.releaseSelfForcedRole(role, uuid);
            revoked++;
            ServerPlayer player = findPlayer(players, uuid);
            // 退还自选卡：这是"选了但没生效"的补偿
            if (player != null) {
                BackpackManager.refundSelfSelectCard(player);
                player.displayClientMessage(refundMessage(role, deny, currentMap, players.size()), false);
            }
            org.agmas.noellesroles.Noellesroles.LOGGER.info(
                    "[SelfSelect] 撤销 {} 的自选职业 {}（原因 {}，地图 {}，人数 {}），已退还自选卡",
                    uuid, role.identifier(), deny, currentMap, players.size());
        }
        return revoked;
    }

    @Nullable
    private static ServerPlayer findPlayer(List<ServerPlayer> players, UUID uuid) {
        for (ServerPlayer p : players) {
            if (p.getUUID().equals(uuid)) {
                return p;
            }
        }
        return null;
    }

    private static Component refundMessage(SRERole role, SelfSelectGate.Deny deny,
            @Nullable String map, int playerCount) {
        if (deny == SelfSelectGate.Deny.WRONG_MAP) {
            return Component.translatable("message.sre.pass.selfselect_wrong_map",
                    Harpymodloader.getRoleName(role),
                    map == null ? "?" : map);
        }
        if (deny == SelfSelectGate.Deny.PLAYER_COUNT) {
            return Component.translatable("message.sre.pass.selfselect_player_count",
                    Harpymodloader.getRoleName(role),
                    SelfSelectGate.describeRequirement(role),
                    playerCount);
        }
        return Component.translatable("message.sre.pass.selfselect_not_selectable",
                Harpymodloader.getRoleName(role));
    }
}
