package org.agmas.noellesroles.content.block_entity.scene;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.agmas.noellesroles.content.block.scene.ReactorBlock;
import org.agmas.noellesroles.init.ModSceneBlocks;
import org.agmas.noellesroles.scene.ReactorRegistry;
import org.agmas.noellesroles.scene.SceneEventManager;
import org.jetbrains.annotations.Nullable;

/**
 * 反应堆方块实体：破坏任务激活时过热，玩家右键完成小游戏后关闭。
 */
public class ReactorBlockEntity extends BlockEntity {

    @Nullable
    private BlockPos partnerPos;

    public ReactorBlockEntity(BlockPos pos, BlockState state) {
        super(ModSceneBlocks.REACTOR_ENTITY, pos, state);
    }

    @Nullable
    public BlockPos getPartnerPos() { return partnerPos; }
    public void setPartnerPos(@Nullable BlockPos pos) { this.partnerPos = pos; setChanged(); }
    public boolean hasPartner() { return partnerPos != null; }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (partnerPos != null) {
            tag.putInt("PartnerX", partnerPos.getX());
            tag.putInt("PartnerY", partnerPos.getY());
            tag.putInt("PartnerZ", partnerPos.getZ());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("PartnerX")) {
            partnerPos = new BlockPos(tag.getInt("PartnerX"), tag.getInt("PartnerY"), tag.getInt("PartnerZ"));
        }
    }

    @Override
    public void setRemoved() {
        if (this.level instanceof ServerLevel serverLevel) {
            ReactorRegistry.remove(serverLevel, this.worldPosition);
        }
        super.setRemoved();
    }

    public boolean isClosed() {
        return getBlockState().getValue(ReactorBlock.CLOSED);
    }

    public boolean isActive() {
        return getBlockState().getValue(ReactorBlock.ACTIVE);
    }

    /** 小游戏完成后关闭反应堆。 */
    public void close() {
        if (this.level instanceof ServerLevel serverLevel) {
            serverLevel.setBlock(this.worldPosition,
                    getBlockState().setValue(ReactorBlock.ACTIVE, false).setValue(ReactorBlock.CLOSED, true),
                    Block.UPDATE_ALL);
            serverLevel.sendParticles(ParticleTypes.CLOUD,
                    this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 1.0, this.worldPosition.getZ() + 0.5,
                    25, 0.4, 0.4, 0.4, 0.05);
            serverLevel.playSound(null, this.worldPosition, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS,
                    1.0F, 1.4F);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ReactorBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        ReactorRegistry.add(serverLevel, pos);
        boolean sabotage = SceneEventManager.isSabotageActive(serverLevel);

        if (sabotage) {
            if (!state.getValue(ReactorBlock.CLOSED) && !state.getValue(ReactorBlock.ACTIVE)) {
                serverLevel.setBlock(pos, state.setValue(ReactorBlock.ACTIVE, true), Block.UPDATE_ALL);
            }
            if (state.getValue(ReactorBlock.ACTIVE)) {
                if (serverLevel.getGameTime() % 4 == 0) {
                    serverLevel.sendParticles(ParticleTypes.FLAME,
                            pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 3, 0.3, 0.2, 0.3, 0.02);
                    serverLevel.sendParticles(ParticleTypes.SMOKE,
                            pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 2, 0.25, 0.15, 0.25, 0.01);
                }
                if (serverLevel.getGameTime() % 30 == 0) {
                    serverLevel.playSound(null, pos, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 0.5F, 0.8F);
                }
            }
        } else {
            if (state.getValue(ReactorBlock.ACTIVE) || state.getValue(ReactorBlock.CLOSED)) {
                serverLevel.setBlock(pos,
                        state.setValue(ReactorBlock.ACTIVE, false).setValue(ReactorBlock.CLOSED, false),
                        Block.UPDATE_ALL);
            }
        }
    }

    /** 反应堆关闭后，检查配对的两个反应堆是否都已关闭。 */
    public void onSelfClosed() {
        if (!(this.level instanceof ServerLevel serverLevel)) return;
        if (!serverLevel.getBlockState(this.worldPosition).getValue(ReactorBlock.CLOSED)) return;

        if (partnerPos != null) {
            if (serverLevel.isLoaded(partnerPos)) {
                BlockEntity be = serverLevel.getBlockEntity(partnerPos);
                if (be instanceof ReactorBlockEntity) {
                    boolean partnerClosed = serverLevel.getBlockState(partnerPos).getValue(ReactorBlock.CLOSED);
                    if (!partnerClosed) return;
                } else {
                    return;
                }
            } else {
                return;
            }
        }

        SceneEventManager.stopSabotage(serverLevel);
        for (var player : serverLevel.players()) {
            player.displayClientMessage(Component.translatable("message.noellesroles.reactor.all_closed"), false);
        }
    }
}
