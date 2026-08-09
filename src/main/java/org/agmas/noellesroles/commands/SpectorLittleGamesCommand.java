package org.agmas.noellesroles.commands;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.packet.OpenGameConsoleS2CPacket;

/**
 * /spectorlittlegames 指令
 * <p>
 * 旁观者模式下打开游戏掌机面板（小游戏选择界面）。
 * 非旁观者执行时返回提示并拒绝打开。
 */
public class SpectorLittleGamesCommand {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("spectorlittlegames")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            // 检查旁观者模式
                            if (!player.isSpectator()) {
                                player.sendSystemMessage(Component
                                        .translatable("command.spectorlittlegames.not_spectator")
                                        .withStyle(ChatFormatting.RED));
                                return 0;
                            }
                            // 旁观者：发送打开掌机面板包
                            ServerPlayNetworking.send(player, new OpenGameConsoleS2CPacket());
                            return 1;
                        })));
    }
}
