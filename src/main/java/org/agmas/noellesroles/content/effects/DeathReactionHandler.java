package org.agmas.noellesroles.content.effects;

import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.event.OnPlayerDeath;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.agmas.harpymodloader.events.GameInitializeEvent;
import org.agmas.noellesroles.init.ModEffects;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 怯懦、胆小鬼、暴怒在死亡事件上的实际反应。 */
public final class DeathReactionHandler {
    private static final double FRONT_RANGE = 24.0D;
    private static final double FRONT_COSINE = 0.4D;
    private static final double NEARBY_RANGE = 16.0D;
    private static final int FEAR_TICKS = 80;
    private static final int RAGE_TICKS = 100;
    private static final int COWARDICE_ESCAPE_TICKS = 100;
    private static final int COWARD_FEAR_COOLDOWN_TICKS = 120 * 20;

    private static final Map<UUID, UUID> rageTargets = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> fearReadyAt = new ConcurrentHashMap<>();
    private static final Map<UUID, EscapeState> escapes = new ConcurrentHashMap<>();
    private static boolean registered;

    private record EscapeState(Vec3 from, int remaining) {
    }

    private DeathReactionHandler() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        OnPlayerDeath.EVENT.register(DeathReactionHandler::onPlayerDeath);
        ServerTickEvents.END_WORLD_TICK.register(DeathReactionHandler::tickWorld);
        OnGameEnd.EVENT.register((level, game) -> clear());
        GameInitializeEvent.EVENT.register((level, game, players) -> clear());
    }

    public static void clear() {
        rageTargets.clear();
        fearReadyAt.clear();
        escapes.clear();
    }

    private static void onPlayerDeath(Player victim, net.minecraft.resources.ResourceLocation reason) {
        if (!(victim.level() instanceof ServerLevel level)) {
            return;
        }
        for (ServerPlayer observer : level.players()) {
            if (observer.getUUID().equals(victim.getUUID()) || !GameUtils.isPlayerAliveAndSurvival(observer)) {
                continue;
            }
            if (observer.hasEffect(ModEffects.COWARD)
                    && isInFront(observer, victim)
                    && tryTriggerFear(observer)) {
                observer.addEffect(new MobEffectInstance(ModEffects.FEAR, FEAR_TICKS, 0, false, false, true));
                observer.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, FEAR_TICKS, 0, false, false, false));
            }
            if (observer.distanceToSqr(victim) <= NEARBY_RANGE * NEARBY_RANGE) {
                if (observer.hasEffect(ModEffects.RAGE)) {
                    observer.addEffect(new MobEffectInstance(ModEffects.RAGE_SURGE, RAGE_TICKS, 0, false, false, false));
                    observer.addEffect(new MobEffectInstance(ModEffects.TURN_BANED, RAGE_TICKS, 0, false, false, false));
                    observer.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, RAGE_TICKS, 1, false, false, true));
                    ServerPlayer target = nearest(observer);
                    if (target != null) {
                        rageTargets.put(observer.getUUID(), target.getUUID());
                    }
                }
                if (observer.hasEffect(ModEffects.COWARDICE)) {
                    escapes.put(observer.getUUID(), new EscapeState(victim.position(), COWARDICE_ESCAPE_TICKS));
                    observer.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,
                            COWARDICE_ESCAPE_TICKS, 1, false, false, true));
                }
            }
        }
    }

    private static boolean tryTriggerFear(ServerPlayer player) {
        long now = GameUtils.getTicksFromGameStart(player.level());
        Long ready = fearReadyAt.get(player.getUUID());
        if (now > 0L && ready != null && now < ready) {
            return false;
        }
        if (now > 0L) {
            fearReadyAt.put(player.getUUID(), now + COWARD_FEAR_COOLDOWN_TICKS);
        }
        return true;
    }

    private static void tickWorld(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            if (player.hasEffect(ModEffects.RAGE_SURGE)) {
                ServerPlayer target = nearest(player, rageTargets.get(player.getUUID()));
                if (target != null) {
                    rageTargets.put(player.getUUID(), target.getUUID());
                    player.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
                }
            } else {
                rageTargets.remove(player.getUUID());
            }

            EscapeState state = escapes.get(player.getUUID());
            if (state == null) {
                continue;
            }
            if (!GameUtils.isPlayerAliveAndSurvival(player) || state.remaining() <= 0) {
                escapes.remove(player.getUUID());
                continue;
            }
            Vec3 away = player.position().subtract(state.from());
            if (away.lengthSqr() < 1.0E-4D) {
                away = player.getLookAngle().reverse();
            }
            away = away.normalize();
            player.setSprinting(true);
            player.setDeltaMovement(away.x * 0.28D, player.getDeltaMovement().y, away.z * 0.28D);
            escapes.put(player.getUUID(), new EscapeState(state.from(), state.remaining() - 1));
        }
    }

    private static ServerPlayer nearest(ServerPlayer observer) {
        return nearest(observer, null);
    }

    private static ServerPlayer nearest(ServerPlayer observer, UUID preferred) {
        if (preferred != null) {
            Player p = observer.serverLevel().getPlayerByUUID(preferred);
            if (p instanceof ServerPlayer sp && GameUtils.isPlayerAliveAndSurvival(sp)
                    && !sp.getUUID().equals(observer.getUUID())) {
                return sp;
            }
        }
        ServerPlayer best = null;
        double distance = Double.MAX_VALUE;
        for (ServerPlayer other : observer.serverLevel().players()) {
            if (other.getUUID().equals(observer.getUUID()) || !GameUtils.isPlayerAliveAndSurvival(other)) {
                continue;
            }
            double current = observer.distanceToSqr(other);
            if (current < distance) {
                distance = current;
                best = other;
            }
        }
        return best;
    }

    private static boolean isInFront(ServerPlayer observer, Player victim) {
        if (observer.distanceToSqr(victim) > FRONT_RANGE * FRONT_RANGE) {
            return false;
        }
        Vec3 direction = victim.getEyePosition().subtract(observer.getEyePosition());
        return direction.lengthSqr() < 1.0E-4D
                || observer.getLookAngle().normalize().dot(direction.normalize()) >= FRONT_COSINE;
    }
}
