package org.agmas.harpymodloader.modded_murder;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.game.modes.SREMurderGameMode;
import io.wifi.starrailexpress.game.utils.RoleInstance;
import org.agmas.harpymodloader.Harpymodloader;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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

    /**
     * 
     * @param companion
     * @param expandedRoles
     * @param companionRoles
     * @param companedRoles
     * @param tryLevel       尝试匹配等级。0：完全相同，1：忽略中立阵营，2：包含平民，3：包括所有
     * @return
     */
    public static boolean tryRemoveARole(SRERole companion, List<RoleInstance> expandedRoles,
            List<SRERole> companionRoles,
            List<SRERole> companedRoles, int tryLevel) {
        final boolean[] isRemoved = { false };

        expandedRoles.removeIf(ro -> {
            if (!isRemoved[0]) {
                var r = ro.role();
                boolean conditionMet = false;
                if (tryLevel == 0) {
                    conditionMet = (PlayerRoleWeightManager
                            .getRoleType(r) == PlayerRoleWeightManager
                                    .getRoleType(companion));
                } else if (tryLevel == 1) {
                    conditionMet = (PlayerRoleWeightManager
                            .getRoleType_IgnoreNeutralType(r) == PlayerRoleWeightManager
                                    .getRoleType_IgnoreNeutralType(companion));
                } else if (tryLevel == 2) {
                    conditionMet = (PlayerRoleWeightManager
                            .getRoleType_OnlyDistinctKiller(r) == PlayerRoleWeightManager
                                    .getRoleType(companion));
                } else {
                    conditionMet = true;
                }

                if (conditionMet && companionRoles.stream()
                        .noneMatch(rd -> rd.getIdentifier().equals(r.getIdentifier()))
                        && companedRoles.stream()
                                .noneMatch(rd -> rd.getIdentifier().equals(r.getIdentifier()))) {
                    isRemoved[0] = true;
                    return true;
                }
            }
            return false;
        });
        return isRemoved[0];
    }

    /**
     * 展开角色列表：如果角色有关联角色，添加关联角色
     * 例如：[医生] -> [医生, 毒师]
     * 注意：允许结果列表中包含重复的角色
     * 
     * @param roles 原始角色列表
     * @return 展开后的角色列表（包含所有关联角色，允许重复）
     */
    public static List<RoleInstance> expandWithCompanionRoles(
            List<RoleInstance> roles) {
        List<RoleInstance> oldRoles = new ArrayList<>(roles);
        List<RoleInstance> expandedRoles = new ArrayList<>(roles);
        List<SRERole> companionRoles = new ArrayList<>();
        List<SRERole> companedRoles = new ArrayList<>();

        for (var role : oldRoles) {
            ArrayList<SRERole> companions = getCompanionRoles(role.role());
            for (var companion : companions) {
                if (companion != null) {
                    companedRoles.add(role.role());
                    {
                        if (!tryRemoveARole(companion, expandedRoles, companionRoles, companedRoles, 0)) {
                            if (!tryRemoveARole(companion, expandedRoles, companionRoles, companedRoles, 1)) {
                                if (!tryRemoveARole(companion, expandedRoles, companionRoles, companedRoles, 2)) {
                                    if (!tryRemoveARole(companion, expandedRoles, companionRoles, companedRoles, 3)) {
                                        Harpymodloader.LOGGER
                                                .error("Unable to remove a role to make room for linked role {}!",
                                                        role.role().identifier().toString());
                                    }
                                }
                            }
                        }
                    }
                    companionRoles.add(companion);
                }
            }
        }

        expandedRoles.addAll(
                companionRoles.stream().map(r -> new RoleInstance(UUID.randomUUID(), r))
                        .toList());

        // ── 去重：互绑关系会产生重复 ──
        //
        // 迪奥与承太郎现在是**互相绑定**（DIO->JOJO 且 JOJO->DIO）。
        // 当两者**同时已经在名单里**时（例如两人分别自选了一个），
        // 逐条展开会各自把对方再"补"进来一次 → 名单里出现两个迪奥/两个承太郎。
        //
        // 这里按名额上限收敛：同一职业的数量超过 ROLE_MAX 的，从后往前移除多余的实例。
        // 只处理"有互绑关系"的职业之外也一并兜住，不会有副作用
        //（正常单绑展开本来就不会产生重复）。
        java.util.Map<net.minecraft.resources.ResourceLocation, Integer> seenPerRole =
                new java.util.HashMap<>();
        java.util.List<RoleInstance> deduped = new ArrayList<>(expandedRoles.size());
        for (var ri : expandedRoles) {
            SRERole r = ri.role();
            if (r == null) {
                deduped.add(ri);
                continue;
            }
            int cap = Math.max(1, Harpymodloader.getRoleCapacity(r));
            int seen = seenPerRole.merge(r.identifier(), 1, Integer::sum);
            if (seen > cap) {
                Harpymodloader.LOGGER.warn(
                        "[RolePool] 绑定展开产生重复的 {}（第 {} 个，上限 {}），已丢弃", r.identifier(), seen, cap);
                continue;
            }
            deduped.add(ri);
        }
        return deduped;
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
        return all_role_instances;
    }
}
