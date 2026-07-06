package org.agmas.noellesroles.content.item;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 沙漠之鹰连续后坐力系统
 * 
 * 连续判定：两发间隔不超过0.9秒
 * 第1-2发：中等幅度上抬视角（-3.0度 pitch）
 * 第3发及以后：大幅度上抬（-6.0度 pitch）+ 水平随机浮动（±8.4度 yaw）
 * 超过0.9秒未射击则重置连续计数
 */
public class DesertEagleRecoilSystem {
    /** 连续射击的最大间隔：0.9秒 = 18 ticks */
    private static final long CONSECUTIVE_WINDOW_MS = 900L;
    /** 中等后坐力（第1-2发）：上抬3.0度 */
    private static final float MEDIUM_PITCH_RECOIL = 3.0f;
    /** 大幅后坐力（第3发及以后）：上抬6.0度 */
    private static final float HEAVY_PITCH_RECOIL = 6.0f;
    /** 大幅后坐力水平浮动范围：±8.4度（基4.2 + 100%） */
    private static final float HEAVY_YAW_FLOAT = 8.4f;

    /** 每个玩家的连续射击状态 */
    private static final Map<UUID, RecoilState> STATES = new HashMap<>();

    private static class RecoilState {
        int consecutiveShots;
        long lastShotTimeMs;

        RecoilState(int shots, long time) {
            this.consecutiveShots = shots;
            this.lastShotTimeMs = time;
        }
    }

    /**
     * 在客户端应用后坐力，由 MouseHandlerMixin 中的射击触发调用。
     * 追踪连续射击次数并施加对应后坐力。
     */
    public static void applyRecoil(Player player) {
        UUID uuid = player.getUUID();
        long now = System.currentTimeMillis();

        RecoilState state = STATES.get(uuid);
        if (state == null || (now - state.lastShotTimeMs) > CONSECUTIVE_WINDOW_MS) {
            // 超过窗口期，重新开始计数
            state = new RecoilState(1, now);
        } else {
            // 在窗口期内，连续计数+1
            state.consecutiveShots++;
            state.lastShotTimeMs = now;
        }
        STATES.put(uuid, state);

        // 应用后坐力
        if (state.consecutiveShots <= 2) {
            // 第1-2发：中等上抬
            player.setXRot(player.getXRot() - MEDIUM_PITCH_RECOIL);
        } else {
            // 第3发及以后：大幅上抬 + 水平浮动
            player.setXRot(player.getXRot() - HEAVY_PITCH_RECOIL);
            float yawOffset = (player.getRandom().nextFloat() - 0.5f) * 2.0f * HEAVY_YAW_FLOAT;
            player.setYRot(player.getYRot() + yawOffset);
        }
    }

    /**
     * 定期清理过期状态，防止内存泄漏。
     * 每 tick 调用，移除超过2秒未更新的条目。
     */
    public static void tickCleanup() {
        long now = System.currentTimeMillis();
        STATES.entrySet().removeIf(entry -> (now - entry.getValue().lastShotTimeMs) > 2000L);
    }

    /**
     * 清理指定玩家数据（玩家退出时调用）
     */
    public static void clearPlayerData(UUID playerUUID) {
        STATES.remove(playerUUID);
    }
}
