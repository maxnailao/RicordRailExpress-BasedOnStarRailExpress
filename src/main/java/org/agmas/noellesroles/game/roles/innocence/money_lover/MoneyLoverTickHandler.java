package org.agmas.noellesroles.game.roles.innocence.money_lover;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.game.roles.innocence.role.ModRoles;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 敛财者角色 Tick 处理器
 *
 * 技能：
 * - 开局仅有 40% 理智值
 * - 理智不会通过任务来恢复
 * - 花费 1 金币恢复 1 点理智（每 10 tick / 0.5 秒判定一次）
 * - 若金币 >= 1，扣除 1 金币恢复 1 点理智值
 * - 理智值满则不进行置换
 */
public class MoneyLoverTickHandler {

    /** 玩家 tick 计数器（仅存储敛财者玩家的 tick 计数） */
    private static final Map<UUID, Integer> tickCounters = new HashMap<>();

    /** 判定间隔：10 tick = 0.5 秒 */
    private static final int CHECK_INTERVAL = 10;

    /** 每次恢复的理智值（mood 范围 0.0-1.0，0.01 = 1% = 1 点理智） */
    private static final float MOOD_RECOVERY = 0.01f;

    /** 每次扣除的金币数 */
    private static final int GOLD_COST = 1;

    /**
     * 每 tick 调用（通过 setServerGameTickEvent 注册）
     */
    public static void serverTick(ServerPlayer player, SREGameWorldComponent gameComponent) {
        // 验证游戏状态
        if (!gameComponent.isRunning() || !GameUtils.isPlayerAliveAndSurvival(player)) {
            tickCounters.remove(player.getUUID());
            return;
        }

        // 验证是否为敛财者角色
        if (!gameComponent.isRole(player, ModRoles.ILIKEMONEY)) {
            tickCounters.remove(player.getUUID());
            return;
        }

        // 获取并递增 tick 计数器
        int tickCount = tickCounters.getOrDefault(player.getUUID(), 0) + 1;
        tickCounters.put(player.getUUID(), tickCount);

        // 每 10 tick 判定一次
        if (tickCount % CHECK_INTERVAL != 0) return;

        // 获取理智值组件
        SREPlayerMoodComponent moodComponent = SREPlayerMoodComponent.KEY.get(player);
        float currentMood = moodComponent.getMood();

        // 理智值已满（>= 1.0），不进行置换
        if (currentMood >= 1.0f) return;

        // 获取金币组件
        SREPlayerShopComponent shopComponent = SREPlayerShopComponent.KEY.get(player);
        int balance = shopComponent.balance;

        // 金币 >= 1 时，扣除 1 金币恢复 1 点理智
        if (balance >= GOLD_COST) {
            shopComponent.addToBalance(-GOLD_COST);
            moodComponent.addMood(MOOD_RECOVERY);
        }
    }

    /**
     * 清理玩家的 tick 计数器（游戏结束时调用）
     */
    public static void removePlayer(UUID playerUUID) {
        tickCounters.remove(playerUUID);
    }

    /**
     * 清理所有 tick 计数器
     */
    public static void clearAll() {
        tickCounters.clear();
    }
}
