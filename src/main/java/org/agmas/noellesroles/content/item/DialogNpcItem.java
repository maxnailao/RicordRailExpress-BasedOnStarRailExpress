package org.agmas.noellesroles.content.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.Mth;
import org.agmas.noellesroles.content.entity.DialogNpcEntity;
import org.agmas.noellesroles.dialog.DialogDataManager;
import org.agmas.noellesroles.init.ModEntities;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 对话角色物品 —— 右键地面放置一个玩家形态的对话 NPC。
 * <p>
 * 物品的 CUSTOM_DATA 中携带 {@code dialog_id}，决定放置出的 NPC 绑定哪份
 * {@code <world>/train_dialogs/} 下的对话配置；未携带时默认使用示例对话。
 * 可通过 {@code /sre:dialognpc give <dialogId>} 获取已绑定配置的物品。
 */
public class DialogNpcItem extends Item {

    public static final String TAG_DIALOG_ID = "dialog_id";

    public DialogNpcItem(Properties properties) {
        super(properties);
    }

    /** 读取物品绑定的对话 id，缺省为示例对话 */
    public static String readDialogId(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        String id = tag.getString(TAG_DIALOG_ID);
        return id.isBlank() ? DialogDataManager.DEFAULT_DIALOG_ID : id;
    }

    /** 给物品写入绑定的对话 id */
    public static void applyDialogId(ItemStack stack, String dialogId) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putString(TAG_DIALOG_ID, dialogId == null ? DialogDataManager.DEFAULT_DIALOG_ID : dialogId);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Override
    public @NotNull InteractionResult useOn(@NotNull UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        BlockPos target = context.getClickedPos().relative(context.getClickedFace());
        BlockState state = level.getBlockState(target);
        if (!state.canBeReplaced()) {
            return InteractionResult.FAIL;
        }
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();

        DialogNpcEntity npc = ModEntities.DIALOG_NPC.create(level);
        if (npc == null) {
            return InteractionResult.FAIL;
        }
        // 朝向放置者
        float yaw = 0.0F;
        if (player != null) {
            yaw = (float) (Mth.atan2(player.getX() - (target.getX() + 0.5),
                    player.getZ() - (target.getZ() + 0.5)) * 180.0F / (float) Math.PI);
        }
        npc.moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, yaw, 0.0F);
        npc.setYHeadRot(yaw);
        npc.setDialogId(readDialogId(stack));
        level.addFreshEntity(npc);

        if (player == null || !player.hasInfiniteMaterials()) {
            stack.shrink(1);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context,
            @NotNull List<Component> tooltipComponents, @NotNull TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("item.noellesroles.dialog_npc.tooltip")
                .withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable("item.noellesroles.dialog_npc.tooltip_id",
                readDialogId(stack)).withStyle(ChatFormatting.DARK_GRAY));
    }
}
