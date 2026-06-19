package org.agmas.noellesroles.content.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import io.wifi.starrailexpress.content.minigame.GameConsoleGames;

import java.util.List;

/**
 * 游戏掌机物品
 * - 右键打开游戏选择界面
 * - 纯客户端交互，不影响游戏状态
 * - 不可堆叠
 */
public class GameConsoleItem extends Item {

    public GameConsoleItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (world.isClientSide) {
            // 客户端：打开游戏选择界面
            openGameConsoleScreen();
        }

        return InteractionResultHolder.sidedSuccess(stack, world.isClientSide);
    }

    /**
     * 在客户端打开游戏选择界面
     * 委托给独立内部类，避免服务端类加载时解析客户端类
     */
    private static void openGameConsoleScreen() {
        ClientHelper.openScreen();
    }

    /**
     * 客户端专属辅助类，仅在客户端调用时才会被JVM加载
     */
    private static class ClientHelper {
        static void openScreen() {
            net.minecraft.client.Minecraft.getInstance()
                    .setScreen(new io.wifi.starrailexpress.client.gui.screen.GameConsoleScreen());
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        int gameCount = GameConsoleGames.getAvailable().size();
        tooltip.add(Component.translatable("item.noellesroles.game_console.tooltip", gameCount)
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.noellesroles.game_console.tooltip2")
                .withStyle(ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, context, tooltip, type);
    }
}
