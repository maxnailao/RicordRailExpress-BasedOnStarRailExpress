package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.agmas.noellesroles.game.roles.neutral.convict.ConvictPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

import java.util.List;

/**
 * 重刑犯押运工具
 * <p>
 * - 一条不会断的拴绳：注册时不设置耐久值，无限耐久。
 * - 右键重刑犯将其套住并牵引运输（狱警专属）。
 * - 完整的套住/牵引/解开逻辑依赖 ConvictPlayerComponent 与重刑犯职业。
 * </p>
 */
public class ConvictEscortLeashItem extends Item {
    public ConvictEscortLeashItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
            InteractionHand hand) {
        if (player.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer jailer) || !(target instanceof ServerPlayer convict)) {
            return InteractionResult.PASS;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(jailer.level());
        // 狱警专属：仅狱警可使用押运工具
        if (!gameWorld.isRole(jailer, ModRoles.JAILER)) {
            jailer.displayClientMessage(
                    Component.translatable("message.noellesroles.escort.not_jailer").withStyle(ChatFormatting.RED),
                    true);
            return InteractionResult.FAIL;
        }
        // 仅对重刑犯生效（与手铐 / 抉择状态兼容，不要求已戴手铐）
        if (jailer == convict || !gameWorld.isRole(convict, ModRoles.CONVICT)) {
            jailer.displayClientMessage(
                    Component.translatable("message.noellesroles.escort.not_convict").withStyle(ChatFormatting.RED),
                    true);
            return InteractionResult.FAIL;
        }

        ConvictPlayerComponent comp = ConvictPlayerComponent.KEY.get(convict);
        // 再次右键同一重刑犯 → 解开拴绳
        if (jailer.getUUID().equals(comp.leashedBy)) {
            comp.leashedBy = null;
            comp.sync();
            jailer.displayClientMessage(Component
                    .translatable("message.noellesroles.escort.released", convict.getName())
                    .withStyle(ChatFormatting.GREEN), true);
            convict.displayClientMessage(Component
                    .translatable("message.noellesroles.escort.you_are_released").withStyle(ChatFormatting.GREEN),
                    true);
            return InteractionResult.SUCCESS;
        }

        // 套住重刑犯：绑定 leashedBy = 狱警 UUID，随后每 tick 由组件牵引跟随（无限耐久，不损耗）
        comp.leashedBy = jailer.getUUID();
        comp.sync();
        jailer.displayClientMessage(Component
                .translatable("message.noellesroles.escort.leashed", convict.getName())
                .withStyle(ChatFormatting.GREEN), true);
        convict.displayClientMessage(Component
                .translatable("message.noellesroles.escort.you_are_leashed", jailer.getName())
                .withStyle(ChatFormatting.RED), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.noellesroles.convict_escort_leash.tooltip")
                .withStyle(ChatFormatting.GRAY));
    }
}
