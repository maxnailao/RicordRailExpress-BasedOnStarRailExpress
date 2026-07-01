package org.agmas.noellesroles.game.roles.innocence.child;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.event.AllowPlayerPunching;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import org.agmas.noellesroles.role.ModRoles;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


//熊孩子攻击类
public class ChildPunchHandler {

    private static final Map<java.util.UUID, PunchMarker> RECENT_CHILD_PUNCHES = new ConcurrentHashMap<>();
    private static final long WINDOW_TICKS = 5L;

    public static void register() {
        AllowPlayerPunching.EVENT.register(player -> {
            var gw = SREGameWorldComponent.KEY.get(player.level());
            return gw.isRole(player, ModRoles.CHILD) && player.getMainHandItem().isEmpty();
        });

        AttackEntityCallback.EVENT.register(ChildPunchHandler::onEntityDamaged);
        AllowPlayerDeathWithKiller.EVENT.register(ChildPunchHandler::onAllowPlayerDeathWithKiller);
    }

    private static InteractionResult onEntityDamaged(
            Player attacker,
            Level level,
            InteractionHand hand,
            Entity entity,
            EntityHitResult hitResult) {

        if (SRE.isLobby) return InteractionResult.PASS;
        if (!(entity instanceof Player victim)) return InteractionResult.PASS;
        if (!GameUtils.isPlayerAliveAndSurvival(attacker)) return InteractionResult.PASS;
        if (!GameUtils.isPlayerAliveAndSurvival(victim)) return InteractionResult.PASS;

        var gw = SREGameWorldComponent.KEY.get(level);
        if (!gw.isRole(attacker, ModRoles.CHILD)) return InteractionResult.PASS;

        if (attacker.getUUID().equals(victim.getUUID())) return InteractionResult.PASS;

        if (!level.isClientSide) {
            RECENT_CHILD_PUNCHES.put(victim.getUUID(), new PunchMarker(attacker.getUUID(), level.getGameTime()));
        }

        return level.isClientSide ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    private static boolean onAllowPlayerDeathWithKiller(Player player, Player killer, net.minecraft.resources.ResourceLocation deathReason) {
        if (player == null || killer == null) return true;

        var gw = SREGameWorldComponent.KEY.get(player.level());
        if (!gw.isRole(killer, ModRoles.CHILD)) return true;

        PunchMarker marker = RECENT_CHILD_PUNCHES.get(player.getUUID());
        if (marker == null) return true;

        long now = player.level().getGameTime();
        if (!marker.attacker.equals(killer.getUUID())) return true;
        if (now - marker.time > WINDOW_TICKS) {
            RECENT_CHILD_PUNCHES.remove(player.getUUID());
            return true;
        }

        RECENT_CHILD_PUNCHES.remove(player.getUUID());
        return false;
    }

    private static class PunchMarker {
        public final java.util.UUID attacker;
        public final long time;

        public PunchMarker(java.util.UUID attacker, long time) {
            this.attacker = attacker;
            this.time = time;
        }
    }
}