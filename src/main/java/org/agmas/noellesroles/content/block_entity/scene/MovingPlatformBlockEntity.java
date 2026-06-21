package org.agmas.noellesroles.content.block_entity.scene;

import java.util.UUID;

import org.agmas.noellesroles.content.block.scene.MovingPlatformBlock;
import org.agmas.noellesroles.content.entity.MovingPlatformEntity;
import org.agmas.noellesroles.init.ModEntities;
import org.agmas.noellesroles.init.ModSceneBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * 移动方块底座方块实体：确保其管理的移动平台实体始终存在（按 UUID 跟踪），底座移除时清除平台。
 */
public class MovingPlatformBlockEntity extends BlockEntity {

    private UUID childUuid;

    public MovingPlatformBlockEntity(BlockPos pos, BlockState state) {
        super(ModSceneBlocks.MOVING_PLATFORM_ENTITY, pos, state);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (childUuid != null) {
            tag.putUUID("Child", childUuid);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID("Child")) {
            childUuid = tag.getUUID("Child");
        }
    }

    @Override
    public void setRemoved() {
        if (this.level instanceof ServerLevel serverLevel && childUuid != null) {
            Entity child = serverLevel.getEntity(childUuid);
            if (child != null) {
                child.discard();
            }
        }
        super.setRemoved();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, MovingPlatformBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel) || serverLevel.getGameTime() % 20 != 0) {
            return;
        }
        Entity child = be.childUuid == null ? null : serverLevel.getEntity(be.childUuid);
        if (child == null || !child.isAlive()) {
            Direction dir = state.getValue(MovingPlatformBlock.FACING);
            MovingPlatformEntity platform = new MovingPlatformEntity(ModEntities.MOVING_PLATFORM, serverLevel);
            Vec3 home = new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
            platform.setup(home, dir, MovingPlatformBlock.TRAVEL_DISTANCE);
            serverLevel.addFreshEntity(platform);
            be.childUuid = platform.getUUID();
            be.setChanged();
        }
    }
}
