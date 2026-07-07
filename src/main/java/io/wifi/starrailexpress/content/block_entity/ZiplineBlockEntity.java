package io.wifi.starrailexpress.content.block_entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ZiplineBlockEntity extends BlockEntity {
    private final Set<BlockPos> connectedPositions = new HashSet<>();

    public ZiplineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public Set<BlockPos> getConnectedPositions() {
        return Collections.unmodifiableSet(connectedPositions);
    }

    public void addConnection(BlockPos pos) {
        if (connectedPositions.add(pos)) {
            setChanged();
            sync();
        }
    }

    public void removeConnection(BlockPos pos) {
        if (connectedPositions.remove(pos)) {
            setChanged();
            sync();
        }
    }

    public void clearConnections() {
        if (!connectedPositions.isEmpty()) {
            connectedPositions.clear();
            setChanged();
            sync();
        }
    }

    public boolean hasConnection(BlockPos pos) {
        return connectedPositions.contains(pos);
    }

    public boolean hasAnyConnection() {
        return !connectedPositions.isEmpty();
    }

    @Nullable
    public BlockPos getNearestConnection(BlockPos from) {
        BlockPos nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (BlockPos connected : connectedPositions) {
            double dist = connected.distSqr(from);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = connected;
            }
        }
        return nearest;
    }

    private void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag list = new ListTag();
        for (BlockPos pos : connectedPositions) {
            CompoundTag posTag = new CompoundTag();
            posTag.putInt("X", pos.getX());
            posTag.putInt("Y", pos.getY());
            posTag.putInt("Z", pos.getZ());
            list.add(posTag);
        }
        tag.put("ConnectedPositions", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        connectedPositions.clear();
        ListTag list = tag.getList("ConnectedPositions", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag posTag = list.getCompound(i);
            connectedPositions.add(new BlockPos(
                    posTag.getInt("X"),
                    posTag.getInt("Y"),
                    posTag.getInt("Z")));
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ListTag list = new ListTag();
        for (BlockPos pos : connectedPositions) {
            CompoundTag posTag = new CompoundTag();
            posTag.putInt("X", pos.getX());
            posTag.putInt("Y", pos.getY());
            posTag.putInt("Z", pos.getZ());
            list.add(posTag);
        }
        tag.put("ConnectedPositions", list);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
