package io.wifi.starrailexpress.game.modes.funny.rotation;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.game.utils.RoleInstance;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.commands.RoleCountManager;
import org.agmas.harpymodloader.modded_murder.RoleAssignmentManager;

import java.util.*;
import java.util.function.Predicate;

/** Final assignments use the same atomic companion groups as the initial role pool. */
public final class RotationRoleDedup {
    private RotationRoleDedup() {
    }

    public static int dedup(Map<UUID, SRERole> selectedRoles, int playerCount, String tag) {
        if (selectedRoles == null || selectedRoles.isEmpty() || playerCount <= 0) {
            return 0;
        }
        Map<UUID, SRERole> original = new LinkedHashMap<>(selectedRoles);
        Map<UUID, SRERole> forced = new LinkedHashMap<>();
        List<RoleInstance> seeds = new ArrayList<>();
        for (var entry : selectedRoles.entrySet()) {
            SRERole role = entry.getValue();
            if (role == null) {
                continue;
            }
            if (Harpymodloader.FORCED_MODDED_ROLE_FLIP.get(entry.getKey()) == role) {
                List<SRERole> proposed = new ArrayList<>(forced.values());
                proposed.add(role);
                List<SRERole> required = RoleAssignmentManager.getRequiredRoles(proposed);
                if (!required.isEmpty() && required.size() <= selectedRoles.size()
                        && proposed.stream().filter(r -> r == role).count() <= Harpymodloader.getRoleCapacity(role)) {
                    forced.put(entry.getKey(), role);
                    continue;
                }
            }
            seeds.add(new RoleInstance(entry.getKey(), role));
        }
        List<RoleInstance> accepted = RoleAssignmentManager.expandWithCompanionRoles(seeds,
                new ArrayList<>(forced.values()), selectedRoles.size());
        Map<UUID, SRERole> result = new LinkedHashMap<>(forced);
        // Preserve player choices when their original instances survive group selection.
        accepted.removeIf(instance -> {
            if (original.get(instance.uuid()) == instance.role() && !result.containsKey(instance.uuid())) {
                result.put(instance.uuid(), instance.role());
                return true;
            }
            return false;
        });
        for (UUID uuid : original.keySet()) {
            if (result.containsKey(uuid)) {
                continue;
            }
            RoleInstance replacement = accepted.stream()
                    .filter(instance -> original.get(uuid) != null
                            && instance.role().getRoleType() == original.get(uuid).getRoleType())
                    .findFirst().orElse(accepted.isEmpty() ? null : accepted.getFirst());
            result.put(uuid, replacement == null ? TMMRoles.CIVILIAN : replacement.role());
            accepted.remove(replacement);
        }
        Set<SRERole> protectedRoles = new HashSet<>(forced.values());
        for (SRERole role : result.values()) {
            if (RoleAssignmentManager.hasCompanionRole(role)) {
                protectedRoles.addAll(RoleAssignmentManager.getCompatibleCompanionGroup(role, result.values()));
            }
        }
        enforceBudget(result, protectedRoles, Math.max(1, RoleCountManager.getKillerCount(playerCount)), SRERole::canUseKiller);
        enforceBudget(result, protectedRoles, Math.max(0, RoleCountManager.getVigilanteCount(playerCount)), SRERole::isVigilanteTeam);
        enforceBudget(result, protectedRoles, Math.max(0, RoleCountManager.getNeutralCount(playerCount)),
                role -> !role.isInnocent() && !role.canUseKiller() && !role.isVigilanteTeam());
        selectedRoles.clear();
        selectedRoles.putAll(result);
        int changed = (int) result.entrySet().stream().filter(e -> original.get(e.getKey()) != e.getValue()).count();
        if (changed > 0) {
            Harpymodloader.LOGGER.info("[{}] Reconciled {} final role assignments", tag, changed);
        }
        return changed;
    }

    private static void enforceBudget(Map<UUID, SRERole> roles, Set<SRERole> protectedRoles,
            int budget, Predicate<SRERole> belongs) {
        int occupied = roles.values().stream().filter(belongs)
                .mapToInt(role -> Math.max(1, role.getOccupiedRoleCount())).sum();
        int over = occupied - budget;
        for (var entry : roles.entrySet()) {
            if (over <= 0) {
                break;
            }
            if (belongs.test(entry.getValue()) && !protectedRoles.contains(entry.getValue())) {
                over -= Math.max(1, entry.getValue().getOccupiedRoleCount());
                entry.setValue(TMMRoles.CIVILIAN);
            }
        }
    }
}
