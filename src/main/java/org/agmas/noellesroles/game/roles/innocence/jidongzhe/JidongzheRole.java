package org.agmas.noellesroles.game.roles.innocence.jidongzhe;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.api.TMMRoles;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.jetbrains.annotations.Nullable;

/**
 * 机动者：靠疾跑与滑铲吃饭的平民。
 *
 * <p>职业规则全部集中在本类与 {@link JidongzheRoleData}，物品 / 网络包 / 界面只做薄转发。</p>
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
    public static final double SLIDE_MIN_DISTANCE = 0.7D;
    public static final double SLIDE_MAX_DISTANCE = 3.5D;
    /** 规划滑铲刻数时用的标称平均速度（格/刻），约 2 倍疾跑速度 */
    public static final double SLIDE_NOMINAL_SPEED_PER_TICK = 0.62D;
    /**
     * 滑铲速度曲线：起手 1.6 倍、收尾 0.4 倍标称速度，累计位移仍严格等于本次滑铲距离。
     * 起手更快、落点更缓，避免匀速位移「突然启动 + 硬刹车」的顿挫感。
     */
    public static final double SLIDE_START_SPEED_RATIO = 1.6D;
    public static final double SLIDE_END_SPEED_RATIO = 0.4D;
    public static final int SLIDE_STAMINA_COST_PERCENT = 8;
    public static final int SLIDE_JUMP_STAMINA_COST_PERCENT = 4;
    /** 滑铲跳在空中的近似刻数，用于把跃距换算成水平速度 */
    public static final int SLIDE_JUMP_AIR_TICKS = 10;
    /** 与原版起跳相当的上抛初速 */
    public static final double SLIDE_JUMP_VERTICAL_MOTION = 0.42D;

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
