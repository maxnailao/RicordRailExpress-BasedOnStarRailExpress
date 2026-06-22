package org.agmas.noellesroles.content.block.scene;

import java.awt.Color;

import com.mojang.serialization.MapCodec;

import org.agmas.noellesroles.content.block_entity.scene.ReactorBlockEntity;
import org.agmas.noellesroles.init.ModSceneBlocks;
import org.agmas.noellesroles.scene.SceneEventManager;

import io.wifi.starrailexpress.client.gui.screen.SimpleQuestMinigameScreen;
import io.wifi.starrailexpress.content.block.api.TaskInstinctShowableInterface;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * 反应堆装置：破坏任务激活时进入过载（active），玩家右键打开温度调节小游戏。
 * 完成小游戏关闭反应堆，两个配对反应堆均关闭后结束破坏任务。
 * 中立/杀手可通过任务透视全局看到反应堆（绿色边框）。
 */
public class ReactorBlock extends BaseEntityBlock implements TaskInstinctShowableInterface {

    public static final int TASK_INSTINCT_ID = 16;

    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final BooleanProperty CLOSED = BooleanProperty.create("closed");

    public ReactorBlock(Properties settings) {
        super(settings);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(ACTIVE, false)
                .setValue(CLOSED, false));
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
        builder.add(ACTIVE, CLOSED);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (level.isClientSide) {
            // 客户端：打开温度调节小游戏
            if (state.getValue(ACTIVE) && !state.getValue(CLOSED)) {
                Minecraft.getInstance().setScreen(
                        new SimpleQuestMinigameScreen(pos,
                                () -> {
                                    // 小游戏完成回调：发送完成包
                                    net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                                            new org.agmas.noellesroles.packet.ReactorMinigameCompleteC2SPacket(pos));
                                },
                                SimpleQuestMinigameScreen.Mode.REACTOR_TEMPERATURE));
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ReactorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state,
            BlockEntityType<T> type) {
        if (world.isClientSide) {
            return null;
        }
        return createTickerHelper(type, ModSceneBlocks.REACTOR_ENTITY,
                (lvl, pos, s, be) -> ReactorBlockEntity.serverTick(lvl, pos, s, be));
    }

    // ── 任务透视接口 ──

    @Override
    public int taskInstinctId() {
        return TASK_INSTINCT_ID;
    }

    @Override
    public boolean shouldRenderTaskInstinct(BlockState state, BlockPos pos, Player player) {
        return true;
    }

    @Override
    public Color taskInstinctRenderColor(BlockState state, BlockPos pos, Player player) {
        return new Color(0x4CAF50); // 绿色，区分于其他任务点
    }
}
