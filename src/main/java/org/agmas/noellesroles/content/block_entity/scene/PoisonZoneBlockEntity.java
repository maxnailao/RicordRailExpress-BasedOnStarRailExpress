package org.agmas.noellesroles.content.block_entity.scene;

import java.util.List;

import org.agmas.noellesroles.init.ModSceneBlocks;
import org.agmas.noellesroles.scene.SceneEventManager;
import org.agmas.noellesroles.scene.SceneParticles;

import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * 有毒区域方块实体：每 tick 统计驻留玩家，连续停留达 {@link #DEATH_TICKS} 即死亡。
 */
public class PoisonZoneBlockEntity extends BlockEntity {

    /** 20 秒 = 400 tick。 */
    public static final int DEATH_TICKS = 400;
    private static final String CHANNEL = "poison";

    public PoisonZoneBlockEntity(BlockPos pos, BlockState state) {
        super(ModSceneBlocks.POISON_ZONE_ENTITY, pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PoisonZoneBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        AABB box = SceneParticles.sceneRegion(pos);
        // 区域内毒气粒子（覆盖 3×3×4 范围）
        if (serverLevel.getGameTime() % 2 == 0) {
            SceneParticles.regionScatter(serverLevel, box, TMMParticles.POISON, 4);
        }
        List<Player> players = serverLevel.getEntitiesOfClass(Player.class, box,
                p -> p.isAlive() && !p.isCreative() && !p.isSpectator());
        for (Player player : players) {
            int dwell = SceneEventManager.reportDwell(serverLevel, player, CHANNEL);
            if (dwell >= DEATH_TICKS) {
                SceneParticles.burst(serverLevel, player.position().add(0, 1, 0), TMMParticles.POISON,
                        30, 0.5, 0.05);
                SceneEventManager.resetDwell(serverLevel, player, CHANNEL);
                GameUtils.killPlayer(player, true, null, GameConstants.DeathReasons.POISON);
            } else if (dwell % 20 == 0) {
                // 每秒一次轻微的中毒提示粒子
                SceneParticles.burst(serverLevel, player.position().add(0, 1, 0), TMMParticles.POISON,
                        4, 0.3, 0.02);
            }
        }
    }
}
