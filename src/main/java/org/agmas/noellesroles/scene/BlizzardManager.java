package org.agmas.noellesroles.scene;

import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMParticles;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.packet.BlizzardStateS2CPacket;

import java.util.Random;

/**
 * 暴风雪机制管理器（服务端）。
 *
 * <p>当地图配置 {@code bigsnowsnow = true} 时启用：
 * <ul>
 *   <li>每隔 90~120 秒触发一次暴风雪，持续 20 秒</li>
 *   <li>暴风雪期间，露天玩家每 2 秒额外 -1 体温值（独立计算，不受皮甲保护）</li>
 *   <li>暴风雪来临前 10 秒开始倒计时播报</li>
 *   <li>暴风雪期间室外玩家周围刷新额外雪粒子</li>
 *   <li>CD 在暴风雪结束后才开始计算</li>
 * </ul>
 */
public final class BlizzardManager {

    /** 暴风雪持续时长（tick） */
    private static final int BLIZZARD_DURATION_TICKS = 20 * 20; // 20 秒
    /** 暴风雪预警提前时间（tick） */
    private static final int WARNING_LEAD_TICKS = 10 * 20; // 10 秒
    /** CD 最小值（tick），90 秒 */
    private static final int COOLDOWN_MIN_TICKS = 90 * 20;
    /** CD 最大值（tick），120 秒 */
    private static final int COOLDOWN_MAX_TICKS = 120 * 20;
    /** 体温扣减间隔（tick），每 2 秒 */
    private static final int WARMTH_INTERVAL_TICKS = 2 * 20;
    /** 最终暴风雪全局体温扣减间隔（tick），每 100 tick */
    private static final int FINAL_BLIZZARD_WARMTH_INTERVAL = 100;
    /** 粒子生成间隔（tick），每 3 tick */
    private static final int PARTICLE_INTERVAL_TICKS = 3;
    /** 每轮粒子数量 */
    private static final int PARTICLES_PER_ROUND = 80;

    private static final Random RANDOM = new Random();

    /** 当前 tick 计数 */
    private static int blizzardTick = 0;
    /** 暴风雪是否正在活跃 */
    private static boolean blizzardActive = false;
    /** 暴风雪开始时的 tick（用于计算持续时间） */
    private static int blizzardStartTick = 0;
    /** 下一次暴风雪触发的 tick */
    private static int nextBlizzardTick = COOLDOWN_MIN_TICKS
            + RANDOM.nextInt(COOLDOWN_MAX_TICKS - COOLDOWN_MIN_TICKS + 1);

    /** 强制暴风雪剩余 tick（商店触发） */
    private static int forcedBlizzardRemainingTicks = 0;

    /** 最终暴风雪是否已激活（雪怪残局被动，持续到游戏结束） */
    private static boolean finalBlizzardActive = false;

    private BlizzardManager() {
    }

    /**
     * 每服务端 tick 调用一次。
     */
    public static void tick(ServerLevel level) {
        if (!isGameRunning(level)) {
            reset();
            return;
        }

        // 最终暴风雪优先级最高（持续到游戏结束）
        if (finalBlizzardActive) {
            tickFinalBlizzard(level);
            return;
        }

        // 强制暴风雪（商店触发）
        if (forcedBlizzardRemainingTicks > 0) {
            forcedBlizzardRemainingTicks--;
            tickActiveBlizzard(level);
            if (forcedBlizzardRemainingTicks <= 0) {
                endBlizzard(level);
            }
            return;
        }

        // 普通暴风雪循环（需要 bigsnowsnow 配置开启）
        if (!isBlizzardEnabled(level)) {
            if (blizzardActive) {
                // 如果配置中途关闭但暴风雪正在活跃，自然结束
                tickActiveBlizzard(level);
                int elapsed = blizzardTick - blizzardStartTick;
                if (elapsed >= BLIZZARD_DURATION_TICKS) {
                    endBlizzard(level);
                }
            }
            return;
        }

        blizzardTick++;

        if (!blizzardActive) {
            tickCountdown(level);
        } else {
            tickActiveBlizzard(level);
        }
    }

    /**
     * 游戏结束时调用，清理所有状态。
     */
    public static void reset() {
        blizzardTick = 0;
        blizzardActive = false;
        blizzardStartTick = 0;
        forcedBlizzardRemainingTicks = 0;
        finalBlizzardActive = false;
        nextBlizzardTick = COOLDOWN_MIN_TICKS
                + RANDOM.nextInt(COOLDOWN_MAX_TICKS - COOLDOWN_MIN_TICKS + 1);
    }

    /**
     * 查询当前暴风雪是否处于活跃状态（供客户端粒子判断）。
     */
    public static boolean isBlizzardActive() {
        return blizzardActive;
    }

    /**
     * 查询最终暴风雪是否已激活（雪怪残局被动）。
     */
    public static boolean isFinalBlizzardActive() {
        return finalBlizzardActive;
    }

    /**
     * 强制触发暴风雪（商店购买用）。
     * 立即开始暴风雪，持续指定 tick 时长，结束后重置冷却计时器。
     *
     * @param level         服务端世界
     * @param durationTicks 持续时长（tick）
     * @return 是否成功触发（暴风雪已活跃时返回 false）
     */
    public static boolean triggerForcedBlizzard(ServerLevel level, int durationTicks) {
        if (blizzardActive || forcedBlizzardRemainingTicks > 0 || finalBlizzardActive) {
            return false;
        }
        blizzardActive = true;
        blizzardStartTick = blizzardTick;
        forcedBlizzardRemainingTicks = durationTicks;

        // 同步暴风雪活跃状态到客户端
        syncBlizzardState(level, BlizzardStateS2CPacket.active(durationTicks));

        // 播报暴风雪来临
        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(
                    Component.translatable("message.noellesroles.blizzard.started")
                            .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        }
        return true;
    }

    /**
     * 激活最终暴风雪（雪怪残局被动：仅剩单一阵营时触发）。
     * 最终暴风雪持续到游戏结束，全图所有玩家每 100 tick 额外掉 1 体温（皮甲无法防护），
     * 火炉回暖失效，室内正常回暖。
     */
    public static void activateFinalBlizzard(ServerLevel level) {
        if (finalBlizzardActive) return;
        finalBlizzardActive = true;
        blizzardActive = true;
        blizzardStartTick = blizzardTick;
        forcedBlizzardRemainingTicks = 0;

        // 同步暴风雪活跃状态
        syncBlizzardState(level, BlizzardStateS2CPacket.active(Integer.MAX_VALUE));

        // 播报最终暴风雪来临
        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(
                    Component.translatable("message.noellesroles.snowguai_wow.final_blizzard")
                            .withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.BOLD));
        }
    }

    // ───────────────── 倒计时阶段 ─────────────────

    private static void tickCountdown(ServerLevel level) {
        int remainingTicks = nextBlizzardTick - blizzardTick;

        // 进入预警窗口（最后 10 秒）
        if (remainingTicks > 0 && remainingTicks <= WARNING_LEAD_TICKS) {
            // 刚进入预警阶段时同步状态
            if (remainingTicks == WARNING_LEAD_TICKS) {
                syncBlizzardState(level, BlizzardStateS2CPacket.warning(remainingTicks));
            }
            int seconds = (remainingTicks + 19) / 20; // 向上取整
            // 只在整秒时播报
            if (remainingTicks % 20 == 0) {
                broadcastBlizzardWarning(level, seconds);
            }
        }

        // 到达触发时间
        if (blizzardTick >= nextBlizzardTick) {
            startBlizzard(level);
        }
    }

    // ───────────────── 暴风雪活跃阶段 ─────────────────

    private static void tickActiveBlizzard(ServerLevel level) {
        int elapsed = blizzardTick - blizzardStartTick;
        int remainingTicks = forcedBlizzardRemainingTicks > 0
                ? forcedBlizzardRemainingTicks
                : BLIZZARD_DURATION_TICKS - elapsed;

        // 每 2 秒对露天存活玩家扣减体温
        if (elapsed % WARMTH_INTERVAL_TICKS == 0) {
            for (ServerPlayer player : level.players()) {
                if (!GameUtils.isPlayerAliveAndSurvival(player)) continue;
                if (!level.canSeeSky(player.blockPosition())) continue;
                MapStatusBarRuntime.addWarmth(player, -1);
            }
        }

        // 每 3 tick 为露天玩家生成额外雪粒子
        if (elapsed % PARTICLE_INTERVAL_TICKS == 0) {
            for (ServerPlayer player : level.players()) {
                if (!GameUtils.isPlayerAliveAndSurvival(player)) continue;
                if (!level.canSeeSky(player.blockPosition())) continue;
                spawnBlizzardParticlesAroundPlayer(level, player);
            }
        }

        // 每秒同步一次剩余时间到客户端
        if (elapsed % 20 == 0 && remainingTicks > 0) {
            syncBlizzardState(level, BlizzardStateS2CPacket.active(remainingTicks));
        }

        // 普通暴风雪结束（强制暴风雪由外层 forcedBlizzardRemainingTicks 控制）
        if (forcedBlizzardRemainingTicks <= 0 && elapsed >= BLIZZARD_DURATION_TICKS) {
            endBlizzard(level);
        }
    }

    // ───────────────── 最终暴风雪阶段 ─────────────────

    private static void tickFinalBlizzard(ServerLevel level) {
        blizzardTick++;
        int elapsed = blizzardTick - blizzardStartTick;

        // 每 100 tick 对全图所有存活玩家额外扣减 1 体温（无视皮甲，火炉无效）
        if (elapsed % FINAL_BLIZZARD_WARMTH_INTERVAL == 0) {
            for (ServerPlayer player : level.players()) {
                if (!GameUtils.isPlayerAliveAndSurvival(player)) continue;
                MapStatusBarRuntime.forceAddWarmth(player, -1);
            }
        }

        // 每 3 tick 为所有存活玩家生成额外雪粒子（室内+室外）
        if (elapsed % PARTICLE_INTERVAL_TICKS == 0) {
            for (ServerPlayer player : level.players()) {
                if (!GameUtils.isPlayerAliveAndSurvival(player)) continue;
                spawnBlizzardParticlesAroundPlayer(level, player);
            }
        }

        // 每 5 秒同步一次状态到客户端（最终暴风雪持续到游戏结束）
        if (elapsed % (5 * 20) == 0) {
            syncBlizzardState(level, BlizzardStateS2CPacket.active(Integer.MAX_VALUE));
        }
        // 最终暴风雪不会自动结束，持续到游戏结束
    }

    private static void startBlizzard(ServerLevel level) {
        blizzardActive = true;
        blizzardStartTick = blizzardTick;

        // 同步暴风雪活跃状态到所有客户端
        syncBlizzardState(level, BlizzardStateS2CPacket.active(BLIZZARD_DURATION_TICKS));

        // 播报暴风雪来临
        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(
                    Component.translatable("message.noellesroles.blizzard.started")
                            .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        }
    }

    private static void endBlizzard(ServerLevel level) {
        blizzardActive = false;

        // 同步暴风雪结束状态到所有客户端
        syncBlizzardState(level, BlizzardStateS2CPacket.idle());

        // 播报暴风雪结束
        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(
                    Component.translatable("message.noellesroles.blizzard.ended")
                            .withStyle(ChatFormatting.GRAY));
        }

        // CD 从暴风雪结束后开始计算
        int cooldownTicks = COOLDOWN_MIN_TICKS
                + RANDOM.nextInt(COOLDOWN_MAX_TICKS - COOLDOWN_MIN_TICKS + 1);
        nextBlizzardTick = blizzardTick + cooldownTicks;
    }

    // ───────────────── 辅助方法 ─────────────────

    private static void broadcastBlizzardWarning(ServerLevel level, int seconds) {
        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(
                    Component.translatable("message.noellesroles.blizzard.warning", seconds)
                            .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        }
    }

    private static void spawnBlizzardParticlesAroundPlayer(ServerLevel level, ServerPlayer player) {
        double px = player.getX();
        double py = player.getY();
        double pz = player.getZ();

        for (int i = 0; i < PARTICLES_PER_ROUND; i++) {
            double offsetX = (level.random.nextDouble() - 0.5) * 30;
            double offsetY = (level.random.nextDouble() - 0.5) * 16;
            double offsetZ = (level.random.nextDouble() - 0.5) * 30;

            // 暴风雪粒子速度更大，模拟强风效果
            double vx = 3.0 + level.random.nextDouble() * 2.0;
            double vy = -0.5 + level.random.nextDouble() * 0.5;
            double vz = level.random.nextDouble() * 1.5 - 0.75;

            level.sendParticles(
                    (SimpleParticleType) TMMParticles.SNOWFLAKE,
                    px + offsetX, py + offsetY, pz + offsetZ,
                    1, vx, vy, vz, 0.0);
        }
    }

    private static void syncBlizzardState(ServerLevel level, BlizzardStateS2CPacket packet) {
        for (ServerPlayer player : level.players()) {
            ServerPlayNetworking.send(player, packet);
        }
    }

    private static boolean isGameRunning(ServerLevel level) {
        return SREGameWorldComponent.KEY.get(level).isRunning();
    }

    private static boolean isBlizzardEnabled(ServerLevel level) {
        return AreasWorldComponent.KEY.get(level).areasSettings.bigsnowsnow;
    }
}
