package io.wifi.starrailexpress.client.gui.screen.ingame;

import io.wifi.starrailexpress.event.EventEntry;
import io.wifi.starrailexpress.event.client.EventClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 全屏活动展示页。卡片风格与公告列表一致；有结束日期的卡片右侧显示倒计时。
 * 配置了图片的卡片以图片铺满作背景板并压暗，文字加黑色阴影保证可读。
 */
public class EventScreen extends Screen {

    private static final int HEADER_H = 18;
    private static final int MARGIN_X = 8;
    private static final int TOP_STRIP = 16;
    private static final int BIG_H = 60;

    private int panelX, panelY, panelW, panelH;
    private int contentY, contentBottom;
    private int scrollOffset = 0;
    private int selectedIdx = -1;
    private int mouseX, mouseY;
    /** 每帧重建的点击热区：{x, y, w, h, 活动下标} */
    private final List<int[]> hits = new ArrayList<>();
    /** 详情弹窗活动（null = 未打开） */
    private EventEntry detail;
    /** 详情弹窗内每帧重建的“领取”热区：{x, y, w, h, 包下标} */
    private final List<int[]> detailClaimHits = new ArrayList<>();

    public EventScreen() {
        super(Component.literal("活动"));
    }

    @Override
    protected void init() {
        super.init();
        // 与公告页同款全屏算法：面板跟随屏幕尺寸
        panelW = this.width - MARGIN_X * 2;
        panelH = this.height - 44;
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2 + HEADER_H;
        contentY = panelY + TOP_STRIP;
        contentBottom = panelY + panelH;
        detail = null;
        if (!EventClient.entries().isEmpty() && !EventClient.hasRequestedSync()) {
            EventClient.requestSync();
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 空实现：阻断主菜单模糊采样链盖在我们的背景上（与 AnnouncementScreen 同款）
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        hits.clear();
        g.fillGradient(0, 0, this.width, this.height, LobbyUiTheme.BACKGROUND, 0xFF04080E);

        int bx = panelX + 10;
        int textX = bx + 10;
        int textW = panelW - 20;

        g.fillGradient(panelX, panelY, panelX + panelW, panelY + panelH, 0xD80C1020, 0xD8101828);
        g.renderOutline(panelX, panelY, panelW, panelH, 0xFF1E3060);

        List<EventEntry> evs = visibleEntries();
        int totalH = computeTotalH(evs);
        int maxScroll = Math.max(0, totalH - (contentBottom - contentY));
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;
        if (scrollOffset < 0) scrollOffset = 0;

        g.enableScissor(bx, contentY, bx + textW, contentBottom);
        g.pose().pushPose();
        g.pose().translate(0, -scrollOffset, 0);

        EventEntry featured = null;
        int fIdx = -1;
        for (int i = 0; i < evs.size(); i++) {
            if (evs.get(i).featured()) { featured = evs.get(i); fIdx = i; break; }
        }
        if (featured != null) {
            card(g, featured, textX - 10, contentY, panelW - 20, BIG_H, fIdx);
        }

        int smallH = 52, gap = 6, cols = 2;
        int cellW = (panelW - 20 - gap * (cols - 1)) / cols;
        int cy = contentY + (featured != null ? BIG_H + 8 : 0);
        for (int i = 0; i < evs.size(); i++) {
            EventEntry e = evs.get(i);
            if (e.featured()) continue;
            int col = i % cols;
            int cx0 = bx + col * (cellW + gap);
            if (col == 0 && cy + smallH > contentBottom + scrollOffset) break;
            card(g, e, cx0, cy, cellW, smallH, i);
            if (col == cols - 1) cy += smallH + gap;
        }

        g.pose().popPose();
        g.disableScissor();

        LobbySubscreenRenderer.header(g, font, panelX, panelY - HEADER_H, panelW, "活动", "");
        int xW = 16, xX = panelX + panelW - xW, xY = panelY - HEADER_H;
        boolean xHov = mouseX >= xX && mouseX < xX + xW && mouseY >= xY && mouseY < xY + HEADER_H;
        g.drawString(font, "×", xX + 4, xY + (HEADER_H - 9) / 2, xHov ? 0xFFFF6666 : 0xFF334455, false);
        hit(xX, xY, xW, HEADER_H, -2);

        int listBottom = contentY + totalH - scrollOffset;
        if (listBottom < contentBottom - 4) {
            String tip = evs.isEmpty() ? "暂无活动" : "— 没有更多活动了 —";
            g.drawCenteredString(font, tip, panelX + panelW / 2, listBottom + 2, 0xFF556677);
        }
        if (maxScroll > 0) {
            int trackTop = contentY + 2, trackH = Math.max(1, contentBottom - trackTop - 4);
            int page = Math.max(1, (contentBottom - contentY) / (smallH + gap));
            int pages = Math.max(1, (totalH + (contentBottom - contentY) - 1) / (contentBottom - contentY));
            int barH = Math.max(10, trackH * page / Math.max(page, pages));
            int barY = trackTop + (int) ((long) scrollOffset * Math.max(0, trackH - barH) / maxScroll);
            g.fill(panelX + panelW - 4, barY, panelX + panelW - 2, barY + barH, 0xFFAECBE8);
        }

        if (detail != null) renderDetail(g);

        super.render(g, mouseX, mouseY, pt);
    }

    /** 单张卡片：有图时图片铺满整卡作背景板并压暗，所有文字加黑色阴影 */
    private void card(GuiGraphics g, EventEntry e, int bx, int cy0, int w, int hh, int idx) {
        boolean hover = mouseX >= bx && mouseX < bx + w && mouseY >= cy0 && mouseY < cy0 + hh;
        boolean selected = hover || idx == selectedIdx;
        g.fill(bx, cy0, bx + w, cy0 + hh, 0xFF14202A);
        g.renderOutline(bx, cy0, w, hh, selected ? LobbyUiTheme.ACCENT : 0xFF1E3060);

        int textX = bx + 10;
        boolean imgBg = !e.imageId().isEmpty() && EventClient.drawImage(g, e.imageId(), bx, cy0, w, hh);
        if (imgBg) {
            g.fill(bx, cy0, bx + w, cy0 + hh, 0x70000000);
        }
        int tw = Math.max(50, bx + w - 10 - textX);
        String cd = countdown(e);
        String title = clip(e.title(), cd.isEmpty() ? tw - 8 : tw - font.width("还剩 " + cd) - 10);
        String range = clip("时间: " + timeRange(e), tw);
        String reward = "点击查看活动详情与实际奖励: " + EventClient.displayRewards(e.rewards());
        if (hh <= 40) reward = clip("奖励: " + EventClient.displayRewards(e.rewards()), tw);
        else reward = clip(reward, tw);

        int tCol = imgBg ? 0xFFFFFFFF : (selected ? 0xFFFFFFFF : LobbyUiTheme.TEXT);
        int rCol = imgBg ? 0xFFE8EEF5 : LobbyUiTheme.TEXT_DIM;
        int cCol = imgBg ? 0xFFC8D4E2 : LobbyUiTheme.TEXT_FAINT;
        if (imgBg) {
            shadow(g, title, textX, cy0 + 6, tCol);
            shadow(g, range, textX, cy0 + 20, rCol);
            if (hh > 40) shadow(g, reward, textX, cy0 + 34, cCol);
            if (!cd.isEmpty()) {
                String cdt = "还剩 " + cd;
                shadow(g, cdt, bx + w - 10 - font.width(cdt), cy0 + 6, 0xFFFFD75E);
            }
        } else {
            g.drawString(font, title, textX, cy0 + 6, tCol, false);
            g.drawString(font, range, textX, cy0 + 20, rCol, false);
            if (hh > 40) g.drawString(font, reward, textX, cy0 + 34, cCol, false);
            if (!cd.isEmpty()) {
                String cdt = "还剩 " + cd;
                g.drawString(font, cdt, bx + w - 10 - font.width(cdt), cy0 + 6, 0xFFFFD75E, false);
            }
        }

        hit(bx, cy0 - scrollOffset, w, hh, idx);
    }

    /** 黑色阴影文字：阴影偏移 1px */
    private void shadow(GuiGraphics g, String s, int x, int y, int color) {
        g.drawString(font, s, x + 1, y + 1, 0xA0000000, false);
        g.drawString(font, s, x, y, color, false);
    }

    /** 详情弹窗：半透明遮罩 + 面板；标题栏有图时铺图片背景板 + 阴影文字 */
    private void renderDetail(GuiGraphics g) {
        detailClaimHits.clear();
        g.fill(0, 0, this.width, this.height, 0xA0000000);
        int w = Math.min(320, this.width - 40), h = 240;
        int x = (this.width - w) / 2, y = (this.height - h) / 2 + 10;
        g.fill(x, y, x + w, y + h, 0xFF14202A);
        g.renderOutline(x, y, w, h, 0xFFAECBE8);
        boolean imgBg = detail.imageId() != null && !detail.imageId().isEmpty()
                && EventClient.drawImage(g, detail.imageId(), x, y - HEADER_H, w, HEADER_H);
        if (imgBg) {
            g.fill(x, y - HEADER_H, x + w, y, 0x70000000);
            shadow(g, clip(detail.title(), w - 16), x + 8, y - HEADER_H + 6, 0xFFFFFFFF);
        } else {
            LobbySubscreenRenderer.header(g, font, x, y - HEADER_H, w, detail.title(), "");
        }

        int ty = y + 18;
        String line = join("活动时间: " + timeRange(detail), "（结束日以服务端为准）");
        for (var ln : font.split(Component.literal(line), w - 24)) {
            g.drawString(font, ln, x + 12, ty, LobbyUiTheme.TEXT_DIM, false);
            ty += 11;
        }
        ty += 4;
        g.drawString(font, "活动内容", x + 12, ty, LobbyUiTheme.ACCENT, false);
        ty += 12;
        String content = getContent(detail);
        for (var ln : font.split(Component.literal(content), w - 24)) {
            g.drawString(font, ln, x + 12, ty, 0xFFFFFFFF, false);
            ty += 11;
            if (ty > y + h - 90) break;
        }
        ty += 4;
        g.drawString(font, "实际奖励", x + 12, ty, LobbyUiTheme.ACCENT, false);
        ty += 12;
        for (var ln : font.split(Component.literal(EventClient.displayRewards(detail.rewards())), w - 24)) {
            g.drawString(font, ln, x + 12, ty, LobbyUiTheme.TEXT_DIM, false);
            ty += 11;
            if (ty > y + h - 70) break;
        }
        java.util.List<io.wifi.starrailexpress.event.EventPack> packs = EventClient.packs(detail);
        boolean headerDrawn = false;
        for (int i = 0; i < packs.size(); i++) {
            var pk = packs.get(i);
            String key = io.wifi.starrailexpress.event.EventManager.packKey(detail.id(), i);
            int[] pr = EventClient.progress(key);
            boolean claimable = pr != null && pr.length >= 4 && pr[3] == 1;
            boolean hasCond = !pk.conditions().isEmpty();
            if (!hasCond && !claimable) continue;
            boolean claimed = pr != null && pr.length > 2 && pr[2] == 1;
            if (!headerDrawn) {
                ty += 4;
                g.drawString(font, "任务与进度", x + 12, ty, LobbyUiTheme.ACCENT, false);
                ty += 12;
                headerDrawn = true;
            }
            if (ty > y + h - 40) {
                g.drawString(font, "…（空间不足，剩余任务省略）", x + 12, ty, LobbyUiTheme.TEXT_DIM, false);
                ty += 12;
                break;
            }
            if (hasCond) {
                g.drawString(font, clip("任务" + (i + 1) + "：" + EventClient.displayCondition(String.join(";", pk.conditions())), w - 24),
                        x + 12, ty, LobbyUiTheme.ACCENT, false);
                ty += 11;
                g.drawString(font, clip("奖励：" + EventClient.packRewardDisplay(pk), w - 24),
                        x + 12, ty, 0xFFCFE8DC, false);
                ty += 11;
                int target = 1;
                for (String c : pk.conditions()) {
                    if (c.startsWith("kill:")) target = Math.max(target, tokenTarget(c));
                }
                int cur = pr == null ? 0 : pr[0];
                int segW = (w - 24 - 70) / 5;
                int filled = claimed ? 5 : Math.min(5, (int) ((long) cur * 5 / target));
                for (int s = 0; s < 5; s++) {
                    int sx = x + 12 + s * segW;
                    g.fill(sx, ty, sx + segW - 2, ty + 8, s < filled ? 0xFF3FBF7F : 0xFF202A30);
                }
                String stat = claimed ? "已发放" : cur + "/" + target;
                g.drawString(font, stat, x + w - 12 - font.width(stat), ty,
                        claimed ? 0xFF55DD88 : LobbyUiTheme.TEXT_DIM, false);
                ty += 12;
            } else {
                int rewardY = ty;
                g.drawString(font, clip("奖励：" + EventClient.packRewardDisplay(pk), w - 60),
                        x + 12, rewardY, 0xFFCFE8DC, false);
                int cw2 = 40, ch2 = 11;
                int cx2 = x + w - 12 - cw2, cyy = rewardY - 1;
                if (claimed) {
                    g.drawString(font, "已领取", cx2, rewardY, 0xFF778899, false);
                } else {
                    boolean ch = mouseX >= cx2 && mouseX < cx2 + cw2 && mouseY >= cyy && mouseY < cyy + ch2;
                    g.fill(cx2, cyy, cx2 + cw2, cyy + ch2, ch ? 0xFF3A5A50 : 0xFF2A3A44);
                    g.drawCenteredString(font, "领取", cx2 + cw2 / 2, cyy + 2, 0xFFFFFFFF);
                    detailClaimHits.add(new int[]{cx2, cyy, cw2, ch2, i});
                }
                ty += 13;
            }
        }
        // 关闭按钮
        int bw2 = 60, bh2 = 14, bxp = x + (w - bw2) / 2, byp = y + h - 18;
        boolean hov = mouseX >= bxp && mouseX < bxp + bw2 && mouseY >= byp && mouseY < byp + bh2;
        g.fill(bxp, byp, bxp + bw2, byp + bh2, hov ? 0xFF3A5A50 : 0xFF2A3A44);
        g.drawCenteredString(font, "关闭", bxp + bw2 / 2, byp + 3, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (detail != null) {
            for (int[] hc : detailClaimHits) {
                if (mx >= hc[0] && mx < hc[0] + hc[2] && my >= hc[1] && my < hc[1] + hc[3]) {
                    EventClient.requestClaim(io.wifi.starrailexpress.event.EventManager
                            .packKey(detail.id(), hc[4]));
                    return true;
                }
            }
            int w = Math.min(320, this.width - 40), h = 240;
            int x = (this.width - w) / 2, y = (this.height - h) / 2 + 10;
            int bw2 = 60, bh2 = 14, bxp = x + (w - bw2) / 2, byp = y + h - 18;
            if (mx >= bxp && mx < bxp + bw2 && my >= byp && my < byp + bh2) detail = null;
            else if (mx < x || mx > x + w || my < y - HEADER_H || my > y + h) detail = null;
            return true;
        }
        if (mx >= panelX && mx <= panelX + panelW && my >= panelY - HEADER_H && my <= contentBottom) {
            if (btn == 1) { onClose(); return true; }
            for (int[] r : hits) {
                if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                    if (r[4] == -2) {
                        onClose();
                        return true;
                    }
                    if (r[4] == -1) return true;
                    List<EventEntry> evs = visibleEntries();
                    if (r[4] < evs.size()) {
                        detail = evs.get(r[4]);
                        selectedIdx = r[4];
                    }
                    return true;
                }
            }
            return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hx, double dy) {
        if (detail != null) return true;
        List<EventEntry> evs = visibleEntries();
        int totalH = computeTotalH(evs);
        int maxScroll = Math.max(0, totalH - (contentBottom - contentY));
        int step = (int) Math.signum(dy) * 18;
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - step));
        return true;
    }

    private void hit(int x, int y, int w, int h, int idx) {
        hits.add(new int[]{x, y, w, h, idx});
    }

    /** 大卡(60+8) 或每行两小卡(52+6)，向上取整 */
    private int computeTotalH(List<EventEntry> evs) {
        int featuredCount = 0;
        int normalCount = 0;
        for (EventEntry e : evs) {
            if (e.featured()) {
                if (featuredCount == 0) featuredCount = 1;
            } else {
                normalCount++;
            }
        }
        int h = 0;
        if (featuredCount > 0) h += BIG_H + 8;
        if (normalCount > 0) h += ((normalCount + 1) / 2) * (52 + 6);
        return h;
    }

    private List<EventEntry> visibleEntries() {
        List<EventEntry> evs = new ArrayList<>();
        for (EventEntry e : EventClient.entries()) {
            if (EventClient.inWindow(e)) evs.add(e);
        }
        return evs;
    }

    private String countdown(EventEntry e) {
        long days = EventClient.remainingDays(e);
        if (days == Long.MIN_VALUE) return "";
        if (days <= 0) return "今天结束";
        return "还剩 " + days + " 天";
    }

    private String timeRange(EventEntry e) {
        String s = e.startDate(), t = e.endDate();
        if (s.isEmpty() && t.isEmpty()) return "长期有效";
        if (s.isEmpty()) return "至 " + t;
        if (t.isEmpty()) return s + " 起";
        return s + " ~ " + t;
    }

    private String join(String a, String b) {
        return a + b;
    }

    private String clip(String s, int w) {
        return font.plainSubstrByWidth(s, Math.max(10, w));
    }

    private EventEntry detail() { return detail; }
    private String getContent(EventEntry e) { return e.content().isEmpty() ? "（暂无内容）" : e.content(); }

    private static int tokenTarget(String tok) {
        int c = tok.indexOf(':');
        if (c < 0) return 1;
        try {
            return Integer.parseInt(tok.substring(c + 1).trim());
        } catch (Exception e) {
            return 1;
        }
    }

    private static int parseTarget(String cond) {
        try {
            return Integer.parseInt(cond.substring(cond.indexOf(':') + 1).trim());
        } catch (Exception e) {
            return 1;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 261) {
            if (detail != null) {
                detail = null;
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}