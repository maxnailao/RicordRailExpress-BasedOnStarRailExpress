package io.wifi.starrailexpress.client.gui.screen;

import io.wifi.starrailexpress.content.musicbox.MusicBox;
import io.wifi.starrailexpress.content.musicbox.MusicBoxPlayerComponent;
import io.wifi.starrailexpress.content.musicbox.MusicBoxRegistry;
import io.wifi.starrailexpress.content.musicbox.network.DrawMusicBoxLotteryC2SPayload;
import io.wifi.starrailexpress.content.musicbox.network.MusicBoxLotteryResultS2CPayload;
import io.wifi.starrailexpress.content.musicbox.network.SelectMusicBoxC2SPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;

import java.util.*;

/**
 * 音乐盒列表选择界面。
 * <p>
 * 展示玩家拥有的所有音乐盒，可选择装备 / 试听 / 取消装备。
 * UI 风格参考 SkinManagementScreen（渐变背景 + 星星粒子 + 列表面板）。
 * </p>
 * <p>回退策略：整个文件删除即可。</p>
 */
public class MusicBoxScreen extends Screen {

    // 颜色常量
    private static final int BG_TOP = 0xFF1A1A2E;
    private static final int BG_BOTTOM = 0xFF16213E;
    private static final int PANEL_BG = 0x90303030;
    private static final int PANEL_BORDER = 0xFF555555;
    private static final int EQUIPPED_HIGHLIGHT = 0x8040AA40;
    private static final int EQUIPPED_BORDER = 0xFF00FF00;
    private static final int HOVER_BG = 0x804488CC;
    private static final int NORMAL_BG = 0x80404040;

    // 客户端缓存的音乐盒数据（从 CCA 组件同步或网络包同步）
    private static List<String> clientOwnedBoxes = null;
    private static String clientEquippedBox = null;

    // 抽奖结果缓存
    private static String lotteryResultMsg = null;
    private static long lotteryResultTime = 0;
    private static int clientLotteryTickets = -1;

    private final List<MusicBoxEntry> entries = new ArrayList<>();
    private int scrollOffset = 0;
    private Button backButton;
    private Button unequipButton;

    public MusicBoxScreen() {
        super(Component.translatable("screen.sre.musicbox.title"));
    }

    /**
     * 由网络包接收器调用，更新本地缓存（可选，CCA 自动同步也可提供数据）。
     */
    public static void updateCache(io.wifi.starrailexpress.content.musicbox.network.SyncMusicBoxS2CPayload payload) {
        clientOwnedBoxes = new ArrayList<>(payload.ownedBoxes());
        clientEquippedBox = payload.equippedBox().isEmpty() ? null : payload.equippedBox();
    }

    /**
     * 抽奖结果回调。
     */
    public static void onLotteryResult(MusicBoxLotteryResultS2CPayload payload) {
        clientLotteryTickets = payload.remainingTickets();
        if (payload.won()) {
            lotteryResultMsg = Component.translatable("screen.sre.musicbox.lottery.win", payload.musicBoxName()).getString();
        } else {
            lotteryResultMsg = Component.translatable("screen.sre.musicbox.lottery.lose").getString();
        }
        lotteryResultTime = System.currentTimeMillis();
        // 重新初始化界面以刷新显示
        var mc = Minecraft.getInstance();
        if (mc.screen instanceof MusicBoxScreen screen) {
            screen.init();
        }
    }

    /**
     * 由 S2C 网络包调用，强制更新客户端抽奖次数缓存。
     */
    public static void setLotteryTickets(int tickets) {
        clientLotteryTickets = tickets;
        var mc = Minecraft.getInstance();
        if (mc.screen instanceof MusicBoxScreen screen) {
            screen.init();
        }
    }

    private List<String> getOwnedBoxes() {
        if (clientOwnedBoxes != null) return clientOwnedBoxes;
        // 回退：直接从客户端 CCA 组件读取
        var player = Minecraft.getInstance().player;
        if (player != null) {
            var comp = MusicBoxPlayerComponent.KEY.maybeGet(player).orElse(null);
            if (comp != null) return new ArrayList<>(comp.getOwnedBoxes());
        }
        return new ArrayList<>();
    }

    private String getEquippedBox() {
        if (clientEquippedBox != null) return clientEquippedBox;
        var player = Minecraft.getInstance().player;
        if (player != null) {
            var comp = MusicBoxPlayerComponent.KEY.maybeGet(player).orElse(null);
            if (comp != null) return comp.getEquippedBox();
        }
        return null;
    }

    private int getLotteryTickets() {
        if (clientLotteryTickets >= 0) return clientLotteryTickets;
        var player = Minecraft.getInstance().player;
        if (player != null) {
            var comp = MusicBoxPlayerComponent.KEY.maybeGet(player).orElse(null);
            if (comp != null) return comp.getLotteryTickets();
        }
        return 0;
    }

    @Override
    protected void init() {
        super.init();
        clearWidgets();
        entries.clear();

        // 构建条目列表
        String equipped = getEquippedBox();

        // 第一条：“不播放音乐盒”选项（始终显示）
        entries.add(new MusicBoxEntry(null, equipped == null, true));

        for (String id : getOwnedBoxes()) {
            MusicBox box = MusicBoxRegistry.get(id);
            if (box != null) {
                entries.add(new MusicBoxEntry(box, id.equals(equipped), false));
            }
        }

        int screenWidth = this.width;
        int screenHeight = this.height;
        int listWidth = Math.min(screenWidth - 40, 400);
        int listX = (screenWidth - listWidth) / 2;
        int listTop = 40;
        int listHeight = screenHeight - 90;

        // 标题
        int titleWidth = Math.min(300, screenWidth - 20);
        int titleX = (screenWidth - titleWidth) / 2;
        addRenderableWidget(new Panel(titleX, 8, titleWidth, 28, PANEL_BG, PANEL_BORDER));
        addRenderableWidget(new CenteredLabel(titleX + titleWidth / 2, 16, this.title, 0xFFFFFFFF));

        if (entries.size() <= 1) {
            // 只有“不播放”选项，没有拥有的音乐盒
            addRenderableWidget(new CenteredLabel(screenWidth / 2, screenHeight / 2,
                    Component.translatable("screen.sre.musicbox.no_boxes"), 0xFFAAAAAA));
            // 仍然渲染“不播放”条目（在列表顶部）
            renderNoMusicEntry(listX, listTop, listWidth);
        } else {
            // 音乐盒列表条目
            int entryHeight = 32;
            int visibleCount = listHeight / entryHeight;
            for (int i = 0; i < Math.min(entries.size(), visibleCount); i++) {
                int idx = i + scrollOffset;
                if (idx >= entries.size()) break;
                MusicBoxEntry entry = entries.get(idx);
                int y = listTop + i * entryHeight;

                // 条目背景面板
                int bg = entry.equipped ? EQUIPPED_HIGHLIGHT : NORMAL_BG;
                int border = entry.equipped ? EQUIPPED_BORDER : PANEL_BORDER;
                addRenderableWidget(new Panel(listX, y, listWidth, entryHeight - 2, bg, border));

                // 名称
                Component displayName = entry.isNoMusic
                        ? Component.translatable("screen.sre.musicbox.no_music")
                        : entry.box.displayName();
                addRenderableWidget(new CenteredLabel(listX + 20, y + 4,
                        displayName, entry.equipped ? 0xFF00FF00 : 0xFFFFFFFF));

                // 试听按钮 & 装备按钮
                int btnW = 50;
                int btnH = 18;
                int equipX = listX + listWidth - btnW - 8;

                if (entry.isNoMusic) {
                    // “不播放音乐盒”没有试听按钮
                    if (entry.equipped) {
                        addRenderableWidget(new CenteredLabel(equipX + btnW / 2, y + 10,
                                Component.translatable("screen.sre.musicbox.equipped"), 0xFF00FF00));
                    } else {
                        addRenderableWidget(Button.builder(
                                Component.translatable("screen.sre.musicbox.equip"),
                                b -> equipMusicBox(""))
                                .pos(equipX, y + 6).size(btnW, btnH).build());
                    }
                } else {
                    int previewX = listX + listWidth - btnW * 2 - 16;
                    addRenderableWidget(Button.builder(
                            Component.translatable("screen.sre.musicbox.preview"),
                            b -> previewMusicBox(entry.box))
                            .pos(previewX, y + 6).size(btnW, btnH).build());

                    if (entry.equipped) {
                        addRenderableWidget(new CenteredLabel(equipX + btnW / 2, y + 10,
                                Component.translatable("screen.sre.musicbox.equipped"), 0xFF00FF00));
                    } else {
                        addRenderableWidget(Button.builder(
                                Component.translatable("screen.sre.musicbox.equip"),
                                b -> equipMusicBox(entry.box.id()))
                                .pos(equipX, y + 6).size(btnW, btnH).build());
                    }
                }
            }
        }

        // 抽奖按钮
        int lotteryBtnW = 120;
        int lotteryBtnH = 20;
        int tickets = getLotteryTickets();
        Component lotteryLabel = tickets > 0
                ? Component.translatable("screen.sre.musicbox.lottery.draw", tickets)
                : Component.translatable("screen.sre.musicbox.lottery.no_tickets");
        Button lotteryButton = Button.builder(lotteryLabel, b -> drawLottery())
                .pos((screenWidth - lotteryBtnW) / 2, screenHeight - 56).size(lotteryBtnW, lotteryBtnH).build();
        lotteryButton.active = tickets > 0;
        addRenderableWidget(lotteryButton);

        // 取消装备按钮
        int btnW = 100;
        int btnH = 20;
        unequipButton = Button.builder(
                Component.translatable("screen.sre.musicbox.unequip"),
                b -> equipMusicBox(""))
                .pos((screenWidth - btnW) / 2 - btnW - 8, screenHeight - 32).size(btnW, btnH).build();
        unequipButton.active = getEquippedBox() != null;
        addRenderableWidget(unequipButton);

        // 返回按钮
        backButton = Button.builder(
                Component.translatable("screen.sre.musicbox.back"),
                b -> this.onClose())
                .pos((screenWidth - btnW) / 2 + 8, screenHeight - 32).size(btnW, btnH).build();
        addRenderableWidget(backButton);
    }

    private void previewMusicBox(MusicBox box) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.playNotifySound(box.soundEvent(), SoundSource.RECORDS, box.volume(), 1.0f);
        }
    }

    /**
     * 渲染“不播放音乐盒”选项（用于空状态列表）。
     */
    private void renderNoMusicEntry(int listX, int listTop, int listWidth) {
        int entryHeight = 32;
        String equipped = getEquippedBox();
        boolean isSelected = equipped == null;
        int bg = isSelected ? EQUIPPED_HIGHLIGHT : NORMAL_BG;
        int border = isSelected ? EQUIPPED_BORDER : PANEL_BORDER;
        addRenderableWidget(new Panel(listX, listTop, listWidth, entryHeight - 2, bg, border));
        addRenderableWidget(new CenteredLabel(listX + 20, listTop + 4,
                Component.translatable("screen.sre.musicbox.no_music"),
                isSelected ? 0xFF00FF00 : 0xFFFFFFFF));
        int btnW = 50;
        int btnH = 18;
        int equipX = listX + listWidth - btnW - 8;
        if (isSelected) {
            addRenderableWidget(new CenteredLabel(equipX + btnW / 2, listTop + 10,
                    Component.translatable("screen.sre.musicbox.equipped"), 0xFF00FF00));
        } else {
            addRenderableWidget(Button.builder(
                    Component.translatable("screen.sre.musicbox.equip"),
                    b -> equipMusicBox(""))
                    .pos(equipX, listTop + 6).size(btnW, btnH).build());
        }
    }

    private void equipMusicBox(String id) {
        ClientPlayNetworking.send(new SelectMusicBoxC2SPayload(id));
        // 乐观更新本地缓存
        clientEquippedBox = id.isEmpty() ? null : id;
        this.init();
    }

    private void drawLottery() {
        ClientPlayNetworking.send(new DrawMusicBoxLotteryC2SPayload());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        // 显示抽奖结果（3秒内显示）
        if (lotteryResultMsg != null && System.currentTimeMillis() - lotteryResultTime < 3000) {
            var font = Minecraft.getInstance().font;
            int tw = font.width(lotteryResultMsg);
            int x = (width - tw) / 2;
            int y = height / 2 - 30;
            boolean isWin = lotteryResultMsg.contains("抽中") || lotteryResultMsg.contains("won");
            graphics.fill(x - 4, y - 4, x + tw + 4, y + font.lineHeight + 4, 0xCC000000);
            graphics.drawString(font, lotteryResultMsg, x, y, isWin ? 0xFF00FF00 : 0xFFAAAAAA, false);
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fillGradient(0, 0, width, height, BG_TOP, BG_BOTTOM);
        // 星星粒子
        long time = System.currentTimeMillis();
        for (int i = 0; i < 15; i++) {
            float x = (float) ((time * 0.2 + i * 50) % width);
            float y = (float) ((Math.sin(time * 0.001 + i) * 30 + height / 2) % height);
            float size = 1 + (float) Math.sin(time * 0.002 + i);
            int alpha = (int) (50 + 100 * Math.sin(time * 0.0005 + i));
            int starColor = (alpha << 24) | 0xFFFFFF;
            graphics.fill((int) x, (int) y, (int) (x + size), (int) (y + size), starColor);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (deltaY < 0 && scrollOffset + 1 < entries.size()) {
            scrollOffset++;
            this.init();
        } else if (deltaY > 0 && scrollOffset > 0) {
            scrollOffset--;
            this.init();
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ── 内部数据结构 ──

    private record MusicBoxEntry(MusicBox box, boolean equipped, boolean isNoMusic) {}

    // ── 内部 Widget ──

    private static class Panel extends AbstractWidget {
        private final int bg, border;

        Panel(int x, int y, int w, int h, int bg, int border) {
            super(x, y, w, h, Component.empty());
            this.bg = bg;
            this.border = border;
        }

        @Override
        public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            g.fill(getX(), getY(), getX() + width, getY() + height, bg);
            g.fill(getX(), getY(), getX() + width, getY() + 1, border);
            g.fill(getX(), getY() + height - 1, getX() + width, getY() + height, border);
            g.fill(getX(), getY(), getX() + 1, getY() + height, border);
            g.fill(getX() + width - 1, getY(), getX() + width, getY() + height, border);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int btn) { return false; }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {}
    }

    private static class CenteredLabel extends AbstractWidget {
        private final Component text;
        private final int color;

        CenteredLabel(int x, int y, Component text, int color) {
            super(x, y, 0, 0, text);
            this.text = text;
            this.color = color;
        }

        @Override
        public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            var font = Minecraft.getInstance().font;
            int tw = font.width(text);
            int th = font.lineHeight;
            g.drawString(font, text, getX() - tw / 2, getY() - th / 2 + 4, color, false);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {}
    }
}
