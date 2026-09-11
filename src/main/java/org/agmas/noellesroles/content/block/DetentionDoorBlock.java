package org.agmas.noellesroles.content.block;

import io.wifi.starrailexpress.content.block.TrainDoorBlock;
import io.wifi.starrailexpress.content.block_entity.SmallDoorBlockEntity;
import io.wifi.starrailexpress.content.item.api.SREItemProperties.DoorCustomOpenItem;
import io.wifi.starrailexpress.index.TMMSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import org.agmas.noellesroles.content.block_entity.DetentionDoorBlockEntity;
import org.agmas.noellesroles.content.item.JailerKeyItem;

import java.util.function.Supplier;

/**
 * 关押门
 * <p>
 * 继承核心 {@link TrainDoorBlock}（双格门 + 动画 BER），但重写开门校验：
 * </p>
 * <ul>
 * <li><b>仅</b> {@link JailerKeyItem}（狱警钥匙）或创造模式可正常开关；</li>
 * <li>撬棍（{@code TMMItems.CROWBAR}，属于 {@link DoorCustomOpenItem}）放行给核心撬棍逻辑强行破坏开启，
 * 并在 {@link DetentionDoorBlockEntity#blast()} 中触发永久警报；</li>
 * <li>其余物品（含普通铁门钥匙、万能钥匙、撬锁器）一律无法开启。</li>
 * </ul>
 */
public class DetentionDoorBlock extends TrainDoorBlock {

    public DetentionDoorBlock(Supplier<BlockEntityType<SmallDoorBlockEntity>> typeSupplier, Properties settings) {
        super(typeSupplier, settings);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player,
            BlockHitResult hit) {
        ItemStack mainHand = player.getMainHandItem();
        // 撬棍等“自定义开门物品”放行给物品自身的 useOn：核心撬棍逻辑会 blast 本门 → 触发永久警报
        if (mainHand.getItem() instanceof DoorCustomOpenItem) {
            return InteractionResult.PASS;
        }
        BlockPos lowerPos = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        if (world.getBlockEntity(lowerPos) instanceof DetentionDoorBlockEntity entity) {
            if (entity.isInCooldown() || entity.isBlasted()) {
                return InteractionResult.FAIL;
            }
            // 仅狱警钥匙（或创造模式）可开关
            if (player.isCreative() || mainHand.getItem() instanceof JailerKeyItem) {
                if (!world.isClientSide) {
                    world.playSound(null, lowerPos.getX() + 0.5, lowerPos.getY() + 1, lowerPos.getZ() + 0.5,
                            TMMSounds.ITEM_KEY_DOOR, SoundSource.BLOCKS, 1f, 1f);
                }
                return open(state, world, entity, lowerPos);
            }
            // 其余情况：锁住
            if (!world.isClientSide) {
                world.playSound(null, lowerPos.getX() + 0.5, lowerPos.getY() + 1, lowerPos.getZ() + 0.5,
                        TMMSounds.BLOCK_DOOR_LOCKED, SoundSource.BLOCKS, 1f, 1f);
                player.displayClientMessage(Component.translatable("tip.door.requires_jailer_key"), true);
            }
            return InteractionResult.FAIL;
        }
        return InteractionResult.PASS;
    }
}
