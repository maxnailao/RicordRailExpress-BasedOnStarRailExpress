package io.wifi.starrailexpress.content.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.wifi.starrailexpress.content.musicbox.MusicBox;
import io.wifi.starrailexpress.content.musicbox.MusicBoxPlayerComponent;
import io.wifi.starrailexpress.content.musicbox.MusicBoxRegistry;
import io.wifi.starrailexpress.content.musicbox.network.SyncLotteryTicketsS2CPayload;
import io.wifi.starrailexpress.SRE;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

/**
 * /givemusicbox &lt;musicbox_id|all&gt; &lt;player&gt;
 * <p>回退策略：整个文件删除即可。</p>
 */
public final class GiveMusicBoxCommand {
    private GiveMusicBoxCommand() {}

    private static final SuggestionProvider<CommandSourceStack> MUSIC_BOX_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(
                    MusicBoxRegistry.getAll().stream().map(MusicBox::id),
                    builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("givemusicbox")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.players())
                        .then(Commands.literal("all")
                                .executes(context -> {
                                    Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "player");
                                    return executeAll(context.getSource(), targets);
                                }))
                        .then(Commands.literal("chance")
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(context -> {
                                            Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "player");
                                            int amount = IntegerArgumentType.getInteger(context, "amount");
                                            return executeChance(context.getSource(), targets, amount);
                                        })))
                        .then(Commands.argument("musicbox_id", StringArgumentType.word())
                                .suggests(MUSIC_BOX_SUGGESTIONS)
                                .executes(context -> {
                                    String id = StringArgumentType.getString(context, "musicbox_id");
                                    Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "player");
                                    return execute(context.getSource(), id, targets);
                                }))));
    }

    private static int executeAll(CommandSourceStack source, Collection<ServerPlayer> targets) {
        for (ServerPlayer target : targets) {
            MusicBoxPlayerComponent comp = MusicBoxPlayerComponent.KEY.get(target);
            for (MusicBox box : MusicBoxRegistry.getAll()) {
                comp.addMusicBox(box.id());
            }
        }
        source.sendSuccess(() -> Component.literal("§a已将全部音乐盒赠予 "
                + targets.size() + " 名玩家"), true);
        return 1;
    }

    private static int executeChance(CommandSourceStack source, Collection<ServerPlayer> targets, int amount) {
        for (ServerPlayer target : targets) {
            MusicBoxPlayerComponent comp = MusicBoxPlayerComponent.KEY.get(target);
            int before = comp.getLotteryTickets();
            for (int i = 0; i < amount; i++) {
                comp.addLotteryTicket();
            }
            int after = comp.getLotteryTickets();
            SRE.LOGGER.info("[MusicBox] 指令给予抽奖次数: 玩家={}, 数量={}, 之前={}, 之后={}",
                    target.getName().getString(), amount, before, after);
            // 强制通过 S2C 包同步到客户端
            ServerPlayNetworking.send(target, new SyncLotteryTicketsS2CPayload(after));
            target.displayClientMessage(
                    Component.literal("你获得了 " + amount + " 次音乐盒抽奖机会 (当前: " + after + ")")
                            .withStyle(ChatFormatting.GOLD), true);
            target.sendSystemMessage(Component.literal("§6你获得了 " + amount + " 次音乐盒抽奖机会 (当前总计: " + after + ")"));
        }
        source.sendSuccess(() -> Component.literal("§a已给予 "
                + targets.size() + " 名玩家各 " + amount + " 次抽奖机会"), true);
        return 1;
    }

    private static int execute(CommandSourceStack source, String id, Collection<ServerPlayer> targets) {
        if (!MusicBoxRegistry.contains(id)) {
            source.sendFailure(Component.literal("未知的音乐盒 ID: " + id));
            return 0;
        }
        MusicBox box = MusicBoxRegistry.get(id);
        for (ServerPlayer target : targets) {
            MusicBoxPlayerComponent comp = MusicBoxPlayerComponent.KEY.get(target);
            comp.addMusicBox(id);
            target.sendSystemMessage(Component.literal("§6你获得了一个新的音乐盒: ")
                    .append(box.displayName()));
        }
        source.sendSuccess(() -> Component.literal("已将音乐盒 [" + id + "] 赠予 "
                + targets.size() + " 名玩家"), true);
        return 1;
    }
}
