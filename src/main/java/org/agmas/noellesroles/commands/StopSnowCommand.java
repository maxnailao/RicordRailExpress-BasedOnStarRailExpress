package org.agmas.noellesroles.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.agmas.noellesroles.scene.BlizzardManager;

/**
 * /stopsnow_now —— 管理员指令，强制停止所有类型的暴风雪（普通/强制/最终）。
 */
public final class StopSnowCommand {

    private StopSnowCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("stopsnow_now")
                    .requires(source -> source.hasPermission(2))
                    .executes(StopSnowCommand::execute));
        });
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        boolean wasActive = BlizzardManager.forceStopAll(level);

        if (wasActive) {
            context.getSource().sendSuccess(
                    () -> Component.literal("已强制停止所有暴风雪，冷却计时器已重置")
                            .withStyle(ChatFormatting.AQUA), true);
        } else {
            context.getSource().sendSuccess(
                    () -> Component.literal("当前没有活跃的暴风雪")
                            .withStyle(ChatFormatting.GRAY), false);
        }
        return Command.SINGLE_SUCCESS;
    }
}
