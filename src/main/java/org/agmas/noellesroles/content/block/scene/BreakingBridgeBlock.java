package org.agmas.noellesroles.content.block.scene;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 断桥方块：玩家踩过后短暂延迟即断裂（失去碰撞、不可见），一段时间后自动恢复。
 * 使用原版木板贴图。
 */
public class BreakingBridgeBlock extends Block {

    public static final BooleanProperty BROKEN = BooleanProperty.create("broken");

    /** 踩上到断裂的延迟。 */
    public static final int BREAK_DELAY = 10;
    /** 断裂到恢复的延迟。 */
    public static final int RECOVER_DELAY = 80;

    public BreakingBridgeBlock(Properties settings) {
        super(settings);
        this.registerDefaultState(this.stateDefinition.any().setValue(BROKEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BROKEN);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return state.getValue(BROKEN) ? Shapes.empty() : Shapes.block();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return state.getValue(BROKEN) ? Shapes.empty() : Shapes.block();
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (!level.isClientSide && !state.getValue(BROKEN) && entity instanceof Player
                && level instanceof ServerLevel serverLevel
                && !serverLevel.getBlockTicks().hasScheduledTick(pos, this)) {
            // 即将断裂提示
            serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                    pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 6, 0.3, 0.05, 0.3, 0.0);
            serverLevel.playSound(null, pos, SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 0.8F, 0.7F);
            serverLevel.scheduleTick(pos, this, BREAK_DELAY);
        }
        super.stepOn(level, pos, state, entity);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(BROKEN)) {
            // 断裂
            level.setBlock(pos, state.setValue(BROKEN, true), Block.UPDATE_ALL);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 25, 0.4, 0.4, 0.4, 0.1);
            level.playSound(null, pos, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1.0F, 0.9F);
            level.scheduleTick(pos, this, RECOVER_DELAY);
        } else {
            // 恢复
            level.setBlock(pos, state.setValue(BROKEN, false), Block.UPDATE_ALL);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_PLANKS.defaultBlockState()),
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 10, 0.3, 0.3, 0.3, 0.0);
            level.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.7F, 1.1F);
        }
    }
}
