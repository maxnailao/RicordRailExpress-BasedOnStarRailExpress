package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.content.block.PlaneTrainDoorBlock;
import io.wifi.starrailexpress.content.block.SmallDoorBlock;
import io.wifi.starrailexpress.content.block.TrainDoorBlock;
import io.wifi.starrailexpress.content.block.UpTrainDoorBlock;
import io.wifi.starrailexpress.content.block_entity.SmallDoorBlockEntity;
import io.wifi.starrailexpress.event.AllowPlayerOpenLockedDoor;
import io.wifi.starrailexpress.event.DisallowPlayerOpenDoor;
import io.wifi.starrailexpress.index.TMMSounds;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * 狱警钥匙的房间门交互。
 * <p>
 * 核心 {@code SmallDoorBlock} 只认「{@code TMMItems.KEY} + LORE 首行等于门 keyName」这一条开门路径，
 * 而 {@code keyName} 正是房号（{@code MapScanner} 也把「SmallDoorBlock + keyName 非空」标记为房间门）。
 * 狱警钥匙要能开任意房间门，就不能靠核心判定，于是在 {@link UseBlockCallback}（先于方块
 * {@code useWithoutItem} 触发）里接管：手持狱警钥匙右键带房号的房间门 → 直接开门。
 * </p>
 * <p>
 * 明确不接管的情况（一律 PASS 交还原逻辑）：
 * </p>
 * <ul>
 * <li>铁门（{@code TrainDoorBlock} / {@code UpTrainDoorBlock} / {@code PlaneTrainDoorBlock}）——
 * 狱警钥匙不该开铁门，且 {@code JailerKeyItem} 已不再继承 {@code IronDoorKeyItem}；</li>
 * <li>关押门——由 {@code DetentionDoorBlock} 自行判定狱警钥匙；</li>
 * <li>无房号（{@code keyName} 为空）的门、已开启 / 卡住 / 冷却中 / 已被破坏的门——
 * 保持原有提示与行为；</li>
 * <li>创造模式、已被 {@code AllowPlayerOpenLockedDoor} 放行或被 {@code DisallowPlayerOpenDoor}
 * 禁止开门的玩家——不越权覆盖核心规则。</li>
 * </ul>
 */
public final class JailerKeyDoorHandler {

    private JailerKeyDoorHandler() {
    }

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (hand != InteractionHand.MAIN_HAND
                    || !(player.getItemInHand(hand).getItem() instanceof JailerKeyItem)) {
                return InteractionResult.PASS;
            }
            if (player.isCreative()
                    || AllowPlayerOpenLockedDoor.EVENT.invoker().allowOpen(player)
                    || DisallowPlayerOpenDoor.EVENT.invoker().cantOpen(player)) {
                return InteractionResult.PASS;
            }
            BlockPos pos = hitResult.getBlockPos();
            BlockState state = world.getBlockState(pos);
            Block block = state.getBlock();
            // 铁门与关押门各有专用开门校验，狱警钥匙不参与
            if (!(block instanceof SmallDoorBlock door)
                    || block instanceof TrainDoorBlock
                    || block instanceof UpTrainDoorBlock
                    || block instanceof PlaneTrainDoorBlock) {
                return InteractionResult.PASS;
            }
            // 双格门：方块实体只存在于下半格
            BlockPos lowerPos = state.getValue(SmallDoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
            if (!(world.getBlockEntity(lowerPos) instanceof SmallDoorBlockEntity entity)) {
                return InteractionResult.PASS;
            }
            // 只接管「带房号且处于锁闭状态」的房间门
            if (entity.getKeyName().isEmpty() || entity.isOpen() || entity.isJammed()
                    || entity.isInCooldown() || entity.isBlasted()) {
                return InteractionResult.PASS;
            }
            if (!world.isClientSide) {
                world.playSound(null, lowerPos.getX() + 0.5, lowerPos.getY() + 1, lowerPos.getZ() + 0.5,
                        TMMSounds.ITEM_KEY_DOOR, SoundSource.BLOCKS, 1f, 1f);
            }
            // 复用核心开门实现：客户端返回 SUCCESS（不预测），服务端切换门状态并返回 CONSUME，
            // 同时带动同一扇门的上半格 / 相邻双开门一起动作
            return door.open(state, world, entity, lowerPos);
        });
    }
}
