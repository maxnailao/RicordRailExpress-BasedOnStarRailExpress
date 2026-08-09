package io.wifi.starrailexpress.client.gui.screen;

import io.wifi.starrailexpress.network.packet.GomokuJoinC2SPacket;
import io.wifi.starrailexpress.network.packet.GomokuMoveC2SPacket;
import io.wifi.starrailexpress.network.packet.GomokuStateS2CPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * 五子棋小游戏客户端界面
 * <p>
 * 联机对战：两名玩家通过服务器匹配，在 19x19 棋盘上对弈。
 * 黑棋先手，五子连珠获胜。
 * </p>
 */
public class GomokuMinigameScreen extends Screen {

    private static final int BOARD_SIZE = 19;
    private static final int BOARD_RENDER_SIZE = 306;   // 棋盘渲染像素大小（306/18=17px间距）
    private static final int PIECE_SIZE = 14;            // 棋子渲染大小
    private static final int GHOST_SIZE = 14;           // 待放置棋子大小
    private static final int BOARD_BG_COLOR = 0xFFC8A84E;   // 棋盘底色（黄褐色）
    private static final int GRID_LINE_COLOR = 0xFF000000;  // 网格线颜色（黑色）

    // 纹理
    private static final ResourceLocation BOARD_TEX =
            ResourceLocation.fromNamespaceAndPath("noellesroles", "textures/gui/wuziqi/qipan.png");
    private static final ResourceLocation BLACK_TEX =
            ResourceLocation.fromNamespaceAndPath("noellesroles", "textures/gui/wuziqi/heizi.png");
    private static final ResourceLocation WHITE_TEX =
            ResourceLocation.fromNamespaceAndPath("noellesroles", "textures/gui/wuziqi/baizi.png");
    private static final ResourceLocation GHOST_TEX =
            ResourceLocation.fromNamespaceAndPath("noellesroles", "textures/gui/wuziqi/daifangzhi.png");

    // 游戏状态
    private enum State { WAITING, PLAYING, ENDED }
    private State state = State.WAITING;

    // 棋盘数据
    private final byte[] boardData = new byte[BOARD_SIZE * BOARD_SIZE];

    // 对局信息
    private boolean isBlack = true;       // 客户端玩家是否为黑棋
    private UUID currentTurn = null;      // 当前回合玩家 UUID
    private UUID localPlayerUUID = null;  // 本地玩家 UUID
    private UUID winnerUUID = null;       // 胜利者 UUID
    private String blackName = "";
    private String whiteName = "";
    private String endMessage = null;     // 结束时显示的消息

    private long endMessageTime = 0;      // 结束消息出现的时间戳

    private final Runnable onSuccess;     // 游戏结束回调

    public GomokuMinigameScreen(BlockPos pos, Runnable onSuccess) {
        super(Component.translatable("screen.starrailexpress.gomoku.title"));
        this.onSuccess = onSuccess;
    }

    // ══════════════════════════════════════════════
    // 初始化与生命周期
    // ══════════════════════════════════════════════

    @Override
    protected void init() {
        super.init();
        if (minecraft != null && minecraft.player != null) {
            localPlayerUUID = minecraft.player.getUUID();
        }
        // 发送加入队列请求
        ClientPlayNetworking.send(new GomokuJoinC2SPacket(GomokuJoinC2SPacket.ACTION_JOIN));
    }

    @Override
    public void onClose() {
        // 仅在界面真正关闭时通知服务端离开（removed() 在打开暂停菜单等临时覆盖时也会触发，会导致误判负）
        ClientPlayNetworking.send(new GomokuJoinC2SPacket(GomokuJoinC2SPacket.ACTION_LEAVE));
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ══════════════════════════════════════════════
    // 网络数据接收（由 ClientNetwork 调用）
    // ══════════════════════════════════════════════

    /** 接收服务端状态包 */
    public void onStateReceived(GomokuStateS2CPacket packet) {
        // 更新棋盘
        System.arraycopy(packet.boardData(), 0, boardData, 0, BOARD_SIZE * BOARD_SIZE);
        isBlack = packet.isBlack();
        currentTurn = packet.currentTurn();
        blackName = packet.blackName();
        whiteName = packet.whiteName();

        switch (packet.action()) {
            case GomokuStateS2CPacket.WAITING:
                state = State.WAITING;
                break;
            case GomokuStateS2CPacket.GAME_START:
            case GomokuStateS2CPacket.MOVE:
                state = State.PLAYING;
                break;
            case GomokuStateS2CPacket.WIN:
                state = State.ENDED;
                winnerUUID = packet.winner();
                if (winnerUUID != null && winnerUUID.equals(localPlayerUUID)) {
                    endMessage = Component.translatable("screen.starrailexpress.gomoku.win").getString();
                } else {
                    endMessage = Component.translatable("screen.starrailexpress.gomoku.lose").getString();
                }
                endMessageTime = System.currentTimeMillis();
                break;
            case GomokuStateS2CPacket.DRAW:
                state = State.ENDED;
                endMessage = Component.translatable("screen.starrailexpress.gomoku.draw").getString();
                endMessageTime = System.currentTimeMillis();
                break;
            case GomokuStateS2CPacket.OPPONENT_LEFT:
                state = State.ENDED;
                endMessage = Component.translatable("screen.starrailexpress.gomoku.opponent_left").getString();
                endMessageTime = System.currentTimeMillis();
                break;
        }
    }

    // ══════════════════════════════════════════════
    // 渲染
    // ══════════════════════════════════════════════

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int boardLeft = centerX - BOARD_RENDER_SIZE / 2;
        int boardTop = centerY - BOARD_RENDER_SIZE / 2;

        if (state == State.WAITING) {
            renderWaiting(g, centerX, centerY);
            return;
        }

        // 绘制棋盘背景
        g.fill(boardLeft, boardTop, boardLeft + BOARD_RENDER_SIZE, boardTop + BOARD_RENDER_SIZE, BOARD_BG_COLOR);

        // 计算交叉点间距：19路棋盘，18个间距
        float spacing = (float) BOARD_RENDER_SIZE / (BOARD_SIZE - 1);

        // 绘制 19x19 网格线（代码绘制，确保精确 19 条线）
        for (int i = 0; i < BOARD_SIZE; i++) {
            int pos = boardLeft + (int) (i * spacing);
            // 竖线
            g.fill(pos, boardTop, pos + 1, boardTop + BOARD_RENDER_SIZE, GRID_LINE_COLOR);
            // 横线
            g.fill(boardLeft, boardTop + (int) (i * spacing), boardLeft + BOARD_RENDER_SIZE, boardTop + (int) (i * spacing) + 1, GRID_LINE_COLOR);
        }

        // 绘制棋子（落在线的交叉处，包括边缘）
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                byte stone = boardData[r * BOARD_SIZE + c];
                if (stone == 0) continue;

                int px = boardLeft + (int) (c * spacing) - PIECE_SIZE / 2 + 1;
                int py = boardTop + (int) (r * spacing) - PIECE_SIZE / 2 + 1;

                ResourceLocation tex = (stone == 1) ? BLACK_TEX : WHITE_TEX;
                g.blit(tex, px, py, 0, 0, PIECE_SIZE, PIECE_SIZE, PIECE_SIZE, PIECE_SIZE);
            }
        }

        // 绘制待放置预览（仅己方回合）
        if (state == State.PLAYING && isMyTurn()) {
            int[] nearest = getNearestIntersection(mouseX, mouseY, boardLeft, boardTop, spacing);
            if (nearest != null) {
                int r = nearest[0], c = nearest[1];
                if (boardData[r * BOARD_SIZE + c] == 0) {
                    int px = boardLeft + (int) (c * spacing) - GHOST_SIZE / 2 + 1;
                    int py = boardTop + (int) (r * spacing) - GHOST_SIZE / 2 + 1;
                    g.blit(GHOST_TEX, px, py, 0, 0, GHOST_SIZE, GHOST_SIZE, GHOST_SIZE, GHOST_SIZE);
                }
            }
        }

        // 顶部信息栏
        renderInfoBar(g, centerX, boardTop - 20);

        // 结束消息
        if (state == State.ENDED && endMessage != null) {
            renderEndMessage(g, centerX, centerY);
        }
    }

    private void renderWaiting(GuiGraphics g, int centerX, int centerY) {
        g.drawCenteredString(this.font,
                Component.translatable("screen.starrailexpress.gomoku.waiting"),
                centerX, centerY - 8, 0xFFFFFF);
        g.drawCenteredString(this.font,
                Component.translatable("screen.starrailexpress.gomoku.waiting_hint"),
                centerX, centerY + 8, 0x888888);
    }

    private void renderInfoBar(GuiGraphics g, int centerX, int y) {
        String myColor = isBlack
                ? Component.translatable("screen.starrailexpress.gomoku.black").getString()
                : Component.translatable("screen.starrailexpress.gomoku.white").getString();

        // 黑棋名称
        g.drawString(this.font, "● " + blackName, centerX - 150, y,
                isBlack ? 0xAAAAAA : 0x333333);
        // 白棋名称
        g.drawString(this.font, "○ " + whiteName, centerX + 70, y,
                !isBlack ? 0xAAAAAA : 0x333333);

        // 回合提示
        if (state == State.PLAYING) {
            if (isMyTurn()) {
                g.drawCenteredString(this.font,
                        Component.translatable("screen.starrailexpress.gomoku.your_turn"),
                        centerX, y + 14, 0x55FF55);
            } else {
                g.drawCenteredString(this.font,
                        Component.translatable("screen.starrailexpress.gomoku.opponent_turn"),
                        centerX, y + 14, 0xFFAA55);
            }
        }
    }

    private void renderEndMessage(GuiGraphics g, int centerX, int centerY) {
        // 半透明背景
        g.fill(centerX - 100, centerY - 20, centerX + 100, centerY + 20, 0xCC000000);
        g.renderOutline(centerX - 100, centerY - 20, 200, 40, 0xFF666666);
        g.drawCenteredString(this.font, endMessage, centerX, centerY - 4, 0xFFFFFF);

        // 3 秒后显示关闭提示
        if (System.currentTimeMillis() - endMessageTime > 3000) {
            g.drawCenteredString(this.font,
                    Component.translatable("screen.starrailexpress.gomoku.close_hint"),
                    centerX, centerY + 30, 0x888888);
        }
    }

    // ══════════════════════════════════════════════
    // 交互
    // ══════════════════════════════════════════════

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        // 结束后点击关闭
        if (state == State.ENDED && System.currentTimeMillis() - endMessageTime > 3000) {
            onClose();
            return true;
        }

        // 仅己方回合可落子
        if (state != State.PLAYING || !isMyTurn()) return true;

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int boardLeft = centerX - BOARD_RENDER_SIZE / 2;
        int boardTop = centerY - BOARD_RENDER_SIZE / 2;
        float spacing = (float) BOARD_RENDER_SIZE / (BOARD_SIZE - 1);

        int[] nearest = getNearestIntersection((int) mouseX, (int) mouseY, boardLeft, boardTop, spacing);
        if (nearest != null) {
            int r = nearest[0], c = nearest[1];
            if (boardData[r * BOARD_SIZE + c] == 0) {
                // 发送落子请求
                ClientPlayNetworking.send(new GomokuMoveC2SPacket(r, c));
            }
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ══════════════════════════════════════════════
    // 辅助方法
    // ══════════════════════════════════════════════

    private boolean isMyTurn() {
        return localPlayerUUID != null && localPlayerUUID.equals(currentTurn);
    }

    /**
     * 获取鼠标最近的交叉点
     * 19路棋盘，交叉点位于 i * spacing 位置（包括边缘线与其他线的交点）
     * @return [row, col] 或 null（超出范围）
     */
    private int[] getNearestIntersection(int mouseX, int mouseY, int boardLeft, int boardTop, float spacing) {
        float relX = mouseX - boardLeft;
        float relY = mouseY - boardTop;

        // 反算交叉点坐标：intersection = i * spacing
        // i = intersection / spacing
        int col = Math.round(relX / spacing);
        int row = Math.round(relY / spacing);

        if (row < 0 || row >= BOARD_SIZE || col < 0 || col >= BOARD_SIZE) return null;

        // 检查距离是否在合理范围内（半个间距）
        float snapDist = spacing * 0.45f;
        float expectedX = col * spacing;
        float expectedY = row * spacing;
        float dx = relX - expectedX;
        float dy = relY - expectedY;
        if (Math.abs(dx) > snapDist || Math.abs(dy) > snapDist) return null;

        return new int[]{row, col};
    }
}
