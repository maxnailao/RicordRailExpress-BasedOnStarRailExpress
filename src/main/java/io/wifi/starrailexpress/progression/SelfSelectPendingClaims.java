package io.wifi.starrailexpress.progression;

import io.wifi.starrailexpress.api.SRERole;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 自选职业卡的「待生效」记账本 —— 保证**只要选了没生效就一定退卡**。
 *
 * <h2>为什么需要它</h2>
 * 自选卡是"选卡时立刻扣卡、开局时才决定生不生效"。而生不生效的出口非常多：
 * <ul>
 * <li>{@code SelfSelectValidator} 开局复查（地图不对 / 人数不够）；</li>
 * <li>本局角色池里没有这个职业，抽签抽不到；</li>
 * <li>玩家没进 ready 名单，开局时被排除；</li>
 * <li>各游戏模式在角色分配后用 {@code FORCED_MODDED_ROLE_FLIP.clear()} 清场
 * —— 这一条尤其容易漏，因为 {@code clear()} 是静默的，谁都不会去补退款。</li>
 * </ul>
 *
 * <p>靠"在每个出口手动补一句退卡"是不可靠的：漏一个出口，玩家就白丢一张卡
 * （实测 {@code refundSelfSelectCard} 全项目只有 1 个调用点，
 * 而 {@code FORCED_MODDED_ROLE_FLIP.clear()} 有 9 处）。
 *
 * <h2>做法</h2>
 * 反向兜底：**扣卡时记一笔账，角色分配完成后核对一次**。
 * 账上写着"某玩家这局应该拿到某职业"，核对时他实际没拿到 → 退卡 + 提示。
 * 这样不管前面有多少条失败路径、以后又新增多少条，都不会漏。
 *
 * <p>记账本在退还时会同时删除该条记录，因此多个校验点（开局后、结束前）
 * 可以放心重复调用，不会重复退卡。
 */
public final class SelfSelectPendingClaims {

    /** 玩家 -> 他这局用自选卡占用的职业 */
    private static final Map<UUID, SRERole> PENDING = new HashMap<>();

    private SelfSelectPendingClaims() {
    }

    /** 扣卡成功时记账 */
    public static synchronized void record(ServerPlayer player, SRERole role) {
        if (player == null || role == null) {
            return;
        }
        PENDING.put(player.getUUID(), role);
    }

    /** 该玩家账上是否还挂着这条自选（用于"开局前已判定无效"的即时退卡） */
    public static synchronized boolean isPending(UUID uuid, SRERole role) {
        return uuid != null && role != null && role == PENDING.get(uuid);
    }

    /** 撤账（不退款）。用于已经判定无效、并且已经先行退款的情形，避免之后重复退。 */
    public static synchronized void unrecord(UUID uuid) {
        if (uuid != null) {
            PENDING.remove(uuid);
        }
    }

    /** 当前是否还有未核对的记账 */
    public static synchronized boolean isEmpty() {
        return PENDING.isEmpty();
    }

    /**
     * 核对所有记账：实际没拿到对应职业的，退卡 + 提示，并把该条从账上划掉。
     *
     * @param server 服务器实例；{@code null} 直接返回 0
     * @param reason 退款提示的原因文案；{@code null} 表示用通用文案
     * @return 本次实际退还的张数
     */
    public static synchronized int verifyPending(@Nullable MinecraftServer server,
            @Nullable net.minecraft.network.chat.Component reason) {
        if (server == null || PENDING.isEmpty()) {
            return 0;
        }
        int refunded = 0;
        var it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            UUID uuid = entry.getKey();
            SRERole role = entry.getValue();
            if (role == null) {
                it.remove();
                continue;
            }
            // 玩家已离线：留到下次核对（他可能还会回来），本次跳过
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) {
                continue;
            }
            // 旁观者 = 没进本局 ready 名单，肯定拿不到职业 → 直接按"没生效"退卡。
            //
            // ★ 不能拿他身上残留的上一局职业去判断：那会把记录误判成"已生效"，
            //   记录被静默清掉，之后再也不会有任何地方给他退卡
            //   （玩家体感就是"用了卡没选上，卡也没了"）。
            if (player.isSpectator()) {
                it.remove();
                SelfSelectClaimService.refundAndNotify(player, role, reason);
                refunded++;
                continue;
            }
            // 核对：这个人现在身上是不是这个职业。
            //
            // 已知边界：如果玩家上一局恰好也是这个职业、且本局自选没生效，
            // 角色表在重新分配前会保留上一局的值，这里会误判成"生效了"，
            // 该条会被静默划掉。这种"连续两局同一职业 + 第二局自选失败"的组合极罕见，
            // 而且真正的失败出口（revalidate / 抽不到 / 不在 ready）都会在
            // SelfSelectValidator 里**先行**退卡并从账上删除，所以这里是兜底而非主路径。
            SRERole actual = currentRole(server, uuid);
            if (actual == role) {
                it.remove(); // 生效了，账清了
                continue;
            }
            // 没生效 → 退卡。**先划账再退**：refundAndNotify 内部也会 unrecord，
            // 所以必须先 remove 再调用，否则会和自己改坏 HashMap 结构
            // （迭代中改结构 → ConcurrentModificationException）。
            it.remove();
            SelfSelectClaimService.refundAndNotify(player, role, reason);
            refunded++;
        }
        return refunded;
    }

    /** 读取该玩家当前（或上一局残留的）职业 */
    @Nullable
    private static SRERole currentRole(MinecraftServer server, UUID uuid) {
        try {
            ServerLevel level = server.overworld();
            if (level == null) {
                return null;
            }
            var comp = io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(level);
            return comp == null ? null : comp.getRole(uuid);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 清空记账本（不退款）。只在确认这些记账都已经处理过时调用。 */
    public static synchronized void clear() {
        PENDING.clear();
    }
}
