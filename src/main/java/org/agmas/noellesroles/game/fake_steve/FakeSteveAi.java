package org.agmas.noellesroles.game.fake_steve;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** 怯懦专用的轻量 Fake Steve 逃离 AI。 */
public final class FakeSteveAi {
    private FakeSteveAi() {
    }

    public static void tickFlee(ServerLevel level, ServerPlayer player,
            FakeSteveAgentState state, BlockPos awayFrom) {
        long now = level.getGameTime();
        if (state.startedTick == 0L) state.startedTick = now;
        if (state.pathGoal == null || now >= state.nextDecisionTick
                || player.blockPosition().closerThan(state.pathGoal, 2.0D)) {
            state.pathGoal = findFleeGoal(level, player, awayFrom);
            state.nextDecisionTick = now + 30L;
        }
        if (state.pathGoal == null) {
            player.setDeltaMovement(0.0D, player.getDeltaMovement().y, 0.0D);
            return;
        }
        Vec3 delta = Vec3.atBottomCenterOf(state.pathGoal).subtract(player.position());
        Vec3 horizontal = new Vec3(delta.x, 0.0D, delta.z);
        if (horizontal.lengthSqr() < 0.04D) return;
        Vec3 direction = horizontal.normalize();
        if (!level.noCollision(player, player.getBoundingBox().move(direction.scale(1.0D)))) {
            direction = new Vec3(-direction.z, 0.0D, direction.x);
            if (!level.noCollision(player, player.getBoundingBox().move(direction.scale(1.0D)))) {
                direction = direction.scale(-1.0D);
            }
        }
        player.setSprinting(true);
        player.setDeltaMovement(direction.x * 0.30D, player.getDeltaMovement().y, direction.z * 0.30D);
        float yaw = (float) (Mth.atan2(-direction.x, direction.z) * Mth.RAD_TO_DEG);
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
    }

    public static boolean hasReachedFleeGoal(ServerPlayer player, FakeSteveAgentState state) {
        return player != null && state != null && state.pathGoal != null
                && player.blockPosition().closerThan(state.pathGoal, 2.0D);
    }

    private static BlockPos findFleeGoal(ServerLevel level, ServerPlayer player, BlockPos awayFrom) {
        Vec3 origin = player.position();
        Vec3 away = origin.subtract(Vec3.atCenterOf(awayFrom == null ? player.blockPosition() : awayFrom));
        if (away.horizontalDistanceSqr() < 0.01D) away = new Vec3(1.0D, 0.0D, 0.0D);
        away = new Vec3(away.x, 0.0D, away.z).normalize();
        for (int distance : new int[] {28, 20, 14, 8}) {
            for (int attempt = 0; attempt < 8; attempt++) {
                double angle = (level.getRandom().nextDouble() - 0.5D) * 1.2D;
                double cos = Math.cos(angle);
                double sin = Math.sin(angle);
                Vec3 direction = new Vec3(away.x * cos - away.z * sin, 0.0D,
                        away.x * sin + away.z * cos);
                BlockPos candidate = BlockPos.containing(origin.add(direction.scale(distance)));
                if (FakeSteveNavigator.safeStand(level, player, candidate)) return candidate.immutable();
            }
        }
        return null;
    }
}
