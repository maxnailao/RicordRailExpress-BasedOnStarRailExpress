package org.agmas.noellesroles.content.block_entity.scene;

import org.agmas.noellesroles.content.block.scene.ReactorBlock;
import org.agmas.noellesroles.init.ModSceneBlocks;
import org.agmas.noellesroles.scene.ReactorRegistry;
import org.agmas.noellesroles.scene.SceneEventManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 反应堆方块实体：随破坏任务过载/复位；提供关闭逻辑与“全部关闭则结束破坏任务”的判定。
 */
public class ReactorBlockEntity extends BlockEntity {

    public ReactorBlockEntity(BlockPos pos, BlockState state) {
        super(ModSceneBlocks.REACTOR_ENTITY, pos, state);
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

    /** 关闭反应堆（绑定工具调用）。 */
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
            // 过载：未关闭则点亮并冒火花
            if (!state.getValue(ReactorBlock.CLOSED) && !state.getValue(ReactorBlock.ACTIVE)) {
                serverLevel.setBlock(pos, state.setValue(ReactorBlock.ACTIVE, true), Block.UPDATE_ALL);
            }
            if (state.getValue(ReactorBlock.ACTIVE)) {
                if (serverLevel.getGameTime() % 6 == 0) {
                    serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                            pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.05);
                }
                if (serverLevel.getGameTime() % 40 == 0) {
                    serverLevel.playSound(null, pos, SoundEvents.CONDUIT_AMBIENT, SoundSource.BLOCKS, 0.6F, 0.8F);
                }
            }
        } else {
            // 破坏任务结束：复位
            if (state.getValue(ReactorBlock.ACTIVE) || state.getValue(ReactorBlock.CLOSED)) {
                serverLevel.setBlock(pos,
                        state.setValue(ReactorBlock.ACTIVE, false).setValue(ReactorBlock.CLOSED, false),
                        Block.UPDATE_ALL);
            }
        }
    }

    /** 在一个反应堆被关闭后调用：若场上所有反应堆均已关闭，则结束破坏任务。 */
    public static void onReactorClosed(ServerLevel level) {
        if (ReactorRegistry.allClosed(level)) {
            SceneEventManager.stopSabotage(level);
            for (var player : level.players()) {
                player.displayClientMessage(Component.translatable("message.noellesroles.reactor.all_closed"), false);
            }
        }
    }
}
