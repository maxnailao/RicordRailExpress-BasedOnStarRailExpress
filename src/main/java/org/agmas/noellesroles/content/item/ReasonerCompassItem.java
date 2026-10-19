package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.role_data.neutral.ReasonerRoleData;
import org.agmas.noellesroles.role.ModRoles;

public class ReasonerCompassItem extends Item {
    public ReasonerCompassItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.success(stack);
        }

        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(level);
        if (!game.isRole(player, ModRoles.REASONER) || !game.isRunning() || !GameUtils.isPlayerAliveAndSurvival(player)) {
            serverPlayer.displayClientMessage(Component.translatable(
                    "message.noellesroles.reasoner.unavailable").withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }

        ReasonerRoleData data = RoleData.getNullable(ReasonerRoleData.class, player);
        if (data != null) {
            data.openCompass(serverPlayer);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
