package org.agmas.harpymodloader.modded_murder;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.game.modes.SREMurderGameMode;
import io.wifi.starrailexpress.game.utils.RoleInstance;
import net.minecraft.resources.ResourceLocation;
import org.agmas.harpymodloader.Harpymodloader;

import java.util.*;

/**
 * 管理角色对应关系和配对分配
 * 支持同时分配两个关联角色（例如医生和毒师）
 */
public class RoleAssignmentManager {

    /**
     * 根据 Occupations_Roles 映射获取关联角色
     * 
     * @param role 主角色
     * @return 关联的角色，如果没有则返回null
     */
    public static ArrayList<SRERole> getCompanionRoles(SRERole role) {
        return Harpymodloader.getOccupationRoles(role);
    }

    /**
     * 检查一个角色是否有关联角色
     */
    public static boolean hasCompanionRole(SRERole role) {
        return Harpymodloader.hasOccupationRole(role);
    }

    private static boolean areOpposing(SRERole first, SRERole second) {
        return first.opposingRoles.stream().anyMatch(role -> role.identifier().equals(second.identifier()))
                || second.opposingRoles.stream().anyMatch(role -> role.identifier().equals(first.identifier()));
    }

    /** 共享配角指向互斥的多个主职业时，它们是备选分支；其它绑定仍须完整保留。 */
    public static List<SRERole> getCompatibleCompanionGroup(SRERole root, Collection<SRERole> reservedRoles) {
        return getCompatibleCompanionGroup(root, reservedRoles, reservedRoles);
    }

    public static List<SRERole> getCompatibleCompanionGroup(SRERole root, Collection<SRERole> reservedRoles,
            Collection<SRERole> preferredRoles) {
        if (reservedRoles.stream().anyMatch(role -> areOpposing(root, role))) {
            return List.of();
        }
        Map<ResourceLocation, SRERole> group = new LinkedHashMap<>();
        group.put(root.identifier(), root);
        List<SRERole> pending = new ArrayList<>();
        pending.add(root);
        for (int i = 0; i < pending.size(); i++) {
            List<SRERole> companions = new ArrayList<>(getCompanionRoles(pending.get(i)));
            companions.removeIf(Objects::isNull);
            // 已自选或已进入组合的分支优先，避免医生反向绑定改变已确定的主职业。
            companions.sort(Comparator.comparingInt(role -> group.containsKey(role.identifier())
                    || preferredRoles.stream().anyMatch(preferred -> preferred.identifier().equals(role.identifier()))
                            ? 0 : 1));
            for (SRERole companion : companions) {
                if (group.containsKey(companion.identifier())) {
                    continue;
                }
                List<SRERole> conflicts = new ArrayList<>();
                for (SRERole accepted : group.values()) {
                    if (areOpposing(companion, accepted)) {
                        conflicts.add(accepted);
                    }
                }
                for (SRERole reserved : reservedRoles) {
                    if (areOpposing(companion, reserved)) {
                        conflicts.add(reserved);
                    }
                }
                if (!conflicts.isEmpty()) {
                    boolean alternative = conflicts.stream().allMatch(conflict -> companions.stream()
                            .anyMatch(other -> other.identifier().equals(conflict.identifier())));
                    if (alternative) {
                        continue;
                    }
                    return List.of();
                }
                group.put(companion.identifier(), companion);
                pending.add(companion);
            }
        }
        return new ArrayList<>(group.values());
    }

    /** 展开完整绑定组合，已有的同伴不重复补入。 */
    public static List<RoleInstance> expandWithCompanionRoles(
            List<RoleInstance> roles) {
        return expandWithCompanionRoles(roles, List.of(), Integer.MAX_VALUE);
    }

    /** 自选职业参与同一次展开，但返回名单只包含尚未分配给玩家的实例。 */
    public static List<RoleInstance> expandWithCompanionRoles(List<RoleInstance> roles,
            List<SRERole> assignedRoles, int maxRoleCount) {
        List<RoleInstance> seeds = new ArrayList<>();
        Set<UUID> assignedIds = new HashSet<>();
        for (SRERole role : assignedRoles) {
            RoleInstance instance = new RoleInstance(UUID.randomUUID(), role);
            seeds.add(instance);
            assignedIds.add(instance.uuid());
        }
        seeds.addAll(roles);

        Map<ResourceLocation, List<RoleInstance>> available = new LinkedHashMap<>();
        for (RoleInstance instance : seeds) {
            SRERole role = instance.role();
            if (role == null) {
                continue;
            }
            List<RoleInstance> instances = available.computeIfAbsent(role.identifier(), key -> new ArrayList<>());
            if (role == TMMRoles.CIVILIAN || instances.size() < Harpymodloader.getRoleCapacity(role)) {
                instances.add(instance);
            }
        }

        List<RoleInstance> selected = new ArrayList<>();
        Map<ResourceLocation, Integer> selectedCounts = new HashMap<>();
        for (RoleInstance seed : seeds) {
            if (assignedIds.contains(seed.uuid())) {
                selected.add(seed);
                selectedCounts.merge(seed.role().identifier(), 1, Integer::sum);
            }
        }
        // 先接纳完整组合，再填普通职业；互斥或名额不足时整组放弃，避免拆散绑定。
        for (RoleInstance seed : seeds) {
            SRERole role = seed.role();
            if (role == null || (!assignedIds.contains(seed.uuid()) && !hasCompanionRole(role))) {
                continue;
            }
            List<SRERole> group = getCompatibleCompanionGroup(role,
                    selected.stream().map(RoleInstance::role).toList());
            List<RoleInstance> additions = new ArrayList<>();
            for (SRERole member : group) {
                List<RoleInstance> existing = available.getOrDefault(member.identifier(), List.of());
                int required = Math.max(Harpymodloader.isSelfPaired(member) ? 2 : 1, existing.size());
                for (int i = selectedCounts.getOrDefault(member.identifier(), 0); i < required; i++) {
                    additions.add(i < existing.size() ? existing.get(i)
                            : new RoleInstance(UUID.randomUUID(), member));
                }
            }
            if (selected.size() + additions.size() > maxRoleCount) {
                Harpymodloader.LOGGER.warn("[RolePool] No room for complete linked role group {}",
                        group.stream().map(SRERole::identifier).toList());
                continue;
            }
            for (RoleInstance instance : additions) {
                selected.add(instance);
                selectedCounts.merge(instance.role().identifier(), 1, Integer::sum);
            }
        }

        int targetSize = Math.min(maxRoleCount, Math.max(seeds.size(), selected.size()));
        for (RoleInstance instance : seeds) {
            SRERole role = instance.role();
            if (selected.size() >= targetSize || role == null || hasCompanionRole(role)) {
                continue;
            }
            if (selected.stream().anyMatch(accepted -> areOpposing(role, accepted.role()))) {
                continue;
            }
            List<RoleInstance> existing = available.get(role.identifier());
            int count = selectedCounts.getOrDefault(role.identifier(), 0);
            if (count < existing.size() && existing.contains(instance) && !selected.contains(instance)) {
                selected.add(instance);
                selectedCounts.merge(role.identifier(), 1, Integer::sum);
            }
        }
        selected.removeIf(instance -> assignedIds.contains(instance.uuid()));
        return selected;
    }

    /**
     * 添加角色对应关系
     */
    public static void addOccupationRole(SRERole mainRole, SRERole companionRole) {
        Harpymodloader.addOccupationRole(mainRole, companionRole);
    }

    public static List<RoleInstance> removeOpposingJobs(List<RoleInstance> roles, RoleAssignmentPool killerPool,
            RoleAssignmentPool neutralsPool,
            RoleAssignmentPool vigilantePool, RoleAssignmentPool civilianPool, boolean haveOccupationRoles,
            int maxDepth) {
        int neutrals = 0, civilian = 0, killer = 0, neturals_for_killer = 0, vigilante = 0;
        List<SRERole> all_roles = new ArrayList<>();
        for (var rolei : roles) {
            all_roles.add(rolei.role());
        }
        List<SRERole> new_all_roles = new ArrayList<>(all_roles);

        for (var role : all_roles) {
            if (!new_all_roles.contains(role))
                continue;
            for (var oprole : role.opposingRoles) {
                if (new_all_roles.contains(oprole)) {
                    int roleType = oprole.getRoleType();
                    while (new_all_roles.remove(oprole)) {
                        // -1: Unknown - 1: Innocent - 2: Neturals but not for killer - 3: Neturals for
                        // killer - 4: Killer - 5: Vigilante
                        switch (roleType) {
                            case 1: {
                                civilian++;
                                break;
                            }
                            case 2: {
                                neutrals++;
                                break;
                            }
                            case 3: {
                                neturals_for_killer++;
                                break;
                            }
                            case 4: {
                                killer++;
                                break;
                            }
                            case 5: {
                                vigilante++;
                                break;
                            }
                            default:
                                civilian++;
                        }
                    }
                }
            }
        }
        List<RoleInstance> all_role_instances = new ArrayList<>();
        for (var r : new_all_roles) {
            all_role_instances.add(new RoleInstance(UUID.randomUUID(), r));
        }
        if (maxDepth <= 0) {
            return all_role_instances;
        }
        int all = killer + civilian + neutrals + neturals_for_killer + vigilante;
        if (all == 0)
            return all_role_instances;
        all_role_instances.addAll(SREMurderGameMode.getAllRoles(killer, vigilante, neutrals + neturals_for_killer, all,
                0, killerPool, neutralsPool, vigilantePool, civilianPool, haveOccupationRoles, List.of(),
                maxDepth - 1));
        // 补抽也必须与保留名单核对，不能只保证补抽的子名单内部没有互斥。
        return removeOpposingJobs(all_role_instances, killerPool, neutralsPool, vigilantePool, civilianPool,
                haveOccupationRoles, 0);
    }
}
