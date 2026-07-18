package org.agmas.noellesroles.scene;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.data.MapStatusBarType;
import io.wifi.starrailexpress.index.TMMParticles;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.packet.BlizzardStateS2CPacket;
import org.agmas.noellesroles.packet.SnowguaiBlizzardInfoS2CPacket;
import org.agmas.noellesroles.role.ModRoles;

import java.util.Random;

/**
 * 暴风雪机制管理器（服务端）。
 *
 * <p>三种暴风雪类型：
 * <ul>
 *   <li><b>普通暴风雪</b>：地图配置 {@code bigsnowsnow = true} 时自动循环，
 *       每 90~120 秒触发，持续 20 秒</li>
 *   <li><b>强制暴风雪</b>：商店购买触发，持续指定时长</li>
 *   <li><b>最终暴风雪</b>：雪怪残局被动，持续到游戏结束，全图效果</li>
 * </ul>
 *
 * <p>关键设计：
 * <ul>
 *   <li>{@link #tickCounter} 在游戏运行期间始终递增，不受配置开关影响</li>
 *   <li>所有体温效果使用 {@code forceAddWarmth} 绕过 {@code shouldTrack} 和
 *       {@code currentStatusBar} 检查，确保在任何地图上都能生效</li>
 *   <li>强制/最终暴风雪自动覆盖 {@code mapStatusBar} 为 WARMTH，结束后恢复</li>
 * </ul>
 */
public final class BlizzardManager {

    // ───────────────── 常量 ─────────────────
    /** 暴风雪持续时长（tick），20 秒 */
    private static final int BLIZZARD_DURATION_TICKS = 20 * 20;
    /** 暴风雪预警提前时间（tick），10 秒 */
    private static final int WARNING_LEAD_TICKS = 10 * 20;
    /** CD 最小值（tick），90 秒 */
    private static final int COOLDOWN_MIN_TICKS = 90 * 20;
    /** CD 最大值（tick），120 秒 */
    private static final int COOLDOWN_MAX_TICKS = 120 * 20;
    /** 普通/强制暴风雪体温扣减间隔（tick），每 2 秒 */
    private static final int WARMTH_INTERVAL_TICKS = 2 * 20;
    /** 最终暴风雪全局体温扣减间隔（tick），每 5 秒 */
    private static final int FINAL_BLIZZARD_WARMTH_INTERVAL = 5 * 20;
    /** 粒子生成间隔（tick） */
    private static final int PARTICLE_INTERVAL_TICKS = 3;
    /** 每轮粒子数量 */
    private static final int PARTICLES_PER_ROUND = 80;

    private static final Random RANDOM = new Random();

    // ───────────────── 状态字段 ─────────────────

    /**
     * 全局 tick 计数器。游戏运行期间始终递增，不受配置开关影响。
     * 这是所有暴风雪计时的基准。
     */
    private static int tickCounter = 0;

    /** 当前是否处于任何类型的暴风雪中 */
    private static boolean blizzardActive = false;

    /** 暴风雪开始时的 tickCounter（用于计算持续时间和结束） */
    private static int blizzardStartTick = 0;

    /** 普通暴风雪：距离下一次暴风雪的 tick */
    private static int nextBlizzardIn = COOLDOWN_MIN_TICKS
            + RANDOM.nextInt(COOLDOWN_MAX_TICKS - COOLDOWN_MIN_TICKS + 1);

    /** 强制暴风雪：剩余 tick（商店触发） */
    private static int forcedRemainingTicks = 0;

    /** 最终暴风雪：是否已激活（雪怪残局被动，持续到游戏结束） */
    private static boolean finalBlizzardActive = false;

    /** 强制/最终暴风雪覆盖前的原始 mapStatusBar（用于结束时恢复） */
    private static MapStatusBarType originalStatusBar = null;
    /** 是否由强制/最终暴风雪覆盖了 mapStatusBar */
    private static boolean statusBarOverridden = false;

    private BlizzardManager() {
    }

    // ═══════════════════════════════════════════════════════════════
    //  主 tick 方法
    // ═══════════════════════════════════════════════════════════════

    /**
     * 每服务端 tick 调用一次（由 {@link SceneRuntimeEvents} 注册在 END_WORLD_TICK）。
     */
    public static void tick(ServerLevel level) {
        // 1. 游戏未运行时重置
        if (!isGameRunning(level)) {
            return;
        }

        // 仅处理有玩家的维度
        if (level.players().isEmpty()) return;

        // 2. tickCounter 始终递增
        tickCounter++;

        // 3. 按优先级处理暴风雪类型
        if (finalBlizzardActive) {
            tickFinalBlizzard(level);
        } else if (forcedRemainingTicks > 0) {
            tickForcedBlizzard(level);
        } else if (blizzardActive) {
            tickNormalBlizzardActive(level);
        } else {
            tickNormalCountdown(level);
        }

        // 4. 每秒向雪怪玩家同步暴风雪倒计时信息
        if (tickCounter % 20 == 0) {
            syncSnowguaiInfo(level);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  强制暴风雪（商店触发）
    // ═══════════════════════════════════════════════════════════════

    private static void tickForcedBlizzard(ServerLevel level) {
        forcedRemainingTicks--;

        // 体温扣减（每 2 秒，露天玩家）
        if (forcedRemainingTicks % WARMTH_INTERVAL_TICKS == 0) {
            for (ServerPlayer player : level.players()) {
                if (!GameUtils.isPlayerAliveAndSurvival(player)) continue;
                if (!level.canSeeSky(player.blockPosition())) continue;
                MapStatusBarRuntime.forceAddWarmth(player, -1);
            }
        }

        // 粒子
        if (forcedRemainingTicks % PARTICLE_INTERVAL_TICKS == 0) {
            for (ServerPlayer player : level.players()) {
                if (!GameUtils.isPlayerAliveAndSurvival(player)) continue;
                if (!level.canSeeSky(player.blockPosition())) continue;
                spawnBlizzardParticlesAroundPlayer(level, player);
            }
        }

        // 每秒同步
        if (forcedRemainingTicks % 20 == 0) {
            syncBlizzardState(level, BlizzardStateS2CPacket.active(Math.max(0, forcedRemainingTicks)));
        }

        // 结束
        if (forcedRemainingTicks <= 0) {
            endBlizzard(level);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  最终暴风雪（雪怪残局，持续到游戏结束）
    // ═══════════════════════════════════════════════════════════════

    private static void tickFinalBlizzard(ServerLevel level) {
        // 每 5 秒对全图所有存活玩家额外扣减 1 体温（无视皮甲，火炉无效，室内外均扣）
        if (tickCounter % FINAL_BLIZZARD_WARMTH_INTERVAL == 0) {
            for (ServerPlayer player : level.players()) {
                if (!GameUtils.isPlayerAliveAndSurvival(player)) continue;
                MapStatusBarRuntime.forceAddWarmth(player, -1);
            }
        }

        // 每 3 tick 为所有存活玩家生成粒子
        if (tickCounter % PARTICLE_INTERVAL_TICKS == 0) {
            for (ServerPlayer player : level.players()) {
                if (!GameUtils.isPlayerAliveAndSurvival(player)) continue;
                spawnBlizzardParticlesAroundPlayer(level, player);
            }
        }

        // 每 5 秒同步状态到客户端
        if (tickCounter % (5 * 20) == 0) {
            syncBlizzardState(level, BlizzardStateS2CPacket.active(Integer.MAX_VALUE));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  普通暴风雪 - 倒计时阶段
    // ═══════════════════════════════════════════════════════════════

    private static void tickNormalCountdown(ServerLevel level) {
        // 普通暴风雪需要 bigsnowsnow 配置开启
        if (!isBlizzardEnabled(level)) {
            return;
        }

        nextBlizzardIn--;

        // 进入预警窗口（最后 10 秒）
        if (nextBlizzardIn > 0 && nextBlizzardIn <= WARNING_LEAD_TICKS) {
            // 刚进入预警阶段时同步状态
            if (nextBlizzardIn == WARNING_LEAD_TICKS) {
                syncBlizzardState(level, BlizzardStateS2CPacket.warning(nextBlizzardIn));
            }
            int seconds = (nextBlizzardIn + 19) / 20; // 向上取整
            // 只在整秒时播报
            if (nextBlizzardIn % 20 == 0) {
                broadcastWarning(level, seconds);
            }
        }

        // 到达触发时间
        if (nextBlizzardIn <= 0) {
            startNormalBlizzard(level);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  普通暴风雪 - 活跃阶段
    // ═══════════════════════════════════════════════════════════════

    private static void tickNormalBlizzardActive(ServerLevel level) {
        // 如果配置中途关闭，仍然自然结束当前暴风雪
        int remaining = BLIZZARD_DURATION_TICKS - (tickCounter - blizzardStartTick);

        // 每 2 秒对露天存活玩家扣减体温
        if (tickCounter % WARMTH_INTERVAL_TICKS == 0) {
            for (ServerPlayer player : level.players()) {
                if (!GameUtils.isPlayerAliveAndSurvival(player)) continue;
                if (!level.canSeeSky(player.blockPosition())) continue;
                MapStatusBarRuntime.forceAddWarmth(player, -1);
            }
        }

        // 每 3 tick 为露天玩家生成粒子
        if (tickCounter % PARTICLE_INTERVAL_TICKS == 0) {
            for (ServerPlayer player : level.players()) {
                if (!GameUtils.isPlayerAliveAndSurvival(player)) continue;
                if (!level.canSeeSky(player.blockPosition())) continue;
                spawnBlizzardParticlesAroundPlayer(level, player);
            }
        }

        // 每秒同步
        if (tickCounter % 20 == 0 && remaining > 0) {
            syncBlizzardState(level, BlizzardStateS2CPacket.active(remaining));
        }

        // 持续时间结束
        if (tickCounter - blizzardStartTick >= BLIZZARD_DURATION_TICKS) {
            endBlizzard(level);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  暴风雪开始 / 结束
    // ═══════════════════════════════════════════════════════════════

    private static void startNormalBlizzard(ServerLevel level) {
        SRE.LOGGER.info("[Blizzard] 普通暴风雪开始! tickCounter={}", tickCounter);
        blizzardActive = true;
        blizzardStartTick = tickCounter;

        syncBlizzardState(level, BlizzardStateS2CPacket.active(BLIZZARD_DURATION_TICKS));

        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(
                    Component.translatable("message.noellesroles.blizzard.started")
                            .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        }
    }

    private static void endBlizzard(ServerLevel level) {
        SRE.LOGGER.info("[Blizzard] 暴风雪结束. tickCounter={}, elapsed={}",
                tickCounter, tickCounter - blizzardStartTick);

        blizzardActive = false;
        forcedRemainingTicks = 0;

        // 同步结束状态
        syncBlizzardState(level, BlizzardStateS2CPacket.idle());

        // 恢复被覆盖的 mapStatusBar
        restoreWarmthStatusBar(level);

        // 播报结束
        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(
                    Component.translatable("message.noellesroles.blizzard.ended")
                            .withStyle(ChatFormatting.GRAY));
        }

        // 计算下一轮冷却（从结束时开始）
        nextBlizzardIn = COOLDOWN_MIN_TICKS
                + RANDOM.nextInt(COOLDOWN_MAX_TICKS - COOLDOWN_MIN_TICKS + 1);
    }

    // ═══════════════════════════════════════════════════════════════
    //  游戏结束重置
    // ═══════════════════════════════════════════════════════════════

    /**
     * 游戏结束时调用，清理所有状态。
     */
    public static void reset() {
        tickCounter = 0;
        blizzardActive = false;
        blizzardStartTick = 0;
        forcedRemainingTicks = 0;
        finalBlizzardActive = false;
        originalStatusBar = null;
        statusBarOverridden = false;
        nextBlizzardIn = COOLDOWN_MIN_TICKS
                + RANDOM.nextInt(COOLDOWN_MAX_TICKS - COOLDOWN_MIN_TICKS + 1);
    }

    // ═══════════════════════════════════════════════════════════════
    //  暴风雪触发接口（由雪怪组件调用）
    // ═══════════════════════════════════════════════════════════════

    /**
     * 强制触发暴风雪（商店购买用）。
     * 立即开始暴风雪，持续指定 tick 时长。
     *
     * @param level         服务端世界
     * @param durationTicks 持续时长（tick）
     * @return 是否成功触发（已有暴风雪活跃时返回 false）
     */
    public static boolean triggerForcedBlizzard(ServerLevel level, int durationTicks) {
        if (blizzardActive || forcedRemainingTicks > 0 || finalBlizzardActive) {
            SRE.LOGGER.warn("[Blizzard] 强制暴风雪触发失败: active={}, forced={}, final={}",
                    blizzardActive, forcedRemainingTicks, finalBlizzardActive);
            return false;
        }

        SRE.LOGGER.info("[Blizzard] 强制暴风雪触发! 持续 {} tick ({} 秒), tickCounter={}",
                durationTicks, durationTicks / 20, tickCounter);
        SRE.LOGGER.info("[Blizzard] 当前玩家数: {}", level.players().size());

        blizzardActive = true;
        blizzardStartTick = tickCounter;
        forcedRemainingTicks = durationTicks;

        // 确保体温栏处于 WARMTH 模式
        ensureWarmthStatusBar(level);

        // 同步状态到客户端
        syncBlizzardState(level, BlizzardStateS2CPacket.active(durationTicks));

        // 播报
        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(
                    Component.translatable("message.noellesroles.blizzard.started")
                            .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        }
        return true;
    }

    /**
     * 激活最终暴风雪（雪怪残局被动：仅剩单一阵营时触发）。
     * 持续到游戏结束，全图所有玩家每 5 秒额外掉 1 体温（皮甲无法防护，火炉无效）。
     */
    public static void activateFinalBlizzard(ServerLevel level) {
        if (finalBlizzardActive) return;

        SRE.LOGGER.info("[Blizzard] 最终暴风雪激活! tickCounter={}", tickCounter);

        finalBlizzardActive = true;
        blizzardActive = true;
        blizzardStartTick = tickCounter;
        forcedRemainingTicks = 0;

        // 确保体温栏处于 WARMTH 模式
        ensureWarmthStatusBar(level);

        // 同步状态
        syncBlizzardState(level, BlizzardStateS2CPacket.active(Integer.MAX_VALUE));

        // 播报
        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(
                    Component.translatable("message.noellesroles.snowguai_wow.final_blizzard")
                            .withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.BOLD));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  查询接口
    // ═══════════════════════════════════════════════════════════════

    /** 当前是否有暴风雪活跃 */
    public static boolean isBlizzardActive() {
        return blizzardActive;
    }

    /** 最终暴风雪是否已激活 */
    public static boolean isFinalBlizzardActive() {
        return finalBlizzardActive;
    }

    /** 获取距离下一次普通暴风雪的剩余 tick */
    public static int getNextBlizzardIn() {
        return Math.max(0, nextBlizzardIn);
    }

    /** 获取当前暴风雪剩余 tick（普通/强制） */
    public static int getActiveRemainingTicks() {
        if (finalBlizzardActive) {
            return Integer.MAX_VALUE;
        } else if (forcedRemainingTicks > 0) {
            return forcedRemainingTicks;
        } else if (blizzardActive) {
            return Math.max(0, BLIZZARD_DURATION_TICKS - (tickCounter - blizzardStartTick));
        }
        return 0;
    }

    // ═══════════════════════════════════════════════════════════════
    //  辅助方法
    // ═══════════════════════════════════════════════════════════════

    private static void broadcastWarning(ServerLevel level, int seconds) {
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

    /**
     * 每秒向雪怪玩家同步暴风雪信息（用于 HUD 倒计时显示）。
     */
    private static void syncSnowguaiInfo(ServerLevel level) {
        byte activeType;
        int activeRemaining;
        if (finalBlizzardActive) {
            activeType = SnowguaiBlizzardInfoS2CPacket.TYPE_FINAL;
            activeRemaining = Integer.MAX_VALUE;
        } else if (forcedRemainingTicks > 0) {
            // 强制暴风雪（商店购买）—— 优先检查 forcedRemainingTicks
            activeType = SnowguaiBlizzardInfoS2CPacket.TYPE_NORMAL;
            activeRemaining = forcedRemainingTicks;
        } else if (blizzardActive) {
            activeType = SnowguaiBlizzardInfoS2CPacket.TYPE_NORMAL;
            activeRemaining = getActiveRemainingTicks();
        } else {
            activeType = SnowguaiBlizzardInfoS2CPacket.TYPE_NONE;
            activeRemaining = 0;
        }
        int nextIn = getNextBlizzardIn();
        SnowguaiBlizzardInfoS2CPacket packet = new SnowguaiBlizzardInfoS2CPacket(
                nextIn, activeType, activeRemaining);
        var gameWorld = SREGameWorldComponent.KEY.get(level);
        int sentCount = 0;
        for (ServerPlayer player : level.players()) {
            if (gameWorld.isRole(player, ModRoles.SNOWGUAI_WOW)) {
                ServerPlayNetworking.send(player, packet);
                sentCount++;
            }
        }
        SRE.LOGGER.info("[Blizzard-SnowguaiSync] type={}, remaining={}tick, nextIn={}tick, sentTo={}players",
                activeType, activeRemaining, nextIn, sentCount);
    }

    private static boolean isGameRunning(ServerLevel level) {
        return SREGameWorldComponent.KEY.get(level).isRunning();
    }

    private static boolean isBlizzardEnabled(ServerLevel level) {
        return AreasWorldComponent.KEY.get(level).areasSettings.bigsnowsnow;
    }

    /**
     * 确保地图的 mapStatusBar 处于 WARMTH 模式。
     * 如果当前不是 WARMTH，保存原始值并覆盖，以便结束时恢复。
     */
    private static void ensureWarmthStatusBar(ServerLevel level) {
        var areas = AreasWorldComponent.KEY.get(level);
        if (areas.areasSettings.mapStatusBar != MapStatusBarType.WARMTH) {
            originalStatusBar = areas.areasSettings.mapStatusBar;
            statusBarOverridden = true;
            areas.areasSettings.mapStatusBar = MapStatusBarType.WARMTH;
            SRE.LOGGER.info("[Blizzard] mapStatusBar 已覆盖为 WARMTH (原: {})", originalStatusBar);
            areas.sync();
        }
    }

    /**
     * 恢复被强制暴风雪覆盖的 mapStatusBar 到原始值。
     */
    private static void restoreWarmthStatusBar(ServerLevel level) {
        if (statusBarOverridden && originalStatusBar != null) {
            var areas = AreasWorldComponent.KEY.get(level);
            areas.areasSettings.mapStatusBar = originalStatusBar;
            SRE.LOGGER.info("[Blizzard] mapStatusBar 已恢复为 {}", originalStatusBar);
            areas.sync();
            originalStatusBar = null;
            statusBarOverridden = false;
        }
    }
}
