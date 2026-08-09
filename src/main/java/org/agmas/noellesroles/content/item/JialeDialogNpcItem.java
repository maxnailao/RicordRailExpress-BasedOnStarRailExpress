package org.agmas.noellesroles.content.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
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
 * JiaLe 引导员物品 —— 右键放置固定绑定 JiaLe 对话的 NPC。
 * <p>
 * 与通用的 {@link DialogNpcItem} 不同，本物品不接受任何自定义数据：
 * 放置出的 NPC 从始至终只使用内置的 JiaLe 新手教程对话
 * （{@code data/noellesroles/dialogs/jiale.json}，随 jar 分发，无需外部文件）。
 */
public class JialeDialogNpcItem extends Item {

    public JialeDialogNpcItem(Properties properties) {
        super(properties);
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
        npc.setDialogId(DialogDataManager.DEFAULT_DIALOG_ID);
        level.addFreshEntity(npc);

        if (player == null || !player.hasInfiniteMaterials()) {
            stack.shrink(1);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context,
            @NotNull List<Component> tooltipComponents, @NotNull TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("item.noellesroles.jiale_npc.tooltip")
                .withStyle(ChatFormatting.GRAY));
    }
}
