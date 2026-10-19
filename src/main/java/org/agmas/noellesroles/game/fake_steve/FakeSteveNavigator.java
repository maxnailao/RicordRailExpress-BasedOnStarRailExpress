package org.agmas.noellesroles.game.fake_steve;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** 为怯懦逃离选择可站立位置，并避免把玩家送进墙体。 */
public final class FakeSteveNavigator {
    private FakeSteveNavigator() {
    }

    public static boolean safeStand(ServerLevel level, ServerPlayer player, BlockPos pos) {
        if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                || !level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) {
            return false;
        }
        if (level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty()) {
            return false;
        }
        return level.noCollision(player, player.getBoundingBox().move(
                pos.getX() + 0.5D - player.getX(), pos.getY() - player.getY(),
                pos.getZ() + 0.5D - player.getZ()));
    }
}
