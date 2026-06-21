package org.agmas.noellesroles.content.block.scene;

import java.awt.Color;

import com.mojang.serialization.MapCodec;

import org.agmas.noellesroles.content.block_entity.scene.ManholeBlockEntity;
import org.agmas.noellesroles.init.ModSceneBlocks;
import org.agmas.noellesroles.scene.ManholeRegistry;
import org.agmas.noellesroles.scene.SceneRoleAccess;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SRERoleWorldComponent;
import io.wifi.starrailexpress.content.block.api.TaskInstinctShowableInterface;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * 井盖：仅中立/杀手（或特定职业）可使用。右键沿视线方向传送到另一个井盖处出来。
 * 中立/杀手的任务透视可看到全图井盖。在井盖上停留超过 10 秒会窒息死亡。
 * 使用原版铁块贴图作为井盖外观。
 */
public class ManholeBlock extends BaseEntityBlock implements TaskInstinctShowableInterface {

    public static final int TASK_INSTINCT_ID = 15;
    /** 传送的最大水平距离。 */
    public static final double TRAVEL_RANGE = 48.0;

    public ManholeBlock(Properties settings) {
        super(settings);
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
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.CONSUME;
        }
        if (!SceneRoleAccess.canEnterRestricted(player, null)) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.manhole.denied"), true);
            serverLevel.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.6F, 0.7F);
            return InteractionResult.CONSUME;
        }
        BlockPos target = ManholeRegistry.findInLookDirection(serverLevel, player, pos, TRAVEL_RANGE);
        if (target == null) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.manhole.no_exit"), true);
            return InteractionResult.CONSUME;
        }

        // 起点特效
        serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                20, 0.3, 0.3, 0.3, 0.02);
        serverLevel.playSound(null, pos, SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE, SoundSource.BLOCKS, 0.9F, 1.2F);

        // 传送
        double tx = target.getX() + 0.5;
        double ty = target.getY() + 1.0;
        double tz = target.getZ() + 0.5;
        sp.teleportTo(tx, ty, tz);
        sp.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 12, 0, false, false, false));

        // 终点特效
        serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, tx, ty, tz, 20, 0.3, 0.3, 0.3, 0.02);
        serverLevel.playSound(null, target, SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE, SoundSource.BLOCKS, 0.9F, 0.9F);
        return InteractionResult.CONSUME;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ManholeBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state,
            BlockEntityType<T> type) {
        if (world.isClientSide) {
            return null;
        }
        return createTickerHelper(type, ModSceneBlocks.MANHOLE_ENTITY,
                (lvl, pos, s, be) -> ManholeBlockEntity.serverTick(lvl, pos, s, be));
    }

    // ── 任务透视：中立/杀手可见全图井盖 ──

    @Override
    public int taskInstinctId() {
        return TASK_INSTINCT_ID;
    }

    @Override
    public boolean shouldRenderTaskInstinct(BlockState state, BlockPos pos, Player player) {
        SRERole role = SRERoleWorldComponent.KEY.get(player.level()).getRole(player);
        return role != null && role.canUseInstinct();
    }

    @Override
    public Color taskInstinctRenderColor(BlockState state, BlockPos pos, Player player) {
        return new Color(0x35C7D6);
    }
}
