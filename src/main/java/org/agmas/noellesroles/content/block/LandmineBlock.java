package org.agmas.noellesroles.content.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.agmas.noellesroles.content.block_entity.LandmineBlockEntity;
import org.agmas.noellesroles.init.ModBlocks;
import org.jetbrains.annotations.Nullable;

/**
 * 反人员地雷方块
 * - 由地雷物品长按右键布设，无碰撞体积，玩家可直接走过
 * - 逻辑由 {@link LandmineBlockEntity} 驱动
 */
public class LandmineBlock extends BaseEntityBlock {
    /** 地雷模型的碰撞/选中形状（与模型范围一致） */
    private static final VoxelShape SHAPE = Block.box(3.3D, 0.0D, 3.3D, 12.7D, 2.3D, 12.7D);

    public LandmineBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return null;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LandmineBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type, ModBlocks.LANDMINE_BLOCK_ENTITY, LandmineBlockEntity::tick);
    }
}
