package io.wifi.starrailexpress.content.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import io.wifi.starrailexpress.data.PlayerEconomyManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

/**
 * /giveCoin &lt;player&gt; add|set|get [amount]
 * <p>
 * 操作 CS2 仓库 / 每日商店使用的"货币"（PlayerEconomyManager.coinNum）。
 * 仅 OP（权限等级 ≥ 2）可用。
 * </p>
 */
public final class GiveCoinCommand {

    private GiveCoinCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("giveCoin")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.players())
                        .then(Commands.literal("add")
                                .then(Commands.argument("amount", IntegerArgumentType.integer())
                                        .executes(ctx -> execute(ctx.getSource(),
                                                EntityArgument.getPlayers(ctx, "player"),
                                                IntegerArgumentType.getInteger(ctx, "amount"), false))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                        .executes(ctx -> execute(ctx.getSource(),
                                                EntityArgument.getPlayers(ctx, "player"),
                                                IntegerArgumentType.getInteger(ctx, "amount"), true))))
                        .then(Commands.literal("get")
                                .executes(ctx -> executeGet(ctx.getSource(),
                                        EntityArgument.getPlayers(ctx, "player"))))));
    }

    private static int execute(CommandSourceStack source, Collection<ServerPlayer> targets,
                               int amount, boolean set) {
        for (ServerPlayer target : targets) {
            if (set) {
                int current = PlayerEconomyManager.getCoinNum(target);
                PlayerEconomyManager.addCoinNum(target, amount - current);
            } else {
                PlayerEconomyManager.addCoinNum(target, amount);
            }
            int now = PlayerEconomyManager.getCoinNum(target);
            target.displayClientMessage(
                    Component.literal("§6[货币] §a当前货币: §e" + now).withStyle(ChatFormatting.GOLD), true);
        }
        final int n = targets.size();
        source.sendSuccess(() -> Component.literal("§a[货币] 已操作 " + n + " 名玩家的 CS2 货币"), true);
        return targets.size();
    }

    private static int executeGet(CommandSourceStack source, Collection<ServerPlayer> targets) {
        int total = 0;
        for (ServerPlayer target : targets) {
            total += PlayerEconomyManager.getCoinNum(target);
        }
        final int t = total;
        source.sendSuccess(() -> Component.literal("§a[货币] 合计: " + t), true);
        return total;
    }
}
