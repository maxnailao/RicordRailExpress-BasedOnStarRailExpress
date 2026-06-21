package io.wifi.starrailexpress.roster;

import net.minecraft.resources.ResourceLocation;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本回合的“地图限制”闸门。
 * <p>
 * 普通模式下，地图特定职业 / 修饰符通过把 {@code ROLE_MAX} / {@code MODIFIER_MAX} 设为 0
 * 来禁止其在非专属地图上刷新。但当职业轮换名单（{@link RoleRosterManager}）启用时，
 * 名单会直接接管启用/禁用与数量，从而绕过了上述基于 MAX 的地图限制，导致地图特定职业
 * 在非专属地图泄漏。
 * <p>
 * 为此，地图限制逻辑在每回合开始时把“被地图限制挡在当前地图之外”的职业 / 修饰符登记到本闸门，
 * 名单接管时（{@code RoleAssignmentPool} / {@code assignModifiers}）会额外排除这些被登记者，
 * 使地图限制始终生效，而人数门槛 / 概率门槛仍交由名单接管。
 * <p>
 * 闸门为服务器全局静态，单局游戏使用；每回合开始（分配角色之前）调用 {@link #reset()} 清空。
 */
public final class MapRestrictionGate {
    private static final Set<ResourceLocation> FORBIDDEN_ROLES = ConcurrentHashMap.newKeySet();
    private static final Set<ResourceLocation> FORBIDDEN_MODIFIERS = ConcurrentHashMap.newKeySet();

    private MapRestrictionGate() {
    }

    /** 每回合分配角色前清空，由 {@code GameUtils} 在触发 GameInitializeEvent 之前调用。 */
    public static void reset() {
        FORBIDDEN_ROLES.clear();
        FORBIDDEN_MODIFIERS.clear();
    }

    public static void markRole(ResourceLocation roleId) {
        if (roleId != null) {
            FORBIDDEN_ROLES.add(roleId);
        }
    }

    public static void markModifier(ResourceLocation modifierId) {
        if (modifierId != null) {
            FORBIDDEN_MODIFIERS.add(modifierId);
        }
    }

    /** 该职业本回合是否被地图限制挡在当前地图之外。 */
    public static boolean isRoleForbidden(ResourceLocation roleId) {
        return roleId != null && FORBIDDEN_ROLES.contains(roleId);
    }

    /** 该修饰符本回合是否被地图限制挡在当前地图之外。 */
    public static boolean isModifierForbidden(ResourceLocation modifierId) {
        return modifierId != null && FORBIDDEN_MODIFIERS.contains(modifierId);
    }
}
