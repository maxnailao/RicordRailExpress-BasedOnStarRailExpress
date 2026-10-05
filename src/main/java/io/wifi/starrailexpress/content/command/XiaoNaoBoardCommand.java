package io.wifi.starrailexpress.content.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.wifi.starrailexpress.api.replay.board.ReplayBoardSavedData;
import io.wifi.starrailexpress.api.replay.board.XiaoNaoBoardService;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 小脑 / 被小脑排行榜的投屏命令。
 *
 * <pre>
 * /sre:xiaonao_board create &lt;小脑|被小脑&gt; &lt;坐标&gt; &lt;宽&gt; &lt;高&gt; &lt;朝向&gt; [槽位]
 * /sre:xiaonao_board show   &lt;榜单id|小脑|被小脑&gt;
 * /sre:xiaonao_board hide   &lt;榜单id&gt;
 * /sre:xiaonao_board remove &lt;榜单id&gt;
 * /sre:xiaonao_board list
 * </pre>
 *
 * <p>位置语义与 {@code /sre:replay_screen} 一致（同一个黑板底板 + 文字屏实现），
 * 所以坐标就是屏幕左下角那块黑板的位置，朝向是文字面朝的方向。
 * 坐标支持相对值（{@code ~ ~ ~}）。
 */
public final class XiaoNaoBoardCommand {

    private static final String[] KINDS = { "xiaonao", "beixiaonao", "小脑", "被小脑" };
    private static final String[] DIRECTIONS = { "north", "south", "east", "west" };
    /** 底板开关的写法 */
    private static final String[] BACKGROUNDS = { "bg", "nobg" };
    /** 建议尺寸：够放下标题 + 10 名 */
    private static final int SUGGESTED_W = 8;
    private static final int SUGGESTED_H = 12;

    private static final SimpleCommandExceptionType ERR_UNKNOWN_PLAYER =
            new SimpleCommandExceptionType(Component.literal(
                    "找不到该玩家（名字或 UUID 都没匹配到）。\n"
                            + "支持：在线玩家名、服务器已知玩家名（含离线）、完整 UUID。多个用逗号分隔。"));

    private XiaoNaoBoardCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // 逐层拼装 create 的参数链，最后挂上「底板开关」这个可选尾巴。
        // 拆成局部变量是为了避免深层嵌套把括号写错（之前就踩过）。
        var bgArg = Commands.argument("background", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(BACKGROUNDS, b))
                .executes(ctx -> create(ctx, StringArgumentType.getString(ctx, "slot"),
                        parseBackground(StringArgumentType.getString(ctx, "background"))));
        var slotArg = Commands.argument("slot", StringArgumentType.word())
                .executes(ctx -> create(ctx, StringArgumentType.getString(ctx, "slot"), true))
                .then(bgArg);
        var dirArg = Commands.argument("direction", StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(DIRECTIONS, builder))
                .executes(ctx -> create(ctx, "", true))
                .then(slotArg);
        var heightArg = Commands.argument("height", IntegerArgumentType.integer(2, 32)).then(dirArg);
        var widthArg = Commands.argument("width", IntegerArgumentType.integer(1, 64)).then(heightArg);
        var posArg = Commands.argument("pos", BlockPosArgument.blockPos()).then(widthArg);
        var kindArg = Commands.argument("kind", StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(KINDS, builder))
                .then(posArg);
        var createArg = Commands.literal("create").then(kindArg);

        dispatcher.register(Commands.literal("sre:xiaonao_board")
                .requires(source -> source.hasPermission(2))
                .then(createArg)
                .then(Commands.literal("show")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider
                                        .suggest(boardIds(context), builder))
                                .executes(XiaoNaoBoardCommand::show)))
                .then(Commands.literal("hide")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider
                                        .suggest(boardIds(context), builder))
                                .executes(XiaoNaoBoardCommand::hide)))
                .then(Commands.literal("remove")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider
                                        .suggest(boardIds(context), builder))
                                .executes(XiaoNaoBoardCommand::remove)))
                .then(Commands.literal("list").executes(XiaoNaoBoardCommand::list))
                // 清理"看得见但删不掉"的孤儿文字实体
                .then(Commands.literal("purge").executes(XiaoNaoBoardCommand::purge))

                // ── 单独改某个人的次数 ──
                // set/add/sub <小脑|被小脑> <玩家> <数量>，玩家支持名字或 UUID，逗号可多个
                .then(Commands.literal("set").then(playerCountArgs(
                        (ctx, board, ids, n) -> applyCount(ctx, board, ids, n, 0))))
                .then(Commands.literal("add").then(playerCountArgs(
                        (ctx, board, ids, n) -> applyCount(ctx, board, ids, n, 1))))
                .then(Commands.literal("sub").then(playerCountArgs(
                        (ctx, board, ids, n) -> applyCount(ctx, board, ids, n, -1))))
                // 查看某人在两个榜上的当前次数
                .then(Commands.literal("stats")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(PLAYER_SUGGESTIONS)
                                .executes(XiaoNaoBoardCommand::stats)))
                // 重置累计统计。破坏性操作，必须显式 confirm
                .then(Commands.literal("reset")
                        .executes(XiaoNaoBoardCommand::resetNeedConfirm)
                        .then(Commands.literal("confirm")
                                .executes(XiaoNaoBoardCommand::reset))));
    }

    /** 补全用：已有的榜单屏幕 id */
    private static List<String> boardIds(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        if (level == null) {
            return List.of();
        }
        return XiaoNaoBoardService.screenIdsOf(level);
    }

    private static int create(CommandContext<CommandSourceStack> context, String slot, boolean background) {
        ServerLevel level = context.getSource().getLevel();
        XiaoNaoBoardService.Kind kind = XiaoNaoBoardService.Kind
                .byName(StringArgumentType.getString(context, "kind"));
        if (kind == null) {
            context.getSource().sendFailure(Component.literal("榜单类型只能是 小脑 / 被小脑（xiaonao / beixiaonao）"));
            return 0;
        }
        BlockPos pos = BlockPosArgument.getBlockPos(context, "pos");
        int width = IntegerArgumentType.getInteger(context, "width");
        int height = IntegerArgumentType.getInteger(context, "height");
        Direction direction = Direction.byName(StringArgumentType.getString(context, "direction"));
        if (direction == null || direction.getAxis().isVertical()) {
            context.getSource().sendFailure(Component.literal("朝向只能是 north / south / east / west"));
            return 0;
        }
        XiaoNaoBoardService.Created created = XiaoNaoBoardService.createUnique(level, kind, pos,
                width, height, direction, slot, background);
        context.getSource().sendSuccess(() -> Component.literal(
                "已创建「" + kind.title + "」投屏，id = " + created.id()
                        + "（位置 " + pos.getX() + "," + pos.getY() + "," + pos.getZ()
                        + " 尺寸 " + width + "x" + height + " 朝向 " + direction.getSerializedName()
                        + (background ? " 含黑色羊毛底板" : " 纯文字、不铺底板") + "）"
                        + (created.renamed()
                                ? "\n§e该 id 已被占用，已自动改为 " + created.id()
                                        + "（若要并列放第二个榜，请用不同的 slot 参数）"
                                : ""))
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    /** bg / nobg -> true / false（也接受 yes/no/true/false） */
    private static boolean parseBackground(String value) {
        if (value == null) {
            return true;
        }
        return switch (value.toLowerCase()) {
            case "nobg", "no", "false", "none", "0" -> false;
            default -> true;
        };
    }

    private static int show(CommandContext<CommandSourceStack> context) {
        String id = resolveId(context, StringArgumentType.getString(context, "id"));
        if (id == null) {
            context.getSource().sendFailure(Component.literal(
                    "没有这样的榜单屏幕。先用 create 创建（建议尺寸 " + SUGGESTED_W + "x" + SUGGESTED_H + "），"
                            + "或 /sre:xiaonao_board list 看看已有哪些。"));
            return 0;
        }
        if (!XiaoNaoBoardService.show(context.getSource().getLevel(), id)) {
            context.getSource().sendFailure(Component.literal("显示失败: " + id));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("已开始显示并自动刷新榜单: " + id)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int hide(CommandContext<CommandSourceStack> context) {
        String id = resolveId(context, StringArgumentType.getString(context, "id"));
        if (id == null || !XiaoNaoBoardService.hide(context.getSource().getLevel(), id)) {
            context.getSource().sendFailure(Component.literal("无法隐藏（id 不存在或未在显示）: "
                    + StringArgumentType.getString(context, "id")));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("已停止刷新并清空显示: " + id)
                .withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> context) {
        String id = resolveId(context, StringArgumentType.getString(context, "id"));
        if (id == null || !XiaoNaoBoardService.remove(context.getSource().getLevel(), id)) {
            context.getSource().sendFailure(Component.literal("没有这样的榜单屏幕: "
                    + StringArgumentType.getString(context, "id")));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("已删除投屏（含底板方块）: " + id)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int resetNeedConfirm(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal(
                "重置会永久清空【累计】的小脑 / 被小脑 统计（不可撤销）。\n"
                        + "确认请执行: /sre:xiaonao_board reset confirm")
                .withStyle(ChatFormatting.YELLOW), false);
        return 0;
    }

    private static int reset(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        int repainted = XiaoNaoBoardService.resetStats(level);
        context.getSource().sendSuccess(() -> Component.literal(
                "已重置小脑排行榜统计（累计数据已清空），已刷新 " + repainted + " 个投屏。")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        List<String> ids = XiaoNaoBoardService.screenIdsOf(level);
        ReplayBoardSavedData data = ReplayBoardSavedData.get(level);
        List<String> lines = new ArrayList<>();
        // 按榜单类型分组编号，和 小脑:2 / 被小脑:2 的写法对应
        for (var kind : XiaoNaoBoardService.Kind.values()) {
            List<String> group = matchesOf(level, kind);
            for (int i = 0; i < group.size(); i++) {
                String id = group.get(i);
                var entry = data.getScreen(id).orElse(null);
                if (entry == null) {
                    continue;
                }
                // 第一个是默认目标（写「小脑」就会命中它），之后依次 小脑:2、小脑:3 …
                String handle = i == 0 ? kind.title
                        : kind.title + ":" + (i + 1);
                lines.add("- " + handle + "  → " + id + " [" + entry.width() + "x" + entry.height() + " "
                        + entry.direction().getSerializedName() + "] @ "
                        + entry.origin().getX() + "," + entry.origin().getY() + "," + entry.origin().getZ()
                        + " (" + entry.dimension().location() + ")");
            }
        }

        // 孤儿：看得见但存档里没记录，用普通 remove 删不掉，需要 purge
        int orphanCount = XiaoNaoBoardService.findOrphanTextDisplays(level).size();

        if (lines.isEmpty() && orphanCount == 0) {
            context.getSource().sendSuccess(() -> Component.literal(
                    "还没有任何小脑排行榜投屏。示例：\n"
                            + "/sre:xiaonao_board create 小脑 ~ ~ ~ " + SUGGESTED_W + " " + SUGGESTED_H + " north\n"
                            + "/sre:xiaonao_board create 被小脑 ~ ~ ~2 " + SUGGESTED_W + " " + SUGGESTED_H + " north\n"
                            + "最后可选 bg（铺黑色羊毛底板，默认）或 nobg（纯文字、不动地图方块）")
                    .withStyle(ChatFormatting.GRAY), false);
            return 1;
        }

        StringBuilder body = new StringBuilder("小脑排行榜投屏：");
        for (String line : lines) {
            body.append("\n").append(line);
        }
        if (lines.isEmpty()) {
            body.append("\n（没有已登记的投屏）");
        }
        if (orphanCount > 0) {
            body.append("\n§e⚠ 检测到 ").append(orphanCount)
                    .append(" 个孤儿文字实体§r（屏幕上看得见、但存档里没记录，")
                    .append("用 remove 删不掉）。\n§e用 /sre:xiaonao_board purge 清理它们§r")
                    .append("（清理后如果还剩黑羊毛底板，需要手动挖掉——底板位置信息已丢失）。");
        }
        Component result = Component.literal(body.toString()).withStyle(ChatFormatting.WHITE);
        context.getSource().sendSuccess(() -> result, false);
        return 1;
    }

    // =========================================================================
    // 单独改某个人的次数
    // =========================================================================

    /** 榜单种类补全 */
    private static final String[] BOARDS = { "小脑", "被小脑", "xiaonao", "beixiaonao" };

    /** 玩家补全：在线玩家 + 榜上已有的人（含离线） */
    private static final com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> PLAYER_SUGGESTIONS =
            (context, builder) -> {
                List<String> ids = new ArrayList<>();
                var server = context.getSource().getServer();
                if (server != null) {
                    for (var p : server.getPlayerList().getPlayers()) {
                        ids.add(p.getGameProfile().getName());
                    }
                }
                // 榜上记录过的人（可能已离线），用名字补全
                for (String name : io.wifi.starrailexpress.api.replay.board.XiaoNaoBoardStats
                        .knownNames().values()) {
                    if (name != null && !name.isBlank() && !ids.contains(name)) {
                        ids.add(name);
                    }
                }
                return SharedSuggestionProvider.suggest(ids, builder);
            };

    /** 把 <玩家> <数量> 这两层包进 <榜单> 里，避免重复写三遍参数链 */
    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> playerCountArgs(
            CountAction action) {
        return Commands.argument("board", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(BOARDS, b))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(PLAYER_SUGGESTIONS)
                        .then(Commands.argument("count", IntegerArgumentType.integer(0, 99999))
                                .executes(ctx -> action.run(ctx,
                                        boardOf(StringArgumentType.getString(ctx, "board")),
                                        resolvePlayers(ctx, StringArgumentType.getString(ctx, "player")),
                                        IntegerArgumentType.getInteger(ctx, "count")))));
    }

    @FunctionalInterface
    private interface CountAction {
        int run(CommandContext<CommandSourceStack> ctx,
                io.wifi.starrailexpress.api.replay.board.XiaoNaoBoardStats.Board board,
                List<UUID> ids, int count) throws CommandSyntaxException;
    }

    /** 数量改动的统一执行体：mode 0=设定 1=增加 -1=减少 */
    private static int applyCount(CommandContext<CommandSourceStack> ctx,
            io.wifi.starrailexpress.api.replay.board.XiaoNaoBoardStats.Board board,
            List<UUID> ids, int count, int mode) throws CommandSyntaxException {
        if (board == null) {
            ctx.getSource().sendFailure(Component.literal("榜单只能是 小脑 / 被小脑（xiaonao / beixiaonao）"));
            return 0;
        }
        List<String> report = new ArrayList<>();
        Map<String, String> names = knownNameMap(ctx);
        for (UUID id : ids) {
            String name = names.getOrDefault(id.toString(), id.toString().substring(0, 8));
            int before = io.wifi.starrailexpress.api.replay.board.XiaoNaoBoardStats.getCount(board, id);
            int after;
            if (mode == 0) {
                after = io.wifi.starrailexpress.api.replay.board.XiaoNaoBoardStats
                        .setCount(board, id, name, count);
            } else {
                after = io.wifi.starrailexpress.api.replay.board.XiaoNaoBoardStats
                        .addCount(board, id, name, mode * count);
            }
            report.add(name + ": " + before + " → " + after);
        }
        io.wifi.starrailexpress.api.replay.board.XiaoNaoBoardStats
                .save(ctx.getSource().getServer());
        int repainted = XiaoNaoBoardService.refreshAll(ctx.getSource().getServer());
        final String boardName = board == io.wifi.starrailexpress.api.replay.board.XiaoNaoBoardStats.Board.XIAONAO
                ? "小脑榜"
                : "被小脑榜";
        final String joined = String.join("；", report);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "已更新 " + boardName + "：" + joined
                        + "（重置投屏 " + repainted + " 个）")
                .withStyle(ChatFormatting.GREEN), true);
        return ids.size();
    }

    private static io.wifi.starrailexpress.api.replay.board.XiaoNaoBoardStats.Board boardOf(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.toLowerCase()) {
            case "小脑", "xiaonao", "top" ->
                io.wifi.starrailexpress.api.replay.board.XiaoNaoBoardStats.Board.XIAONAO;
            case "被小脑", "beixiaonao", "beaten" ->
                io.wifi.starrailexpress.api.replay.board.XiaoNaoBoardStats.Board.BEI_XIAONAO;
            default -> null;
        };
    }

    /** 查看某人在两个榜上的次数 */
    private static int stats(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        List<UUID> ids = resolvePlayers(context, StringArgumentType.getString(context, "player"));
        Map<String, String> names = knownNameMap(context);
        for (UUID id : ids) {
            String name = names.getOrDefault(id.toString(), id.toString().substring(0, 8));
            int xn = io.wifi.starrailexpress.api.replay.board.XiaoNaoBoardStats
                    .getCount(io.wifi.starrailexpress.api.replay.board.XiaoNaoBoardStats.Board.XIAONAO, id);
            int bx = io.wifi.starrailexpress.api.replay.board.XiaoNaoBoardStats
                    .getCount(io.wifi.starrailexpress.api.replay.board.XiaoNaoBoardStats.Board.BEI_XIAONAO, id);
            final String line = name + "  → 小脑 " + xn + " 次，被小脑 " + bx + " 次  (" + id + ")";
            context.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    /**
     * 解析玩家参数：支持名字（在线优先，其次服务器已知玩家/榜上记录）和直接写 UUID。
     * 逗号分隔可一次多个。
     */
    private static List<UUID> resolvePlayers(CommandContext<CommandSourceStack> context, String raw)
            throws CommandSyntaxException {
        List<UUID> out = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (String part : raw.split(",")) {
            String sel = part.trim();
            if (sel.isEmpty()) {
                continue;
            }
            UUID found = resolveOnePlayer(context, sel);
            if (found == null) {
                throw ERR_UNKNOWN_PLAYER.create();
            }
            if (seen.add(found)) {
                out.add(found);
            }
        }
        if (out.isEmpty()) {
            throw ERR_UNKNOWN_PLAYER.create();
        }
        return out;
    }

    private static UUID resolveOnePlayer(CommandContext<CommandSourceStack> context, String sel) {
        // 直接给 UUID
        try {
            return UUID.fromString(sel);
        } catch (IllegalArgumentException ignored) {
            // 不是 UUID，按名字找
        }
        var server = context.getSource().getServer();
        if (server != null) {
            var online = server.getPlayerList().getPlayerByName(sel);
            if (online != null) {
                return online.getUUID();
            }
        }
        // 榜上记录过的名字（可能已离线）
        for (Map.Entry<UUID, String> e
                : io.wifi.starrailexpress.api.replay.board.XiaoNaoBoardStats.knownNames().entrySet()) {
            if (sel.equalsIgnoreCase(e.getValue())) {
                return e.getKey();
            }
        }
        // 服务器已知玩家（usercache，含从未上榜的离线玩家）
        if (server != null) {
            for (Map.Entry<UUID, String> e
                    : io.wifi.starrailexpress.content.mail.OfflineMailService.knownPlayers(server).entrySet()) {
                if (sel.equalsIgnoreCase(e.getValue())) {
                    return e.getKey();
                }
            }
        }
        return null;
    }

    /** uuid 字符串 -> 名字，来自 usercache + 榜上记录 */
    private static Map<String, String> knownNameMap(CommandContext<CommandSourceStack> context) {
        Map<String, String> out = new HashMap<>();
        for (Map.Entry<UUID, String> e
                : io.wifi.starrailexpress.api.replay.board.XiaoNaoBoardStats.knownNames().entrySet()) {
            out.put(e.getKey().toString(), e.getValue());
        }
        var server = context.getSource().getServer();
        if (server != null) {
            for (Map.Entry<UUID, String> e
                    : io.wifi.starrailexpress.content.mail.OfflineMailService.knownPlayers(server).entrySet()) {
                out.putIfAbsent(e.getKey().toString(), e.getValue());
            }
        }
        return out;
    }

    private static int purge(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        int n = XiaoNaoBoardService.purgeOrphans(level);
        if (n == 0) {
            context.getSource().sendSuccess(() -> Component.literal("没有发现孤儿榜单文字实体。")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        final int total = n;
        context.getSource().sendSuccess(() -> Component.literal(
                "已清理 " + total + " 个孤儿榜单文字实体。\n"
                        + "§7提示：文字消失了，但如果原地还剩黑色羊毛底板，需要手动挖掉"
                        + "——孤儿屏的底板位置信息随被覆盖的存档记录一起丢了。")
                .withStyle(ChatFormatting.GREEN), true);
        return total;
    }

    /**
     * 允许直接写「小脑 / 被小脑」，自动解析成完整屏幕 id。
     *
     * <p>同名可能有多个（历史遗留的重名，或自动加后缀的 {@code _2}/{@code _3}），
     * 所以除了精确匹配，还会按前缀找出所有相关屏幕：
     * <ul>
     * <li>写 {@code 小脑} / {@code xiaonao}：优先精确 id，其次第一个前缀匹配（用于 show）；</li>
     * <li>写 {@code 小脑:2}：取第 2 个前缀匹配（用于删掉"先放的那块"以外的任意一块）。</li>
     * </ul>
     */
    private static String resolveId(CommandContext<CommandSourceStack> context, String raw) {
        ServerLevel level = context.getSource().getLevel();
        if (level == null) {
            return null;
        }
        ReplayBoardSavedData data = ReplayBoardSavedData.get(level);
        // 精确匹配优先（用户可能直接粘贴 list 里的完整 id）
        if (data.getScreen(raw).isPresent()) {
            return raw;
        }
        // 小脑:2 形式
        String name = raw;
        Integer pick = null;
        int colon = raw.lastIndexOf(':');
        if (colon > 0) {
            try {
                pick = Integer.parseInt(raw.substring(colon + 1));
                name = raw.substring(0, colon);
            } catch (NumberFormatException ignored) {
                pick = null;
                name = raw;
            }
        }
        XiaoNaoBoardService.Kind kind = XiaoNaoBoardService.Kind.byName(name);
        if (kind == null) {
            return null;
        }
        List<String> matches = matchesOf(level, kind);
        if (matches.isEmpty()) {
            return null;
        }
        if (pick != null) {
            return (pick >= 1 && pick <= matches.size()) ? matches.get(pick - 1) : null;
        }
        return matches.get(0);
    }

    /** 某个榜单类型下的所有屏幕 id（精确 id 排最前，然后按后缀顺序） */
    private static List<String> matchesOf(ServerLevel level, XiaoNaoBoardService.Kind kind) {
        ReplayBoardSavedData data = ReplayBoardSavedData.get(level);
        List<String> out = new ArrayList<>();
        if (data.getScreen(kind.screenId).isPresent()) {
            out.add(kind.screenId);
        }
        List<String> rest = new ArrayList<>();
        for (String id : XiaoNaoBoardService.screenIdsOf(level)) {
            if (id.equals(kind.screenId)) {
                continue;
            }
            if (id.startsWith(kind.screenId)) {
                rest.add(id);
            }
        }
        // 让 _2 排在 _10 前面，而不是按字符串排序
        rest.sort((a, b) -> {
            int sa = suffixOf(a, kind.screenId);
            int sb = suffixOf(b, kind.screenId);
            if (sa != sb) {
                return Integer.compare(sa, sb);
            }
            return a.compareTo(b);
        });
        out.addAll(rest);
        return out;
    }

    private static int suffixOf(String id, String base) {
        String tail = id.substring(base.length());
        if (tail.startsWith("_")) {
            try {
                return Integer.parseInt(tail.substring(1));
            } catch (NumberFormatException ignored) {
                // 自定义 slot 后缀，排在数字后缀之后
                return Integer.MAX_VALUE;
            }
        }
        return 0;
    }
}
