package io.wifi.starrailexpress.progression;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.backpack.BackpackManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.harpymodloader.Harpymodloader;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * 自选职业卡的「占位」规则，唯一权威实现。
 *
 * <h2>规则</h2>
 * <ol>
 * <li><b>先到先得</b>：占位顺序就是使用自选卡的顺序，先用的玩家优先。</li>
 * <li><b>名额以 {@code ROLE_MAX} 为准</b>：某职业上限是 N，就先来的 N 位占满，
 * 第 N+1 位起占位失败。上限默认 1（即"只有一位"），但配成 2/3 就允许那么多人。</li>
 * <li><b>不生效就退还</b>：占位失败不扣卡，只提示本人；分配失败退还已扣的卡。</li>
 * <li>改选复用尚未生效的卡片及每日次数。</li>
 * </ol>
 *
 * <p>调用方（{@code sre:pass selfselect} 命令、{@code BackpackManager.useSelfSelectCard}）
 * 都走这里，避免两处判断口径不一致。客户端界面另用
 * {@link Harpymodloader#isRoleOverflow(SRERole, UUID)} 把名额已满的职业置灰。
 */
public final class SelfSelectClaimService {

    /** 占位结果 */
    public enum Result {
        /** 占位成功（或改选成功），已扣卡 */
        OK,
        /** 该职业本局名额已满（ROLE_MAX）：未扣卡 */
        SLOT_FULL,
        /** 人数不满足该职业的刷新门槛：未扣卡，需告知 */
        PLAYER_COUNT,
        /** 地图不对：该职业只在特定地图刷新：未扣卡，需告知 */
        WRONG_MAP,
        /** 该职业不允许自选：未扣卡，需告知 */
        NOT_SELECTABLE,
        /** 本人没有自选卡 */
        NO_CARD,
        /** 本人今天用自选卡的次数已达每日上限：未扣卡，需告知 */
        DAILY_LIMIT,
        /** 重复选择同一职业：不扣卡，也不用广播 */
        SAME_ROLE,
        /** 场上已有玩家自选了与目标职业互斥的角色：未扣卡，需告知 */
        OPPOSING_ROLE
    }

    private SelfSelectClaimService() {
    }

    /**
     * 尝试用自选卡占用某职业。
     *
     * @param player 使用自选卡的玩家
     * @param role   目标职业
     * @return 结果；只有 {@link Result#OK} 会真正扣除自选卡
     */
    public static Result claim(ServerPlayer player, SRERole role) {
        return claim(player, role, currentPlayers(player));
    }

    /** 当前对局玩家列表（用于人数门槛）；取不到时返回空列表 */
    public static List<ServerPlayer> currentPlayers(@Nullable ServerPlayer player) {
        if (player == null || player.getServer() == null) {
            return List.of();
        }
        return player.getServer().getPlayerList().getPlayers();
    }

    /**
     * 下一局会用到的地图名。
     *
     * <p>地图在开局时才 {@code loadMap}，而选卡发生在开局前 —— 但地图是跨局保留的，
     * {@code areas.mapName} 就是下一局要加载的那张。所以这里读到的基本就是"下张地图"。
     * 开局时还会用实际地图再复查一次（见 {@code SelfSelectValidator}）。
     */
    @Nullable
    public static String currentMapName(@Nullable ServerPlayer player) {
        if (player == null || player.level() == null) {
            return null;
        }
        try {
            var areas = io.wifi.starrailexpress.cca.AreasWorldComponent.KEY.get(player.level());
            return areas == null ? null : areas.mapName;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 尝试用自选卡占用某职业。
     *
     * <p><b>选卡阶段不判地图</b>：真实流程是「先选职业卡，再投票选地图」，
     * 选卡时还不知道下一局是什么地图。特殊地图职业（雪原猎手、重刑犯…）在这里一律放行，
     * 地图是否匹配留到开局时由 {@code SelfSelectValidator.revalidate} 用实际地图复查；
     * 不匹配就撤销占位并退还自选卡。
     *
     * @param players 用于人数门槛判定的玩家列表
     */
    public static Result claim(ServerPlayer player, SRERole role, List<ServerPlayer> players) {
        return claim(player, role, players, null);
    }

    /**
     * 尝试用自选卡占用某职业。
     *
     * @param mapName 已确定的下一局地图名；传 {@code null} 表示"还没投票/未知"。
     *                只有调用方**确定**地图已经定了才应该传值。
     */
    public static Result claim(ServerPlayer player, SRERole role, List<ServerPlayer> players,
            @Nullable String mapName) {
        if (player == null || role == null) {
            return Result.NO_CARD;
        }
        UUID id = player.getUUID();
        SRERole current = PendingRoleCards.selfSelectedRole(id);
        if (current == null) {
            current = Harpymodloader.FORCED_MODDED_ROLE_FLIP.get(id);
        }
        // 重复选同一职业：什么也不做，也不用广播打扰全服
        if (current == role) {
            return Result.SAME_ROLE;
        }
        // 能不能选：人数门槛 / 是否允许自选（**不含地图**，地图等开局复查）
        SelfSelectGate.Deny deny = mapName == null || mapName.isBlank()
                ? SelfSelectGate.check(role, players)
                : SelfSelectGate.checkForRound(role, players, mapName);
        if (deny != SelfSelectGate.Deny.NONE) {
            return switch (deny) {
                case PLAYER_COUNT -> Result.PLAYER_COUNT;
                case WRONG_MAP -> Result.WRONG_MAP;
                default -> Result.NOT_SELECTABLE;
            };
        }
        // 互斥职业判定：已有其他玩家自选了与目标职业互斥的角色时拦下（不扣卡）。
        // 自然生成阶段的互斥由 removeOpposingJobs 保证角色池不会同时出现互斥对，
        // 但两个玩家各用自选卡占用互斥职业不经过那条路径，在这里补齐。
        // ★ 必须放在 SLOT_FULL 之前：互斥职业本身占着名额，
        //   先查名额只会报「名额已满」，报不出「互斥」。
        for (var claimed : Harpymodloader.FORCED_MODDED_ROLE_FLIP.entrySet()) {
            if (claimed.getKey().equals(id)) {
                continue; // 自己不阻断自己（改选时旧占用马上会释放）
            }
            SRERole other = claimed.getValue();
            if (other == null || other == role) {
                continue;
            }
            // 与 removeOpposingJobs 语义一致：任一方向声明了互斥即视为互斥
            if (role.opposingRoles.contains(other) || other.opposingRoles.contains(role)) {
                return Result.OPPOSING_ROLE;
            }
        }
        // 名额是否已满：以 ROLE_MAX 为准（默认 1，配成 2/3 的就允许那么多人）
        if (Harpymodloader.isRoleOverflow(role, id)) {
            return Result.SLOT_FULL;
        }
        // 每日用量上限：用完就拦（不扣卡）。放在扣卡前判断，
        // 否则会被 consumeSelfSelectCard 静默拒绝并误报成"没有卡"。
        boolean replacing = PendingRoleCards.isPending(id, current);
        if (!replacing && BackpackManager.isDailySelfSelectLimitReached(player)) {
            return Result.DAILY_LIMIT;
        }
        // 改选其它职业：先释放自己此前的占用，避免一人占多个职业
        if (current != null) {
            Harpymodloader.releaseSelfForcedRole(current, id);
        }
        if (!replacing && !BackpackManager.consumeSelfSelectCard(player)) {
            // 没卡就回滚刚释放的占用，别把玩家原先的选择弄丢
            if (current != null) {
                Harpymodloader.addToForcedRoles(current, player);
            }
            return Result.NO_CARD;
        }
        Harpymodloader.addToForcedRoles(role, player);
        PendingRoleCards.record(player, role);
        return Result.OK;
    }

    // ========================================================================
    // 统一退还入口
    // ========================================================================

    /**
     * 自选卡「选了但这一局没生效」时，统一在这里**退还卡 + 提示玩家**。
     *
     * <p>这是唯一权威的退入口：撤销占位（{@code FORCED_MODDED_ROLE_FLIP}）+ 卡退回背包
     * + 给玩家一条提示。所有"没生效"的路径都必须走这里，避免出现
     * "有的路径退、有的路径不退"（玩家最直接的体感就是"卡被吃了"）。
     *
     * <p><b>提示方式对齐阵营卡</b>：阵营卡没生效时用的是
     * {@code BroadcastCommand.BroadcastMessage} + {@code message.sre.pass.faction.assign_failed}
     * （一条红色、客户端弹窗式的消息）。自选卡这里用同一套通道和同一个红色样式，
     * 玩家看到的观感一致。
     *
     * @param player 用卡的玩家
     * @param role   选中的职业
     * @param reason 没生效的原因文案；{@code null} 表示用通用文案
     * @return 是否真的退还成功（玩家在线且卡确实退回）
     */
    public static boolean refundAndNotify(@Nullable ServerPlayer player, @Nullable SRERole role,
            @Nullable Component reason) {
        if (player == null) {
            return false;
        }
        // 1) 撤销占位：先释放，避免下一局还按"已自选"算
        if (role != null) {
            try {
                Harpymodloader.releaseSelfForcedRole(role, player.getUUID());
            } catch (Throwable t) {
                org.agmas.noellesroles.Noellesroles.LOGGER.warn(
                        "[SelfSelect] 撤销占位失败（role={}）", role.identifier(), t);
            }
        }
        // 2) 退还卡
        if (!PendingRoleCards.refundSelfSelect(player, role)) {
            return false;
        }
        // 3) 提示（阵营卡同款通道 + 同款红色）
        Component msg = reason != null ? reason
                : Component.translatable("message.sre.pass.selfselect_refunded",
                        role == null ? "?" : Harpymodloader.getRoleName(role).getString());
        org.agmas.noellesroles.commands.BroadcastCommand.BroadcastMessage(
                player, msg.copy().withStyle(net.minecraft.ChatFormatting.RED));
        org.agmas.noellesroles.Noellesroles.LOGGER.info(
                "[SelfSelect] 已退还自选卡并提示玩家 {}（职业 {}）",
                player.getGameProfile().getName(), role == null ? "?" : role.identifier());
        return true;
    }
}
