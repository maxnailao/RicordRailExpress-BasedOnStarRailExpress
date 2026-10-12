package io.wifi.starrailexpress.game.modes.funny.rotation;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.commands.RoleCountManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 轮选模式的「最终角色表收敛」，所有轮选模式共用。
 *
 * <h2>要解决的三类问题</h2>
 * <ol>
 * <li><b>绑定职业重复</b>：绑定关系是双向的（重刑犯←→狱警、迪奥←→承太郎…），
 * 自选主职业时会多生成一份主职业。实测：自选重刑犯 → 2 个重刑犯 + 1 个狱警。</li>
 * <li><b>绑定职业成对性</b>：反过来，自选狱警时主职业方（重刑犯）没被带出来，
 * 出现"孤儿绑定职业"。实测：自选狱警 → 只有狱警、没有重刑犯。</li>
 * <li><b>阵营越预算</b>：自选迪奥时承太郎会刷出来，但警长阵营被挤成 3 个
 *（应为 承太郎 + 1 个普通警长 = 2 个）。</li>
 * </ol>
 *
 * <h2>为什么必须在这里做</h2>
 * 池子构建阶段的 {@code trimFaction} 只能约束**候选池**，
 * 而最终人数还受"换牌阶段（canReplaceRole）"和绑定补位影响。
 * 所以校验必须放在**角色最终落定、写进 gameComp 之前**
 * —— 谋杀模式就是在终局做的，所以它没有这些问题。
 *
 * <h2>规则优先级</h2>
 * <ol>
 * <li><b>自选占位的职业</b>绝不动（玩家花了卡，必须拿到）；</li>
 * <li><b>成对的绑定职业</b>其次（主职业在 → 必须带出绑定职业，反之亦然）；</li>
 * <li>都不满足时，把<em>随机分配到的</em>超编角色退回平民。</li>
 * </ol>
 */
public final class RotationRoleDedup {

    private RotationRoleDedup() {
    }

    private static boolean isKiller(SRERole r) {
        return r.canUseKiller();
    }

    private static boolean isVigilante(SRERole r) {
        return r.isVigilanteTeam();
    }

    private static boolean isNeutral(SRERole r) {
        return !r.isInnocent() && !r.canUseKiller() && !r.isVigilanteTeam();
    }

    /**
     * 就地收敛 {@code selectedRoles}。
     *
     * @param selectedRoles 玩家 → 职业；**会被就地修改**
     * @param playerCount   本局人数（用于算阵营预算）
     * @param tag           日志前缀，便于区分是哪个模式触发的
     * @return 被改动的玩家数量（退平民 / 补齐）
     */
    public static int dedup(Map<UUID, SRERole> selectedRoles, int playerCount, String tag) {
        if (selectedRoles == null || selectedRoles.isEmpty() || playerCount <= 0) {
            return 0;
        }
        int changed = 0;

        selectedRoles.entrySet().removeIf(e -> e.getValue() == null);

        // 自选占位者（这份无论如何保留）
        Set<UUID> forcedHolders = new LinkedHashSet<>();
        Set<SRERole> forcedRoles = new LinkedHashSet<>();
        for (var e : selectedRoles.entrySet()) {
            SRERole r = e.getValue();
            if (Harpymodloader.FORCED_MODDED_ROLE_FLIP.get(e.getKey()) == r) {
                forcedHolders.add(e.getKey());
                forcedRoles.add(r);
            }
        }

        // ── 1) 去重：同一职业超过 ROLE_MAX（默认 1）的多余实例退回平民 ──
        Map<net.minecraft.resources.ResourceLocation, Integer> seen = new HashMap<>();
        for (var e : new ArrayList<>(selectedRoles.entrySet())) {
            SRERole r = e.getValue();
            int cap = Math.max(1, Harpymodloader.getRoleCapacity(r));
            int n = seen.merge(r.identifier(), 1, Integer::sum);
            if (n <= cap) {
                continue;
            }
            if (forcedHolders.contains(e.getKey())) {
                Harpymodloader.LOGGER.info("[{}] {} 第 {} 份是自选占位，保留", tag, r.identifier(), n);
                continue;
            }
            Harpymodloader.LOGGER.warn("[{}] 去重：{} 第 {} 份超过上限 {}，退回平民",
                    tag, r.identifier(), n, cap);
            selectedRoles.put(e.getKey(), TMMRoles.CIVILIAN);
            changed++;
        }

        // ── 2) 成对性：绑定职业与主职业互相补齐（只加人，不删人）──
        changed += ensurePairs(selectedRoles, forcedRoles, tag);

        // ── 3) 阵营预算：超编的随机角色退回平民 ──
        int budgetK = Math.max(1, RoleCountManager.getKillerCount(playerCount));
        int budgetV = Math.max(0, RoleCountManager.getVigilanteCount(playerCount));
        int budgetN = Math.max(0, RoleCountManager.getNeutralCount(playerCount));
        changed += enforceBudget(selectedRoles, forcedHolders, budgetK, tag, "杀手", RotationRoleDedup::isKiller);
        changed += enforceBudget(selectedRoles, forcedHolders, budgetV, tag, "警长", RotationRoleDedup::isVigilante);
        changed += enforceBudget(selectedRoles, forcedHolders, budgetN, tag, "中立", RotationRoleDedup::isNeutral);

        return changed;
    }

    /**
     * 补齐绑定配对：名单里出现"成对职业"的任一方时，把另一方也放进来。
     *
     * <p>做法是找一个当前是平民的玩家承载它，总人数不变；
     * 找不到空位就记日志放弃（不硬塞，避免又造成越预算）。
     */
    private static int ensurePairs(Map<UUID, SRERole> roles, Set<SRERole> forcedRoles, String tag) {
        Set<net.minecraft.resources.ResourceLocation> present = new LinkedHashSet<>();
        for (SRERole r : roles.values()) {
            if (r != null) {
                present.add(r.identifier());
            }
        }
        int added = 0;
        for (var e : new ArrayList<>(roles.entrySet())) {
            SRERole main = e.getValue();
            if (main == null) {
                continue;
            }
            var companions = Harpymodloader.getOccupationRoles(main);
            if (companions == null || companions.isEmpty()) {
                continue;
            }
            for (SRERole comp : companions) {
                if (comp == null || present.contains(comp.identifier())) {
                    continue;
                }
                int cap = Math.max(1, Harpymodloader.getRoleCapacity(comp));
                long already = roles.values().stream()
                        .filter(x -> x != null && x.identifier().equals(comp.identifier())).count();
                if (already >= cap) {
                    continue;
                }
                // 找一个平民玩家来承载
                UUID slot = null;
                for (var x : roles.entrySet()) {
                    if (x.getValue() == TMMRoles.CIVILIAN) {
                        slot = x.getKey();
                        break;
                    }
                }
                if (slot == null) {
                    Harpymodloader.LOGGER.warn(
                            "[{}] 绑定职业 {} 需随 {} 一起出现，但场上没有平民空位可承载，跳过",
                            tag, comp.identifier(), main.identifier());
                    continue;
                }
                roles.put(slot, comp);
                present.add(comp.identifier());
                added++;
                Harpymodloader.LOGGER.info("[{}] 补齐绑定：{} 放成 {}（随 {} 一起出现）",
                        tag, slot, comp.identifier(), main.identifier());
            }
        }
        return added;
    }

    /**
     * 把某个阵营压回预算：超编时优先把**随机分配到的**角色退回平民。
     *
     * <p>自选占位排在最前面保护：玩家花了自选卡，必须拿到职业 ——
     * 即使因此轻微超编也不动它。
     */
    private static int enforceBudget(Map<UUID, SRERole> roles, Set<UUID> forcedHolders,
            int budget, String tag, String label,
            java.util.function.Predicate<SRERole> belongs) {
        List<UUID> holders = new ArrayList<>();
        for (var e : roles.entrySet()) {
            if (e.getValue() != null && belongs.test(e.getValue())) {
                holders.add(e.getKey());
            }
        }
        int over = holders.size() - budget;
        if (over <= 0) {
            return 0;
        }
        Harpymodloader.LOGGER.warn("[{}] {} 阵营 {} 人 > 预算 {}，需退回 {} 个平民",
                tag, label, holders.size(), budget, over);

        int done = 0;
        for (UUID u : holders) {
            if (over <= 0) {
                break;
            }
            if (forcedHolders.contains(u)) {
                continue;
            }
            SRERole old = roles.get(u);
            roles.put(u, TMMRoles.CIVILIAN);
            over--;
            done++;
            Harpymodloader.LOGGER.warn("[{}]   {} 由 {} 退回平民",
                    tag, u, old == null ? "?" : old.identifier());
        }
        if (over > 0) {
            Harpymodloader.LOGGER.warn("[{}]   {} 阵营仍有 {} 人超出（全是自选占位），不再强退",
                    tag, label, over);
        }
        return done;
    }
}
