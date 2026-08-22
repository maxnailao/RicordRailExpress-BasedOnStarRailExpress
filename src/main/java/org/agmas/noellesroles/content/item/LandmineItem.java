package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.agmas.noellesroles.content.block_entity.LandmineBlockEntity;
import org.agmas.noellesroles.init.ModBlocks;

/**
 * 反人员地雷
 * - 长按右键 3 秒在当前位置布设地雷
 * - 布设后需布设者离开布设位置，离开后 3 秒地雷才进入待发状态
 * - 待发状态下玩家踩到地雷会发出按钮声，离开踩踏位置即引爆
 */
public class LandmineItem extends Item {
    /** 布设所需长按时长（3秒） */
    public static final int PLACE_TICKS = 60;

    public LandmineItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (!GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(player)) {
            return InteractionResultHolder.pass(stack);
        }
        // 脚下位置必须可替换（空气/草等），否则无法布设
        if (!level.getBlockState(player.blockPosition()).canBeReplaced()) {
            return InteractionResultHolder.pass(stack);
        }
        player.startUsingItem(usedHand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return PLACE_TICKS;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
        if (!(level instanceof ServerLevel serverLevel) || !(user instanceof ServerPlayer player)) {
            return stack;
        }
        if (!GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(player)) {
            return stack;
        }
        BlockPos placePos = player.blockPosition();
        if (!serverLevel.getBlockState(placePos).canBeReplaced()) {
            return stack;
        }
        // 在当前位置放置地雷方块并记录布设者
        serverLevel.setBlock(placePos, ModBlocks.LANDMINE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        if (serverLevel.getBlockEntity(placePos) instanceof LandmineBlockEntity landmine) {
            landmine.setOwner(player.getUUID());
        }
        serverLevel.playSound(null, placePos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS,
                0.6F, 0.7F);
        stack.shrink(1);
        return stack;
    }
}
