package org.agmas.noellesroles.game.fake_steve;

import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.agmas.noellesroles.init.ModEffects;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 怯懦：每局第一次看到附近死亡后，短暂由 Fake Steve 接管逃跑。 */
public final class CowardiceFakeSteveControl {
    private static final int CONTROL_EFFECT_TICKS = 30;
    private static final int MAX_FLEE_TICKS = 160;
    private static final int SPEED_AMPLIFIER = 3;
    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    private static final Set<UUID> USED_THIS_GAME = ConcurrentHashMap.newKeySet();

    private CowardiceFakeSteveControl() {
    }

    public static boolean isControlling(UUID playerId) { return SESSIONS.containsKey(playerId); }

    public static void tryStart(ServerPlayer player, BlockPos awayFrom) {
        if (player == null || awayFrom == null) return;
        UUID id = player.getUUID();
        if (USED_THIS_GAME.contains(id) || SESSIONS.containsKey(id)) return;
        FakeSteveAgentState state = new FakeSteveAgentState();
        SESSIONS.put(id, new Session(state, awayFrom.immutable(), player.level().getGameTime()));
        USED_THIS_GAME.add(id);
        player.addEffect(ModEffects.of(ModEffects.NO_COLLIDE, MAX_FLEE_TICKS, 0, false, false, false));
        player.addEffect(ModEffects.of(MobEffects.MOVEMENT_SPEED, MAX_FLEE_TICKS,
                SPEED_AMPLIFIER, false, false, true));
        applyControl(player);
    }

    public static void tick(ServerPlayer player) {
        if (player == null) return;
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) return;
        if (!GameUtils.isPlayerAliveAndSurvival(player)
                || player.level().getGameTime() - session.startedTick >= MAX_FLEE_TICKS) {
            stop(player);
            return;
        }
        applyControl(player);
        FakeSteveAi.tickFlee(player.serverLevel(), player, session.state, session.awayFrom);
        if (FakeSteveAi.hasReachedFleeGoal(player, session.state)) stop(player);
    }

    public static void stop(ServerPlayer player) {
        if (player == null) return;
        Session session = SESSIONS.remove(player.getUUID());
        if (session != null) session.state.clear();
        removeControl(player);
        removeIfOurs(player, ModEffects.NO_COLLIDE, MAX_FLEE_TICKS);
        removeIfOurs(player, MobEffects.MOVEMENT_SPEED, MAX_FLEE_TICKS);
    }

    public static void clear() {
        SESSIONS.clear();
        USED_THIS_GAME.clear();
    }

    private static void applyControl(ServerPlayer player) {
        addControl(player, ModEffects.MOVE_BANED);
        addControl(player, ModEffects.TURN_BANED);
        addControl(player, ModEffects.USED_BANED);
        addControl(player, ModEffects.INVENTORY_BANED);
        addControl(player, ModEffects.SKILL_BANED);
    }

    private static void addControl(ServerPlayer player, Holder<MobEffect> effect) {
        player.addEffect(new MobEffectInstance(effect, CONTROL_EFFECT_TICKS, 0, false, false, false));
    }

    private static void removeControl(ServerPlayer player) {
        removeIfOurs(player, ModEffects.MOVE_BANED, CONTROL_EFFECT_TICKS);
        removeIfOurs(player, ModEffects.TURN_BANED, CONTROL_EFFECT_TICKS);
        removeIfOurs(player, ModEffects.USED_BANED, CONTROL_EFFECT_TICKS);
        removeIfOurs(player, ModEffects.INVENTORY_BANED, CONTROL_EFFECT_TICKS);
        removeIfOurs(player, ModEffects.SKILL_BANED, CONTROL_EFFECT_TICKS);
    }

    private static void removeIfOurs(ServerPlayer player, Holder<MobEffect> effect, int maxDuration) {
        MobEffectInstance instance = player.getEffect(effect);
        if (instance != null && !instance.isInfiniteDuration()
                && instance.getDuration() > 0 && instance.getDuration() <= maxDuration) {
            player.removeEffect(effect);
        }
    }

    private record Session(FakeSteveAgentState state, BlockPos awayFrom, long startedTick) {}
}
