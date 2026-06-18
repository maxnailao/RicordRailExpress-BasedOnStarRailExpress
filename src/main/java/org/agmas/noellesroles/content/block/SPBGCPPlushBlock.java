package org.agmas.noellesroles.content.block;

import org.agmas.noellesroles.content.block_entity.SREPlushBlockEntity;
import org.agmas.noellesroles.init.NRSounds;
import org.agmas.noellesroles.init.SREFumoBlocks;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.List;

public class SPBGCPPlushBlock extends SREPlushBlock {
    private static final MapCodec<SPBGCPPlushBlock> CODEC = simpleCodec(SPBGCPPlushBlock::new);

    private static final List<SoundEvent> SPBGCP_SOUNDS = Arrays.asList(
            NRSounds.SPBGCP_SOUND1,
            NRSounds.SPBGCP_SOUND2,
            NRSounds.SPBGCP_SOUND3,
            NRSounds.SPBGCP_SOUND4
    );

    public SPBGCPPlushBlock(BlockBehaviour.Properties settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends SPBGCPPlushBlock> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!world.isClientSide) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof SREPlushBlockEntity plushie) {
                int soundIndex = plushie.getNextSoundIndex(SPBGCP_SOUNDS.size());

                Vec3 mid = Vec3.atCenterOf(pos);
                world.playSound(null, mid.x(), mid.y(), mid.z(),
                        SPBGCP_SOUNDS.get(soundIndex), SoundSource.BLOCKS, 1.0F, 1.0F);

                plushie.squish(1);
            }
        }

        return InteractionResult.SUCCESS;
    }
}
