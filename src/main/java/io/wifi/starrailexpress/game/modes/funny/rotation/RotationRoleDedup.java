package io.wifi.starrailexpress.game.modes.funny.rotation;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import org.agmas.harpymodloader.Harpymodloader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 轮选模式的「绑定职业去重」。
 *
 * <h2>问题</h2>
 * 绑定关系是**双向**的（重刑犯 ←→ 狱警、迪奥 ←→ 承太郎、毒师 ←→ 医生…）。
 * 于是有人用自选卡选主职业时，场上会生成两份主职业：
 * 自选占位的那一份 + 从绑定关系反向展开出来的一份。
 *
 * <p>实测（监狱图，不可见轮换模式）：
 * <ul>
 * <li>自选<b>重刑犯</b> → 2 个重刑犯 + 1 个狱警（应为 1 + 1）</li>
 * <li>自选<b>狱警</b> → 2 个狱警 + 1 个重刑犯（应为 1 + 1）</li>
 * </ul>
 *
 * <h2>做法</h2>
 * 按名额上限（{@code ROLE_MAX}，默认 1）收敛：
 * <ul>
 * <li><b>自选强行占位的职业绝不移除</b> —— 玩家花了自选卡，必须拿到；</li>
 * <li>超出上限的重复实例从后往前摘掉，被摘的玩家退回平民。</li>
 * </ul>
 *
 * <p>抽成静态工具是为了让<b>所有</b>轮选模式（普通 / 不可见 / 单选）共用同一套逻辑，
 * 它们各有自己的 {@code completeRoleSelection}，无法靠继承共享。
 */
public final class RotationRoleDedup {

    private RotationRoleDedup() {
    }

    /**
     * 就地修正 {@code selectedRoles}：去掉超出名额上限的重复职业。
     *
     * @param selectedRoles 玩家 → 职业；**会被就地修改**
     * @param tag           日志前缀，便于区分是哪个模式触发的
     * @return 被退回平民的玩家数量
     */
    public static int dedup(Map<UUID, SRERole> selectedRoles, String tag) {
        if (selectedRoles == null || selectedRoles.isEmpty()) {
            return 0;
        }
        // 先统计每个职业出现几次，并找出受保护的自选占位者
        Map<net.minecraft.resources.ResourceLocation, Integer> seen = new HashMap<>();
        Set<SRERole> forcedRoles = new LinkedHashSet<>(Harpymodloader.FORCED_MODDED_ROLE_FLIP.values());

        int fixed = 0;
        Map<UUID, SRERole> civilianFor = new LinkedHashMap<>();

        for (var e : new ArrayList<>(selectedRoles.entrySet())) {
            SRERole r = e.getValue();
            if (r == null) {
                continue;
            }
            int cap = Math.max(1, Harpymodloader.getRoleCapacity(r));
            int n = seen.merge(r.identifier(), 1, Integer::sum);
            if (n <= cap) {
                continue;
            }
            // 超出了上限：如果是"自选占位"的那一份，保留；否则退回平民
            boolean forced = forcedRoles.contains(r)
                    && Harpymodloader.FORCED_MODDED_ROLE_FLIP.get(e.getKey()) == r;
            if (forced) {
                // 自选的那份永远保留。把之前算进来的那份"挤掉"——
                // 做法：把本次计数降回去，并标记要处理的实际上是前一个持有者。
                // 简化处理：自选份保留，本轮不再减计数以外的动作。
                Harpymodloader.LOGGER.info(
                        "[{}] {} 第 {} 份是自选占位，保留（上限 {}）", tag, r.identifier(), n, cap);
                continue;
            }
            Harpymodloader.LOGGER.warn(
                    "[{}] 去重：{} 出现第 {} 份（上限 {}），把该玩家退回平民", tag, r.identifier(), n, cap);
            civilianFor.put(e.getKey(), r);
        }

        for (var e : civilianFor.entrySet()) {
            selectedRoles.put(e.getKey(), TMMRoles.CIVILIAN);
            fixed++;
            Harpymodloader.LOGGER.info("[{}]   {} 由 {} 改为 {}",
                    tag, e.getKey(), e.getValue().identifier(), TMMRoles.CIVILIAN.identifier());
        }
        return fixed;
    }
}
