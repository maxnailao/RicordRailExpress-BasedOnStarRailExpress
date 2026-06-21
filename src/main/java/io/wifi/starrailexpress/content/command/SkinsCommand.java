package io.wifi.starrailexpress.content.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.wifi.starrailexpress.content.item.SkinableItem;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.network.OpenSkinScreenPaylod;
import io.wifi.starrailexpress.util.SkinManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.agmas.harpymodloader.Harpymodloader;

import java.util.Collection;
import java.util.HashMap;

public class SkinsCommand {

    /** 物品类型补全 */
    private static final SuggestionProvider<CommandSourceStack> SKIN_TYPE_SUGGESTIONS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    SkinManager.getSkins().keySet(), builder);

    /** 皮肤ID补全（根据已输入的物品类型动态补全） */
    private static final SuggestionProvider<CommandSourceStack> SKIN_ID_SUGGESTIONS =
            (ctx, builder) -> {
                String typeName = StringArgumentType.getString(ctx, "type");
                HashMap<String, SkinManager.Skin> skins = SkinManager.getSkins(typeName);
                if (skins != null) {
                    return SharedSuggestionProvider.suggest(skins.keySet(), builder);
                }
                return SharedSuggestionProvider.suggest(new String[0], builder);
            };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("tmm:skins")
                        .requires((t) -> Harpymodloader.officialVerify)
                        .executes(context -> execute(context.getSource(), null))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> execute(context.getSource(),
                                        EntityArgument.getPlayer(context, "player"))))
                        // give 子命令: /tmm:skins give <玩家> <物品类型> <皮肤ID>
                        .then(Commands.literal("give")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .then(Commands.argument("type", StringArgumentType.word())
                                                .suggests(SKIN_TYPE_SUGGESTIONS)
                                                .then(Commands.argument("skin", StringArgumentType.word())
                                                        .suggests(SKIN_ID_SUGGESTIONS)
                                                        .executes(SkinsCommand::executeGive))))));
    }

    private static int execute(CommandSourceStack source, ServerPlayer player)
            throws CommandSyntaxException {
        ServerPlayer sender = source.getPlayerOrException();

        if (player == null) {
            openSkinScreen(sender);
            source.sendSuccess(() -> Component.translatable("commands.sre.showskin.self"), false);
        } else {
            openSkinScreen(player);
            source.sendSuccess(() -> Component.translatable("commands.sre.showskin.other", player.getName()),
                    false);
        }
        return 1;
    }

    /**
     * 执行给予皮肤命令
     * /tmm:skins give <targets> <type> <skin>
     */
    private static int executeGive(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        String typeName = StringArgumentType.getString(ctx, "type");
        String skinName = StringArgumentType.getString(ctx, "skin");

        // 验证物品类型是否已注册
        if (SkinManager.getSkinTypeId(typeName) < 0) {
            ctx.getSource().sendFailure(
                    Component.translatable("commands.sre.skins.give.unknown_type", typeName));
            return 0;
        }

        // 验证皮肤是否存在
        HashMap<String, SkinManager.Skin> skins = SkinManager.getSkins(typeName);
        if (skins == null || !skins.containsKey(skinName)) {
            ctx.getSource().sendFailure(
                    Component.translatable("commands.sre.skins.give.unknown_skin", skinName, typeName));
            return 0;
        }

        // 为目标玩家解锁并装备皮肤
        int count = 0;
        for (ServerPlayer target : targets) {
            // 1. 解锁皮肤
            SkinManager.unlockSkinForItemType(target, typeName, skinName);
            // 2. 设置为当前装备皮肤
            SkinManager.setEquippedSkinForItemType(target, typeName, skinName);
            // 3. 在背包中找到匹配物品并设置 SKIN DataComponent
            for (ItemStack stack : target.getInventory().items) {
                if (stack.getItem() instanceof SkinableItem skinable
                        && typeName.equals(skinable.getItemSkinType())) {
                    stack.set(SREDataComponentTypes.SKIN, skinName);
                }
            }
            // 4. 同步到客户端
            SkinManager.sync(target);
            count++;
        }

        final int finalCount = count;
        final String targetNames = targets.stream()
                .map(p -> p.getName().getString())
                .reduce((a, b) -> a + ", " + b)
                .orElse("unknown");

        ctx.getSource().sendSuccess(
                () -> Component.translatable("commands.sre.skins.give.success",
                        skinName, typeName, finalCount, targetNames),
                true);

        return count;
    }

    private static void openSkinScreen(ServerPlayer player) {
        ServerPlayNetworking.send(player, new OpenSkinScreenPaylod());
    }
}