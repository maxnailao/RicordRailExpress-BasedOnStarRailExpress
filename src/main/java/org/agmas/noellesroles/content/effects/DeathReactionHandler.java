package org.agmas.noellesroles.content.effects;

import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.event.OnPlayerDeath;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.agmas.harpymodloader.events.GameInitializeEvent;
import org.agmas.noellesroles.game.fake_steve.CowardiceFakeSteveControl;
import org.agmas.noellesroles.init.ModEffects;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 胆小鬼、怯懦、暴怒在死亡事件上的完整反应。 */
public final class DeathReactionHandler {
    public static final double FRONT_RANGE = 24.0D;
    public static final double FRONT_COSINE = 0.4D;
    public static final double NEARBY_RANGE = 16.0D;
    public static final int RAGE_SURGE_TICKS = 100;
    public static final int RAGE_SPEED_AMPLIFIER = 1;
    public static final int COWARD_FEAR_COOLDOWN_TICKS = 120 * 20;
    private static final Map<UUID, UUID> RAGE_LOCK = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> COWARD_FEAR_READY_AT = new ConcurrentHashMap<>();
    private static boolean registered;

    private DeathReactionHandler() {
    }

    public static void register() {
        if (registered) return;
        registered = true;
        OnPlayerDeath.EVENT.register(DeathReactionHandler::onPlayerDeath);
        ServerTickEvents.END_WORLD_TICK.register(DeathReactionHandler::tickWorld);
        OnGameEnd.EVENT.register((level, game) -> clear());
        GameInitializeEvent.EVENT.register((level, game, players) -> clear());
    }

    public static void clear() {
        RAGE_LOCK.clear();
        COWARD_FEAR_READY_AT.clear();
        CowardiceFakeSteveControl.clear();
    }

    private static void onPlayerDeath(Player victim, net.minecraft.resources.ResourceLocation deathReason) {
        if (!(victim.level() instanceof ServerLevel level)) return;
        for (ServerPlayer observer : level.players()) {
            if (observer.getUUID().equals(victim.getUUID()) || !GameUtils.isPlayerAliveAndSurvival(observer)) continue;
            if (observer.hasEffect(ModEffects.COWARD) && isInFront(observer, victim)
                    && tryTriggerCowardFear(observer)) applyFear(observer);
            if (observer.distanceToSqr(victim) > NEARBY_RANGE * NEARBY_RANGE) continue;
            if (observer.hasEffect(ModEffects.RAGE)) applyRageSurge(observer);
            if (observer.hasEffect(ModEffects.COWARDICE)) {
                CowardiceFakeSteveControl.tryStart(observer, victim.blockPosition());
            }
        }
    }

    private static void tickWorld(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            CowardiceFakeSteveControl.tick(player);
            tickRageLook(player);
        }
    }

    private static boolean tryTriggerCowardFear(ServerPlayer player) {
        long ticks = GameUtils.getTicksFromGameStart(player.level());
        if (ticks <= 0L) return true;
        Long readyAt = COWARD_FEAR_READY_AT.get(player.getUUID());
        if (readyAt != null && ticks < readyAt) return false;
        COWARD_FEAR_READY_AT.put(player.getUUID(), ticks + COWARD_FEAR_COOLDOWN_TICKS);
        return true;
    }

    private static void applyFear(ServerPlayer player) {
        if (player.hasEffect(ModEffects.FEAR)) return;
        player.addEffect(ModEffects.of(ModEffects.FEAR, FearEffects.DURATION_TICKS, 0, false, false, true));
        player.addEffect(ModEffects.of(ModEffects.MOVE_BANED, FearEffects.DURATION_TICKS, 0, false, false, false));
    }

    private static void applyRageSurge(ServerPlayer player) {
        player.addEffect(ModEffects.of(ModEffects.RAGE_SURGE, RAGE_SURGE_TICKS, 0, false, false, false));
        player.addEffect(ModEffects.of(ModEffects.TURN_BANED, RAGE_SURGE_TICKS, 0, false, false, false));
        player.addEffect(ModEffects.of(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED,
                RAGE_SURGE_TICKS, RAGE_SPEED_AMPLIFIER, false, false, true));
        ServerPlayer target = findNearest(player, null);
        if (target == null) RAGE_LOCK.remove(player.getUUID());
        else RAGE_LOCK.put(player.getUUID(), target.getUUID());
    }

    private static void tickRageLook(ServerPlayer player) {
        if (!player.hasEffect(ModEffects.RAGE_SURGE) || !GameUtils.isPlayerAliveAndSurvival(player)) {
            RAGE_LOCK.remove(player.getUUID());
            return;
        }
        ServerPlayer target = findNearest(player, RAGE_LOCK.get(player.getUUID()));
        if (target == null) {
            RAGE_LOCK.remove(player.getUUID());
            return;
        }
        RAGE_LOCK.put(player.getUUID(), target.getUUID());
        player.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
    }

    private static ServerPlayer findNearest(ServerPlayer observer, UUID preferredId) {
        if (preferredId != null) {
            Player preferred = observer.serverLevel().getPlayerByUUID(preferredId);
            if (preferred instanceof ServerPlayer candidate && GameUtils.isPlayerAliveAndSurvival(candidate)
                    && !candidate.getUUID().equals(observer.getUUID())) return candidate;
        }
        ServerPlayer best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ServerPlayer other : observer.serverLevel().players()) {
            if (other.getUUID().equals(observer.getUUID()) || !GameUtils.isPlayerAliveAndSurvival(other)) continue;
            double distance = observer.distanceToSqr(other);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = other;
            }
        }
        return best;
    }

    private static boolean isInFront(ServerPlayer observer, Player victim) {
        if (observer.distanceToSqr(victim) > FRONT_RANGE * FRONT_RANGE) return false;
        Vec3 direction = victim.getEyePosition().subtract(observer.getEyePosition());
        if (direction.lengthSqr() < 1.0E-4D) return true;
        return observer.getLookAngle().normalize().dot(direction.normalize()) >= FRONT_COSINE;
    }
}
