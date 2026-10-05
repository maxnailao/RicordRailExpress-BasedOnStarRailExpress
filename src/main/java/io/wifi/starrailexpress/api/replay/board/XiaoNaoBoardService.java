package io.wifi.starrailexpress.api.replay.board;

import io.wifi.starrailexpress.api.replay.board.ReplayBoardSavedData.ReplayScreenEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 「小脑 / 被小脑」排行榜投屏。
 *
 * <p>复用回放屏幕那一套：黑板（{@code Blocks.BLACK_WOOL}）当底板 +
 * {@code Display.TextDisplay} 文字浮在底板前方，所以位置、朝向、宽高的语义
 * 与 {@code /sre:replay_screen} 完全一致。屏幕的**位置**存在
 * {@link ReplayBoardSavedData}（同一个存档文件），因此重启后依然在。
 *
 * <p>内容<b>不做周期刷新</b>：只在建屏、每局结束（{@code GameMode#showReplay}）、
 * 管理员操作时更新一次。更新采用就地改文本而非销毁重建，所以画面静止、不会闪烁。
 */
public final class XiaoNaoBoardService {

    /** 榜单屏幕 id 前缀，用来和回放屏幕区分（同一个 SavedData 里共存） */
    public static final String ID_PREFIX = "xiaonao_";
    /** 小脑榜 id */
    public static final String BOARD_XIAONAO = ID_PREFIX + "top";
    /** 被小脑榜 id */
    public static final String BOARD_BEI_XIAONAO = ID_PREFIX + "beaten";
    /** 榜单最多列几名（屏幕再高也不会无限往下列） */
    private static final int MAX_RANK_ROWS = 10;
    /** 文字可见距离 */
    private static final float DISPLAY_VIEW_RANGE = 0.6F;
    private static final String ENTITY_NAME_PREFIX = "SRE XiaoNao Board:";

    /** 被 hide 关掉的投屏 id（不参与统一刷新） */
    private static final Set<String> HIDDEN = new HashSet<>();

    private XiaoNaoBoardService() {
    }

    /** 小脑榜（误杀次数）还是被小脑榜（被误杀次数） */
    public enum Kind {
        XIAONAO(BOARD_XIAONAO, "小脑榜", ChatFormatting.RED),
        BEI_XIAONAO(BOARD_BEI_XIAONAO, "被小脑榜", ChatFormatting.AQUA);

        public final String screenId;
        public final String title;
        public final ChatFormatting color;

        Kind(String screenId, String title, ChatFormatting color) {
            this.screenId = screenId;
            this.title = title;
            this.color = color;
        }

        public static Kind byName(String name) {
            if (name == null) {
                return null;
            }
            return switch (name.toLowerCase()) {
                case "xiaonao", "小脑" -> XIAONAO;
                case "beixiaonao", "被小脑" -> BEI_XIAONAO;
                default -> null;
            };
        }
    }

    // =========================================================================
    // 创建 / 移除 / 显示
    // =========================================================================

    /**
     * 创建榜单屏幕（按需铺黑板底板并立刻画一次内容）。
     *
     * @param slot       同一位置可以放两个榜（小脑 / 被小脑），用 slot 区分屏幕 id
     * @param background 是否铺黑色羊毛底板；false = 纯文字悬浮，不改动地图方块
     */
    public static ReplayScreenEntry create(ServerLevel level, Kind kind, BlockPos origin,
            int width, int height, Direction direction, String slot, boolean background) {
        return createUnique(level, kind, origin, width, height, direction, slot, background).entry();
    }

    /**
     * 创建榜单屏幕，并保证 **id 唯一**。
     *
     * <p>为什么必须唯一：屏幕存档是 {@code Map<id, entry>}。若用同一个 id 建第二次，
     * 新的 entry 会**覆盖**旧的，旧屏幕的文字实体却还留在地上 —— 于是出现
     * "两块屏共用同一个 id / 只能删掉后放的那块 / 先放的那块删不掉"。
     * 这里在 id 被占用时自动加 {@code _2}、{@code _3} 后缀，从根上避免。
     *
     * @return 实际使用的 id + 建好的 entry
     */
    public static Created createUnique(ServerLevel level, Kind kind, BlockPos origin,
            int width, int height, Direction direction, String slot, boolean background) {
        String base = kind.screenId + (slot == null || slot.isBlank() ? "" : "_" + slot);
        String id = uniqueId(level, base);
        ReplayScreenEntry entry = ReplayBoardService.createScreen(level, id, origin, width, height,
                direction, background);
        HIDDEN.remove(id);
        repaint(level.getServer(), id);
        return new Created(id, entry, !id.equals(base));
    }

    /** 实际使用的屏幕 id、Entry，以及是否因重名被自动改名 */
    public record Created(String id, ReplayScreenEntry entry, boolean renamed) {
    }

    /** 找一个没被占用的 id：base → base_2 → base_3 … */
    private static String uniqueId(ServerLevel level, String base) {
        ReplayBoardSavedData data = ReplayBoardSavedData.get(level);
        if (data.getScreen(base).isEmpty()) {
            return base;
        }
        for (int n = 2; n < 1000; n++) {
            String candidate = base + "_" + n;
            if (data.getScreen(candidate).isEmpty()) {
                return candidate;
            }
        }
        return base + "_" + System.currentTimeMillis();
    }

    /** 兼容旧调用：默认铺底板 */
    public static ReplayScreenEntry create(ServerLevel level, Kind kind, BlockPos origin,
            int width, int height, Direction direction, String slot) {
        return create(level, kind, origin, width, height, direction, slot, true);
    }

    /**
     * 删除榜单屏幕。
     *
     * <p>除了删掉存档里的 entry，还会按"自定义名"清掉**同 id 的所有残留文字实体**，
     * 避免历史遗留（重名建屏时代留下的孤儿实体）继续挂在地图上。
     */
    public static boolean remove(ServerLevel level, String id) {
        HIDDEN.remove(id);
        var opt = ReplayBoardSavedData.get(level).getScreen(id);
        boolean removed = ReplayBoardService.removeScreen(level, id);
        // 存档里已经没有 entry 了，但孤儿文字实体可能还在（按名字匹配清理）
        clearTextDisplaysByName(level, id);
        if (!removed && opt.isEmpty()) {
            return false;
        }
        return true;
    }

    /**
     * 显示/刷新某个已创建的榜单屏幕。
     *
     * @return 屏幕不存在时返回 false
     */
    public static boolean show(ServerLevel level, String id) {
        if (ReplayBoardSavedData.get(level).getScreen(id).isEmpty()) {
            return false;
        }
        HIDDEN.remove(id);
        return repaint(level.getServer(), id);
    }

    /**
     * 停止显示并清空画面，但**保留**屏幕定义（文件里位置还在，随时 show 回来）。
     *
     * @return 屏幕不存在时返回 false
     */
    public static boolean hide(ServerLevel level, String id) {
        HIDDEN.add(id);
        var opt = ReplayBoardSavedData.get(level).getScreen(id);
        if (opt.isEmpty()) {
            return false;
        }
        clearTextDisplaysByName(level, id);
        return true;
    }

    /**
     * 刷新**所有**榜单投屏。由对局结束时调用（见 {@code GameMode#showReplay}）。
     *
     * <p>刻意不做成每 tick 周期重画：旧实现每 20 tick 丢弃并重建文字实体，
     * 玩家看到的就是"一闪一闪"。现在只在
     * <ul>
     * <li>建屏时，</li>
     * <li>每局结束时，</li>
     * <li>管理员 show / reset / 改次数时</li>
     * </ul>
     * 各更新一次，画面静止不动。
     *
     * @return 实际刷新的投屏数量
     */
    public static int refreshAll(MinecraftServer server) {
        if (server == null) {
            return 0;
        }
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return 0;
        }
        int n = 0;
        for (String id : screenIdsOf(overworld)) {
            if (HIDDEN.contains(id)) {
                continue;
            }
            if (repaint(server, id)) {
                n++;
            }
        }
        return n;
    }

    /**
     * 重置榜单统计并**立刻**把投屏刷新成空榜。
     *
     * @return 被重置影响的投屏数量
     */
    public static int resetStats(ServerLevel level) {
        MinecraftServer server = level.getServer();
        XiaoNaoBoardStats.resetAll(server);
        int repainted = 0;
        for (String id : screenIdsOf(level)) {
            if (HIDDEN.contains(id)) {
                continue;
            }
            if (repaint(server, id)) {
                repainted++;
            }
        }
        return repainted;
    }

    /** 找出与某个名字/类型匹配的榜单屏幕 id（命令里允许直接写 xiaonao / 被小脑） */
    public static List<String> screenIdsOf(ServerLevel level) {
        List<String> ids = new ArrayList<>();
        for (String id : ReplayBoardSavedData.get(level).screens().keySet()) {
            if (id.startsWith(ID_PREFIX)) {
                ids.add(id);
            }
        }
        return ids;
    }

    // =========================================================================
    // 孤儿实体（看得见但存档无记录）
    // =========================================================================

    /**
     * 找出"孤儿"榜单文字实体：**必须**是本模组排行榜的实体（名字形如
     * {@code SRE XiaoNao Board:<xiaonao_ 开头的 id>[#行号]}），且存档里已经没有对应屏幕。
     *
     * <p>判定条件收得很紧，三层都要满足，避免把别人的东西算进来：
     * <ol>
     * <li>自定义名以 {@link #ENTITY_NAME_PREFIX} 开头（回放屏是
     * {@code SRE Replay Screen:...}，第一步就被排除）；</li>
     * <li>去掉 {@code #行号} 后，剩下的 id 还要以 {@link #ID_PREFIX}（{@code xiaonao_}）
     * 开头 —— 这是排行榜屏幕的专属前缀，不可能误伤其它屏；</li>
     * <li>该 id 不在当前屏幕清单里（确实没有对应记录了）。</li>
     * </ol>
     *
     * <p>之前这里只做了第 1 步，判定过宽，导致"幽灵屏"的统计会把无关的文字实体也算进去，
     * 看起来像是有幽灵屏 —— 其实没有。
     */
    public static List<Entity> findOrphanTextDisplays(ServerLevel level) {
        Set<String> known = new HashSet<>();
        for (String id : screenIdsOf(level)) {
            known.add(ENTITY_NAME_PREFIX + id);
        }
        List<Entity> orphans = new ArrayList<>();
        for (ServerLevel lv : allLevels(level)) {
            lv.getAllEntities().forEach(entity -> {
                if (!(entity instanceof Display.TextDisplay)) {
                    return;
                }
                String base = baseNameOf(entity);
                // 必须确实是排行榜实体：前缀 + xiaonao_ 开头的 id
                if (!base.startsWith(ENTITY_NAME_PREFIX)) {
                    return;
                }
                String screenId = base.substring(ENTITY_NAME_PREFIX.length());
                if (!screenId.startsWith(ID_PREFIX)) {
                    return;
                }
                if (!known.contains(base)) {
                    orphans.add(entity);
                }
            });
        }
        return orphans;
    }

    /**
     * 清除所有孤儿榜单文字实体。
     *
     * <p>注意：孤儿屏的**黑板方块**清不掉 —— 底板的位置信息只存在被覆盖掉的 entry 里，
     * 已经丢失了。那些黑羊毛需要管理员手动挖掉，本方法只负责让文字消失。
     *
     * @return 清掉的实体数量
     */
    public static int purgeOrphans(ServerLevel level) {
        List<Entity> orphans = findOrphanTextDisplays(level);
        for (Entity e : orphans) {
            e.discard();
        }
        return orphans.size();
    }

    private static List<ServerLevel> allLevels(ServerLevel any) {
        MinecraftServer server = any.getServer();
        if (server == null) {
            return List.of(any);
        }
        List<ServerLevel> out = new ArrayList<>();
        for (ServerLevel lv : server.getAllLevels()) {
            out.add(lv);
        }
        return out;
    }

    // =========================================================================
    // 内容与绘制
    // =========================================================================

    /**
     * 把某个榜单的当前数据画到屏幕上。
     *
     * <p><b>按行号就地更新</b>：行号写在实体自定义名里（{@code <前缀><id>#<行号>}），
     * 每行都有确定的实体，只改文本、不销毁重建。
     *
     * <p>早期实现有两处会让文字串到一起：
     * <ol>
     * <li>靠 {@code y} 坐标排序来"认"第几行 —— 行距是小数，排序结果一旦不稳定，
     * 第 2 行就可能被当成第 1 行，于是把新文本写到了别的行上；</li>
     * <li>文字超宽时实体会按 {@code lineWidth} 自动折成两行，而我的行距是按一行算的，
     * 折出来的第二行就压到下一行上 —— 这正是"字体混一块去了"。</li>
     * </ol>
     * 现在前者改成按行号索引，后者由 {@link #truncate} 保证每行不超宽。
     */
    private static boolean repaint(MinecraftServer server, String id) {
        if (server == null) {
            return false;
        }
        ReplayBoardSavedData data = ReplayBoardSavedData.get(server);
        var opt = data.getScreen(id);
        if (opt.isEmpty()) {
            return false;
        }
        ReplayScreenEntry entry = opt.get();
        ServerLevel level = server.getLevel(entry.dimension());
        if (level == null) {
            return false;
        }
        int rows = ReplayBoardService.maxRowsFor(entry);
        List<Component> lines = buildLines(id, server);
        int want = Math.min(lines.size(), rows);

        // 按行号建索引：rowIndex -> 实体。不认识的行号（旧格式无 # 的）归到 -1
        Map<Integer, Display.TextDisplay> byRow = collectByRow(level, entry);

        for (int i = 0; i < want; i++) {
            Component text = lines.get(i);
            Display.TextDisplay display = byRow.remove(i);
            if (display != null) {
                if (!text.equals(display.getText())) {
                    display.setText(text);
                }
            } else {
                spawnLine(level, entry, text, i, rows);
            }
        }
        // 不再需要的行（内容变短，或旧格式遗留）全部销毁
        for (Display.TextDisplay leftover : byRow.values()) {
            leftover.discard();
        }
        return true;
    }

    /** 行号 -> 文字实体。行号取自自定义名里的 {@code #N}；旧格式（无 #）记为 -1 */
    private static Map<Integer, Display.TextDisplay> collectByRow(ServerLevel level, ReplayScreenEntry entry) {
        String prefix = ENTITY_NAME_PREFIX + entry.id();
        Map<Integer, Display.TextDisplay> out = new HashMap<>();
        level.getAllEntities().forEach(entity -> {
            if (!(entity instanceof Display.TextDisplay)) {
                return;
            }
            String name = entity.getCustomName() == null ? "" : entity.getCustomName().getString();
            if (name.equals(prefix)) {
                out.put(-1, (Display.TextDisplay) entity);
                return;
            }
            if (!name.startsWith(prefix + "#")) {
                return;
            }
            try {
                out.put(Integer.parseInt(name.substring(prefix.length() + 1)),
                        (Display.TextDisplay) entity);
            } catch (NumberFormatException ignored) {
                out.put(-1, (Display.TextDisplay) entity);
            }
        });
        return out;
    }

    /** 实体自定义名去掉 {@code #行号} 后的基底名 */
    private static String baseNameOf(Entity entity) {
        String name = entity.getCustomName() == null ? "" : entity.getCustomName().getString();
        if (!name.startsWith(ENTITY_NAME_PREFIX)) {
            return "";
        }
        int hash = name.lastIndexOf('#');
        return hash >= 0 ? name.substring(0, hash) : name;
    }

    /**
     * 生成榜单文本（标题 + 名次行）。
     *
     * <p>每行都按**像素**裁到屏幕宽度内，保证一条数据只占一行
     * （否则文字实体会自动折行，折出的第二行会压到下一行上）。
     *
     * <p>版式尽量紧凑以给名字留空间：名次后面只跟一个空格，次数用 {@code ×N}
     * 而不是 {@code "  x" + N}。3 格宽的屏幕本来就没多少余量，这几个字符很关键。
     */
    private static List<Component> buildLines(String id, MinecraftServer server) {
        Kind kind = id.startsWith(BOARD_BEI_XIAONAO) ? Kind.BEI_XIAONAO
                : id.startsWith(BOARD_XIAONAO) ? Kind.XIAONAO : null;
        List<Component> lines = new ArrayList<>();
        if (kind == null) {
            lines.add(Component.literal("未知榜单: " + id).withStyle(ChatFormatting.GRAY));
            return lines;
        }
        ReplayScreenEntry entry = ReplayBoardSavedData.get(server).getScreen(id).orElse(null);
        int budget = entry == null ? 128 : lineBudgetPx(entry);
        lines.add(Component.literal(TextFitter.fit(kind.title, budget))
                .withStyle(kind.color, ChatFormatting.BOLD));
        List<XiaoNaoBoardStats.Entry> top = kind == Kind.XIAONAO
                ? XiaoNaoBoardStats.topXiaoNao(MAX_RANK_ROWS)
                : XiaoNaoBoardStats.topBeiXiaoNao(MAX_RANK_ROWS);
        if (top.isEmpty()) {
            lines.add(Component.literal("（暂无记录）").withStyle(ChatFormatting.DARK_GRAY));
            return lines;
        }
        int rank = 1;
        for (XiaoNaoBoardStats.Entry e : top) {
            ChatFormatting nameColor = switch (rank) {
                case 1 -> ChatFormatting.GOLD;
                case 2 -> ChatFormatting.YELLOW;
                case 3 -> ChatFormatting.WHITE;
                default -> ChatFormatting.GRAY;
            };
            String rankStr = rank + " ";
            String countStr = "×" + e.count();
            String name;
            if (TextFitter.pixelWidth(rankStr) + TextFitter.pixelWidth(countStr) >= budget) {
                // 极端情况：名次+次数都放不下（窄屏 + 超高次数）。退化成溢出标记。
                countStr = "×99+";
                name = TextFitter.fit(e.name(), Math.max(0, budget
                        - TextFitter.pixelWidth(rankStr) - TextFitter.pixelWidth(countStr)));
            } else {
                name = TextFitter.fit(e.name(), budget
                        - TextFitter.pixelWidth(rankStr) - TextFitter.pixelWidth(countStr));
            }
            lines.add(Component.literal(rankStr).withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(name).withStyle(nameColor))
                    .append(Component.literal(countStr).withStyle(kind.color)));
            rank++;
        }
        return lines;
    }

    /**
     * 一行的像素预算（模型空间）。
     *
     * <p>屏幕世界宽 = {@code 宽格数 × 16} 像素；文字会被缩放 {@code textScale}，
     * 所以模型空间里能用的宽度是 {@code 世界宽度 / scale}。
     * 再留 2px 余量，避免刚好卡在边界上被自动折行。
     */
    static int lineBudgetPx(ReplayScreenEntry entry) {
        double scale = ReplayBoardService.textScale(entry);
        return Math.max(24, (int) Math.floor(entry.width() * 16.0D / scale) - 2);
    }

    /** 在屏幕前方生成一行文字 */
    private static void spawnLine(ServerLevel level, ReplayScreenEntry entry, Component text,
            double row, int visibleRows) {
        Display.TextDisplay display = new Display.TextDisplay(EntityType.TEXT_DISPLAY, level);
        display.setText(text);
        display.setNoGravity(true);
        display.setBillboardConstraints(Display.BillboardConstraints.FIXED);
        display.setYRot(ReplayBoardService.yawFor(entry.direction()));
        display.setXRot(0.0F);
        display.setViewRange(DISPLAY_VIEW_RANGE);
        // setLineWidth 是"缩放前"的模型空间单位：文字先按它换行，再整体乘 scale，
        // 所以最终世界宽度 = lineWidth × scale。想收在 w 格内就该给 w×16/scale。
        double scale = ReplayBoardService.textScale(entry);
        display.setLineWidth(Math.max(16, (int) Math.floor(entry.width() * 16.0D / scale)));
        display.setBackgroundColor(0x00000000);
        display.setTransformation(new com.mojang.math.Transformation(
                new org.joml.Matrix4f().scale((float) scale)));
        ReplayBoardService.positionLine(display, entry, row, visibleRows);
        // 行号写进名字：下次刷新按行号精确归位，不依赖 y 坐标排序
        display.setCustomName(Component.literal(
                ENTITY_NAME_PREFIX + entry.id() + "#" + (int) row).withStyle(ChatFormatting.GRAY));
        display.setCustomNameVisible(false);
        level.addFreshEntity(display);
    }

    /** 按屏幕 id 清掉所有对应的文字实体（兼容新旧两种命名） */
    private static void clearTextDisplaysByName(ServerLevel level, String screenId) {
        String prefix = ENTITY_NAME_PREFIX + screenId;
        List<Entity> stale = new ArrayList<>();
        level.getAllEntities().forEach(entity -> {
            if (entity instanceof Display.TextDisplay) {
                String name = entity.getCustomName() == null ? "" : entity.getCustomName().getString();
                if (name.equals(prefix) || name.startsWith(prefix + "#")) {
                    stale.add(entity);
                }
            }
        });
        for (Entity entity : stale) {
            entity.discard();
        }
    }

    // =========================================================================
    // 启动恢复
    // =========================================================================

    /** 服务器启动后只做一次的画面恢复（重启后屏幕不该是空的） */
    private static boolean restored = false;

    /**
     * 服务器启动时调用一次：把存档里已有的榜单投屏按其统计数据重新画一遍。
     *
     * <p>注意这里**不是**周期刷新。之前每 20 tick 重画一次，因为会销毁重建文字实体，
     * 屏幕上就一直"一闪一闪"。现在榜单只在建屏 / 每局结束 / 管理员操作时更新。
     */
    public static void restoreOnStartup(MinecraftServer server) {
        if (restored || server == null) {
            return;
        }
        restored = true;
        refreshAll(server);
    }
}
