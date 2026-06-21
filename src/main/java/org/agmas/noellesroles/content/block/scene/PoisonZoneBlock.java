package org.agmas.noellesroles.content.block.scene;

import com.mojang.serialization.MapCodec;

import org.agmas.noellesroles.content.block_entity.scene.PoisonZoneBlockEntity;
import org.agmas.noellesroles.init.ModSceneBlocks;

import io.wifi.starrailexpress.index.TMMParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 有毒区域：玩家在区域内连续停留 20 秒死亡，离开时停留计时重置。
 * 半透明绿色方块（原版史莱姆块贴图），并持续散发毒气粒子。无碰撞，玩家可穿行。
 */
public class PoisonZoneBlock extends BaseEntityBlock {

    public PoisonZoneBlock(Properties settings) {
        super(settings.noOcclusion().noCollission());
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return null;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(2) == 0) {
            double x = pos.getX() + random.nextDouble();
            double y = pos.getY() + random.nextDouble();
            double z = pos.getZ() + random.nextDouble();
            level.addParticle(TMMParticles.POISON, x, y, z, 0.0, 0.01, 0.0);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PoisonZoneBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state,
            BlockEntityType<T> type) {
        if (world.isClientSide) {
            return null;
        }
        return createTickerHelper(type, ModSceneBlocks.POISON_ZONE_ENTITY,
                (lvl, pos, s, be) -> PoisonZoneBlockEntity.serverTick(lvl, pos, s, be));
    }
}
