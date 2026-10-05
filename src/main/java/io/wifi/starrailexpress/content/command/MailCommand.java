package io.wifi.starrailexpress.content.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.wifi.starrailexpress.content.mail.Mail;
import io.wifi.starrailexpress.content.mail.MailboxComponent;
import io.wifi.starrailexpress.content.mail.OfflineMailService;
import io.wifi.starrailexpress.content.musicbox.MusicBoxRegistry;
import io.wifi.starrailexpress.util.ItemSkinManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.agmas.noellesroles.cs2.CS2BoxConfig;
import org.agmas.noellesroles.cs2.CS2BoxManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 邮件管理命令：给玩家发带奖励的邮件。
 *
 * <pre>
 * /mail send &lt;玩家&gt; &lt;标题&gt; &lt;正文&gt; [附加项...]
 * /mail list &lt;玩家&gt;
 * /mail clear &lt;玩家&gt;
 * </pre>
 *
 * <p><b>附加项</b>（正文之后按空格续写，顺序随意、可重复）：
 * <ul>
 * <li>{@code --money &lt;数量&gt;} 金币，领取时执行 {@code /tmm:money add}</li>
 * <li>{@code --item &lt;物品id&gt; [数量]} 普通物品，直接进背包（默认 1）</li>
 * <li>{@code --cs2 skin|box|key|music &lt;id&gt; [数量]} 仓库物品（默认 1）</li>
 * <li>{@code --days &lt;天数&gt;} 有效期，默认永不过期</li>
 * </ul>
 *
 * <p>金币与仓库物品统一转成 {@code claimCommands}（因为仓库存的是 id 计数、不是 ItemStack），
 * 普通物品走 {@code attachments}。
 *
 * <p>例子：
 * <pre>
 * /mail send Steve 补偿 服务器昨晚炸了，一点心意 --money 500 --cs2 skin hat/hat_ricord_xiaodangao
 * /mail send Alex 欢迎 送你一套 --item minecraft:diamond 3 --cs2 box hat_box --cs2 music triumph_01
 * </pre>
 */
public final class MailCommand {

    private MailCommand() {
    }

    private static final SimpleCommandExceptionType ERR_NO_ITEM =
            new SimpleCommandExceptionType(Component.literal("未知的物品 id"));
    private static final SimpleCommandExceptionType ERR_BAD_CS2_TYPE =
            new SimpleCommandExceptionType(Component.literal("--cs2 的类型只能是 skin / box / key / music"));
    private static final SimpleCommandExceptionType ERR_UNKNOWN_CS2_ID =
            new SimpleCommandExceptionType(Component.literal("未知的仓库物品 id"));
    private static final SimpleCommandExceptionType ERR_BAD_FLAG =
            new SimpleCommandExceptionType(Component.literal("附加项参数不足或格式不对"));
    private static final SimpleCommandExceptionType ERR_NOTHING =
            new SimpleCommandExceptionType(Component.literal("邮件既没有正文也没有附件，已取消发送"));

    /** 目标补全：在线玩家 + all/@a 等"含离线玩家"的写法 */
    private static final com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> TARGET_SUGGESTIONS =
            (context, builder) -> {
                List<String> ids = new ArrayList<>();
                ids.add("all");
                ids.add("@a");
                ids.add("@p");
                ids.add("@s");
                var server = context.getSource().getServer();
                if (server != null) {
                    for (var p : server.getPlayerList().getPlayers()) {
                        ids.add(p.getGameProfile().getName());
                    }
                }
                return SharedSuggestionProvider.suggest(ids, builder);
            };

    /** 附加项补全：把常用旗标与可用的仓库 id 都列出来 */
    private static final com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> FLAG_SUGGESTIONS =
            (context, builder) -> {
                List<String> ids = new ArrayList<>();
                ids.add("--money");
                ids.add("--item");
                ids.add("--cs2");
                ids.add("--days");
                ids.add("--cs2 skin");
                ids.add("--cs2 box");
                ids.add("--cs2 key");
                ids.add("--cs2 music");
                // 皮肤
                for (var entry : ItemSkinManager.getSkins().entrySet()) {
                    for (String skinName : entry.getValue().keySet()) {
                        if (!"default".equals(skinName)) {
                            ids.add("--cs2 skin " + entry.getKey() + "/" + skinName);
                        }
                    }
                }
                // 箱子 / 钥匙
                var mgr = CS2BoxManager.getInstance();
                if (mgr != null) {
                    for (String boxId : mgr.getBoxIds()) {
                        ids.add("--cs2 box " + boxId);
                    }
                    for (CS2BoxConfig cfg : mgr.getAllBoxes()) {
                        String key = cfg.getKeyName();
                        if (key != null && !key.isEmpty()) {
                            ids.add("--cs2 key " + key);
                        }
                    }
                }
                // 音乐盒
                for (var mb : MusicBoxRegistry.getAll()) {
                    ids.add("--cs2 music " + mb.id());
                }
                for (ResourceLocation rl : BuiltInRegistries.ITEM.keySet()) {
                    ids.add("--item " + rl);
                }
                return SharedSuggestionProvider.suggest(ids, builder);
            };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mail")
                .requires(source -> source.hasPermission(2))

                .then(Commands.literal("send")
                        // 用 word 而不是 greedyString：greedyString 会把后面的标题也吞掉。
                        // 玩家名/选择器都不含空格，word 足够；多个目标用逗号分隔。
                        .then(Commands.argument("targets", StringArgumentType.word())
                                .suggests(TARGET_SUGGESTIONS)
                                .then(Commands.argument("title", StringArgumentType.string())
                                        .then(Commands.argument("content", StringArgumentType.string())
                                                .executes(ctx -> send(ctx, ""))
                                                // 附加项：整个尾部作为一段文本自己解析，
                                                // 这样顺序随意、可重复，也不用为每种组合写死语法树
                                                .then(Commands.argument("flags", StringArgumentType.greedyString())
                                                        .suggests(FLAG_SUGGESTIONS)
                                                        .executes(ctx -> send(ctx,
                                                                StringArgumentType.getString(ctx, "flags"))))))))

                .then(Commands.literal("list")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(MailCommand::list)))

                .then(Commands.literal("clear")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(MailCommand::clear))));
    }

    // =========================================================================
    // 发送
    // =========================================================================

    private static int send(CommandContext<CommandSourceStack> ctx, String flags)
            throws CommandSyntaxException {
        String rawTargets = StringArgumentType.getString(ctx, "targets");
        String title = StringArgumentType.getString(ctx, "title");
        String content = StringArgumentType.getString(ctx, "content");

        // ── 解析附加项 ──
        List<ItemStack> attachments = new ArrayList<>();
        List<String> commands = new ArrayList<>();
        int days = 0;

        String[] tok = flags.trim().isEmpty() ? new String[0] : flags.trim().split("\\s+");
        for (int i = 0; i < tok.length; i++) {
            String flag = tok[i];
            switch (flag) {
                case "--money" -> {
                    int amount = readInt(tok, ++i, "--money");
                    if (amount <= 0) throw ERR_BAD_FLAG.create();
                    commands.add("tmm:money add " + amount + " {player}");
                }
                case "--days" -> days = readInt(tok, ++i, "--days");
                case "--item" -> {
                    String id = readStr(tok, ++i, "--item");
                    int count = readOptionalInt(tok, i + 1);
                    if (count > 0) i++;
                    Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
                    if (item == null || item == Items.AIR) throw ERR_NO_ITEM.create();
                    attachments.add(new ItemStack(item, Math.max(1, count)));
                }
                case "--cs2" -> {
                    String type = readStr(tok, ++i, "--cs2").toLowerCase();
                    String id = readStr(tok, ++i, "--cs2");
                    int count = readOptionalInt(tok, i + 1);
                    if (count > 0) i++;
                    commands.addAll(cs2Commands(type, id, Math.max(1, count)));
                }
                default -> throw ERR_BAD_FLAG.create();
            }
        }

        if (content.isBlank() && attachments.isEmpty() && commands.isEmpty()) {
            throw ERR_NOTHING.create();
        }

        // ── 解析收件人 ──
        // all / @a：服务器见过的所有玩家（含离线）。@p/@s/名字：走原版选择器（只能在线）。
        boolean toAll = "all".equalsIgnoreCase(rawTargets) || "*".equals(rawTargets)
                || "@a".equals(rawTargets);
        List<ServerPlayer> onlineTargets = new ArrayList<>();
        Map<UUID, String> offlineTargets = new LinkedHashMap<>();
        if (toAll) {
            if (!OfflineMailService.isAvailable()) {
                ctx.getSource().sendFailure(Component.literal(
                        "无法发给全体（含离线）: " + OfflineMailService.unavailableReason()));
                return 0;
            }
            Map<UUID, String> known = OfflineMailService.knownPlayers(ctx.getSource().getServer());
            var server = ctx.getSource().getServer();
            Set<UUID> onlineIds = new HashSet<>();
            if (server != null) {
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    onlineIds.add(p.getUUID());
                }
            }
            for (Map.Entry<UUID, String> e : known.entrySet()) {
                if (onlineIds.contains(e.getKey())) {
                    continue; // 在线的走组件投递，稍后单独处理
                }
                offlineTargets.put(e.getKey(), e.getValue());
            }
            if (server != null) {
                onlineTargets.addAll(server.getPlayerList().getPlayers());
            }
            if (onlineTargets.isEmpty() && offlineTargets.isEmpty()) {
                ctx.getSource().sendFailure(Component.literal("服务器没有已知玩家（usercache.json 为空）"));
                return 0;
            }
        } else {
            // 允许逗号分隔多个名字 / 混合选择器
            for (String part : rawTargets.split(",")) {
                String sel = part.trim();
                if (sel.isEmpty()) {
                    continue;
                }
                // 纯玩家名（或 UUID）自动包成 name= 选择器；其余按原版选择器解析
                String expr = sel.startsWith("@") ? sel
                        : "@a[name=\"" + sel.replace("\"", "") + "\"]";
                List<ServerPlayer> found;
                try {
                    var parser = new EntitySelectorParser(new com.mojang.brigadier.StringReader(expr), true);
                    found = parser.parse().findPlayers(ctx.getSource());
                } catch (Exception e) {
                    ctx.getSource().sendFailure(Component.literal("找不到玩家或选择器无效: " + sel));
                    return 0;
                }
                if (found.isEmpty()) {
                    ctx.getSource().sendFailure(Component.literal(
                            "没有匹配到在线玩家: " + sel + "（离线玩家请用 all）"));
                    return 0;
                }
                for (ServerPlayer p : found) {
                    if (!onlineTargets.contains(p)) {
                        onlineTargets.add(p);
                    }
                }
            }
            if (onlineTargets.isEmpty()) {
                ctx.getSource().sendFailure(Component.literal("没有匹配到任何在线玩家: " + rawTargets));
                return 0;
            }
        }

        // ── 构造并投递 ──
        long now = System.currentTimeMillis();
        long expiresAt = days > 0 ? now + (long) days * 24L * 60L * 60L * 1000L : 0L;
        String sender = ctx.getSource().getTextName();
        int money = commands.stream().anyMatch(c -> c.startsWith("tmm:money add")) ? 1 : 0;

        int sentOnline = 0;
        for (ServerPlayer target : onlineTargets) {
            MailboxComponent box = MailboxComponent.KEY.get(target);
            box.sendMail(new Mail(UUID.randomUUID(), sender, title, content,
                    attachments, commands, now, expiresAt));
            box.sync();
            sentOnline++;
        }

        // 离线玩家：直接写持久层（没有 CCA 组件实例可用）
        int sentOffline = 0;
        int failedOffline = 0;
        if (!offlineTargets.isEmpty()) {
            var server = ctx.getSource().getServer();
            for (UUID uuid : offlineTargets.keySet()) {
                Mail mail = new Mail(UUID.randomUUID(), sender, title, content,
                        attachments, commands, now, expiresAt);
                if (OfflineMailService.deliver(server, uuid, mail)) {
                    sentOffline++;
                } else {
                    failedOffline++;
                }
            }
        }

        final int total = sentOnline + sentOffline;
        final int cmdCount = commands.size();
        final int itemCount = attachments.size();
        final int validDays = days;
        final int offFail = failedOffline;
        final int onCount = sentOnline;
        final int offCount = sentOffline;
        final boolean hasAttachments = !attachments.isEmpty();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "已发送邮件「" + title + "」：在线 " + onCount + " 人"
                        + (offCount > 0 ? "，离线 " + offCount + " 人" : "")
                        + (offFail > 0 ? "，§c离线失败 " + offFail + " 人§r" : "")
                        + (money > 0 ? " §a含金币" : "")
                        + (cmdCount > 0 ? " §a领取指令x" + cmdCount : "")
                        + (itemCount > 0 ? " §a物品x" + itemCount : "")
                        + (validDays > 0 ? " §7有效期 " + validDays + " 天" : "")
                        + (hasAttachments && offCount > 0
                                ? "\n§e注意：物品附件对离线玩家会丢失（邮箱 JSON 不存 ItemStack）；"
                                        + "金币与仓库物品不受影响"
                                : ""))
                .withStyle(s -> s.withColor(0x00FF00)), true);
        return total;
    }

    /**
     * 把仓库物品转成领取指令（{@code /giveCS2box} 支持 box/key/skin/music）。
     *
     * <p>该命令一次只发 1 个，所以数量 N 就展开成 N 条独立指令。
     * <b>不能</b>用 {@code "cmd1; cmd2"} 拼接：{@code performPrefixedCommand} 不认分号链，
     * 会把 {@code "id;"} 当成参数的一部分。
     */
    private static List<String> cs2Commands(String type, String id, int count)
            throws CommandSyntaxException {
        String sub = switch (type) {
            case "skin" -> "skin";
            case "box" -> "box";
            case "key" -> "key";
            case "music" -> "music";
            default -> throw ERR_BAD_CS2_TYPE.create();
        };
        if (!isKnownCs2Id(sub, id)) {
            throw ERR_UNKNOWN_CS2_ID.create();
        }
        List<String> out = new ArrayList<>();
        for (int n = 0; n < count; n++) {
            out.add("giveCS2box {player} " + sub + " " + id);
        }
        return out;
    }

    /** 校验仓库物品 id 是否真实存在，避免发出去一封领不到东西的邮件 */
    private static boolean isKnownCs2Id(String sub, String id) {
        var mgr = CS2BoxManager.getInstance();
        switch (sub) {
            case "box" -> {
                return mgr != null && mgr.getBoxIds().contains(id);
            }
            case "key" -> {
                if (mgr == null) return false;
                for (CS2BoxConfig cfg : mgr.getAllBoxes()) {
                    if (id.equals(cfg.getKeyName())) return true;
                }
                return false;
            }
            case "music" -> {
                return MusicBoxRegistry.get(id) != null;
            }
            case "skin" -> {
                int slash = id.indexOf('/');
                if (slash <= 0) return false;
                String itemType = id.substring(0, slash);
                String skinName = id.substring(slash + 1);
                var skins = ItemSkinManager.getSkins(itemType);
                return skins != null && skins.containsKey(skinName);
            }
            default -> {
                return false;
            }
        }
    }

    // ── 小工具 ──

    private static String readStr(String[] tok, int idx, String flag) throws CommandSyntaxException {
        if (idx >= tok.length || tok[idx].startsWith("--")) throw ERR_BAD_FLAG.create();
        return tok[idx];
    }

    private static int readInt(String[] tok, int idx, String flag) throws CommandSyntaxException {
        String s = readStr(tok, idx, flag);
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw ERR_BAD_FLAG.create();
        }
    }

    /** 下一个 token 是纯数字就当作数量，否则返回 -1 表示未指定 */
    private static int readOptionalInt(String[] tok, int idx) {
        if (idx >= tok.length) return -1;
        try {
            return Integer.parseInt(tok[idx]);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // =========================================================================
    // 查看 / 清空
    // =========================================================================

    private static int list(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var targets = EntityArgument.getPlayers(ctx, "targets");
        for (ServerPlayer target : targets) {
            MailboxComponent box = MailboxComponent.KEY.get(target);
            List<Mail> mails = box.getMails();
            final String name = target.getName().getString();
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "§e" + name + " §7共 " + mails.size() + " 封（未读 " + box.getUnreadCount()
                            + "，可领取 " + box.getClaimableCount() + "）"),
                    false);
            for (Mail m : mails) {
                StringBuilder tags = new StringBuilder();
                if (!m.attachments.isEmpty()) tags.append(" 物品x").append(m.attachments.size());
                if (!m.claimCommands.isEmpty()) tags.append(" 指令x").append(m.claimCommands.size());
                if (m.isExpired()) tags.append(" §c已过期");
                else if (!m.claimed && m.hasRewards()) tags.append(" §a可领取");
                else if (m.claimed) tags.append(" §7已领取");
                final String line = "  §8- §f" + m.title + " §7by " + m.sender + tags;
                ctx.getSource().sendSuccess(() -> Component.literal(line), false);
            }
        }
        return 1;
    }

    private static int clear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var targets = EntityArgument.getPlayers(ctx, "targets");
        int n = 0;
        for (ServerPlayer target : targets) {
            MailboxComponent box = MailboxComponent.KEY.get(target);
            n += box.getMails().size();
            box.clearAllMails();
            box.sync();
        }
        final int total = n;
        ctx.getSource().sendSuccess(() -> Component.literal("已清空 " + total + " 封邮件")
                .withStyle(s -> s.withColor(0xFFAA00)), true);
        return total;
    }
}
