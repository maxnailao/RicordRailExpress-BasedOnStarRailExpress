package io.wifi.starrailexpress.client.gui.screen.ingame;

import io.wifi.starrailexpress.client.data.ClientPlayerDataCache;
import io.wifi.starrailexpress.progression.ProgressionState.FactionCardType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.HashSet;
import java.util.Set;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * 职业卡界面（从主菜单"职业卡"按钮进入）。
 * <p>
 * 顶部五张卡面：杀(红) / 平(绿) / 中(黄) / 中(紫, 杀手中立) / 自(彩色, 自选卡)，
 * 下面一行显示当前拥有的各职业卡数量。面板风格与 {@link MenuHubScreen} 一致。
 * </p>
 */
public class RoleCardScreen extends Screen {

    // 与主菜单同源的配色
    private static final int BG_COLOR = LobbyUiTheme.BACKGROUND;
    private static final int PANEL_COLOR = LobbyUiTheme.PANEL;
    private static final int ACCENT = LobbyUiTheme.ACCENT;
    private static final int TEXT_COLOR = LobbyUiTheme.TEXT;
    private static final int TEXT_DIM = LobbyUiTheme.TEXT_DIM;

    // 白色标题栏/描边 + 深色标题文字（与主菜单统一）
    private static final int BORDER_COLOR = LobbyUiTheme.BORDER;
    private static final int HEADER_TEXT = LobbyUiTheme.TEXT;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final int TILE = 40;
    private static final int TILE_GAP = 10;
    private static final int MINI = 28;

    // 布局（init 时计算，render/按钮共用）
    private int panelX, panelY, panelW, panelH;
    private int tileRowX, tileRowY;

    /** 已使用状态：客户端记录（本客户端会话内有效，重开界面仍显示"已使用"） */
    private static final Set<Integer> USED_KINDS = new HashSet<>();
    private static final int STATUS_USED_COLOR = 0xFF55FF55;

    /** 一张卡面的定义：字、底色、是否彩色、对应阵营、名称、字色 */
    private record CardKind(String glyph, int color, boolean rainbow, FactionCardType type,
                            String name, int textColor) {
    }

    private static final CardKind[] KINDS = {
            new CardKind("杀", 0xFFAA0000, false, FactionCardType.KILLER, "杀手卡", 0xFFFFFFFF),
            new CardKind("平", 0xFF00AA00, false, FactionCardType.CIVILIAN, "平民卡", 0xFFFFFFFF),
            new CardKind("中", 0xFFFFAA00, false, FactionCardType.NEUTRAL, "中立卡", 0xFF202020),
            new CardKind("中", 0xFFAA00AA, false, FactionCardType.NEUTRAL_FOR_KILLER, "狼中卡", 0xFFFFFFFF),
            new CardKind("自", 0xFF8855FF, true, null, "自选卡", 0xFFFFFFFF),
    };

    private final Screen parent;

    public RoleCardScreen(Screen parent) {
        super(Component.literal("职业卡"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        panelW = Math.min(width - 16, 360);
        panelH = Math.min(height - 48, 190);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2 + 14;

        int rowW = KINDS.length * TILE + (KINDS.length - 1) * TILE_GAP;
        tileRowX = panelX + (panelW - rowW) / 2;
        // 整体居中：内容块 = 卡面 + 间距 + 两行文字（状态行 + 卡名行），在面板内垂直居中
        int blockH = TILE + 8 + 11 + font.lineHeight;
        tileRowY = panelY + (panelH - blockH) / 2;

        for (int i = 0; i < KINDS.length; i++) {
            final int index = i;
            addRenderableWidget(new CardTileButton(tileRowX + i * (TILE + TILE_GAP), tileRowY, KINDS[i], index,
                    b -> useCard(index)));
        }

        addRenderableWidget(Button.builder(Component.literal("返回"), b ->
                minecraft.setScreen(parent != null ? parent : new MenuHubScreen()))
                .pos(panelX + 6, panelY + panelH - 26).size(50, 20).build());
    }

    /** 读取当前玩家该卡面的拥有数量（与仓库"职业卡"分类同源） */
    private int countOf(CardKind kind) {
        if (minecraft.player == null) return 0;
        var backpack = ClientPlayerDataCache.backpack(minecraft.player.getUUID());
        if (kind.type() == null) {
            return backpack.selfSelectCards;
        }
        return backpack.cards.getOrDefault(kind.type(), 0);
    }

    /** 点击卡面：使用对应职业卡（服务端真实消耗），并标记为已使用；再次点击已使用的 = 取消退卡 */
    private void useCard(int index) {
        CardKind kind = KINDS[index];
        if (USED_KINDS.contains(index)) {
            // 取消使用：服务端撤销锁定并退回卡片
            if (kind.type() != null && minecraft.player != null && minecraft.player.connection != null) {
                minecraft.player.connection.sendCommand("sre:pass cancel " + kind.type().questKey);
                USED_KINDS.remove(index);
                actionBar("§e已取消使用：" + kind.name() + "（已退回）");
            } else {
                actionBar("§a本局已使用" + kind.name());
            }
            return;
        }
        if (kind.type() == null) {
            // 自选卡必须指定具体职业才能消耗，本界面不做假扣卡
            if (countOf(kind) <= 0) {
                actionBar("§c未拥有自选职业卡");
            } else {
                minecraft.setScreen(new org.agmas.noellesroles.client.screen.SelfSelectCardScreen(this));
            }
            return;
        }
        if (countOf(kind) <= 0) {
            actionBar("§c未拥有" + kind.name());
            return;
        }
        if (minecraft.player != null && minecraft.player.connection != null) {
            minecraft.player.connection.sendCommand("sre:pass activate " + kind.type().questKey);
            USED_KINDS.add(index);
            actionBar("§a已使用：" + kind.name());
        }
    }

    private void actionBar(String msg) {
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.literal(msg), true);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        LobbySubscreenRenderer.background(graphics, width, height);

        int headerH = 24;
        LobbySubscreenRenderer.panel(graphics, panelX, panelY - headerH, panelW, panelH + headerH);
        LobbySubscreenRenderer.header(graphics, font, panelX, panelY - headerH, panelW, "职业卡档案", null);
        String timeStr = LocalTime.now().format(TIME_FMT);
        graphics.drawString(font, timeStr, panelX + panelW - font.width(timeStr) - 12, panelY - headerH + 10, TEXT_DIM);
        LobbySubscreenRenderer.section(graphics, font, panelX + 12, tileRowY - 20, panelW - 24, "可用通行证");

        super.render(graphics, mouseX, mouseY, partialTick);

        // 严格对齐在每张卡面正下方：上一行"已拥有N张/已使用/未拥有"，下一行卡名
        int rowY = tileRowY + TILE + 8;
        for (int i = 0; i < KINDS.length; i++) {
            CardKind kind = KINDS[i];
            // 列中心 = 对应顶部卡面的水平中心
            int cx = tileRowX + i * (TILE + TILE_GAP) + TILE / 2;

            int count = countOf(kind);
            String status;
            int statusColor;
            if (USED_KINDS.contains(i)) {
                status = "已使用";
                statusColor = STATUS_USED_COLOR;
            } else if (count > 0) {
                status = "已拥有" + count + "张";
                statusColor = TEXT_COLOR;
            } else {
                status = "未拥有";
                statusColor = TEXT_DIM;
            }
            graphics.drawCenteredString(font, status, cx, rowY, statusColor);
            // 数量下面一行：卡名
            graphics.drawCenteredString(font, kind.name(), cx, rowY + 11, TEXT_DIM);
        }
        LobbySubscreenRenderer.footer(graphics, font, panelX + 8, panelY + panelH - 34, panelW - 16,
                "点击卡片使用；自选卡会打开职业选择页");
    }

    // 自绘界面已铺满整屏底色；屏蔽原版 renderBackground，避免背景模糊与泥土遮罩盖在上面发糊。
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 画卡面底色（彩色卡为竖向彩虹条纹） */
    private static void drawTileBackground(GuiGraphics graphics, int x, int y, int w, int h, CardKind kind) {
        if (kind.rainbow()) {
            int[] rainbow = {0xFFFF4444, 0xFFFFAA33, 0xFFFFFF55, 0xFF55DD55, 0xFF5599FF, 0xFFBB66FF};
            int stripes = rainbow.length;
            for (int i = 0; i < stripes; i++) {
                int sx = x + w * i / stripes;
                int ex = x + w * (i + 1) / stripes;
                graphics.fill(sx, y, ex, y + h, rainbow[i]);
            }
        } else {
            graphics.fill(x, y, x + w, y + h, kind.color());
        }
    }

    /** 顶部卡面按钮：已使用时卡面上显示绿色"取消"，点击取消使用 */
    private class CardTileButton extends Button {
        private final CardKind kind;
        private final int index;

        CardTileButton(int x, int y, CardKind kind, int index, OnPress action) {
            super(x, y, TILE, TILE, Component.literal(kind.glyph()), action, DEFAULT_NARRATION);
            this.kind = kind;
            this.index = index;
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            drawTileBackground(graphics, getX(), getY(), getWidth(), getHeight(), kind);
            boolean used = USED_KINDS.contains(index);
            graphics.renderOutline(getX(), getY(), getWidth(), getHeight(),
                    used ? STATUS_USED_COLOR : (isHoveredOrFocused() ? 0xFFFFFFFF : 0xFFB8C4DC));
            int cx = getX() + getWidth() / 2;
            if (used) {
                // 已使用：字上移，下方显示"取消"
                graphics.drawCenteredString(font, kind.glyph(), cx, getY() + 7, kind.textColor());
                graphics.drawCenteredString(font, "取消", cx, getY() + TILE - 13, STATUS_USED_COLOR);
            } else {
                graphics.drawCenteredString(font, kind.glyph(), cx,
                        getY() + (getHeight() - font.lineHeight) / 2, kind.textColor());
            }
        }
    }
}
