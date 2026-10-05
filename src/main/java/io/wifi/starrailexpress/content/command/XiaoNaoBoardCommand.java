package io.wifi.starrailexpress.content.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
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
import java.util.List;

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
        ReplayBoardSavedData.ReplayScreenEntry entry = XiaoNaoBoardService.create(level, kind, pos,
                width, height, direction, slot, background);
        context.getSource().sendSuccess(() -> Component.literal(
                "已创建「" + kind.title + "」投屏，id = " + entry.id()
                        + "（位置 " + pos.getX() + "," + pos.getY() + "," + pos.getZ()
                        + " 尺寸 " + width + "x" + height + " 朝向 " + direction.getSerializedName()
                        + (background ? " 含黑色羊毛底板" : " 纯文字、不铺底板") + "）")
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
        if (ids.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal(
                    "还没有任何小脑排行榜投屏。示例：\n"
                            + "/sre:xiaonao_board create 小脑 ~ ~ ~ " + SUGGESTED_W + " " + SUGGESTED_H + " north\n"
                            + "/sre:xiaonao_board create 被小脑 ~ ~ ~2 " + SUGGESTED_W + " " + SUGGESTED_H + " north\n"
                            + "最后可选 bg（铺黑色羊毛底板，默认）或 nobg（纯文字、不动地图方块）")
                    .withStyle(ChatFormatting.GRAY), false);
            return 1;
        }
        ReplayBoardSavedData data = ReplayBoardSavedData.get(level);
        List<String> lines = new ArrayList<>();
        for (String id : ids) {
            var entry = data.getScreen(id).orElse(null);
            if (entry == null) {
                continue;
            }
            lines.add("- " + id + " [" + entry.width() + "x" + entry.height() + " "
                    + entry.direction().getSerializedName() + "] @ "
                    + entry.origin().getX() + "," + entry.origin().getY() + "," + entry.origin().getZ()
                    + " (" + entry.dimension().location() + ")");
        }
        Component message = Component.literal("小脑排行榜投屏：").withStyle(ChatFormatting.GOLD);
        for (String line : lines) {
            message = message.copy().append(Component.literal("\n" + line).withStyle(ChatFormatting.WHITE));
        }
        Component result = message;
        context.getSource().sendSuccess(() -> result, false);
        return 1;
    }

    /** 允许直接写「小脑 / 被小脑」，自动解析成完整屏幕 id */
    private static String resolveId(CommandContext<CommandSourceStack> context, String raw) {
        ServerLevel level = context.getSource().getLevel();
        if (level == null) {
            return null;
        }
        XiaoNaoBoardService.Kind kind = XiaoNaoBoardService.Kind.byName(raw);
        if (kind != null) {
            // 没有槽位后缀的优先；否则取第一个匹配前缀的
            ReplayBoardSavedData data = ReplayBoardSavedData.get(level);
            if (data.getScreen(kind.screenId).isPresent()) {
                return kind.screenId;
            }
            for (String id : XiaoNaoBoardService.screenIdsOf(level)) {
                if (id.startsWith(kind.screenId)) {
                    return id;
                }
            }
            return null;
        }
        return ReplayBoardSavedData.get(level).getScreen(raw).isPresent() ? raw : null;
    }
}
