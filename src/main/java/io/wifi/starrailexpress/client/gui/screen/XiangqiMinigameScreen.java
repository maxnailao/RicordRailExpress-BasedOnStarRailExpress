package io.wifi.starrailexpress.client.gui.screen;

import io.wifi.starrailexpress.content.minigame.xiangqi.XiangqiSession;
import io.wifi.starrailexpress.network.packet.XiangqiJoinC2SPacket;
import io.wifi.starrailexpress.network.packet.XiangqiMoveC2SPacket;
import io.wifi.starrailexpress.network.packet.XiangqiStateS2CPacket;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * 象棋小游戏客户端界面
 * <p>
 * 联机对战：两名玩家通过服务器匹配。先加入者为红方（先行）。
 * 吃掉对方的将/帅即获胜。
 * </p>
 */
public class XiangqiMinigameScreen extends Screen {

    private static final int ROWS = 10;
    private static final int COLS = 9;
    private static final int SPACING = 36;
    private static final int RIVER_H = 24;
    private static final int BOARD_W = (COLS - 1) * SPACING;   // 288
    private static final int BOARD_H = (ROWS - 1) * SPACING + RIVER_H; // 384
    private static final int PIECE_SIZE = 32;
    private static final int BG_COLOR = 0xFFC8A84E;
    private static final int LINE_COLOR = 0xFF000000;
    private static final int RIVER_COLOR = 0xFFB8944A;
    private static final int SELECT_COLOR = 0xAA00FF00;

    private static final ResourceLocation PIECE_TEX =
            ResourceLocation.fromNamespaceAndPath("noellesroles", "textures/gui/xiangqi/qizimoban.png");

    // 棋子汉字（索引: pieceType 1-7）
    private static final String[] RED_CHARS = {"", "帅", "仕", "相", "马", "车", "炮", "兵"};
    private static final String[] BLACK_CHARS = {"", "将", "士", "象", "马", "车", "炮", "卒"};

    // 状态
    private enum State { WAITING, PLAYING, ENDED }
    private State state = State.WAITING;

    private final byte[] boardData = new byte[ROWS * COLS];
    private boolean isRed = true;
    private UUID currentTurn = null;
    private UUID localUUID = null;
    private UUID winnerUUID = null;
    private String redName = "", blackName = "";
    private String endMessage = null;

    // 选中棋子（逻辑坐标，未翻转）
    private int selRow = -1, selCol = -1;

    private final Runnable onSuccess;

    public XiangqiMinigameScreen(BlockPos pos, Runnable onSuccess) {
        super(Component.translatable("screen.starrailexpress.xiangqi.title"));
        this.onSuccess = onSuccess;
    }

    @Override
    protected void init() {
        super.init();
        if (minecraft != null && minecraft.player != null)
            localUUID = minecraft.player.getUUID();
        ClientPlayNetworking.send(new XiangqiJoinC2SPacket(XiangqiJoinC2SPacket.ACTION_JOIN));
    }

    @Override
    public void removed() {
        ClientPlayNetworking.send(new XiangqiJoinC2SPacket(XiangqiJoinC2SPacket.ACTION_LEAVE));
        super.removed();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ══════════════════════════════════════════════
    // 网络数据接收
    // ══════════════════════════════════════════════

    public void onStateReceived(XiangqiStateS2CPacket packet) {
        System.arraycopy(packet.boardData(), 0, boardData, 0, ROWS * COLS);
        isRed = packet.isRed();
        currentTurn = packet.currentTurn();
        redName = packet.redName();
        blackName = packet.blackName();

        switch (packet.action()) {
            case XiangqiStateS2CPacket.WAITING -> state = State.WAITING;
            case XiangqiStateS2CPacket.GAME_START, XiangqiStateS2CPacket.MOVE -> {
                state = State.PLAYING;
                selRow = -1; selCol = -1;
            }
            case XiangqiStateS2CPacket.WIN -> {
                state = State.ENDED;
                winnerUUID = packet.winner();
                endMessage = (winnerUUID != null && winnerUUID.equals(localUUID))
                        ? Component.translatable("screen.starrailexpress.xiangqi.win").getString()
                        : Component.translatable("screen.starrailexpress.xiangqi.lose").getString();
            }
            case XiangqiStateS2CPacket.OPPONENT_LEFT -> {
                state = State.ENDED;
                endMessage = Component.translatable("screen.starrailexpress.xiangqi.opponent_left").getString();
            }
        }
    }

    // ══════════════════════════════════════════════
    // 坐标转换（视角翻转）
    // ══════════════════════════════════════════════

    /** 逻辑行 → 屏幕行 */
    private int viewRow(int r) { return isRed ? r : (ROWS - 1 - r); }
    /** 逻辑列 → 屏幕列 */
    private int viewCol(int c) { return isRed ? c : (COLS - 1 - c); }
    /** 屏幕行 → 逻辑行 */
    private int logicRow(int vr) { return isRed ? vr : (ROWS - 1 - vr); }
    /** 屏幕列 → 逻辑列 */
    private int logicCol(int vc) { return isRed ? vc : (COLS - 1 - vc); }

    /** 获取屏幕坐标处的逻辑棋子 */
    private byte getViewPiece(int vr, int vc) {
        return boardData[logicRow(vr) * COLS + logicCol(vc)];
    }

    /** 逻辑行对应的像素 Y（含楚河偏移） */
    private int rowToPixelY(int r) { return r <= 4 ? r * SPACING : r * SPACING + RIVER_H; }
    /** 逻辑列对应的像素 X */
    private int colToPixelX(int c) { return c * SPACING; }

    // ══════════════════════════════════════════════
    // 渲染
    // ══════════════════════════════════════════════

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);

        int cx = this.width / 2, cy = this.height / 2;
        int boardLeft = cx - BOARD_W / 2;
        int boardTop = cy - BOARD_H / 2;

        if (state == State.WAITING) { renderWaiting(g, cx, cy); return; }

        // 棋盘背景
        g.fill(boardLeft - 4, boardTop - 4, boardLeft + BOARD_W + 4, boardTop + BOARD_H + 4, 0xFF8B7040);
        g.fill(boardLeft, boardTop, boardLeft + BOARD_W, boardTop + BOARD_H, BG_COLOR);

        drawGrid(g, boardLeft, boardTop);
        drawPalaceDiagonals(g, boardLeft, boardTop);
        drawRiver(g, boardLeft, boardTop);
        drawPositionMarkers(g, boardLeft, boardTop);
        drawPieces(g, boardLeft, boardTop);
        drawSelection(g, boardLeft, boardTop);
        drawInfoBar(g, cx, boardTop - 28);

        if (state == State.ENDED) drawEndOverlay(g, cx, cy);
    }

    // ── 网格 ──

    private void drawGrid(GuiGraphics g, int bl, int bt) {
        // 10 条横线
        for (int r = 0; r < ROWS; r++) {
            int y = bt + rowToPixelY(r);
            g.fill(bl, y, bl + BOARD_W, y + 1, LINE_COLOR);
        }
        // 9 条竖线（楚河处断开）
        for (int c = 0; c < COLS; c++) {
            int x = bl + colToPixelX(c);
            // 上半部分（行0-4）
            g.fill(x, bt, x + 1, bt + rowToPixelY(4) + 1, LINE_COLOR);
            // 下半部分（行5-9）
            int y5 = bt + rowToPixelY(5);
            g.fill(x, y5, x + 1, bt + rowToPixelY(9) + 1, LINE_COLOR);
        }
        // 边线贯穿楚河
        g.fill(bl, bt, bl + 1, bt + BOARD_H, LINE_COLOR);
        g.fill(bl + BOARD_W - 1, bt, bl + BOARD_W, bt + BOARD_H, LINE_COLOR);
    }

    // ── 九宫斜线 ──

    private void drawPalaceDiagonals(GuiGraphics g, int bl, int bt) {
        drawX(g, bl, bt, 3, 0, 5, 2); // 黑方九宫
        drawX(g, bl, bt, 3, 7, 5, 9); // 红方九宫
    }

    private void drawX(GuiGraphics g, int bl, int bt, int c1, int r1, int c2, int r2) {
        int x1 = bl + colToPixelX(c1), y1 = bt + rowToPixelY(r1);
        int x2 = bl + colToPixelX(c2), y2 = bt + rowToPixelY(r2);
        drawLine(g, x1, y1, x2, y2);
        drawLine(g, bl + colToPixelX(c2), y1, bl + colToPixelX(c1), y2);
    }

    private void drawLine(GuiGraphics g, int x1, int y1, int x2, int y2) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        for (int i = 0; i <= steps; i++) {
            int x = x1 + (x2 - x1) * i / steps;
            int y = y1 + (y2 - y1) * i / steps;
            g.fill(x, y, x + 1, y + 1, LINE_COLOR);
        }
    }

    // ── 楚河汉界 ──

    private void drawRiver(GuiGraphics g, int bl, int bt) {
        int riverY = bt + rowToPixelY(4) + 1;
        int riverBottom = bt + rowToPixelY(5);
        g.fill(bl + 1, riverY, bl + BOARD_W - 1, riverBottom, RIVER_COLOR);

        String left = isRed ? "楚 河" : "汉 界";
        String right = isRed ? "汉 界" : "楚 河";
        int textY = riverY + (riverBottom - riverY) / 2 - 4;
        g.drawString(this.font, left, bl + BOARD_W / 4 - 18, textY, 0xFF6B5030);
        g.drawString(this.font, right, bl + BOARD_W * 3 / 4 - 18, textY, 0xFF6B5030);
    }

    // ── 位置标记（十字花） ──

    private void drawPositionMarkers(GuiGraphics g, int bl, int bt) {
        // 炮位: (2,1),(2,7),(7,1),(7,7)
        int[][] cannonPos = {{2,1},{2,7},{7,1},{7,7}};
        for (int[] p : cannonPos) drawCross(g, bl, bt, p[0], p[1]);
        // 兵/卒位
        for (int c = 0; c < 9; c += 2) { drawCross(g, bl, bt, 3, c); drawCross(g, bl, bt, 6, c); }
    }

    private void drawCross(GuiGraphics g, int bl, int bt, int r, int c) {
        int x = bl + colToPixelX(viewCol(c));
        int y = bt + rowToPixelY(viewRow(r));
        int s = 4, gap = 2;
        // 四角小短线（避开边缘）
        if (c > 0) {
            g.fill(x - gap - s, y - gap - 1, x - gap, y - gap, LINE_COLOR);
            g.fill(x - gap - 1, y - gap - s, x - gap, y - gap, LINE_COLOR);
            g.fill(x - gap - s, y + gap, x - gap, y + gap + 1, LINE_COLOR);
            g.fill(x - gap - 1, y + gap, x - gap, y + gap + s, LINE_COLOR);
        }
        if (c < 8) {
            g.fill(x + gap, y - gap - 1, x + gap + s, y - gap, LINE_COLOR);
            g.fill(x + gap, y - gap - s, x + gap + 1, y - gap, LINE_COLOR);
            g.fill(x + gap, y + gap, x + gap + s, y + gap + 1, LINE_COLOR);
            g.fill(x + gap, y + gap, x + gap + 1, y + gap + s, LINE_COLOR);
        }
    }

    // ── 棋子 ──

    private void drawPieces(GuiGraphics g, int bl, int bt) {
        for (int vr = 0; vr < ROWS; vr++) {
            for (int vc = 0; vc < COLS; vc++) {
                byte p = getViewPiece(vr, vc);
                if (p == 0) continue;
                int px = bl + colToPixelX(vc) - PIECE_SIZE / 2;
                int py = bt + rowToPixelY(vr) - PIECE_SIZE / 2;

                // 底图
                g.blit(PIECE_TEX, px, py, 0, 0, PIECE_SIZE, PIECE_SIZE, PIECE_SIZE, PIECE_SIZE);

                // 汉字（需要处理翻转）
                int type = XiangqiSession.getType(p);
                int side = XiangqiSession.getSide(p);
                String ch = (side == 0) ? RED_CHARS[type] : BLACK_CHARS[type];
                int color = (side == 0) ? 0xFFCC0000 : 0xFF111111;

                if (!isRed) {
                    // 黑方视角：棋子需要180度翻转，使文字正向
                    var pose = g.pose();
                    pose.pushPose();
                    pose.translate(px + PIECE_SIZE / 2f, py + PIECE_SIZE / 2f, 0);
                    pose.mulPose(Axis.ZP.rotation((float) Math.PI));
                    pose.translate(-PIECE_SIZE / 2f, -PIECE_SIZE / 2f, 0);
                    g.drawString(this.font, ch, 8, 8, color);
                    pose.popPose();
                } else {
                    g.drawString(this.font, ch, px + 8, py + 8, color);
                }
            }
        }
    }

    // ── 选中高亮 ──

    private void drawSelection(GuiGraphics g, int bl, int bt) {
        if (selRow < 0) return;
        int vc = viewCol(selCol), vr = viewRow(selRow);
        int px = bl + colToPixelX(vc) - PIECE_SIZE / 2;
        int py = bt + rowToPixelY(vr) - PIECE_SIZE / 2;
        g.fill(px - 2, py - 2, px + PIECE_SIZE + 2, py + PIECE_SIZE + 2, SELECT_COLOR);
    }

    // ── 信息栏 ──

    private void drawInfoBar(GuiGraphics g, int cx, int y) {
        g.fill(cx - BOARD_W / 2, y, cx + BOARD_W / 2, y + 22, 0xEE1A1A2E);
        g.renderOutline(cx - BOARD_W / 2, y, BOARD_W, 22, 0xFF4A4A6A);

        boolean myTurn = currentTurn != null && currentTurn.equals(localUUID);
        String turnText = myTurn
                ? Component.translatable("screen.starrailexpress.xiangqi.your_turn").getString()
                : Component.translatable("screen.starrailexpress.xiangqi.opponent_turn").getString();
        int turnColor = myTurn ? 0x55FF55 : 0xFF8888;
        g.drawString(this.font, turnText, cx - BOARD_W / 2 + 10, y + 7, turnColor);

        String names = redName + " vs " + blackName;
        g.drawString(this.font, names, cx + BOARD_W / 2 - this.font.width(names) - 10, y + 7, 0xBBBBBB);
    }

    // ── 等待 / 结束 ──

    private void renderWaiting(GuiGraphics g, int cx, int cy) {
        g.fill(cx - 120, cy - 40, cx + 120, cy + 40, 0xEE1A1A2E);
        g.renderOutline(cx - 120, cy - 40, 240, 80, 0xFF4A4A6A);
        g.drawCenteredString(this.font,
                Component.translatable("screen.starrailexpress.xiangqi.waiting").getString(),
                cx, cy - 8, 0xFFFFFF);
    }

    private void drawEndOverlay(GuiGraphics g, int cx, int cy) {
        g.fill(cx - 100, cy - 20, cx + 100, cy + 20, 0xCC000000);
        g.drawCenteredString(this.font, endMessage, cx, cy - 4, 0xFFFF4444);
    }

    // ══════════════════════════════════════════════
    // 交互
    // ══════════════════════════════════════════════

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (state != State.PLAYING || button != 0) return super.mouseClicked(mouseX, mouseY, button);
        if (currentTurn == null || !currentTurn.equals(localUUID)) return true; // 不是自己回合

        int cx = this.width / 2, cy = this.height / 2;
        int boardLeft = cx - BOARD_W / 2, boardTop = cy - BOARD_H / 2;
        float relX = (float) (mouseX - boardLeft);
        float relY = (float) (mouseY - boardTop);

        // 反算屏幕坐标
        int vc = Math.round(relX / SPACING);
        // 需要处理楚河偏移
        int vr;
        if (relY <= rowToPixelY(4) + SPACING / 2f) {
            vr = Math.round(relY / SPACING);
        } else {
            vr = Math.round((relY - RIVER_H) / SPACING);
        }
        if (vr < 0 || vr >= ROWS || vc < 0 || vc >= COLS) return true;

        int lr = logicRow(vr), lc = logicCol(vc);
        byte target = boardData[lr * COLS + lc];
        boolean targetIsMine = target != 0 && (XiangqiSession.getSide(target) == 0) == isRed;

        if (selRow >= 0) {
            if (targetIsMine) {
                // 点击己方棋子：切换选中
                selRow = lr; selCol = lc;
            } else {
                // 尝试移动
                ClientPlayNetworking.send(new XiangqiMoveC2SPacket(selRow, selCol, lr, lc));
                selRow = -1; selCol = -1;
            }
        } else if (targetIsMine) {
            selRow = lr; selCol = lc;
        }
        return true;
    }
}
