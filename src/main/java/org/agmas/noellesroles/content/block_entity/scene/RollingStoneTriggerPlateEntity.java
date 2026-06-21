package org.agmas.noellesroles.content.block_entity.scene;

import org.agmas.noellesroles.content.block.scene.RollingStoneTriggerPlate;
import org.agmas.noellesroles.init.ModSceneBlocks;
import org.agmas.noellesroles.scene.SceneEventManager;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 滚石触发板方块实体：踩踏触发冷却 + 破坏任务激活时周期性召唤滚石。
 */
public class RollingStoneTriggerPlateEntity extends BlockEntity {

    /** 踩踏触发冷却。 */
    public static final int STEP_COOLDOWN = 40;
    /** 破坏任务期间召唤间隔。 */
    public static final int SABOTAGE_INTERVAL = 100;

    private long lastTrigger = Long.MIN_VALUE;

    public RollingStoneTriggerPlateEntity(BlockPos pos, BlockState state) {
        super(ModSceneBlocks.ROLLING_STONE_TRIGGER_ENTITY, pos, state);
    }

    /** 踩踏尝试触发，受冷却限制。 */
    public boolean tryTrigger(ServerLevel level) {
        long now = level.getGameTime();
        if (now - lastTrigger < STEP_COOLDOWN) {
            return false;
        }
        lastTrigger = now;
        return true;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RollingStoneTriggerPlateEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (SceneEventManager.isSabotageActive(serverLevel)
                && serverLevel.getGameTime() % SABOTAGE_INTERVAL == 0) {
            RollingStoneTriggerPlate.spawnStone(serverLevel, pos, state.getValue(RollingStoneTriggerPlate.FACING));
        }
    }
}
