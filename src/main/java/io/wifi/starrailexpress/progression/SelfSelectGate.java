package io.wifi.starrailexpress.progression;

import io.wifi.starrailexpress.api.SRERole;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.harpymodloader.Harpymodloader;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * 自选职业卡的「这一局到底能不能选它」判定。
 *
 * <p>自选以前只看 {@code ROLE_MAX}，漏掉了两件事：
 * <ol>
 * <li><b>人数门槛</b>：像鹈鹕这种要 16/18 人才刷的职业，人数不够时本来就不该出现，
 * 自选同样不该放行；</li>
 * <li><b>彩蛋职业</b>：芙兰朵露 / 迪奥这类 {@code EggRole} 只有在"本局彩蛋启用"时才进池，
 * 平时 {@code ROLE_MAX} 是 0，于是自选也被一起挡掉了。</li>
 * </ol>
 *
 * <p>这里统一裁决，服务端和客户端界面共用同一套判断，避免两边不一致。
 * 注意：本类只管「能不能选」，不管「名额满没满」（那个是 {@code isRoleOverflow} 的事）。
 */
public final class SelfSelectGate {

    /** 不可选的原因；{@link #NONE} 表示可选 */
    public enum Deny {
        /** 可以选 */
        NONE,
        /** 人数不够：该职业要 {@code min} 人才刷新 */
        PLAYER_COUNT,
        /** 地图不对：该职业只在特定地图刷新（雪原猎手 / 重刑犯…） */
        WRONG_MAP,
        /** 该职业本来就不允许自选（转化 / 派生 / 特殊地图职业等） */
        NOT_SELECTABLE
    }

    private SelfSelectGate() {
    }

    /** 该职业的人数门槛；{@code <=0} 表示无门槛 */
    public static int minPlayers(SRERole role) {
        return role == null ? 0 : role.spawnInfo.minEnabledPlayer;
    }

    /** 该职业的人数上限；{@code <0} 表示无上限 */
    public static int maxPlayers(SRERole role) {
        return role == null ? -1 : role.spawnInfo.maxEnabledPlayer;
    }

    /** 是否为"彩蛋职业"（芙兰朵露 / 迪奥 这类） */
    public static boolean isEggRole(SRERole role) {
        return role instanceof io.wifi.starrailexpress.api.EggRole;
    }

    /**
     * 该职业的地图门禁是否满足。
     *
     * <p>特殊地图职业（雪原猎手 / 重刑犯 / 预备魔女…）只在配置的地图里刷新，
     * 判断复用 {@code InitModRolesMax.isSpecialMapRoleEnabled}，保证和自然刷新同一套口径。
     *
     * @param mapName 本局地图名；{@code null}/空 表示"地图未知"，
     *                此时**不拦**（避免在拿不到地图时误判成不可选）
     */
    public static boolean mapMatches(SRERole role, @Nullable String mapName) {
        if (role == null || !role.isSpecialMapRole()) {
            return true; // 非地图限定职业，任何地图都行
        }
        if (mapName == null || mapName.isBlank() || "unknown".equals(mapName)) {
            return true; // 地图未知，交给开局复查去裁决
        }
        try {
            return org.agmas.noellesroles.init.InitModRolesMax.isSpecialMapRoleEnabled(
                    role, mapName, org.agmas.noellesroles.config.NoellesRolesConfig.instance());
        } catch (Throwable t) {
            return true;
        }
    }

    /**
     * 「必须由别的职业产生、不会自然刷新」的职业，一律不可自选。
     *
     * <p>这些职业的 {@code ROLE_MAX} 平时是 0，靠体系 / 召唤 / 绑定产生。
     * 其中**有些是 {@code EggRole}**，会被"彩蛋职业可直选"的例外放行 ——
     * 教父的家族成员（黑手党 / 清洁工 / 营养师 / 阳伞）就是这么被误放进自选卡的。
     * 所以这里再兜一层，与彩蛋例外无关。
     */
    private static final Set<String> GENERATED_ONLY_PATHS = Set.of(
            // 蜜蜂家族：蜂后召唤出来
            "bee_worker", "bee_wasp",
            // 教父的家族成员：由黑手党体系产生
            "mafioso", "janitor", "nutritionist", "parasol",
            // 绑定副职业：由主职业带出来，自己不该被单独选
            // 医生 ← 毒师 / 疫使；锁匠 ← 工程师；钳工 ← 悍匪
            "doctor", "locksmith",
            // 由别的职业产生（彩蛋通道曾把它们放进来，这里先于彩蛋例外拦下）
            "cat_killer", "manipulator", "puppeteer");

    /**
     * 该职业是否属于「只能由别的职业产生」而不能自选。
     *
     * @param role 目标职业
     */
    public static boolean isGeneratedOnly(@Nullable SRERole role) {
        if (role == null) {
            return false;
        }
        // 1) 显式声明不可自选（转化 / 派生职业通常这么标）
        if (!role.isSelfSelectable()) {
            return true;
        }
        // 2) 我们额外维护的"只能被生成"名单
        return GENERATED_ONLY_PATHS.contains(role.identifier().getPath());
    }

    /**
     * 判断该职业此刻能否被自选 —— <b>选卡阶段</b>用的判定。
     *
     * <h2>为什么不在这里判地图</h2>
     * 实际流程是「**先选职业卡，再投票选地图**」：选卡那一刻根本还不知道下一局是什么地图。
     * 所以这一层**只看人数**，特殊地图职业（雪原猎手、重刑犯…）一律放行；
     * 地图是否匹配留到**开局时**用实际地图复查（{@code SelfSelectValidator}），
     * 不匹配就撤销占位并把自选卡退回去。
     *
     * @param role    目标职业
     * @param players 当前对局的玩家（用于人数门槛）
     */
    public static Deny check(@Nullable SRERole role, List<ServerPlayer> players) {
        if (role == null) {
            return Deny.NOT_SELECTABLE;
        }
        // 「必须由别的职业产生」的职业优先拦下 —— 这一层**先于**彩蛋例外，
        // 否则教父的家族成员会被彩蛋通道放进来。
        if (isGeneratedOnly(role)) {
            return Deny.NOT_SELECTABLE;
        }
        // 彩蛋职业与特殊地图职业允许自选：
        //   - 彩蛋职业此前被"本局彩蛋未启用"（ROLE_MAX=0）挡掉；
        //   - 特殊地图职业此前被 isSpecialMapRole() 直接排除，
        //     且地图还没投票，此刻无法判断，必须放行由开局复查裁决。
        boolean special = isEggRole(role) || role.isSpecialMapRole();
        if (!special && !io.wifi.starrailexpress.api.TMMRoles.isSelfSelectableRole(role)) {
            return Deny.NOT_SELECTABLE;
        }
        int count = players == null ? 0 : players.size();
        int min = minPlayers(role);
        if (min > 0 && count < min) {
            return Deny.PLAYER_COUNT;
        }
        int max = maxPlayers(role);
        if (max >= 0 && count > max) {
            return Deny.PLAYER_COUNT;
        }
        // 注意：这里**故意不判地图**
        return Deny.NONE;
    }

    /**
     * 开局前的复查：人数用最终值、地图用实际值。
     *
     * <p>与 {@link #check} 的区别只在地图未知时不放行 —— 这里地图**一定**是已知的。
     */
    public static Deny checkForRound(@Nullable SRERole role, List<ServerPlayer> players,
            @Nullable String mapName) {
        if (role == null) {
            return Deny.NOT_SELECTABLE;
        }
        if (isGeneratedOnly(role)) {
            return Deny.NOT_SELECTABLE;
        }
        boolean special = isEggRole(role) || role.isSpecialMapRole();
        if (!special && !io.wifi.starrailexpress.api.TMMRoles.isSelfSelectableRole(role)) {
            return Deny.NOT_SELECTABLE;
        }
        int count = players == null ? 0 : players.size();
        int min = minPlayers(role);
        if (min > 0 && count < min) {
            return Deny.PLAYER_COUNT;
        }
        int max = maxPlayers(role);
        if (max >= 0 && count > max) {
            return Deny.PLAYER_COUNT;
        }
        if (role.isSpecialMapRole() && (mapName == null || mapName.isBlank())) {
            return Deny.WRONG_MAP; // 地图拿不到就不让特殊地图职业白占位
        }
        if (!mapMatches(role, mapName)) {
            return Deny.WRONG_MAP;
        }
        return Deny.NONE;
    }

    /** 便捷判断：该职业此刻是否可选 */
    public static boolean isSelectable(@Nullable SRERole role, List<ServerPlayer> players) {
        return check(role, players) == Deny.NONE;
    }

    /** 人数门槛提示文案用的参数（{@code -1} 表示无上限） */
    public static String describeRequirement(SRERole role) {
        int min = minPlayers(role);
        int max = maxPlayers(role);
        if (min > 0 && max >= 0) {
            return min + "-" + max + " 人";
        }
        if (min > 0) {
            return min + " 人及以上";
        }
        if (max >= 0) {
            return max + " 人及以下";
        }
        return "任意人数";
    }

    /**
     * 绑定职业：选了主职业时要一起生成的关联职业。
     *
     * <p>关系由 {@code Harpymodloader.addOccupationRole} 注册
     * （毒师→医生、悍匪→钳工、迪奥→承太郎 等）。
     */
    public static List<SRERole> companionsOf(@Nullable SRERole role) {
        if (role == null) {
            return List.of();
        }
        var list = Harpymodloader.getOccupationRoles(role);
        return list == null ? List.of() : list;
    }
}
