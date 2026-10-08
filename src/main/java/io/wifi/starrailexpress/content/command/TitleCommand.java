package io.wifi.starrailexpress.content.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.wifi.starrailexpress.content.title.Title;
import io.wifi.starrailexpress.content.title.TitleManager;
import io.wifi.starrailexpress.content.title.TitlePlayerComponent;
import io.wifi.starrailexpress.content.title.TitleSavedData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.List;

/**
 * 称号管理命令（管理员）。
 *
 * <pre>
 * /sre:title grant &lt;玩家&gt; &lt;颜色&gt; &lt;pre|suf&gt; &lt;称号文本…&gt;
 * /sre:title revoke &lt;玩家&gt; &lt;称号id&gt;
 * /sre:title list [玩家]
 * /sre:title catalog
 * /sre:title delete &lt;称号id&gt;
 * </pre>
 *
 * <p>颜色支持 MC 的 16 个颜色名（{@code gold}/{@code red}/…）或 {@code #RRGGBB}。
 * 称号文本放最后，用 greedyString 所以**可以带空格**。
 */
public final class TitleCommand {

    private TitleCommand() {
    }

    /** 颜色名补全：16 个 MC 颜色 */
    private static final SuggestionProvider<CommandSourceStack> COLOR_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(
                    List.of("white", "gold", "yellow", "aqua", "blue", "green", "dark_green",
                            "red", "dark_red", "light_purple", "dark_purple", "gray", "dark_gray",
                            "black", "dark_aqua", "dark_blue"),
                    builder);

    /** 已存在称号 id 的补全 */
    private static SuggestionProvider<CommandSourceStack> TITLE_ID_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(
                    TitleSavedData.get(context.getSource().getServer()).all().stream()
                            .map(Title::id),
                    builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sre:title")
                .requires(source -> source.hasPermission(2))

                // ── 发放 ──
                .then(Commands.literal("grant")
                        .then(Commands.argument("player", EntityArgument.players())
                                .then(Commands.argument("color", StringArgumentType.word())
                                        .suggests(COLOR_SUGGESTIONS)
                                        .then(Commands.argument("pos", StringArgumentType.word())
                                                .suggests((c, b) -> SharedSuggestionProvider
                                                        .suggest(List.of("pre", "suf"), b))
                                                .then(Commands.argument("text", StringArgumentType.greedyString())
                                                        .executes(ctx -> grant(
                                                                ctx.getSource(),
                                                                EntityArgument.getPlayers(ctx, "player"),
                                                                StringArgumentType.getString(ctx, "color"),
                                                                StringArgumentType.getString(ctx, "pos"),
                                                                StringArgumentType.getString(ctx, "text"))))))))

                // ── 收回 ──
                .then(Commands.literal("revoke")
                        .then(Commands.argument("player", EntityArgument.players())
                                .then(Commands.argument("titleId", StringArgumentType.word())
                                        .suggests(TITLE_ID_SUGGESTIONS)
                                        .executes(ctx -> revoke(
                                                ctx.getSource(),
                                                EntityArgument.getPlayers(ctx, "player"),
                                                StringArgumentType.getString(ctx, "titleId"))))))

                // ── 查询某个玩家拥有什么 ──
                .then(Commands.literal("list")
                        .executes(ctx -> list(ctx.getSource(), List.of(ctx.getSource().getPlayerOrException())))
                        .then(Commands.argument("player", EntityArgument.players())
                                .executes(ctx -> list(ctx.getSource(),
                                        EntityArgument.getPlayers(ctx, "player")))))

                // ── 全部称号定义 ──
                .then(Commands.literal("catalog")
                        .executes(ctx -> catalog(ctx.getSource())))

                // ── 删除称号定义（所有人都会失去它）──
                .then(Commands.literal("delete")
                        .then(Commands.argument("titleId", StringArgumentType.word())
                                .suggests(TITLE_ID_SUGGESTIONS)
                                .executes(ctx -> delete(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "titleId"))))));
    }

    // ────────────────────────────────────────────────────────────

    private static int grant(CommandSourceStack source, Collection<ServerPlayer> targets,
            String rawColor, String rawPos, String rawText) {
        String text = rawText == null ? "" : rawText.trim();
        if (text.isEmpty()) {
            source.sendFailure(Component.literal("§c称号文本不能为空"));
            return 0;
        }
        if (text.length() > Title.MAX_TEXT_LENGTH) {
            source.sendFailure(Component.literal("§c称号太长（最多 " + Title.MAX_TEXT_LENGTH
                    + " 个字符，当前 " + text.length() + "）"));
            return 0;
        }
        String color = Title.parseColor(rawColor);
        if (color == null) {
            source.sendFailure(Component.literal("§c无法识别的颜色: " + rawColor
                    + "。用 16 色名（gold/red/aqua…）或 #RRGGBB"));
            return 0;
        }
        // 位置：先校验，再决定前后，避免 default 分支里发错误信息却继续往下走
        String pos = rawPos.toLowerCase();
        boolean suffix;
        switch (pos) {
            case "suf", "suffix", "after", "post" -> suffix = true;
            case "pre", "prefix", "before" -> suffix = false;
            default -> {
                source.sendFailure(Component.literal("§c位置只能是 pre（名字前）或 suf（名字后），收到: " + rawPos));
                return 0;
            }
        }

        TitleSavedData data = TitleSavedData.get(source.getServer());
        Title title = data.putIfAbsent(Title.of(text, color, suffix));

        for (ServerPlayer target : targets) {
            TitlePlayerComponent comp = TitlePlayerComponent.KEY.get(target);
            boolean isNew = comp.grant(title.id());
            TitleManager.apply(source.getServer(), target, data);
            target.displayClientMessage(
                    Component.literal(isNew ? "§a你获得了称号：" : "§e你已经有这个称号了：")
                            .append(title.component()),
                    false);
        }

        Component where = Component.literal(suffix ? "名字后" : "名字前");
        // 定义表变了，推给所有在线玩家，客户端才能把 id 渲染成带颜色的文本
        io.wifi.starrailexpress.content.title.TitleNetwork.broadcastCatalog(source.getServer());
        source.sendSuccess(() -> Component.literal("§a已发放称号 ")
                .append(title.component())
                .append(Component.literal(" §7(id=" + title.id() + "，颜色 #" + color + "，位置 " + where.getString() + ") 给 "))
                .append(Component.literal(targets.size() + " 名玩家")), true);
        return targets.size();
    }

    private static int revoke(CommandSourceStack source, Collection<ServerPlayer> targets, String titleId) {
        TitleSavedData data = TitleSavedData.get(source.getServer());
        Title title = data.get(titleId);
        String label = title == null ? titleId : title.displayText();
        int n = 0;
        for (ServerPlayer target : targets) {
            TitlePlayerComponent comp = TitlePlayerComponent.KEY.get(target);
            if (comp.revoke(titleId)) {
                // 如果正装备着，组件已经清空装备，这里把记分板也同步掉
                TitleManager.apply(source.getServer(), target, data);
                target.displayClientMessage(
                        Component.literal("§c你的称号已被收回：" + label), false);
                n++;
            }
        }
        if (n == 0) {
            source.sendFailure(Component.literal("§c目标玩家没有这个称号：" + label));
            return 0;
        }
        final int count = n;
        source.sendSuccess(() -> Component.literal("§a已收回 " + count + " 名玩家的称号：" + label), true);
        return count;
    }

    private static int list(CommandSourceStack source, Collection<ServerPlayer> targets) {
        TitleSavedData data = TitleSavedData.get(source.getServer());
        for (ServerPlayer target : targets) {
            TitlePlayerComponent comp = TitlePlayerComponent.KEY.get(target);
            source.sendSuccess(() -> Component.literal("§6=== " + target.getName().getString()
                    + " 的称号（" + comp.getOwned().size() + " 个）==="), false);
            if (comp.getOwned().isEmpty()) {
                source.sendSuccess(() -> Component.literal("  §7（无）"), false);
                continue;
            }
            for (String id : comp.getOwned()) {
                Title t = data.get(id);
                boolean equipped = comp.isEquipped(id);
                if (t == null) {
                    source.sendSuccess(() -> Component.literal("  §7[已失效] " + id), false);
                    continue;
                }
                source.sendSuccess(() -> Component.literal("  " + (equipped ? "§a✔ " : "§7- "))
                        .append(t.component())
                        .append(Component.literal(" §8(id=" + id + ", " + (t.suffix() ? "名字后" : "名字前") + ")")),
                        false);
            }
        }
        return targets.size();
    }

    private static int catalog(CommandSourceStack source) {
        TitleSavedData data = TitleSavedData.get(source.getServer());
        List<Title> all = data.all();
        source.sendSuccess(() -> Component.literal("§6=== 全部称号（" + all.size() + " 个）==="), false);
        if (all.isEmpty()) {
            source.sendSuccess(() -> Component.literal("  §7（还没有任何称号，用 /sre:title grant 创建）"), false);
            return 0;
        }
        for (Title t : all) {
            source.sendSuccess(() -> Component.literal("  ").append(t.component())
                    .append(Component.literal(" §8#" + t.colorHex() + " "
                            + (t.suffix() ? "名字后" : "名字前") + " id=" + t.id())), false);
        }
        return all.size();
    }

    private static int delete(CommandSourceStack source, String titleId) {
        TitleSavedData data = TitleSavedData.get(source.getServer());
        Title title = data.get(titleId);
        if (title == null) {
            source.sendFailure(Component.literal("§c没有这个称号: " + titleId));
            return 0;
        }
        // 先从所有玩家身上收回，避免留下"拥有但定义不存在"的悬挂记录
        int cleared = 0;
        for (ServerPlayer p : source.getServer().getPlayerList().getPlayers()) {
            TitlePlayerComponent comp = TitlePlayerComponent.KEY.get(p);
            if (comp.revoke(titleId)) {
                TitleManager.apply(source.getServer(), p, data);
                p.displayClientMessage(Component.literal("§c称号已被管理员删除：" + title.displayText()), false);
                cleared++;
            }
        }
        data.remove(titleId);
        TitleManager.removeTeam(source.getServer(), title);
        io.wifi.starrailexpress.content.title.TitleNetwork.broadcastCatalog(source.getServer());
        final int c = cleared;
        source.sendSuccess(() -> Component.literal("§a已删除称号定义 " + title.displayText()
                + "（从 " + c + " 名在线玩家身上收回）"), true);
        return 1;
    }
}
