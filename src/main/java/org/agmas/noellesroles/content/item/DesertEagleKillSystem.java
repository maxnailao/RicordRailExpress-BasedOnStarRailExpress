package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 沙漠之鹰独特击杀系统
 * 
 * 爆头命中 → 直接击杀
 * 身体命中 → 施加缓慢II(5秒) + 反胃(5秒)
 * 累计3发身体命中 → 击杀
 * 累计值10秒无更新则清零
 */
public class DesertEagleKillSystem {
    /** 累计命中致死次数 */
    private static final int KILL_THRESHOLD = 3;
    /** 累计值过期时间：10秒 = 200 ticks */
    private static final int HIT_EXPIRE_TICKS = 200;
    /** 致残效果持续时间：5秒 = 100 ticks */
    private static final int DEBUFF_DURATION = 100;
    /** 缓慢等级（缓慢II = 等级1） */
    private static final int SLOWNESS_LEVEL = 1;

    /**
     * 命中记录
     * 外层Key: 攻击者UUID
     * 内层Key: 目标UUID
     * 值: HitRecord（命中次数 + 最后命中tick）
     */
    private static final Map<UUID, Map<UUID, HitRecord>> HIT_TRACKER = new HashMap<>();

    private static class HitRecord {
        int hitCount;
        int lastHitTick;

        HitRecord(int count, int tick) {
            this.hitCount = count;
            this.lastHitTick = tick;
        }
    }

    /**
     * 处理命中事件（服务端调用）
     * 
     * @param shooter    射击者
     * @param target     被命中目标
     * @param isHeadshot 是否为爆头（由客户端射线检测判定）
     */
    public static void processHit(ServerPlayer shooter, ServerPlayer target, boolean isHeadshot) {
        if (isHeadshot) {
            // 爆头：直接击杀
            GameUtils.killPlayer(target, true, shooter, GameConstants.DeathReasons.DESERT_EAGLE);
            // 清除该攻击者对该目标的命中记录
            clearTargetHits(shooter.getUUID(), target.getUUID());
            return;
        }

        // 身体命中：施加致残效果
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, DEBUFF_DURATION, SLOWNESS_LEVEL, false, false));
        target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, DEBUFF_DURATION, 0, false, false));

        // 更新命中计数
        UUID shooterUUID = shooter.getUUID();
        UUID targetUUID = target.getUUID();
        int currentTick = shooter.serverLevel().getServer().getTickCount();

        Map<UUID, HitRecord> shooterHits = HIT_TRACKER.computeIfAbsent(shooterUUID, k -> new HashMap<>());
        HitRecord record = shooterHits.get(targetUUID);

        int newCount;
        if (record == null || (currentTick - record.lastHitTick) > HIT_EXPIRE_TICKS) {
            // 首次命中或已过期，重新开始计数
            newCount = 1;
        } else {
            newCount = record.hitCount + 1;
        }

        shooterHits.put(targetUUID, new HitRecord(newCount, currentTick));

        // 检查是否达到累计击杀阈值
        if (newCount >= KILL_THRESHOLD) {
            GameUtils.killPlayer(target, true, shooter, GameConstants.DeathReasons.DESERT_EAGLE);
            shooterHits.remove(targetUUID);
        }
    }

    /**
     * 定期清理过期的命中记录（每 tick 调用）
     */
    public static void tickCleanup() {
        // 此处无法直接获取 server tick，使用 System 时间近似
        // 实际的 tick 清理在 processHit 中已做过期判断
        // 此方法仅清理空 map 条目防止内存泄漏
        HIT_TRACKER.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    /**
     * 清理特定 tick 的过期记录（在服务端 tick 事件中调用）
     */
    public static void tickCleanup(int currentTick) {
        HIT_TRACKER.forEach((shooterUUID, targets) -> {
            targets.entrySet().removeIf(entry -> 
                (currentTick - entry.getValue().lastHitTick) > HIT_EXPIRE_TICKS);
        });
        HIT_TRACKER.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    /**
     * 清除攻击者对特定目标的命中记录
     */
    private static void clearTargetHits(UUID shooterUUID, UUID targetUUID) {
        Map<UUID, HitRecord> targets = HIT_TRACKER.get(shooterUUID);
        if (targets != null) {
            targets.remove(targetUUID);
        }
    }

    /**
     * 清理指定玩家的所有数据（玩家退出/游戏结束时调用）
     */
    public static void clearPlayerData(UUID playerUUID) {
        HIT_TRACKER.remove(playerUUID);
        for (Map<UUID, HitRecord> targets : HIT_TRACKER.values()) {
            targets.remove(playerUUID);
        }
    }
}
