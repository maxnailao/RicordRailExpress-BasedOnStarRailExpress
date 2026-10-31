package org.agmas.noellesroles.game.roles.innocence.jidongzhe;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.api.data.RoleData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.jetbrains.annotations.Nullable;

/**
 * 机动者：靠疾跑与滑铲吃饭的平民。
 *
 * <p>职业规则全部集中在本类与 {@link JidongzheRoleData}，物品 / 网络包 / 界面只做薄转发。
 * 滑铲的「冷却 → 距离」换算放在本类里做成静态方法，服务端结算与客户端本地预测共用同一份公式，
 * 否则玩家自己看到的动作长度会和实际位移对不上。</p>
 */
public class JidongzheRole extends NormalRole {

    /** 超载技能 id（注册与冷却都以此为准） */
    public static final ResourceLocation SKILL_ID = Noellesroles.id("jidongzhe_overload");

    /** 基础上限取自平民基准（10 秒），也是滑铲扣体力的计算基准 */
    public static final int BASE_STAMINA_TICKS = TMMRoles.CIVILIAN.getMaxSprintTime();

    // ---- 被动·滑铲 ----
    /** 内置冷却 1.5 秒 */
    public static final int SLIDE_COOLDOWN_TICKS = 30;
    /** 剩余冷却超过冷却总长的 2/3（即 1~1.5 秒）时，滑铲距离固定为最短档 */
    public static final int SLIDE_SHORT_RANGE_TICKS = SLIDE_COOLDOWN_TICKS * 2 / 3;
    /** 距离单位是「格」（方块边长），不是刻、不是米 */
    public static final double SLIDE_MIN_DISTANCE = 0.7D;
    public static final double SLIDE_MAX_DISTANCE = 4.0D;
    /**
     * 每刻水平速度基准，单位「格/刻」——就是实际前进的格数，不是药水倍率。
     * 疾跑本身约 0.28 格/刻，这里约 1.8 倍；上一版用 teleport 时的平均速度是 1 格/刻，按要求减半。
     * 实际每刻速度按进度从 1.6 倍基准衰减到 0.4 倍，所以滑行是先冲后收的。
     */
    public static final double SLIDE_SPEED_PER_TICK = 0.5D;
    public static final double SLIDE_START_SPEED_RATIO = 1.6D;
    public static final double SLIDE_END_SPEED_RATIO = 0.4D;
    /**
     * 姿态时长估算的刻数上下限。真正的收尾靠量测实际走过的格数，
     * 这里只负责给出「这次滑铲大概持续多久」的估算，上限防止撞墙时姿态一直挂着。
     */
    public static final int SLIDE_MIN_TICKS = 3;
    public static final int SLIDE_MAX_TICKS = 20;
    /**
     * 滑铲以「实际走过的格数」收尾，所以真实刻数可能比按距离估算的多几刻。
     * 服务端用它当上限、姿态包把它算进时长、客户端本地保持同一口径，
     * 三处一致才能保证姿态覆盖整个位移阶段、又在结束包到达时立刻取消。
     */
    public static final int SLIDE_TICK_TOLERANCE = 6;
    /** 体力消耗比例，基准都是「基础上限」（即未超载时的 200 刻） */
    public static final int SLIDE_STAMINA_COST_PERCENT = 5;
    public static final int SLIDE_JUMP_STAMINA_COST_PERCENT = 3;
    /** 滑铲跳在空中的近似刻数，用于把跃距换算成水平速度 */
    public static final int SLIDE_JUMP_AIR_TICKS = 8;
    /** 与原版起跳相当的上抛初速 */
    public static final double SLIDE_JUMP_VERTICAL_MOTION = 0.42D;
    /**
     * 滑铲跳的宽限刻数：服务端认可「滑铲结束后若干刻内」按下的跳。
     * 客户端要跳得准，靠的既是本地预测状态也是这段宽限，否则网络抖动一次机制就整段失效。
     */
    public static final int SLIDE_JUMP_GRACE_TICKS = 6;
    /**
     * 「刚刚在疾跑」的记忆窗口（刻）。原版在按下蹲键的同刻就关掉疾跑，
     * 服务端处理滑铲包时 {@code isSprinting()} 早已是 false，直接判它会让滑铲永远触发不了。
     */
    public static final int SPRINT_MEMORY_TICKS = 3;

    // ---- 主动·超载 ----
    public static final int OVERLOAD_COOLDOWN_SECONDS = 100;
    public static final int OVERLOAD_DURATION_TICKS = 500;
    public static final int OVERLOAD_KILL_EXTENSION_TICKS = 200;
    /** 击杀延长的总量封顶，防止连续击杀把超载无限续下去 */
    public static final int OVERLOAD_MAX_TICKS = 1500;
    public static final int OVERLOAD_STAMINA_MULTIPLIER = 2;
    /** 开启超载时回复「翻倍后体力上限」的 65% */
    public static final int OVERLOAD_RECOVERY_PERCENT = 65;
    /** 速度 I（amplifier 0）：本仓库玩家移速最终按 1+(amplifier+1)×0.25 结算，即 +25% */
    public static final int SPEED_AMPLIFIER = 0;

    public JidongzheRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            SRERole.MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    /**
     * 本次滑铲能滑多远（格）：剩余冷却超过 1 秒固定最短档，0~1 秒随冷却减少线性提升到满档。
     * 服务端与客户端共用，保证预测和实际一致。
     *
     * @param remainingCooldownTicks 距冷却回满还剩下的刻数（0 表示已满）
     */
    public static double slideDistanceForCooldown(long remainingCooldownTicks) {
        double ratio;
        if (remainingCooldownTicks > SLIDE_SHORT_RANGE_TICKS) {
            ratio = 0.0D;
        } else {
            ratio = (SLIDE_SHORT_RANGE_TICKS - remainingCooldownTicks) / (double) SLIDE_SHORT_RANGE_TICKS;
        }
        return SLIDE_MIN_DISTANCE + (SLIDE_MAX_DISTANCE - SLIDE_MIN_DISTANCE) * ratio;
    }

    /**
     * 本次滑铲预计持续多少刻，用于姿态包的时长与客户端本地预测。
     */
    public static int estimateSlideTicks(double distance) {
        int ticks = (int) Math.round(distance / SLIDE_SPEED_PER_TICK);
        return Math.max(SLIDE_MIN_TICKS, Math.min(SLIDE_MAX_TICKS, ticks));
    }

    /**
     * 体力上限：超载期间翻倍。同时被服务端结算与客户端 HUD 读取，因此两端都会走到这里。
     */
    public static int staminaCap(Player player) {
        return BASE_STAMINA_TICKS * (isOverloaded(player) ? OVERLOAD_STAMINA_MULTIPLIER : 1);
    }

    public static boolean isOverloaded(@Nullable Player player) {
        if (player == null) {
            return false;
        }
        JidongzheRoleData data = RoleData.getNullable(JidongzheRoleData.class, player);
        return data != null && data.isOverloaded();
    }

    /**
     * 超载期间击杀玩家 -> 延长超载并体力回满。
     */
    @Override
    public void onKill(Player victim, boolean spawnBody, @Nullable Player killer, ResourceLocation deathReason) {
        if (!(killer instanceof ServerPlayer serverKiller)) {
            return;
        }
        JidongzheRoleData data = RoleData.getNullable(JidongzheRoleData.class, serverKiller);
        if (data != null) {
            data.extendOverloadOnKill(serverKiller);
        }
    }

    // ---- 供网络包接收器调用的入口（注册清单字段类型是 SRERole，故一律走静态方法） ----

    public static boolean trySlide(ServerPlayer player) {
        JidongzheRoleData data = RoleData.getNullable(JidongzheRoleData.class, player);
        return data != null && data.beginSlide(player);
    }

    public static boolean trySlideJump(ServerPlayer player) {
        JidongzheRoleData data = RoleData.getNullable(JidongzheRoleData.class, player);
        return data != null && data.beginSlideJump(player);
    }

    public static boolean startOverload(ServerPlayer player) {
        JidongzheRoleData data = RoleData.getNullable(JidongzheRoleData.class, player);
        return data != null && data.startOverload(player);
    }
}
