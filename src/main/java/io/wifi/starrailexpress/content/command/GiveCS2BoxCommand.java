package io.wifi.starrailexpress.content.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.wifi.starrailexpress.cca.CS2InventoryComponent;
import io.wifi.starrailexpress.content.musicbox.MusicBoxRegistry;
import io.wifi.starrailexpress.util.ItemSkinManager;
import org.agmas.noellesroles.cs2.CS2BoxConfig;
import org.agmas.noellesroles.cs2.CS2BoxManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * /giveCS2box &lt;player&gt; box|key|skin|music &lt;id&gt;
 * <p>
 * 给予玩家 CS2 仓库系统中的箱子、钥匙、皮肤或音乐盒。
 * 仅 OP（权限等级≥2）可用。
 * </p>
 * <p>回退策略：整个文件删除即可。</p>
 */
public final class GiveCS2BoxCommand {

    private GiveCS2BoxCommand() {}

    /** 箱子 ID 自动补全 */
    private static final SuggestionProvider<CommandSourceStack> BOX_ID_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(
                    CS2BoxManager.getInstance().getBoxIds(),
                    builder);

    /** 钥匙 ID 自动补全（从所有箱子配置的 keyName 提取） */
    private static final SuggestionProvider<CommandSourceStack> KEY_ID_SUGGESTIONS =
            (context, builder) -> {
                var keyIds = CS2BoxManager.getInstance().getAllBoxes().stream()
                        .map(CS2BoxConfig::getKeyName)
                        .filter(k -> k != null && !k.isEmpty())
                        .distinct()
                        .collect(Collectors.toList());
                return SharedSuggestionProvider.suggest(keyIds, builder);
            };

    /** 皮肤 ID 自动补全（格式: itemType/skinName） */
    private static final SuggestionProvider<CommandSourceStack> SKIN_ID_SUGGESTIONS =
            (context, builder) -> {
                var skinIds = new java.util.ArrayList<String>();
                for (var entry : ItemSkinManager.getSkins().entrySet()) {
                    String itemType = entry.getKey();
                    for (String skinName : entry.getValue().keySet()) {
                        if (!"default".equals(skinName)) {
                            skinIds.add(itemType + "/" + skinName);
                        }
                    }
                }
                return SharedSuggestionProvider.suggest(skinIds, builder);
            };

    /** 音乐盒 ID 自动补全 */
    private static final SuggestionProvider<CommandSourceStack> MUSIC_ID_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(
                    MusicBoxRegistry.getAll().stream().map(b -> b.id()),
                    builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("giveCS2box")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.players())

                        // box 子命令
                        .then(Commands.literal("box")
                                .then(Commands.argument("boxId", StringArgumentType.word())
                                        .suggests(BOX_ID_SUGGESTIONS)
                                        .executes(ctx -> execute(ctx.getSource(),
                                                EntityArgument.getPlayers(ctx, "player"),
                                                "box",
                                                StringArgumentType.getString(ctx, "boxId")))))

                        // key 子命令
                        .then(Commands.literal("key")
                                .then(Commands.argument("keyId", StringArgumentType.word())
                                        .suggests(KEY_ID_SUGGESTIONS)
                                        .executes(ctx -> execute(ctx.getSource(),
                                                EntityArgument.getPlayers(ctx, "player"),
                                                "key",
                                                StringArgumentType.getString(ctx, "keyId")))))
    
                        // skin 子命令（greedyString 支持 knife/skinName 格式，无需引号）
                        .then(Commands.literal("skin")
                                .then(Commands.argument("skinId", StringArgumentType.greedyString())
                                        .suggests(SKIN_ID_SUGGESTIONS)
                                        .executes(ctx -> execute(ctx.getSource(),
                                                EntityArgument.getPlayers(ctx, "player"),
                                                "skin",
                                                StringArgumentType.getString(ctx, "skinId")))))
    
                        // music 子命令
                        .then(Commands.literal("music")
                                .then(Commands.argument("musicId", StringArgumentType.word())
                                        .suggests(MUSIC_ID_SUGGESTIONS)
                                        .executes(ctx -> execute(ctx.getSource(),
                                                EntityArgument.getPlayers(ctx, "player"),
                                                "music",
                                                StringArgumentType.getString(ctx, "musicId")))))));
    }

    private static int execute(CommandSourceStack source,
                               Collection<ServerPlayer> targets,
                               String type,
                               String id) {
        // 验证 ID 是否存在
        if ("box".equals(type)) {
            CS2BoxConfig config = CS2BoxManager.getInstance().getBox(id);
            if (config == null) {
                source.sendFailure(Component.literal("§c未知的箱子 ID: " + id
                        + "。可用箱子: " + CS2BoxManager.getInstance().getBoxIds()));
                return 0;
            }
        }

        for (ServerPlayer target : targets) {
            CS2InventoryComponent inv = CS2InventoryComponent.KEY.get(target);
            if ("box".equals(type)) {
                inv.addBox(id, 1);
            } else if ("key".equals(type)) {
                inv.addKey(id, 1);
            } else if ("skin".equals(type)) {
                inv.addSkin(id, 1);
                // 同时解锁皮肤（双系统：PlayerEconomyManager + CCA 组件，避免皮肤界面装备校验被拒）
                String[] parts = id.split("/");
                if (parts.length >= 2) {
                    io.wifi.starrailexpress.util.ItemSkinManager.unlockSkinForItemType(target, parts[0], parts[1]);
                    io.wifi.starrailexpress.cca.SREPlayerSkinsComponent.KEY.get(target).syncSkinsToClient();
                }
            } else if ("music".equals(type)) {
                inv.addMusicBox(id, 1);
                // 同时添加到 MusicBoxPlayerComponent
                io.wifi.starrailexpress.content.musicbox.MusicBoxPlayerComponent musicComp =
                        io.wifi.starrailexpress.content.musicbox.MusicBoxPlayerComponent.KEY.get(target);
                musicComp.addMusicBox(id);
            }

            String typeLabel = switch (type) {
                case "box" -> "箱子";
                case "key" -> "钥匙";
                case "skin" -> "皮肤";
                case "music" -> "音乐盒";
                default -> type;
            };
            target.displayClientMessage(
                    Component.literal("§6[CS2] §a你获得了 " + typeLabel + ": §e" + id)
                            .withStyle(ChatFormatting.GOLD), true);
        }

        String typeLabel = switch (type) {
            case "box" -> "箱子";
            case "key" -> "钥匙";
            case "skin" -> "皮肤";
            case "music" -> "音乐盒";
            default -> type;
        };
        final String typeLabelFinal = typeLabel;
        source.sendSuccess(() -> Component.literal("§a[CS2] 已将 " + typeLabelFinal + " [" + id + "] 赠予 "
                + targets.size() + " 名玩家"), true);
        return targets.size();
    }
}
