package io.wifi.starrailexpress.content.block_entity;

import io.wifi.starrailexpress.content.block.SmallDoorBlock;
import io.wifi.starrailexpress.game.GameConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

public class PlaneSmallDoorBlockEntity extends SmallDoorBlockEntity {

    public PlaneSmallDoorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    protected void toggleBlocks() {
        if (this.level == null) {
            return;
        }
        this.level.setBlock(this.worldPosition, this.getBlockState().setValue(SmallDoorBlock.OPEN, this.open),
                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        this.level.setBlock(this.worldPosition.above(), this.getBlockState().setValue(SmallDoorBlock.OPEN, this.open)
                .setValue(SmallDoorBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    @Override
    protected void toggleOpen() {
        toggleOpen(GameConstants.DOOR_AUTOCLOSE_TIME);
    }

    @Override
    protected void toggleOpen(int ticks) {
        super.toggleOpen(ticks);
    }
}
