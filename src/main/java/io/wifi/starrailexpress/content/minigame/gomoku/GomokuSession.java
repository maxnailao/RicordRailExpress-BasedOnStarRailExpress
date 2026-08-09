package io.wifi.starrailexpress.content.minigame.gomoku;

import io.wifi.starrailexpress.network.packet.GomokuStateS2CPacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * 五子棋游戏会话
 * <p>
 * 管理一局五子棋的棋盘状态、回合轮转和胜负检测。
 * 棋盘为 19x19，黑棋先手，五子连珠获胜。
 * </p>
 */
public class GomokuSession {

    public static final int BOARD_SIZE = 19;
    public static final byte EMPTY = 0;
    public static final byte BLACK = 1;
    public static final byte WHITE = 2;

    private final byte[][] board = new byte[BOARD_SIZE][BOARD_SIZE];

    private final ServerPlayer blackPlayer;  // 先手（黑棋）
    private final ServerPlayer whitePlayer;  // 后手（白棋）

    private UUID currentTurn;  // 当前回合玩家 UUID
    private boolean finished = false;
    private UUID winner = null;

    public GomokuSession(ServerPlayer blackPlayer, ServerPlayer whitePlayer) {
        this.blackPlayer = blackPlayer;
        this.whitePlayer = whitePlayer;
        this.currentTurn = blackPlayer.getUUID(); // 黑棋先手
    }

    // ══════════════════════════════════════════════
    // 棋盘查询
    // ══════════════════════════════════════════════

    public byte getStone(int row, int col) {
        if (row < 0 || row >= BOARD_SIZE || col < 0 || col >= BOARD_SIZE) return -1;
        return board[row][col];
    }

    public boolean isFinished() {
        return finished;
    }

    public UUID getCurrentTurn() {
        return currentTurn;
    }

    public ServerPlayer getBlackPlayer() {
        return blackPlayer;
    }

    public ServerPlayer getWhitePlayer() {
        return whitePlayer;
    }

    public ServerPlayer getOpponent(ServerPlayer player) {
        return player.getUUID().equals(blackPlayer.getUUID()) ? whitePlayer : blackPlayer;
    }

    public boolean isBlackPlayer(ServerPlayer player) {
        return player.getUUID().equals(blackPlayer.getUUID());
    }

    /** 将棋盘展平为 byte[361] */
    public byte[] flattenBoard() {
        byte[] flat = new byte[BOARD_SIZE * BOARD_SIZE];
        for (int r = 0; r < BOARD_SIZE; r++) {
            System.arraycopy(board[r], 0, flat, r * BOARD_SIZE, BOARD_SIZE);
        }
        return flat;
    }

    // ══════════════════════════════════════════════
    // 落子与胜负检测
    // ══════════════════════════════════════════════

    /**
     * 尝试落子
     * @return true 落子成功, false 非法操作
     */
    public boolean placeStone(int row, int col, ServerPlayer player) {
        if (finished) return false;
        if (!player.getUUID().equals(currentTurn)) return false;
        if (row < 0 || row >= BOARD_SIZE || col < 0 || col >= BOARD_SIZE) return false;
        if (board[row][col] != EMPTY) return false;

        byte stone = player.getUUID().equals(blackPlayer.getUUID()) ? BLACK : WHITE;
        board[row][col] = stone;

        // 检测五连
        if (checkWin(row, col, stone)) {
            finished = true;
            winner = player.getUUID();
            broadcastWin();
            return true;
        }

        // 检测平局（棋盘满）
        if (isBoardFull()) {
            finished = true;
            broadcastDraw();
            return true;
        }

        // 切换回合
        currentTurn = currentTurn.equals(blackPlayer.getUUID())
                ? whitePlayer.getUUID()
                : blackPlayer.getUUID();

        // 广播落子状态
        broadcastMoveState();
        return true;
    }

    /**
     * 从落子点向 4 个方向检测是否连成五子
     */
    private boolean checkWin(int row, int col, byte stone) {
        // 四个方向: 横(0,1), 竖(1,0), 左斜(1,1), 右斜(1,-1)
        int[][] directions = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};

        for (int[] dir : directions) {
            int count = 1; // 包含自身

            // 正方向
            for (int i = 1; i < 5; i++) {
                int r = row + dir[0] * i;
                int c = col + dir[1] * i;
                if (r < 0 || r >= BOARD_SIZE || c < 0 || c >= BOARD_SIZE) break;
                if (board[r][c] != stone) break;
                count++;
            }

            // 反方向
            for (int i = 1; i < 5; i++) {
                int r = row - dir[0] * i;
                int c = col - dir[1] * i;
                if (r < 0 || r >= BOARD_SIZE || c < 0 || c >= BOARD_SIZE) break;
                if (board[r][c] != stone) break;
                count++;
            }

            if (count >= 5) return true;
        }
        return false;
    }

    private boolean isBoardFull() {
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (board[r][c] == EMPTY) return false;
            }
        }
        return true;
    }

    // ══════════════════════════════════════════════
    // 状态广播
    // ══════════════════════════════════════════════

    private void broadcastMoveState() {
        byte[] flat = flattenBoard();
        String bName = blackPlayer.getName().getString();
        String wName = whitePlayer.getName().getString();
        // 给黑棋玩家发送 isBlack=true
        ServerPlayNetworking.send(blackPlayer,
                GomokuStateS2CPacket.move(flat, currentTurn, true, bName, wName));
        // 给白棋玩家发送 isBlack=false
        ServerPlayNetworking.send(whitePlayer,
                GomokuStateS2CPacket.move(flat, currentTurn, false, bName, wName));
    }

    private void broadcastDraw() {
        byte[] flat = flattenBoard();
        String bName = blackPlayer.getName().getString();
        String wName = whitePlayer.getName().getString();
        ServerPlayNetworking.send(blackPlayer,
                GomokuStateS2CPacket.draw(flat, true, bName, wName));
        ServerPlayNetworking.send(whitePlayer,
                GomokuStateS2CPacket.draw(flat, false, bName, wName));
    }

    /** 对手离开，当前玩家获胜 */
    public void handleOpponentLeft(ServerPlayer leaver) {
        if (finished) return;
        finished = true;
        ServerPlayer remaining = getOpponent(leaver);
        winner = remaining.getUUID();

        byte[] flat = flattenBoard();
        String bName = blackPlayer.getName().getString();
        String wName = whitePlayer.getName().getString();
        // 通知剩余玩家：对手已离开（视为获胜）
        ServerPlayNetworking.send(remaining,
                GomokuStateS2CPacket.opponentLeft(flat, isBlackPlayer(remaining), bName, wName));
    }

    /** 游戏开始广播 */
    public void broadcastGameStart() {
        byte[] flat = flattenBoard();
        String bName = blackPlayer.getName().getString();
        String wName = whitePlayer.getName().getString();
        ServerPlayNetworking.send(blackPlayer,
                GomokuStateS2CPacket.gameStart(flat, currentTurn, true, bName, wName));
        ServerPlayNetworking.send(whitePlayer,
                GomokuStateS2CPacket.gameStart(flat, currentTurn, false, bName, wName));
    }

    /** 向指定玩家重发当前棋盘状态（客户端重连/重开界面时补发） */
    public void resyncTo(ServerPlayer player) {
        byte[] flat = flattenBoard();
        String bName = blackPlayer.getName().getString();
        String wName = whitePlayer.getName().getString();
        boolean isBlack = isBlackPlayer(player);
        if (finished) {
            ServerPlayNetworking.send(player,
                    GomokuStateS2CPacket.win(flat, winner, isBlack, bName, wName));
        } else {
            ServerPlayNetworking.send(player,
                    GomokuStateS2CPacket.gameStart(flat, currentTurn, isBlack, bName, wName));
        }
    }

    private void broadcastWin() {
        byte[] flat = flattenBoard();
        String bName = blackPlayer.getName().getString();
        String wName = whitePlayer.getName().getString();
        ServerPlayNetworking.send(blackPlayer,
                GomokuStateS2CPacket.win(flat, winner, true, bName, wName));
        ServerPlayNetworking.send(whitePlayer,
                GomokuStateS2CPacket.win(flat, winner, false, bName, wName));
    }
}
