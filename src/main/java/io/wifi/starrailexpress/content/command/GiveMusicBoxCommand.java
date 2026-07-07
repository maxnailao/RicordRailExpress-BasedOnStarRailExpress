package io.wifi.starrailexpress.content.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.wifi.starrailexpress.content.musicbox.MusicBox;
import io.wifi.starrailexpress.content.musicbox.MusicBoxPlayerComponent;
import io.wifi.starrailexpress.content.musicbox.MusicBoxRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /givemusicbox &lt;musicbox_id&gt; &lt;player&gt;
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
                .then(Commands.argument("musicbox_id", StringArgumentType.word())
                        .suggests(MUSIC_BOX_SUGGESTIONS)
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> {
                                    String id = StringArgumentType.getString(context, "musicbox_id");
                                    ServerPlayer target = EntityArgument.getPlayer(context, "player");
                                    return execute(context.getSource(), id, target);
                                }))));
    }

    private static int execute(CommandSourceStack source, String id, ServerPlayer target) {
        if (!MusicBoxRegistry.contains(id)) {
            source.sendFailure(Component.literal("未知的音乐盒 ID: " + id));
            return 0;
        }
        MusicBoxPlayerComponent comp = MusicBoxPlayerComponent.KEY.get(target);
        boolean added = comp.addMusicBox(id);
        if (added) {
            MusicBox box = MusicBoxRegistry.get(id);
            source.sendSuccess(() -> Component.literal("已将音乐盒 [" + id + "] 赠予玩家 "
                    + target.getName().getString()), true);
            target.sendSystemMessage(Component.literal("§6你获得了一个新的音乐盒: ")
                    .append(box.displayName()));
        } else {
            source.sendFailure(Component.literal("玩家 " + target.getName().getString()
                    + " 已拥有音乐盒 [" + id + "]"));
        }
        return 1;
    }
}
