package org.agmas.noellesroles.game.roles.innocence.zhensouzhe;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.role.ModRoles;

import java.util.UUID;

/**
 * 侦搜者服务端逻辑
 *
 * 平民阵营，真实心情，有限体力，可见时间
 *
 * 技能：
 * - 技能1：打开物品栏点击玩家头像，花费75金币知晓该玩家是否存活（无冷却）
 * - 技能2：按下技能键知晓场上剩余存活人数，冷却90秒（由统一技能系统管理）
 */
public final class ZhensouzheHandler {

    /** 技能1：查询单个玩家存活状态的花费 */
    public static final int QUERY_COST = 75;

    private ZhensouzheHandler() {
    }

    /**
     * 技能1：花费金币查询目标玩家是否存活
     *
     * @param scout     使用技能的侦搜者
     * @param targetUuid 被查询玩家的 UUID
     */
    public static void queryTargetAlive(ServerPlayer scout, UUID targetUuid) {
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(scout.level());
        if (!gameWorldComponent.isRunning()) return;
        if (!gameWorldComponent.isRole(scout, ModRoles.ZHENSOUZHE)) return;

        // 检查自身是否存活
        if (!GameUtils.isPlayerAliveAndSurvival(scout)) return;

        if (targetUuid == null) return;
        Player target = scout.level().getPlayerByUUID(targetUuid);
        if (target == null) return;

        // 检查金币余额
        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(scout);
        if (shop.balance < QUERY_COST) {
            scout.displayClientMessage(
                    Component.translatable("message.noellesroles.zhensouzhe.insufficient_gold", QUERY_COST)
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        // 扣除金币
        shop.addToBalance(-QUERY_COST);

        // 反馈目标存活状态
        boolean alive = GameUtils.isPlayerAliveAndSurvival(target);
        scout.displayClientMessage(
                Component.translatable(
                        alive ? "message.noellesroles.zhensouzhe.target_alive"
                                : "message.noellesroles.zhensouzhe.target_dead",
                        target.getName().getString())
                        .withStyle(alive ? ChatFormatting.GREEN : ChatFormatting.RED),
                true);
    }

    /**
     * 技能2：统计并告知场上剩余存活人数
     *
     * @param scout 使用技能的侦搜者
     * @return true 表示技能成功释放（进入冷却）
     */
    public static boolean scanAliveCount(ServerPlayer scout) {
        if (scout.isSpectator()) return false;

        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(scout.level());
        if (!gameWorldComponent.isRunning()) return false;
        if (!gameWorldComponent.isRole(scout, ModRoles.ZHENSOUZHE)) return false;
        if (!GameUtils.isPlayerAliveAndSurvival(scout)) return false;

        int aliveCount = 0;
        for (ServerPlayer p : scout.serverLevel().players()) {
            if (GameUtils.isPlayerAliveAndSurvival(p)) {
                aliveCount++;
            }
        }

        scout.displayClientMessage(
                Component.translatable("message.noellesroles.zhensouzhe.scan_result", aliveCount)
                        .withStyle(ChatFormatting.AQUA),
                true);
        return true;
    }
}
