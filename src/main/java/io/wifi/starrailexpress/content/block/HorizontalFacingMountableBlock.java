package io.wifi.starrailexpress.content.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public abstract class HorizontalFacingMountableBlock extends MountableBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public static final EnumProperty<PartType> PART = EnumProperty.create("part", PartType.class);

    public HorizontalFacingMountableBlock(Properties settings) {
        super(settings);
    }

    public final BlockState getDefaultBlockState() {
        return defaultBlockState().setValue(FACING, Direction.NORTH)
                .setValue(PART, PartType.CENTER);
    }

    @Override
    public Vec3 getSitPos(Level world, BlockState state, BlockPos pos) {
        Vec3 sitPos = this.getNorthFacingSitPos(world, state, pos);
        return switch (state.getValue(FACING)) {
            case EAST -> new Vec3(sitPos.z, sitPos.y, 1 - sitPos.x);
            case SOUTH -> new Vec3(1 - sitPos.x, sitPos.y, 1 - sitPos.z);
            case WEST -> new Vec3(1 - sitPos.z, sitPos.y, sitPos.x);
            default -> sitPos;
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    
    public final void registerDefaultStateMirrored(BlockState blockState) {
        this.registerDefaultState(blockState);
    }

    public static enum PartType implements StringRepresentable {
        LEFT("left"),
        CENTER("center"),
        RIGHT("right");

        private final String name;

        PartType(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }

    public abstract Vec3 getNorthFacingSitPos(Level world, BlockState state, BlockPos pos);

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

}
