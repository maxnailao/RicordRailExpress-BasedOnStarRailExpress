package io.wifi.starrailexpress.content.minigame.xiangqi;

import io.wifi.starrailexpress.network.packet.XiangqiStateS2CPacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * 象棋游戏会话
 * <p>
 * 管理棋盘状态、回合轮转和移动规则校验。
 * 棋盘 10 行 x 9 列，黑方在上（行 0-4），红方在下（行 5-9）。
 * 红方先行，吃掉对方将/帅即获胜。
 * </p>
 */
public class XiangqiSession {

    public static final int ROWS = 10;
    public static final int COLS = 9;

    // 棋子类型 (低3位)
    public static final int GENERAL = 1;  // 帅/将
    public static final int ADVISOR = 2;  // 仕/士
    public static final int ELEPHANT = 3; // 相/象
    public static final int HORSE = 4;    // 马
    public static final int CHARIOT = 5;  // 车
    public static final int CANNON = 6;   // 炮
    public static final int SOLDIER = 7;  // 兵/卒

    // 阵营 (第4位: 0=红, 1=黑)
    public static final int RED = 0;
    public static final int BLACK = 1;

    private final byte[][] board = new byte[ROWS][COLS];
    private final ServerPlayer redPlayer;   // 红方（先行）
    private final ServerPlayer blackPlayer; // 黑方
    private UUID currentTurn;
    private boolean finished = false;
    private UUID winner = null;

    public XiangqiSession(ServerPlayer redPlayer, ServerPlayer blackPlayer) {
        this.redPlayer = redPlayer;
        this.blackPlayer = blackPlayer;
        this.currentTurn = redPlayer.getUUID(); // 红方先行
        initBoard();
    }

    // ══════════════════════════════════════════════
    // 棋子编码工具
    // ══════════════════════════════════════════════

    /** 创建棋子字节: type | (side << 3) */
    private static byte piece(int type, int side) {
        return (byte) (type | (side << 3));
    }

    /** 获取棋子类型 (1-7) */
    public static int getType(byte p) { return p & 0x07; }

    /** 获取阵营 (0=红, 1=黑) */
    public static int getSide(byte p) { return (p >> 3) & 0x01; }

    /** 判断棋子属于哪方 */
    private boolean isRed(byte p) { return p != 0 && getSide(p) == RED; }

    // ══════════════════════════════════════════════
    // 初始棋盘布局
    // ══════════════════════════════════════════════

    private void initBoard() {
        // 黑方（行 0-4）
        board[0][0] = piece(CHARIOT, BLACK); board[0][1] = piece(HORSE, BLACK);
        board[0][2] = piece(ELEPHANT, BLACK); board[0][3] = piece(ADVISOR, BLACK);
        board[0][4] = piece(GENERAL, BLACK); board[0][5] = piece(ADVISOR, BLACK);
        board[0][6] = piece(ELEPHANT, BLACK); board[0][7] = piece(HORSE, BLACK);
        board[0][8] = piece(CHARIOT, BLACK);
        board[2][1] = piece(CANNON, BLACK); board[2][7] = piece(CANNON, BLACK);
        for (int c = 0; c < 9; c += 2) board[3][c] = piece(SOLDIER, BLACK);

        // 红方（行 5-9）
        for (int c = 0; c < 9; c += 2) board[6][c] = piece(SOLDIER, RED);
        board[7][1] = piece(CANNON, RED); board[7][7] = piece(CANNON, RED);
        board[9][0] = piece(CHARIOT, RED); board[9][1] = piece(HORSE, RED);
        board[9][2] = piece(ELEPHANT, RED); board[9][3] = piece(ADVISOR, RED);
        board[9][4] = piece(GENERAL, RED); board[9][5] = piece(ADVISOR, RED);
        board[9][6] = piece(ELEPHANT, RED); board[9][7] = piece(HORSE, RED);
        board[9][8] = piece(CHARIOT, RED);
    }

    // ══════════════════════════════════════════════
    // 棋盘查询
    // ══════════════════════════════════════════════

    public byte getPiece(int r, int c) {
        if (r < 0 || r >= ROWS || c < 0 || c >= COLS) return -1;
        return board[r][c];
    }

    public boolean isFinished() { return finished; }
    public UUID getCurrentTurn() { return currentTurn; }
    public ServerPlayer getRedPlayer() { return redPlayer; }
    public ServerPlayer getBlackPlayer() { return blackPlayer; }
    public ServerPlayer getOpponent(ServerPlayer p) {
        return p.getUUID().equals(redPlayer.getUUID()) ? blackPlayer : redPlayer;
    }
    public boolean isRedPlayer(ServerPlayer p) { return p.getUUID().equals(redPlayer.getUUID()); }

    /** 展平为 byte[90] */
    public byte[] flattenBoard() {
        byte[] flat = new byte[ROWS * COLS];
        for (int r = 0; r < ROWS; r++)
            System.arraycopy(board[r], 0, flat, r * COLS, COLS);
        return flat;
    }

    // ══════════════════════════════════════════════
    // 移动入口
    // ══════════════════════════════════════════════

    public boolean movePiece(int fr, int fc, int tr, int tc, ServerPlayer player) {
        if (finished) return false;
        if (!player.getUUID().equals(currentTurn)) return false;
        if (fr < 0 || fr >= ROWS || fc < 0 || fc >= COLS) return false;
        if (tr < 0 || tr >= ROWS || tc < 0 || tc >= COLS) return false;
        if (fr == tr && fc == tc) return false;

        byte src = board[fr][fc];
        byte dst = board[tr][tc];
        if (src == 0) return false;

        // 必须是己方棋子
        boolean playerIsRed = player.getUUID().equals(redPlayer.getUUID());
        if (playerIsRed != isRed(src)) return false;

        // 不能吃自己的棋子
        if (dst != 0 && isRed(src) == isRed(dst)) return false;

        // 规则校验
        if (!isValidMove(src, fr, fc, tr, tc)) return false;

        // 执行移动
        board[tr][tc] = src;
        board[fr][fc] = 0;

        // 检测胜负：吃掉了将/帅
        if (dst != 0 && getType(dst) == GENERAL) {
            finished = true;
            winner = player.getUUID();
            broadcastWin();
            return true;
        }

        // 切换回合
        currentTurn = currentTurn.equals(redPlayer.getUUID())
                ? blackPlayer.getUUID() : redPlayer.getUUID();
        broadcastMoveState();
        return true;
    }

    // ══════════════════════════════════════════════
    // 移动规则校验
    // ══════════════════════════════════════════════

    private boolean isValidMove(byte src, int fr, int fc, int tr, int tc) {
        return switch (getType(src)) {
            case GENERAL -> validGeneral(fr, fc, tr, tc, getSide(src));
            case ADVISOR -> validAdvisor(fr, fc, tr, tc, getSide(src));
            case ELEPHANT -> validElephant(fr, fc, tr, tc, getSide(src));
            case HORSE -> validHorse(fr, fc, tr, tc);
            case CHARIOT -> validChariot(fr, fc, tr, tc);
            case CANNON -> validCannon(fr, fc, tr, tc);
            case SOLDIER -> validSoldier(fr, fc, tr, tc, getSide(src));
            default -> false;
        };
    }

    /** 帅/将: 一步一格正交，九宫内；支持飞将（同列无阻挡直取对方将/帅） */
    private boolean validGeneral(int fr, int fc, int tr, int tc, int side) {
        // 飞将：同列、中间无棋子、目标是对方将/帅
        if (fc == tc && board[tr][tc] != 0 && getType(board[tr][tc]) == GENERAL) {
            if (countBetween(fr, fc, tr, tc) == 0) return true;
        }
        // 常规走法：一步一格正交，九宫内
        int dr = Math.abs(tr - fr), dc = Math.abs(tc - fc);
        if (dr + dc != 1) return false;
        return inPalace(tr, tc, side);
    }

    /** 仕/士: 一步斜一格，九宫内 */
    private boolean validAdvisor(int fr, int fc, int tr, int tc, int side) {
        if (Math.abs(tr - fr) != 1 || Math.abs(tc - fc) != 1) return false;
        return inPalace(tr, tc, side);
    }

    /** 相/象: 走田字（斜2格），不过河，象眼无阻挡 */
    private boolean validElephant(int fr, int fc, int tr, int tc, int side) {
        if (Math.abs(tr - fr) != 2 || Math.abs(tc - fc) != 2) return false;
        // 不能过河
        if (side == RED && (tr < 5)) return false;
        if (side == BLACK && (tr > 4)) return false;
        // 象眼检查
        int er = (fr + tr) / 2, ec = (fc + tc) / 2;
        return board[er][ec] == 0;
    }

    /** 马: 走日字，蹩马腿 */
    private boolean validHorse(int fr, int fc, int tr, int tc) {
        int dr = Math.abs(tr - fr), dc = Math.abs(tc - fc);
        if (!((dr == 2 && dc == 1) || (dr == 1 && dc == 2))) return false;

        // 蹩马腿：检查前进方向第一步
        if (dr == 2) {
            // 纵向日字，先纵后横
            int legR = fr + (tr > fr ? 1 : -1);
            if (board[legR][fc] != 0) return false;
        } else {
            // 横向日字，先横后纵
            int legC = fc + (tc > fc ? 1 : -1);
            if (board[fr][legC] != 0) return false;
        }
        return true;
    }

    /** 车: 直线任意距离，路径无阻挡 */
    private boolean validChariot(int fr, int fc, int tr, int tc) {
        return countBetween(fr, fc, tr, tc) == 0;
    }

    /** 炮: 移动同车（0阻挡），吃子必须恰好1个炮架 */
    private boolean validCannon(int fr, int fc, int tr, int tc) {
        int between = countBetween(fr, fc, tr, tc);
        if (board[tr][tc] == 0) return between == 0;  // 移动不吃子
        return between == 1; // 吃子需要恰好1个炮架
    }

    /** 兵/卒: 过河前前进一格，过河后可前进或横移一格 */
    private boolean validSoldier(int fr, int fc, int tr, int tc, int side) {
        int dr = tr - fr, dc = Math.abs(tc - fc);
        int forward = (side == RED) ? -1 : 1; // 红向上(行减小)，黑向下(行增大)
        boolean crossed = (side == RED) ? (fr <= 4) : (fr >= 5); // 是否已过河

        if (dc == 0 && dr == forward) return true; // 前进一步
        if (crossed && dr == 0 && dc == 1) return true; // 过河后可横移
        return false;
    }

    // ══════════════════════════════════════════════
    // 公共静态验证（客户端本地预判 + 服务端共用）
    // ══════════════════════════════════════════════

    /**
     * 客户端本地校验走法是否合法（用于待落子预览）。
     * @param flatBoard byte[90] 展平棋盘
     */
    public static boolean isValidMoveClient(byte[] flatBoard, int fr, int fc, int tr, int tc) {
        if (fr == tr && fc == tc) return false;
        if (fr < 0 || fr >= ROWS || fc < 0 || fc >= COLS) return false;
        if (tr < 0 || tr >= ROWS || tc < 0 || tc >= COLS) return false;
        byte src = flatBoard[fr * COLS + fc];
        byte dst = flatBoard[tr * COLS + tc];
        if (src == 0) return false;
        if (dst != 0 && getSide(src) == getSide(dst)) return false;
        byte[][] b = new byte[ROWS][COLS];
        for (int r = 0; r < ROWS; r++)
            System.arraycopy(flatBoard, r * COLS, b[r], 0, COLS);
        return isValidMoveStatic(b, src, fr, fc, tr, tc);
    }

    private static boolean isValidMoveStatic(byte[][] board, byte src, int fr, int fc, int tr, int tc) {
        return switch (getType(src)) {
            case GENERAL -> validGeneralS(board, fr, fc, tr, tc, getSide(src));
            case ADVISOR -> validAdvisorS(board, fr, fc, tr, tc, getSide(src));
            case ELEPHANT -> validElephantS(board, fr, fc, tr, tc, getSide(src));
            case HORSE -> validHorseS(board, fr, fc, tr, tc);
            case CHARIOT -> validChariotS(board, fr, fc, tr, tc);
            case CANNON -> validCannonS(board, fr, fc, tr, tc);
            case SOLDIER -> validSoldierS(fr, fc, tr, tc, getSide(src));
            default -> false;
        };
    }

    private static boolean validGeneralS(byte[][] board, int fr, int fc, int tr, int tc, int side) {
        // 飞将：同列、中间无棋子、目标是对方将/帅
        if (fc == tc && board[tr][tc] != 0 && getType(board[tr][tc]) == GENERAL) {
            if (countBetweenS(board, fr, fc, tr, tc) == 0) return true;
        }
        // 常规走法
        int dr = Math.abs(tr - fr), dc = Math.abs(tc - fc);
        if (dr + dc != 1) return false;
        return inPalaceS(tr, tc, side);
    }

    private static boolean validAdvisorS(byte[][] board, int fr, int fc, int tr, int tc, int side) {
        if (Math.abs(tr - fr) != 1 || Math.abs(tc - fc) != 1) return false;
        return inPalaceS(tr, tc, side);
    }

    private static boolean validElephantS(byte[][] board, int fr, int fc, int tr, int tc, int side) {
        if (Math.abs(tr - fr) != 2 || Math.abs(tc - fc) != 2) return false;
        if (side == RED && tr < 5) return false;
        if (side == BLACK && tr > 4) return false;
        int er = (fr + tr) / 2, ec = (fc + tc) / 2;
        return board[er][ec] == 0;
    }

    private static boolean validHorseS(byte[][] board, int fr, int fc, int tr, int tc) {
        int dr = Math.abs(tr - fr), dc = Math.abs(tc - fc);
        if (!((dr == 2 && dc == 1) || (dr == 1 && dc == 2))) return false;
        if (dr == 2) {
            int legR = fr + (tr > fr ? 1 : -1);
            if (board[legR][fc] != 0) return false;
        } else {
            int legC = fc + (tc > fc ? 1 : -1);
            if (board[fr][legC] != 0) return false;
        }
        return true;
    }

    private static boolean validChariotS(byte[][] board, int fr, int fc, int tr, int tc) {
        return countBetweenS(board, fr, fc, tr, tc) == 0;
    }

    private static boolean validCannonS(byte[][] board, int fr, int fc, int tr, int tc) {
        int between = countBetweenS(board, fr, fc, tr, tc);
        if (board[tr][tc] == 0) return between == 0;
        return between == 1;
    }

    private static boolean validSoldierS(int fr, int fc, int tr, int tc, int side) {
        int dr = tr - fr, dc = Math.abs(tc - fc);
        int forward = (side == RED) ? -1 : 1;
        boolean crossed = (side == RED) ? (fr <= 4) : (fr >= 5);
        if (dc == 0 && dr == forward) return true;
        if (crossed && dr == 0 && dc == 1) return true;
        return false;
    }

    private static boolean inPalaceS(int r, int c, int side) {
        if (c < 3 || c > 5) return false;
        return side == RED ? (r >= 7 && r <= 9) : (r >= 0 && r <= 2);
    }

    private static int countBetweenS(byte[][] board, int fr, int fc, int tr, int tc) {
        if (fr != tr && fc != tc) return -1;
        int count = 0;
        if (fr == tr) {
            int minC = Math.min(fc, tc), maxC = Math.max(fc, tc);
            for (int c = minC + 1; c < maxC; c++)
                if (board[fr][c] != 0) count++;
        } else {
            int minR = Math.min(fr, tr), maxR = Math.max(fr, tr);
            for (int r = minR + 1; r < maxR; r++)
                if (board[r][fc] != 0) count++;
        }
        return count;
    }

    // ══════════════════════════════════════════════
    // 辅助方法
    // ══════════════════════════════════════════════

    /** 判断位置是否在九宫内 */
    private boolean inPalace(int r, int c, int side) {
        if (c < 3 || c > 5) return false;
        return side == RED ? (r >= 7 && r <= 9) : (r >= 0 && r <= 2);
    }

    /** 计算两点之间的棋子数（不含端点，必须同行或同列） */
    private int countBetween(int fr, int fc, int tr, int tc) {
        if (fr != tr && fc != tc) return -1; // 非直线
        int count = 0;
        if (fr == tr) {
            int minC = Math.min(fc, tc), maxC = Math.max(fc, tc);
            for (int c = minC + 1; c < maxC; c++)
                if (board[fr][c] != 0) count++;
        } else {
            int minR = Math.min(fr, tr), maxR = Math.max(fr, tr);
            for (int r = minR + 1; r < maxR; r++)
                if (board[r][fc] != 0) count++;
        }
        return count;
    }

    // ══════════════════════════════════════════════
    // 状态广播
    // ══════════════════════════════════════════════

    private void broadcastMoveState() {
        byte[] flat = flattenBoard();
        String rN = redPlayer.getName().getString();
        String bN = blackPlayer.getName().getString();
        ServerPlayNetworking.send(redPlayer,
                XiangqiStateS2CPacket.move(flat, currentTurn, true, rN, bN));
        ServerPlayNetworking.send(blackPlayer,
                XiangqiStateS2CPacket.move(flat, currentTurn, false, rN, bN));
    }

    public void broadcastGameStart() {
        byte[] flat = flattenBoard();
        String rN = redPlayer.getName().getString();
        String bN = blackPlayer.getName().getString();
        ServerPlayNetworking.send(redPlayer,
                XiangqiStateS2CPacket.gameStart(flat, currentTurn, true, rN, bN));
        ServerPlayNetworking.send(blackPlayer,
                XiangqiStateS2CPacket.gameStart(flat, currentTurn, false, rN, bN));
    }

    private void broadcastWin() {
        byte[] flat = flattenBoard();
        String rN = redPlayer.getName().getString();
        String bN = blackPlayer.getName().getString();
        ServerPlayNetworking.send(redPlayer,
                XiangqiStateS2CPacket.win(flat, winner, true, rN, bN));
        ServerPlayNetworking.send(blackPlayer,
                XiangqiStateS2CPacket.win(flat, winner, false, rN, bN));
    }

    public void handleOpponentLeft(ServerPlayer leaver) {
        if (finished) return;
        finished = true;
        ServerPlayer remaining = getOpponent(leaver);
        winner = remaining.getUUID();
        byte[] flat = flattenBoard();
        String rN = redPlayer.getName().getString();
        String bN = blackPlayer.getName().getString();
        ServerPlayNetworking.send(remaining,
                XiangqiStateS2CPacket.opponentLeft(flat, isRedPlayer(remaining), rN, bN));
    }
}
