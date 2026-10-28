package io.wifi.starrailexpress.client.gui.screen.ingame;

import io.wifi.starrailexpress.announcement.AnnouncementEntry;
import io.wifi.starrailexpress.announcement.client.AnnouncementClientCache;
import io.wifi.starrailexpress.announcement.network.AnnouncementRequestC2SPayload;
import io.wifi.starrailexpress.client.gui.endfield.menu.animation.PageTransition;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 公告界面（主从布局）：主菜单"公告"按钮进入。
 * <p>左侧为可滚动、可点击选中的公告条目（#序号 + 标题）；右侧大栏显示选中公告的
 * 完整内容（顶部标题、正文自动换行、右下角发布时间与发布者）。</p>
 */
public class AnnouncementScreen extends Screen {
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter CLOCK_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int TEXT_COLOR = 0xFFF5E8C8;
    private static final int TEXT_DIM = 0xFF9E8B6E;
    private static final int GOLD = 0xFFFFD24A;

    private static final int ITEM_H = 26;
    private static final int LEFT_W = 150;
    private static final int GAP = 8;

    private final Screen parent;
    private int panelX, panelY, panelW, panelH;

    // 左侧列表几何（render 时更新，供点击命中判定复用）
    private int listX, listTop, listBottom;
    private int listScroll = 0;
    private int selectedIndex = 0;
    private final List<AnnouncementEntry> displayList = new ArrayList<>();
    private final PageTransition transition = new PageTransition();

    public AnnouncementScreen(Screen parent) {
        super(Component.literal("公告"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        // 全屏布局：白标题栏、卡片、字体配色全部沿用，仅放开面板尺寸上限
        panelW = width - 16;
        panelH = height - 44;
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2 + 12;

        addRenderableWidget(Button.builder(Component.literal("返回"), b ->
                transition.startReturn(() -> minecraft.execute(() -> minecraft.setScreen(parent))))
                .pos(panelX + 6, panelY + panelH - 24).size(48, 18).build());

        transition.beginCrtOnce(minecraft);

        // 打开即请求最新公告列表
        ClientPlayNetworking.send(new AnnouncementRequestC2SPayload());
    }

    /** 服务端推送列表后由接收器回调（render 每帧读缓存，这里仅重置越界的选中/滚动量） */
    public void refresh() {
        int size = AnnouncementClientCache.get().size();
        if (selectedIndex >= size) selectedIndex = Math.max(0, size - 1);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        renderContent(g, mouseX, mouseY, delta);
        transition.render(g, width, height);
    }

    private void renderContent(GuiGraphics g, int mouseX, int mouseY, float delta) {
        LobbySubscreenRenderer.background(g, width, height);
        int headerH = 24;
        LobbySubscreenRenderer.panel(g, panelX, panelY - headerH, panelW, panelH + headerH);
        LobbySubscreenRenderer.header(g, font, panelX, panelY - headerH, panelW, "公告", null);
        String clock = LocalTime.now().format(CLOCK_FMT);
        g.drawString(font, clock, panelX + panelW - font.width(clock) - 12, panelY - headerH + 10, TEXT_DIM);

        List<AnnouncementEntry> list = AnnouncementClientCache.get();
        // 左上角：公告数量
        g.drawString(font, "公告数量: " + list.size(), panelX + 8, panelY + 6, TEXT_COLOR, false);

        // 布局几何
        listX = panelX + 8;
        listTop = panelY + 20;
        listBottom = panelY + panelH - 30;
        int detailX = listX + LEFT_W + GAP;
        int detailW = panelX + panelW - 8 - detailX;
        int detailH = listBottom - listTop;

        // 最新在上
        displayList.clear();
        displayList.addAll(list);
        Collections.reverse(displayList);

        if (displayList.isEmpty()) {
            g.drawCenteredString(font, Component.literal("暂无公告").withStyle(ChatFormatting.GRAY),
                    panelX + panelW / 2, listTop + 40, TEXT_DIM);
            super.render(g, mouseX, mouseY, delta);
            return;
        }

        // 选中/滚动越界修正
        if (selectedIndex >= displayList.size()) selectedIndex = displayList.size() - 1;
        if (selectedIndex < 0) selectedIndex = 0;
        int total = displayList.size() * ITEM_H;
        int maxScroll = Math.max(0, total - (listBottom - listTop));
        if (listScroll > maxScroll) listScroll = maxScroll;
        if (listScroll < 0) listScroll = 0;

        renderLeftList(g, mouseX, mouseY);
        renderDetail(g, detailX, listTop, detailW, detailH);

        super.render(g, mouseX, mouseY, delta);
    }

    private void renderLeftList(GuiGraphics g, int mouseX, int mouseY) {
        g.enableScissor(listX, listTop, listX + LEFT_W, listBottom);
        int y = listTop - listScroll;
        for (int i = 0; i < displayList.size(); i++) {
            boolean selected = i == selectedIndex;
            boolean hovered = mouseX >= listX && mouseX < listX + LEFT_W
                    && mouseY >= y && mouseY < y + ITEM_H - 2;
            LobbySubscreenRenderer.card(g, listX, y, LEFT_W, ITEM_H - 2, selected, hovered);

            AnnouncementEntry e = displayList.get(i);
            g.drawString(font, "#" + (i + 1), listX + 6, y + (ITEM_H - 2 - font.lineHeight) / 2, GOLD, false);
            String title = clip(e.title(), LEFT_W - 34);
            g.drawString(font, title, listX + 28, y + (ITEM_H - 2 - font.lineHeight) / 2,
                    selected ? TEXT_COLOR : TEXT_DIM, false);
            y += ITEM_H;
        }
        g.disableScissor();
    }

    private void renderDetail(GuiGraphics g, int x, int y, int w, int h) {
        LobbySubscreenRenderer.card(g, x, y, w, h, false, false);
        AnnouncementEntry e = displayList.get(selectedIndex);

        // 顶部标题
        g.drawString(font, clip(e.title(), w - 16), x + 8, y + 8, GOLD, false);
        LobbySubscreenRenderer.divider(g, x + 8, y + 22, w - 16);

        // 正文（按换行标记 \n 分段，再按宽度自动换行，裁剪到详情区）
        int innerW = w - 16;
        int lineY = y + 30;
        g.enableScissor(x + 2, y + 24, x + w - 2, y + h - 20);
        for (String seg : e.content().split("\\\\n", -1)) {
            if (seg.isEmpty()) {
                lineY += 10;
                continue;
            }
            for (var line : font.split(Component.literal(seg), innerW)) {
                g.drawString(font, line, x + 8, lineY, TEXT_COLOR, false);
                lineY += 10;
            }
        }
        g.disableScissor();

        // 右下角：发布时间(年月日) + 发布者
        String time = LocalDateTime.ofInstant(Instant.ofEpochMilli(e.timestamp()), ZoneId.systemDefault())
                .format(DATE_FMT);
        String footer = time + "  ·  发布者: " + e.publisher();
        g.drawString(font, clip(footer, w - 16), x + w - 8 - font.width(footer), y + h - 12, TEXT_DIM, false);
    }

    // 自绘界面已铺满整屏底色；屏蔽原版 renderBackground，避免主菜单背景模糊采样链盖在上面导致文字发糊。
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    private String clip(String s, int maxW) {
        if (font.width(s) <= maxW) return s;
        return font.plainSubstrByWidth(s, Math.max(0, maxW - font.width("…"))) + "…";
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && !displayList.isEmpty()) {
            int x = (int) mouseX, y = (int) mouseY;
            if (x >= listX && x < listX + LEFT_W && y >= listTop && y < listBottom) {
                int idx = (y - listTop + listScroll) / ITEM_H;
                if (idx >= 0 && idx < displayList.size()) {
                    selectedIndex = idx;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        listScroll -= (int) (scrollY * ITEM_H);
        if (listScroll < 0) listScroll = 0;
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}