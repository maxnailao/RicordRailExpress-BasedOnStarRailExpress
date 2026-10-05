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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 「小脑 / 被小脑」排行榜投屏。
 *
 * <p>复用回放屏幕那一套：黑板（{@code Blocks.BLACK_WOOL}）当底板 +
 * {@code Display.TextDisplay} 文字浮在底板前方，所以位置、朝向、宽高的语义
 * 与 {@code /sre:replay_screen} 完全一致。屏幕的**位置**存在
 * {@link ReplayBoardSavedData}（同一个存档文件），因此重启后依然在。
 *
 * <p>内容每 {@link #REPAINT_INTERVAL_TICKS} tick 重画一次，局内数据变化能自动跟上。
 */
public final class XiaoNaoBoardService {

    /** 榜单屏幕 id 前缀，用来和回放屏幕区分（同一个 SavedData 里共存） */
    public static final String ID_PREFIX = "xiaonao_";
    /** 小脑榜 id */
    public static final String BOARD_XIAONAO = ID_PREFIX + "top";
    /** 被小脑榜 id */
    public static final String BOARD_BEI_XIAONAO = ID_PREFIX + "beaten";
    /** 榜单最多列几名 */
    private static final int MAX_ROWS = 10;
    /** 重画间隔（tick） */
    private static final int REPAINT_INTERVAL_TICKS = 20;
    /** 文字可见距离 */
    private static final float DISPLAY_VIEW_RANGE = 0.6F;
    private static final String ENTITY_NAME_PREFIX = "SRE XiaoNao Board:";

    /** 当前需要持续刷新的榜单屏幕 id */
    private static final Set<String> ACTIVE = new HashSet<>();
    /** 重启后把存档里的榜单屏幕恢复显示（只做一次） */
    private static boolean restored = false;
    private static int tickCounter = 0;

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
        String id = kind.screenId + (slot == null || slot.isBlank() ? "" : "_" + slot);
        ReplayScreenEntry entry = ReplayBoardService.createScreen(level, id, origin, width, height,
                direction, background);
        ACTIVE.add(id);
        repaint(level.getServer(), id);
        return entry;
    }

    /** 兼容旧调用：默认铺底板 */
    public static ReplayScreenEntry create(ServerLevel level, Kind kind, BlockPos origin,
            int width, int height, Direction direction, String slot) {
        return create(level, kind, origin, width, height, direction, slot, true);
    }

    public static boolean remove(ServerLevel level, String id) {
        ACTIVE.remove(id);
        return ReplayBoardService.removeScreen(level, id);
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
        ACTIVE.add(id);
        return repaint(level.getServer(), id);
    }

    /**
     * 停止刷新并清空画面，但**保留**屏幕定义（文件里位置还在，随时 show 回来）。
     *
     * @return 屏幕不存在时返回 false
     */
    public static boolean hide(ServerLevel level, String id) {
        ACTIVE.remove(id);
        var opt = ReplayBoardSavedData.get(level).getScreen(id);
        if (opt.isEmpty()) {
            return false;
        }
        clearTextDisplays(level, opt.get());
        return true;
    }

    /**
     * 重置榜单统计并**立刻**把已显示的投屏刷新成空榜。
     *
     * <p>如果没有这一步，重置后玩家要等下一个重画周期（最多 20 tick）才会看到变化，
     * 期间屏幕上还挂着旧数据，容易以为没生效。
     *
     * @return 被重置影响的投屏数量
     */
    public static int resetStats(ServerLevel level) {
        MinecraftServer server = level.getServer();
        XiaoNaoBoardStats.resetAll(server);
        int repainted = 0;
        for (String id : screenIdsOf(level)) {
            if (ACTIVE.contains(id) && repaint(server, id)) {
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
    // 内容与绘制
    // =========================================================================

    /** 把某个榜单的当前数据重画到屏幕上 */
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
        List<Component> lines = buildLines(id, server);
        clearTextDisplays(level, entry);
        int rows = ReplayBoardService.visibleRows(entry);
        for (int i = 0; i < lines.size() && i < rows; i++) {
            spawnLine(level, entry, lines.get(i), i, rows, server);
        }
        return true;
    }

    /** 生成榜单文本（标题 + 名次行） */
    private static List<Component> buildLines(String id, MinecraftServer server) {
        Kind kind = id.startsWith(BOARD_BEI_XIAONAO) ? Kind.BEI_XIAONAO
                : id.startsWith(BOARD_XIAONAO) ? Kind.XIAONAO : null;
        List<Component> lines = new ArrayList<>();
        if (kind == null) {
            lines.add(Component.literal("未知榜单: " + id).withStyle(ChatFormatting.GRAY));
            return lines;
        }
        lines.add(Component.literal("—— " + kind.title + " ——").withStyle(kind.color, ChatFormatting.BOLD));
        List<XiaoNaoBoardStats.Entry> top = kind == Kind.XIAONAO
                ? XiaoNaoBoardStats.topXiaoNao(MAX_ROWS)
                : XiaoNaoBoardStats.topBeiXiaoNao(MAX_ROWS);
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
            lines.add(Component.literal(rank + ". ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(e.name()).withStyle(nameColor))
                    .append(Component.literal("  x" + e.count()).withStyle(kind.color)));
            rank++;
        }
        return lines;
    }

    /** 在屏幕前方生成一行文字 */
    private static void spawnLine(ServerLevel level, ReplayScreenEntry entry, Component text,
            double row, int visibleRows, MinecraftServer server) {
        Display.TextDisplay display = new Display.TextDisplay(EntityType.TEXT_DISPLAY, level);
        display.setText(text);
        display.setNoGravity(true);
        display.setBillboardConstraints(Display.BillboardConstraints.FIXED);
        display.setYRot(ReplayBoardService.yawFor(entry.direction()));
        display.setXRot(0.0F);
        display.setViewRange(DISPLAY_VIEW_RANGE);
        display.setLineWidth(Math.max(80, entry.width() * 40));
        display.setBackgroundColor(0x00000000);
        display.setTransformation(new com.mojang.math.Transformation(
                new org.joml.Matrix4f().scale(ReplayBoardService.textScale(entry))));
        ReplayBoardService.positionLine(display, entry, row, visibleRows);
        display.setCustomName(Component.literal(ENTITY_NAME_PREFIX + entry.id()).withStyle(ChatFormatting.GRAY));
        display.setCustomNameVisible(false);
        level.addFreshEntity(display);
    }

    /** 只清掉本榜单自己生成的文字实体（按自定义名匹配，不误删回放屏幕的文字） */
    private static void clearTextDisplays(ServerLevel level, ReplayScreenEntry entry) {
        String expected = ENTITY_NAME_PREFIX + entry.id();
        List<Entity> stale = new ArrayList<>();
        level.getAllEntities().forEach(entity -> {
            if (entity instanceof Display.TextDisplay) {
                String name = entity.getCustomName() == null ? "" : entity.getCustomName().getString();
                if (expected.equals(name)) {
                    stale.add(entity);
                }
            }
        });
        for (Entity entity : stale) {
            entity.discard();
        }
    }

    // =========================================================================
    // 每 tick 刷新
    // =========================================================================

    public static void tick(MinecraftServer server) {
        if (server == null) {
            return;
        }
        // 服务器重启后：把存档里已有的榜单屏幕恢复成持续刷新
        if (!restored) {
            restored = true;
            ServerLevel overworld = server.overworld();
            if (overworld != null) {
                for (String id : screenIdsOf(overworld)) {
                    ACTIVE.add(id);
                }
            }
        }
        if (ACTIVE.isEmpty()) {
            return;
        }
        if (++tickCounter < REPAINT_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;
        for (String id : new ArrayList<>(ACTIVE)) {
            if (!repaint(server, id)) {
                ACTIVE.remove(id);
            }
        }
    }
}
