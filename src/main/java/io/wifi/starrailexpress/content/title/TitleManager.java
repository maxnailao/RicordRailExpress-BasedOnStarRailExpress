package io.wifi.starrailexpress.content.title;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 称号 → 玩家名字的实际应用。
 *
 * <h2>为什么用 scoreboard team</h2>
 * MC 里能在名字旁显示自定义文字的**唯一**原版机制就是队伍 prefix/suffix，
 * 而且本模组的客户端已经在用它渲染名字
 * （{@code SREClientEvents.getDisplayName} → {@code PlayerTeam.formatNameForTeam}），
 * 所以这里只要把玩家放进对应队伍、设好 prefix/suffix，显示就自动生效，
 * 不需要碰客户端渲染代码。
 *
 * <h2>一人一队</h2>
 * 一个玩家**同时只能在一个队伍**里，所以"同时只能装备一个称号"天然成立。
 *
 * <p>队伍是按**称号**建的（不是按玩家），颜色/文本写在队伍上，
 * 所以同称号的玩家共用一个队伍，改一次全体生效。
 */
public final class TitleManager {

    /** 队伍名前缀，避免和别的机制（如 zhuimu_pige）撞名 */
    private static final String TEAM_PREFIX = "sre_title_";

    /** 每个称号对应的队伍名缓存：titleId -> teamName */
    private static final Map<String, String> TEAM_NAMES = new ConcurrentHashMap<>();

    private TitleManager() {
    }

    /** 称号对应的队伍名 */
    public static String teamNameFor(Title title) {
        return TEAM_NAMES.computeIfAbsent(title.id(), id -> TEAM_PREFIX + id);
    }

    /**
     * 把玩家的称号同步到记分板。
     *
     * <p>幂等：重复调用不会有副作用。玩家没装备称号时会被移出称号队伍。
     *
     * @param server 服务器（拿 Scoreboard）
     * @param player 目标玩家
     * @param data   称号定义表
     */
    public static void apply(MinecraftServer server, ServerPlayer player, TitleSavedData data) {
        if (server == null || player == null || data == null) {
            return;
        }
        Scoreboard scoreboard = server.getScoreboard();
        String playerName = player.getScoreboardName();
        TitlePlayerComponent comp = TitlePlayerComponent.KEY.get(player);
        String equippedId = comp.getEquipped();
        Title title = equippedId == null ? null : data.get(equippedId);

        // 称号不存在（被管理员删了）→ 当作没装备
        if (title == null) {
            if (equippedId != null) {
                comp.clearEquipped();
            }
            removeFromTitleTeams(scoreboard, playerName);
            return;
        }

        String teamName = teamNameFor(title);
        PlayerTeam team = scoreboard.getPlayerTeam(teamName);
        if (team == null) {
            team = scoreboard.addPlayerTeam(teamName);
        }
        // 文本 + 颜色写在队伍上
        // prefix 显示在名字前，suffix 显示在名字后；方向由创建称号时决定。
        if (title.suffix()) {
            team.setPlayerPrefix(Component.empty());
            team.setPlayerSuffix(title.spacedComponent());
        } else {
            team.setPlayerPrefix(title.spacedComponent());
            team.setPlayerSuffix(Component.empty());
        }

        // 已在这个队伍里就不用再动
        if (team.getPlayers().contains(playerName)) {
            return;
        }
        // 先离开其它称号队伍（scoreboard 会自动把人从旧队移出，但显式做更清楚）
        removeFromTitleTeams(scoreboard, playerName);
        scoreboard.addPlayerToTeam(playerName, team);
    }

    /** 把玩家移出所有**称号**队伍（不动其它机制的队伍，如 zhuimu_pige） */
    public static void removeFromTitleTeams(Scoreboard scoreboard, String playerName) {
        for (PlayerTeam team : new java.util.ArrayList<>(scoreboard.getPlayerTeams())) {
            if (team.getName().startsWith(TEAM_PREFIX) && team.getPlayers().contains(playerName)) {
                scoreboard.removePlayerFromTeam(playerName, team);
            }
        }
    }

    /**
     * 删除称号定义时清理它的队伍，避免残留空队伍。
     */
    public static void removeTeam(MinecraftServer server, Title title) {
        if (server == null || title == null) {
            return;
        }
        Scoreboard scoreboard = server.getScoreboard();
        String teamName = teamNameFor(title);
        PlayerTeam team = scoreboard.getPlayerTeam(teamName);
        if (team != null) {
            scoreboard.removePlayerTeam(team);
        }
        TEAM_NAMES.remove(title.id());
    }

    /**
     * 登录 / 换世界后重新应用一次。
     *
     * <p>记分板是**存档级**的，理论上跨重启保留；但队伍可能被别的插件/指令清掉，
     * 所以玩家加入时补一次，保证显示和组件状态一致。
     */
    public static void reapplyOnJoin(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        try {
            apply(server, player, TitleSavedData.get(server));
        } catch (Exception e) {
            SRE.LOGGER.warn("[Title] 应用称号失败: {}", player.getName().getString(), e);
        }
    }

    /** 供命令/界面把称号文本渲染成带颜色的组件 */
    @Nullable
    public static Component describe(TitleSavedData data, @Nullable String titleId) {
        if (data == null || titleId == null) {
            return null;
        }
        Title t = data.get(titleId);
        return t == null ? null : t.component();
    }

    /**
     * 取出某玩家**当前显示用**的称号文本（含与名字之间的空格）。
     *
     * <p>直接读该玩家所在的**记分板队伍**的 prefix/suffix —— 这份数据服务端和客户端都有，
     * 所以这个方法两边都能调用（{@code PlayerPrefixMixin} 在客户端也要用它渲染准星名字）。
     *
     * <p>只认 {@code sre_title_} 开头的队伍，避免把别的机制（如 {@code zhuimu_pige}）的
     * 前后缀当成称号。
     *
     * @return 带颜色的称号组件；没有则返回 {@code null}
     */
    @Nullable
    public static MutableComponent displayTitleFor(@Nullable Player player) {
        if (player == null) {
            return null;
        }
        try {
            PlayerTeam team = player.getTeam();
            if (team == null || !team.getName().startsWith(TEAM_PREFIX)) {
                return null;
            }
            // 我们只往其中一边写称号，另一边是空组件
            Component prefix = team.getPlayerPrefix();
            Component suffix = team.getPlayerSuffix();
            if (prefix != null && !prefix.getString().isEmpty()) {
                return prefix.copy();
            }
            if (suffix != null && !suffix.getString().isEmpty()) {
                return suffix.copy();
            }
        } catch (Throwable ignored) {
            // 记分板还没同步好时不要影响名字显示
        }
        return null;
    }
}
