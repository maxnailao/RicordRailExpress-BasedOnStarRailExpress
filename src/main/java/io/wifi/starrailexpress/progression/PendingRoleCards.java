package io.wifi.starrailexpress.progression;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.backpack.BackpackManager;
import io.wifi.starrailexpress.backpack.DailyCardUsage;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.progression.ProgressionState.FactionCardType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.modded_murder.ForceTeamInfo;
import org.agmas.harpymodloader.modded_murder.ForceTeamInfo.ForceTeamType;
import org.agmas.harpymodloader.modded_murder.PlayerRoleWeightManager;
import org.agmas.noellesroles.commands.BroadcastCommand;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Paid reservations are settled once, against the final roles of their own round. */
public final class PendingRoleCards {
    private record Claim<T>(T target, String usageDay, ServerLevel round) {
        Claim<T> forRound(ServerLevel level) {
            return new Claim<>(target, usageDay, level);
        }
    }

    private static final Map<UUID, Claim<SRERole>> SELF_SELECT = new HashMap<>();
    private static final Map<UUID, Claim<FactionCardType>> FACTION = new HashMap<>();

    private PendingRoleCards() {
    }

    public static void record(ServerPlayer player, SRERole role) {
        Claim<SRERole> previous = SELF_SELECT.get(player.getUUID());
        SELF_SELECT.put(player.getUUID(), new Claim<>(role,
                previous == null ? DailyCardUsage.today() : previous.usageDay(),
                previous == null ? null : previous.round()));
    }

    public static boolean isPending(UUID uuid, SRERole role) {
        Claim<SRERole> claim = SELF_SELECT.get(uuid);
        return claim != null && claim.target() == role;
    }

    public static SRERole selfSelectedRole(UUID uuid) {
        Claim<SRERole> claim = SELF_SELECT.get(uuid);
        return claim == null ? null : claim.target();
    }

    public static void recordFaction(ServerPlayer player, FactionCardType type) {
        FACTION.put(player.getUUID(), new Claim<>(type, DailyCardUsage.today(), null));
    }

    public static boolean hasFaction(UUID uuid) {
        return FACTION.containsKey(uuid);
    }

    public static void beginRound(ServerLevel level, List<ServerPlayer> participants) {
        // A claim bought during an active game belongs to the next round, not its end event.
        bindRound(SELF_SELECT, level);
        bindRound(FACTION, level);
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            boolean participating = participants.stream().anyMatch(p -> p.getUUID().equals(player.getUUID()));
            if (!participating) {
                refundRound(player, level);
                continue;
            }
            Claim<SRERole> self = SELF_SELECT.get(player.getUUID());
            if (self != null && self.round() == level
                    && Harpymodloader.FORCED_MODDED_ROLE_FLIP.get(player.getUUID()) != self.target()) {
                Harpymodloader.addToForcedRoles(self.target(), player);
            }
            Claim<FactionCardType> faction = FACTION.get(player.getUUID());
            if (faction != null && faction.round() == level) {
                PlayerRoleWeightManager.ForcePlayerTeam.put(player.getUUID(),
                        new ForceTeamInfo(faction.target().getTypeId(), ForceTeamType.CARD));
            }
        }
    }

    private static <T> void bindRound(Map<UUID, Claim<T>> claims, ServerLevel level) {
        claims.replaceAll((uuid, claim) -> claim.round() == null ? claim.forRound(level) : claim);
    }

    public static void verifyPending(ServerLevel level) {
        var roles = SREGameWorldComponent.KEY.get(level);
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            UUID uuid = player.getUUID();
            SRERole actual = roles.getRole(uuid);
            Claim<SRERole> self = SELF_SELECT.get(uuid);
            if (self != null && self.round() == level) {
                if (actual == self.target()) {
                    SELF_SELECT.remove(uuid);
                } else {
                    SelfSelectClaimService.refundAndNotify(player, self.target(), null);
                }
            }
            Claim<FactionCardType> faction = FACTION.get(uuid);
            if (faction != null && faction.round() == level) {
                if (matchesFaction(actual, faction.target())) {
                    FACTION.remove(uuid);
                } else {
                    refundFaction(player);
                }
            }
        }
    }

    public static boolean matchesFaction(SRERole role, FactionCardType type) {
        return type != FactionCardType.NONE && role != null
                && PlayerRoleWeightManager.getRoleType(role) == type.getTypeId();
    }

    public static boolean refundSelfSelect(ServerPlayer player, SRERole role) {
        Claim<SRERole> claim = SELF_SELECT.get(player.getUUID());
        if (claim == null || claim.target() != role) {
            return false;
        }
        SELF_SELECT.remove(player.getUUID());
        BackpackManager.refundSelfSelectCard(player, claim.usageDay());
        return true;
    }

    public static boolean refundFaction(ServerPlayer player) {
        Claim<FactionCardType> claim = FACTION.remove(player.getUUID());
        if (claim == null) {
            return false;
        }
        ForceTeamInfo forced = PlayerRoleWeightManager.ForcePlayerTeam.get(player.getUUID());
        if (forced != null && forced.type() == ForceTeamType.CARD
                && forced.roleType() == claim.target().getTypeId()) {
            PlayerRoleWeightManager.ForcePlayerTeam.remove(player.getUUID());
        }
        BackpackManager.refundFactionCard(player, claim.target(), claim.usageDay());
        BroadcastCommand.BroadcastMessage(player,
                Component.translatable("message.sre.pass.faction.assign_failed")
                        .withStyle(net.minecraft.ChatFormatting.RED));
        return true;
    }

    private static void refundRound(ServerPlayer player, ServerLevel level) {
        Claim<SRERole> self = SELF_SELECT.get(player.getUUID());
        if (self != null && self.round() == level) {
            SelfSelectClaimService.refundAndNotify(player, self.target(), null);
        }
        Claim<FactionCardType> faction = FACTION.get(player.getUUID());
        if (faction != null && faction.round() == level) {
            refundFaction(player);
        }
    }

    public static void cancelRound(ServerLevel level) {
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            refundRound(player, level);
        }
    }

    /** Runs before backpack disconnect/shutdown persistence so refunds survive reconnecting. */
    public static void refundAll(ServerPlayer player) {
        Claim<SRERole> self = SELF_SELECT.get(player.getUUID());
        if (self != null) {
            SelfSelectClaimService.refundAndNotify(player, self.target(), null);
        }
        refundFaction(player);
    }
}
