package io.wifi.starrailexpress.content.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.wifi.starrailexpress.backpack.BackpackManager;
import io.wifi.starrailexpress.progression.ProgressionState.FactionCardType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class BackpackGrantCommand {
    private BackpackGrantCommand() {}

    private static final SuggestionProvider<CommandSourceStack> CARD_TYPE_SUGGESTIONS =
            (context, builder) -> {
                List<String> types = new ArrayList<>();
                for (FactionCardType t : FactionCardType.values()) {
                    if (t != FactionCardType.NONE) types.add(t.questKey);
                }
                types.add("selfselect");
                return SharedSuggestionProvider.suggest(types, builder);
            };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sre:backpack")
                .then(Commands.literal("grant")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests(CARD_TYPE_SUGGESTIONS)
                                .executes(context -> grant(
                                        List.of(context.getSource().getPlayerOrException()),
                                        StringArgumentType.getString(context, "type"),
                                        1))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 99))
                                        .executes(context -> grant(
                                                List.of(context.getSource().getPlayerOrException()),
                                                StringArgumentType.getString(context, "type"),
                                                IntegerArgumentType.getInteger(context, "count")))
                                        .then(Commands.argument("target", EntityArgument.players())
                                                .executes(context -> grant(
                                                        EntityArgument.getPlayers(context, "target"),
                                                        StringArgumentType.getString(context, "type"),
                                                        IntegerArgumentType.getInteger(context, "count"))))))));
    }

    private static int grant(Collection<ServerPlayer> targets, String rawType, int count) {
        if ("selfselect".equalsIgnoreCase(rawType)) {
            for (ServerPlayer target : targets) {
                BackpackManager.addSelfSelectCard(target, count);
                target.sendSystemMessage(Component.literal("§a获得 " + count + " 张 自选职业卡"));
            }
            return targets.size();
        }
        FactionCardType type = FactionCardType.fromString(rawType);
        if (type == FactionCardType.NONE) {
            return 0;
        }
        for (ServerPlayer target : targets) {
            BackpackManager.addCard(target, type, count);
            target.sendSystemMessage(Component.literal(
                    "§a获得 " + count + " 张 " + Component.translatable(type.displayName).getString() + " 卡"));
        }
        return targets.size();
    }
}
