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
     * <p>承载方式按优先级：
     * <ol>
     *   <li>找一个当前是<b>平民</b>的玩家（总人数不变，最干净）；</li>
     *   <li>没有平民位时，<b>挤掉一个同阵营的普通角色</b>（非自选、非成对）——
     *       这样"补进绑定职业"与"该阵营人数不变"同时成立，
     *       和自然刷新的池子口径一致；</li>
     *   <li>再不行就挤掉其它阵营的普通角色；</li>
     *   <li>全是受保护角色时才放弃。</li>
     * </ol>
     *
     * <p><b>为什么必须有不依赖平民位的兜底</b>：旧实现只认平民位，
     * 一旦平民位被换牌阶段吃光就只能放弃，日志是
     * 「绑定职业 X 需随 Y 一起出现，但场上没有平民空位可承载，跳过」——
     * 实测表现就是"自选诡客却没刷出布袋鬼，反而只刷了一个巫师"。
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
                UUID slot = pickSlotFor(roles, comp, forcedRoles);
                if (slot == null) {
                    Harpymodloader.LOGGER.warn(
                            "[{}] 绑定职业 {} 需随 {} 一起出现，但场上没有可承载它的位子，跳过",
                            tag, comp.identifier(), main.identifier());
                    continue;
                }
                SRERole displaced = roles.put(slot, comp);
                present.add(comp.identifier());
                added++;
                Harpymodloader.LOGGER.info("[{}] 补齐绑定：{} 放成 {}（随 {} 一起出现，顶掉 {}）",
                        tag, slot, comp.identifier(), main.identifier(),
                        displaced == null ? "?" : displaced.identifier());
            }
        }
        return added;
    }

    /**
     * 给绑定职业挑一个承载位。
     *
     * <p>顺序：平民位 → 同阵营普通人 → 其它阵营普通人。
     * "普通人"= 非自选占位、且不属于任何完整配对。
     */
    private static UUID pickSlotFor(Map<UUID, SRERole> roles, SRERole comp, Set<SRERole> forcedRoles) {
        Set<UUID> locked = protectedHolders(roles, forcedRoles);

        // 1) 平民位
        for (var x : roles.entrySet()) {
            if (x.getValue() == TMMRoles.CIVILIAN) {
                return x.getKey();
            }
        }
        // 2) 同阵营普通人
        java.util.function.Predicate<SRERole> sameFaction;
        if (isKiller(comp)) {
            sameFaction = RotationRoleDedup::isKiller;
        } else if (isVigilante(comp)) {
            sameFaction = RotationRoleDedup::isVigilante;
        } else {
            sameFaction = RotationRoleDedup::isNeutral;
        }
        UUID fallback = null;
        for (var x : roles.entrySet()) {
            SRERole r = x.getValue();
            if (r == null || r == TMMRoles.CIVILIAN || locked.contains(x.getKey())) {
                continue;
            }
            if (sameFaction.test(r)) {
                return x.getKey();
            }
            if (fallback == null) {
                fallback = x.getKey();
            }
        }
        // 3) 其它阵营普通人
        return fallback;
    }

    /**
     * 不可被"挤位"的玩家集合：自选占位者 + <b>已成对职业的双方</b>。
     *
     * <p>成对双方都不能动 —— 挤掉任何一方都会把配对拆散，正是要修的问题。
     */
    private static Set<UUID> protectedHolders(Map<UUID, SRERole> roles, Set<SRERole> forcedRoles) {
        Set<UUID> out = new LinkedHashSet<>();
        Set<net.minecraft.resources.ResourceLocation> present = new LinkedHashSet<>();
        for (SRERole r : roles.values()) {
            if (r != null) {
                present.add(r.identifier());
            }
        }
        for (var e : roles.entrySet()) {
            SRERole r = e.getValue();
            if (r == null) {
                continue;
            }
            // 自选占位
            if (forcedRoles != null && forcedRoles.stream()
                    .anyMatch(f -> f != null && f.identifier().equals(r.identifier()))) {
                out.add(e.getKey());
                continue;
            }
            // 已成对：自己的任意一个伙伴也在场上
            var partners = Harpymodloader.getOccupationRoles(r);
            if (partners != null && partners.stream()
                    .anyMatch(p -> p != null && present.contains(p.identifier()))) {
                out.add(e.getKey());
            }
        }
        return out;
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

        // 受保护的除了自选占位，还包括**已成对职业的双方** ——
        // 否则上一步 ensurePairs 刚补进来的绑定职业会被这里当成"超编"退回平民，
        // 表现就是"补了又退"，一样缺绑定职业。
        Set<UUID> locked = new LinkedHashSet<>(forcedHolders);

        int done = 0;
        for (UUID u : holders) {
            if (over <= 0) {
                break;
            }
            if (locked.contains(u)) {
                continue;
            }
            SRERole old = roles.get(u);
            if (old != null && isPairedIn(old, roles)) {
                Harpymodloader.LOGGER.info("[{}]   {} 是成对职业（{}），保留不退回",
                        tag, u, old.identifier());
                continue;
            }
            roles.put(u, TMMRoles.CIVILIAN);
            over--;
            done++;
            Harpymodloader.LOGGER.warn("[{}]   {} 由 {} 退回平民",
                    tag, u, old == null ? "?" : old.identifier());
        }
        if (over > 0) {
            Harpymodloader.LOGGER.warn("[{}]   {} 阵营仍有 {} 人超出（都是自选/成对职业），不再强退",
                    tag, label, over);
        }
        return done;
    }

    /** {@code role} 是否与名单里的某个角色构成完整配对（任一方互为绑定职业）。 */
    private static boolean isPairedIn(SRERole role, Map<UUID, SRERole> roles) {
        var partners = Harpymodloader.getOccupationRoles(role);
        if (partners == null || partners.isEmpty()) {
            return false;
        }
        for (SRERole r : roles.values()) {
            if (r == null) {
                continue;
            }
            for (SRERole p : partners) {
                if (p != null && p.identifier().equals(r.identifier())) {
                    return true;
                }
            }
        }
        return false;
    }
}
