package org.agmas.noellesroles.content.block.scene;

import com.mojang.serialization.MapCodec;

import org.agmas.noellesroles.content.block_entity.scene.MovingPlatformBlockEntity;
import org.agmas.noellesroles.init.ModSceneBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import org.jetbrains.annotations.Nullable;

/**
 * 移动方块（底座）：放置后在其上方生成一个移动平台实体，沿 FACING 方向往返 5 格，玩家可站立被带动。
 * 底座使用原版平滑石头贴图。
 */
public class MovingPlatformBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final double TRAVEL_DISTANCE = 5.0;

    public MovingPlatformBlock(Properties settings) {
        super(settings);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
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
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MovingPlatformBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state,
            BlockEntityType<T> type) {
        if (world.isClientSide) {
            return null;
        }
        return createTickerHelper(type, ModSceneBlocks.MOVING_PLATFORM_ENTITY,
                (lvl, pos, s, be) -> MovingPlatformBlockEntity.serverTick(lvl, pos, s, be));
    }
}
