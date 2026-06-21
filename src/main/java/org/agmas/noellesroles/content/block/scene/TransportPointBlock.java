package org.agmas.noellesroles.content.block.scene;

import org.agmas.noellesroles.scene.SceneTaskManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 运输点（场景任务「运输点任务」）：在起点右键取货，在终点（end=true）右键交货完成。原版木桶贴图。
 */
public class TransportPointBlock extends Block {

    public static final BooleanProperty END = BooleanProperty.create("end");

    public TransportPointBlock(Properties settings) {
        super(settings);
        this.registerDefaultState(this.stateDefinition.any().setValue(END, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(END);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer sp) {
            if (state.getValue(END)) {
                SceneTaskManager.reportTransportDeliver(sp);
            } else {
                SceneTaskManager.reportTransportPickup(sp);
            }
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1.0,
                        pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.0);
                serverLevel.playSound(null, pos, SoundEvents.BARREL_OPEN, SoundSource.BLOCKS, 0.8F, 1.0F);
            }
        }
        return InteractionResult.CONSUME;
    }
}
