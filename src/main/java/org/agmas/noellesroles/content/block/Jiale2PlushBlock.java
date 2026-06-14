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

public class Jiale2PlushBlock extends SREPlushBlock {
    private static final MapCodec<Jiale2PlushBlock> CODEC = simpleCodec(Jiale2PlushBlock::new);

    private static final List<SoundEvent> JIALE2_SOUNDS = Arrays.asList(
            NRSounds.GUO1_SOUND,
            NRSounds.GUO2_SOUND,
            NRSounds.GUO3_SOUND,
            NRSounds.GUO4_SOUND
    );

    public Jiale2PlushBlock(BlockBehaviour.Properties settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends Jiale2PlushBlock> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!world.isClientSide) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof SREPlushBlockEntity plushie) {
                int soundIndex = plushie.getNextSoundIndex(JIALE2_SOUNDS.size());

                Vec3 mid = Vec3.atCenterOf(pos);
                world.playSound(null, mid.x(), mid.y(), mid.z(),
                        JIALE2_SOUNDS.get(soundIndex), SoundSource.BLOCKS, 1.0F, 1.0F);

                plushie.squish(1);
            }
        }

        return InteractionResult.SUCCESS;
    }
}
