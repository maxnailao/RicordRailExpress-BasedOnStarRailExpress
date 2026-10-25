package io.wifi.starrailexpress.content.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.wifi.starrailexpress.backpack.BackpackManager;
import io.wifi.starrailexpress.cca.CS2InventoryComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.content.title.TitlePlayerComponent;
import io.wifi.starrailexpress.network.AdminInspectRequestC2SPayload;
import io.wifi.starrailexpress.network.AdminInspectS2CPayload;
import io.wifi.starrailexpress.progression.ProgressionState.FactionCardType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 管理员后台服务端能力：
 * <ul>
 * <li>{@code /sre:admin takecard <玩家> <类型> <数量>} —— 删除阵营卡（含 selfselect 自选卡）</li>
 * <li>{@code /sre:admin reminv <玩家> <类型> <id> <数量>} —— 删除仓库内物品（box/key/skin/musicbox）</li>
 * </ul>
 * 并注册 {@link AdminInspectRequestC2SPayload} 的服务端接收器，聚合目标玩家数据回发管理员。
 * 全部要求权限 2。
 */
public final class AdminManageCommand {

    private AdminManageCommand() {
    }

    private static final SuggestionProvider<CommandSourceStack> CARD_SUGGESTIONS = (ctx, b) -> {
        List<String> list = new ArrayList<>();
        for (FactionCardType t : FactionCardType.values()) {
            if (t != FactionCardType.NONE) list.add(t.questKey);
        }
        list.add("selfselect");
        return SharedSuggestionProvider.suggest(list, b);
    };

    private static final SuggestionProvider<CommandSourceStack> WH_TYPE_SUGGESTIONS =
            (ctx, b) -> SharedSuggestionProvider.suggest(List.of("box", "key", "skin", "musicbox"), b);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sre:admin")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("takecard")
                        .then(Commands.argument("target", EntityArgument.players())
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests(CARD_SUGGESTIONS)
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 9999))
                                                .executes(ctx -> takeCard(
                                                        ctx.getSource(),
                                                        EntityArgument.getPlayers(ctx, "target"),
                                                        StringArgumentType.getString(ctx, "type"),
                                                        IntegerArgumentType.getInteger(ctx, "count")))))))
                .then(Commands.literal("reminv")
                        .then(Commands.argument("target", EntityArgument.players())
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests(WH_TYPE_SUGGESTIONS)
                                        .then(Commands.argument("id", StringArgumentType.string())
                                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 9999))
                                                        .executes(ctx -> removeInventory(
                                                                ctx.getSource(),
                                                                EntityArgument.getPlayers(ctx, "target"),
                                                                StringArgumentType.getString(ctx, "type"),
                                                                StringArgumentType.getString(ctx, "id"),
                                                                IntegerArgumentType.getInteger(ctx, "count")))))))));
    }

    private static int takeCard(CommandSourceStack source, Collection<ServerPlayer> targets, String rawType, int count) {
        int done = 0;
        for (ServerPlayer t : targets) {
            if ("selfselect".equalsIgnoreCase(rawType)) {
                int have = BackpackManager.getSelfSelectCount(t);
                int del = Math.min(have, count);
                if (del > 0) {
                    BackpackManager.addSelfSelectCard(t, -del);
                    done++;
                }
            } else {
                FactionCardType type = FactionCardType.fromString(rawType);
                if (type == FactionCardType.NONE) continue;
                int have = BackpackManager.getCardCount(t, type);
                int del = Math.min(have, count);
                if (del > 0 && BackpackManager.removeCard(t, type, del)) {
                    done++;
                }
            }
        }
        final int d = done;
        source.sendSuccess(() -> Component.literal("§a已为 " + d + " 名玩家删除 " + count + " 张 " + rawType + " 卡"), true);
        return d;
    }

    private static int removeInventory(CommandSourceStack source, Collection<ServerPlayer> targets,
            String type, String id, int count) {
        int done = 0;
        for (ServerPlayer t : targets) {
            CS2InventoryComponent inv = CS2InventoryComponent.KEY.get(t);
            boolean ok = switch (type) {
                case "box" -> {
                    int have = inv.getBoxCount(id);
                    yield have > 0 && inv.removeBox(id, Math.min(have, count));
                }
                case "key" -> {
                    int have = inv.getKeyCount(id);
                    yield have > 0 && inv.removeKey(id, Math.min(have, count));
                }
                case "skin" -> {
                    int have = inv.getSkinCount(id);
                    yield have > 0 && inv.removeSkin(id, Math.min(have, count));
                }
                case "musicbox" -> {
                    int have = inv.getMusicBoxCount(id);
                    yield have > 0 && inv.removeMusicBox(id, Math.min(have, count));
                }
                default -> false;
            };
            if (ok) done++;
        }
        final int d = done;
        source.sendSuccess(() -> Component.literal("§a已从 " + d + " 名玩家仓库删除 " + type + " : " + id), true);
        return d;
    }

    /** 服务端：注册查询接收器（在模组初始化阶段调用）。 */
    public static void registerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(AdminInspectRequestC2SPayload.ID, (payload, context) -> {
            ServerPlayer viewer = context.player();
            context.server().execute(() -> {
                if (viewer == null || !viewer.hasPermissions(2)) {
                    return;
                }
                String wantName = payload.target() == null ? "" : payload.target().trim();
                AdminInspectS2CPayload resp = buildSnapshot(viewer.getServer(), wantName);
                ServerPlayNetworking.send(viewer, resp);
            });
        });
    }

    private static AdminInspectS2CPayload buildSnapshot(net.minecraft.server.MinecraftServer server, String wantName) {
        ServerPlayer target = findByName(server, wantName);
        List<String> cardKeys = new ArrayList<>();
        List<Integer> cardCounts = new ArrayList<>();
        List<String> whTypes = new ArrayList<>();
        List<String> whIds = new ArrayList<>();
        List<Integer> whCounts = new ArrayList<>();
        List<String> titleIds = new ArrayList<>();
        int balance = 0;
        int selfSelect = 0;

        if (target != null) {
            balance = SREPlayerShopComponent.KEY.get(target).balance;
            selfSelect = BackpackManager.getSelfSelectCount(target);
            Map<FactionCardType, Integer> cards = BackpackManager.getCards(target);
            for (FactionCardType t : FactionCardType.values()) {
                if (t == FactionCardType.NONE) continue;
                int c = cards.getOrDefault(t, 0);
                if (c > 0) {
                    cardKeys.add(t.questKey);
                    cardCounts.add(c);
                }
            }
            CS2InventoryComponent inv = CS2InventoryComponent.KEY.get(target);
            addWh(whTypes, whIds, whCounts, "box", inv.getBoxes());
            addWh(whTypes, whIds, whCounts, "key", inv.getKeys());
            addWh(whTypes, whIds, whCounts, "skin", inv.getSkins());
            addWh(whTypes, whIds, whCounts, "musicbox", inv.getMusicBoxes());
            titleIds.addAll(TitlePlayerComponent.KEY.get(target).getOwned());
        }
        return new AdminInspectS2CPayload(wantName, target != null, balance, selfSelect,
                cardKeys, cardCounts, whTypes, whIds, whCounts, titleIds);
    }

    private static void addWh(List<String> types, List<String> ids, List<Integer> counts, String type,
            Map<String, Integer> map) {
        for (Map.Entry<String, Integer> e : map.entrySet()) {
            int c = e.getValue();
            if (c <= 0) continue;
            types.add(type);
            ids.add(e.getKey());
            counts.add(c);
        }
    }

    private static ServerPlayer findByName(net.minecraft.server.MinecraftServer server, String name) {
        if (name == null || name.isEmpty() || name.startsWith("@") || name.startsWith("#")) {
            return null;
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.getName().getString().equalsIgnoreCase(name)) {
                return p;
            }
        }
        return null;
    }
}