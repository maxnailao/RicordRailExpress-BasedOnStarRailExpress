package org.agmas.noellesroles.content.block.scene;

import com.mojang.serialization.MapCodec;

import org.agmas.noellesroles.client.screen.MovingPlatformConfigScreen;
import org.agmas.noellesroles.content.block_entity.scene.MovingPlatformBlockEntity;
import org.agmas.noellesroles.init.ModSceneBlocks;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * 移动方块（底座）：放置后在其上方生成一个移动平台实体，沿 FACING 方向往返移动，玩家可站立被带动。
 * 创造模式右键打开配置GUI。底座使用原版平滑石头贴图。
 * 距离/速度/碰撞箱通过方块实体NBT配置。
 */
public class MovingPlatformBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final int DEFAULT_DISTANCE = 5;
    public static final int MAX_DISTANCE = 50;

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

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (level.isClientSide && player.isCreative()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MovingPlatformBlockEntity mbe) {
                Minecraft.getInstance().setScreen(new MovingPlatformConfigScreen(
                        pos, mbe.getDistance(), mbe.getSpeed(), mbe.getCollisionSize()));
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
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
