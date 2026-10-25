package io.wifi.starrailexpress.announcement;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.List;

/**
 * /sre:announce add <标题> <正文>   —— 发布公告（推给所有在线玩家并弹顶部提示）
 * /sre:announce remove <序号>          —— 按界面序号删除
 * /sre:announce list                    —— 列出
 * /sre:announce clear                   —— 清空全部
 */
public class AnnouncementCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sre:announce")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("add")
                        .then(Commands.argument("title", StringArgumentType.string())
                                .then(Commands.argument("content", StringArgumentType.greedyString())
                                        .executes(AnnouncementCommand::publish))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                .executes(AnnouncementCommand::remove)))
                .then(Commands.literal("list").executes(AnnouncementCommand::list))
                .then(Commands.literal("clear").executes(AnnouncementCommand::clear)));
    }

    private static int publish(CommandContext<CommandSourceStack> ctx) {
        String title = StringArgumentType.getString(ctx, "title");
        String content = StringArgumentType.getString(ctx, "content");
        CommandSourceStack source = ctx.getSource();
        String publisher = source.getTextName();
        MinecraftServer server = source.getServer();
        AnnouncementManager.getInstance().publish(server, title, content, publisher);
        source.sendSuccess(() -> Component.literal("已发布公告：" + title).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> ctx) {
        int index = IntegerArgumentType.getInteger(ctx, "index");
        boolean ok = AnnouncementManager.getInstance().remove(index, ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> ok
                ? Component.literal("已删除第 " + index + " 条公告").withStyle(ChatFormatting.GREEN)
                : Component.literal("序号不存在：" + index).withStyle(ChatFormatting.RED), true);
        return ok ? 1 : 0;
    }

    private static int clear(CommandContext<CommandSourceStack> ctx) {
        AnnouncementManager.getInstance().clear(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal("已清空全部公告").withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        List<AnnouncementEntry> all = AnnouncementManager.getInstance().getEntries();
        if (all.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("当前没有公告").withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        for (int i = 0; i < all.size(); i++) {
            AnnouncementEntry e = all.get(i);
            final int no = i + 1;
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "#" + no + "  " + e.title() + "  (" + e.publisher() + ")"), false);
        }
        return all.size();
    }
}