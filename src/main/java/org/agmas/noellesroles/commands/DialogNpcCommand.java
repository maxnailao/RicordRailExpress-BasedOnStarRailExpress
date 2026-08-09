package org.agmas.noellesroles.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.content.item.DialogNpcItem;
import org.agmas.noellesroles.dialog.DialogDataManager;
import org.agmas.noellesroles.init.ModItems;

/**
 * 对话 NPC 管理命令 {@code /sre:dialognpc}。
 * <ul>
 * <li>{@code give <dialogId>} —— 发放绑定了对话配置的对话角色物品；</li>
 * <li>{@code list} —— 列出 {@code <world>/train_dialogs/} 下所有可用对话配置；</li>
 * <li>{@code reload} —— 清空对话配置缓存，热更新 JSON 修改。</li>
 * </ul>
 */
public class DialogNpcCommand {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("sre:dialognpc").requires(source -> source.hasPermission(2))
                    .then(Commands.literal("give")
                            .then(Commands.argument("dialogId", StringArgumentType.greedyString())
                                    .executes(ctx -> executeGive(ctx, StringArgumentType.getString(ctx, "dialogId")))))
                    .then(Commands.literal("list")
                            .executes(DialogNpcCommand::executeList))
                    .then(Commands.literal("reload")
                            .executes(DialogNpcCommand::executeReload)));
        });
    }

    private static int executeGive(CommandContext<CommandSourceStack> context, String dialogId) {
        if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
            context.getSource().sendFailure(Component.literal("该命令仅玩家可用"));
            return 0;
        }
        ItemStack stack = new ItemStack(ModItems.DIALOG_NPC_ITEM);
        DialogNpcItem.applyDialogId(stack, dialogId);
        stack.set(net.minecraft.core.component.DataComponents.ITEM_NAME,
                Component.translatable("item.noellesroles.dialog_npc.named", dialogId));
        if (!player.addItem(stack)) {
            player.drop(stack, false);
        }
        context.getSource().sendSuccess(() -> Component.translatable(
                "message.noellesroles.dialog_npc.give", dialogId).withStyle(ChatFormatting.GREEN), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeList(CommandContext<CommandSourceStack> context) {
        var dialogs = DialogDataManager.getAvailableDialogs(context.getSource().getServer());
        if (dialogs.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("train_dialogs 中没有对话配置"), false);
            return 1;
        }
        context.getSource().sendSuccess(() -> Component.literal("可用对话配置 (" + dialogs.size() + "):"), false);
        dialogs.forEach(id -> context.getSource()
                .sendSuccess(() -> Component.literal(" - " + id), false));
        return dialogs.size();
    }

    private static int executeReload(CommandContext<CommandSourceStack> context) {
        DialogDataManager.clearCache();
        context.getSource().sendSuccess(() -> Component.translatable(
                "message.noellesroles.dialog_npc.reloaded").withStyle(ChatFormatting.GREEN), true);
        return Command.SINGLE_SUCCESS;
    }
}
