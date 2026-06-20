package io.wifi.starrailexpress.client.gui.screen;

import io.wifi.starrailexpress.content.minigame.mahjong.MahjongSession;
import io.wifi.starrailexpress.network.packet.MahjongActionC2SPacket;
import io.wifi.starrailexpress.network.packet.MahjongDiscardC2SPacket;
import io.wifi.starrailexpress.network.packet.MahjongJoinC2SPacket;
import io.wifi.starrailexpress.network.packet.MahjongStateS2CPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.*;

/**
 * 麻将小游戏客户端界面（纯代码绘制，无贴图）
 * 4人联网对战，服务端权威。
 */
public class MahjongMinigameScreen extends Screen {

    // ── 颜色方案 ──
    private static final int C_TABLE = 0xFF0D3B0E;       // 深绿桌面外框
    private static final int C_TABLE_BORDER = 0xFF4E342E; // 桌边木纹(深棕)
    private static final int C_TABLE_INNER = 0xFF1B5E20;  // 浅绿中央
    private static final int C_TABLE_FELT = 0xFF2E7D32;   // 中央毛毡
    private static final int C_TILE_BG = 0xFFF5F0E0;      // 牌底色(米白)
    private static final int C_TILE_BORDER = 0xFF5D4037;   // 牌边框(深棕)
    private static final int C_TILE_SHADOW = 0x60000000;   // 牌阴影
    private static final int C_TILE_SELECTED = 0xFFFFF9C4; // 选中牌(浅黄)
    private static final int C_TILE_BACK = 0xFF1B5E20;    // 牌背(深绿)
    private static final int C_TILE_BACK2 = 0xFF388E3C;   // 牌背内(浅绿)
    private static final int C_WAN = 0xFFC62828;           // 万(红)
    private static final int C_TIAO = 0xFF2E7D32;          // 条(绿)
    private static final int C_BING = 0xFF1565C0;          // 饼(蓝)
    private static final int C_ZI_NORMAL = 0xFF212121;     // 字牌(黑)
    private static final int C_ZI_ZHONG = 0xFFC62828;      // 中(红)
    private static final int C_ZI_FA = 0xFF2E7D32;         // 发(绿)
    private static final int C_ZI_BAI = 0xFF9E9E9E;        // 白(灰)
    private static final int C_DISCARD_BG = 0x50000000;    // 弃牌区半透明黑
    private static final int C_LAST_DISCARD = 0xFFFFD54F;   // 最后出牌高亮(金)
    private static final int C_NAME_BG = 0x80000000;       // 名字背景半透明
    private static final int C_GOLD = 0xFFFFD54F;           // 金色
    private static final int C_TURN_GLOW = 0xFFFFF176;      // 回合高亮(黄)

    // 字牌名称与颜色
    private static final String[] ZI_NAMES = {"东", "南", "西", "北", "中", "发", "白"};
    private static final int[] ZI_COLORS = {C_ZI_NORMAL, C_ZI_NORMAL, C_ZI_NORMAL, C_ZI_NORMAL,
            C_ZI_ZHONG, C_ZI_FA, C_ZI_BAI};

    // 中文数字（用于万/条/饼左上角标注）
    private static final String[] CN_NUM = {"", "一", "二", "三", "四", "五", "六", "七", "八", "九"};

    // ── 饼子圆点布局（3x3网格，相对中心偏移）──
    private static final int[][][] DOT_P = {
        null,
        {{0,0}},
        {{-6,-7},{6,7}},
        {{-6,-7},{0,0},{6,7}},
        {{-6,-7},{6,-7},{-6,7},{6,7}},
        {{-6,-7},{6,-7},{0,0},{-6,7},{6,7}},
        {{-6,-7},{0,-7},{6,-7},{-6,7},{0,7},{6,7}},
        {{-6,-7},{0,-7},{6,-7},{0,0},{-6,7},{0,7},{6,7}},
        {{-6,-7},{0,-7},{6,-7},{-6,0},{6,0},{-6,7},{0,7},{6,7}},
        {{-6,-7},{0,-7},{6,-7},{-6,0},{0,0},{6,0},{-6,7},{0,7},{6,7}}
    };
    // ── 条子竹节布局（列x偏移，相对中心）──
    private static final int[][] BAM_X = {
        null,
        {0},
        {-5,5},
        {-8,0,8},
        {-9,-3,3,9},
        {-10,-5,0,5,10},
        {-7,0,7,-7,0,7},     // 6: 3+3两行
        {-9,-3,3,9,-6,0,6},  // 7: 4+3两行
        {-9,-3,3,9,-9,-3,3,9}, // 8: 4+4两行
        {-9,-3,3,9,-10,-5,0,5,10}  // 9: 4+5两行
    };

    // ── 尺寸常量 ──
    private static final int TW = 28;     // 手牌宽
    private static final int TH = 38;     // 手牌高
    private static final int TS = 2;      // 手牌间距
    private static final int SW = 18;     // 小牌宽
    private static final int SH = 24;     // 小牌高
    private static final int DW = 16;     // 弃牌宽
    private static final int DH = 22;     // 弃牌高

    // ── 游戏状态 ──
    private byte phase = 0;
    private int currentTurn = -1;
    private int dealerIndex = -1;
    private int playerIndex = 0;
    private int[] myHand = new int[0];
    private int[] oppCounts = new int[3];
    private int[][] myMelds = new int[0][];
    private int[][][] oppMelds = new int[3][][];
    private int wallRemaining = 0;
    private int[][] allDiscards = new int[4][0];
    private int lastDiscard = -1;
    private int lastDiscardBy = -1;
    private byte[] availableActions = new byte[0];
    private String[] playerNames = {"", "", "", ""};
    private int winnerIndex = -1;
    private byte winType = 0;

    // ── 交互 ──
    private int selectedTileIdx = -1;
    private Button btnLeave;
    private final List<Button> actionButtons = new ArrayList<>();
    private long actionWindowStartClient = 0; // 客户端动作窗口开始时间

    private final Runnable onSuccess;

    public MahjongMinigameScreen(BlockPos pos, Runnable onSuccess) {
        super(Component.literal("麻将"));
        this.onSuccess = onSuccess;
    }

    // ══════════════════════════════════════════════
    // 初始化
    // ══════════════════════════════════════════════

    @Override
    protected void init() {
        super.init();
        ClientPlayNetworking.send(new MahjongJoinC2SPacket(MahjongJoinC2SPacket.ACTION_JOIN));

        btnLeave = Button.builder(Component.literal("离开"), b -> {
            ClientPlayNetworking.send(new MahjongJoinC2SPacket(MahjongJoinC2SPacket.ACTION_LEAVE));
            onClose();
        }).pos(5, 5).size(50, 20).build();
        addRenderableWidget(btnLeave);
    }

    // ══════════════════════════════════════════════
    // 接收服务端状态
    // ══════════════════════════════════════════════

    public void onStateReceived(MahjongStateS2CPacket pkt) {
        phase = pkt.phase();
        currentTurn = pkt.currentTurn();
        dealerIndex = pkt.dealerIndex();
        playerIndex = pkt.playerIndex();

        myHand = new int[pkt.myHand().length];
        for (int i = 0; i < myHand.length; i++) myHand[i] = pkt.myHand()[i] & 0xFF;

        oppCounts = new int[3];
        for (int i = 0; i < 3; i++) oppCounts[i] = pkt.oppCounts()[i] & 0xFF;

        myMelds = MahjongStateS2CPacket.decodeMelds(pkt.myMeldsData());
        oppMelds = MahjongStateS2CPacket.decodeOppMelds(pkt.oppMeldsData());
        wallRemaining = pkt.wallRemaining() & 0xFF;

        allDiscards = new int[4][];
        for (int i = 0; i < 4; i++) {
            byte[] d = pkt.allDiscards()[i];
            allDiscards[i] = new int[d.length];
            for (int j = 0; j < d.length; j++) allDiscards[i][j] = d[j] & 0xFF;
        }

        lastDiscard = pkt.lastDiscard() & 0xFF;
        if (pkt.lastDiscard() == -1) lastDiscard = -1;
        lastDiscardBy = pkt.lastDiscardBy();
        if (pkt.lastDiscardBy() == -1) lastDiscardBy = -1;

        availableActions = pkt.availableActions();
        playerNames = pkt.names();
        winnerIndex = pkt.winnerIndex();
        winType = pkt.winType();

        // 记录动作窗口开始时间（用于倒计时显示）
        if (phase == 3 && availableActions != null && availableActions.length > 0) {
            if (actionWindowStartClient == 0) actionWindowStartClient = System.currentTimeMillis();
        } else {
            actionWindowStartClient = 0;
        }

        updateButtons();
    }

    private void updateButtons() {
        for (Button b : actionButtons) removeWidget(b);
        actionButtons.clear();

        if (phase == 3 && availableActions != null && availableActions.length > 0) {
            // 解析可用动作（去重）
            Set<Byte> actionTypes = new LinkedHashSet<>();
            Map<Byte, Byte> actionTileMap = new LinkedHashMap<>();
            for (int i = 0; i < availableActions.length - 1; i += 2) {
                byte act = availableActions[i];
                actionTypes.add(act);
                if (!actionTileMap.containsKey(act)) actionTileMap.put(act, availableActions[i + 1]);
            }

            int totalBtns = actionTypes.size();
            int btnW = 50, btnH = 22, gap = 6;
            int totalW = totalBtns * btnW + (totalBtns - 1) * gap;
            int bx = (width - totalW) / 2;
            int by = height / 2 + 40;

            for (byte act : actionTypes) {
                String label = switch (act) {
                    case 1 -> "吃";
                    case 2 -> "碰";
                    case 3 -> "杠";
                    case 4 -> "胡";
                    case 5 -> "过";
                    case 6 -> "自摸";
                    default -> null;
                };
                if (label == null) continue;
                byte finalAct = act;
                byte finalTileType = actionTileMap.getOrDefault(act, (byte) 0);
                Button btn = Button.builder(Component.literal(label), b -> {
                    ClientPlayNetworking.send(new MahjongActionC2SPacket(finalAct, finalTileType));
                }).pos(bx, by).size(btnW, btnH).build();
                actionButtons.add(btn);
                addRenderableWidget(btn);
                bx += btnW + gap;
            }
        }
    }

    // ══════════════════════════════════════════════
    // 渲染
    // ══════════════════════════════════════════════

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);

        // 牌桌
        g.fill(0, 0, width, height, C_TABLE);
        int innerM = 12;
        g.fill(innerM, innerM, width - innerM, height - innerM, C_TABLE_INNER);

        if (phase == 0) {
            renderWaiting(g);
        } else {
            renderGame(g);
        }
        super.render(g, mx, my, pt);
    }

    private void renderWaiting(GuiGraphics g) {
        int cx = width / 2, cy = height / 2;
        // 装饰边框
        g.fill(0, 0, width, 4, C_TABLE_BORDER);
        g.fill(0, height - 4, width, height, C_TABLE_BORDER);
        g.fill(0, 0, 4, height, C_TABLE_BORDER);
        g.fill(width - 4, 0, width, height, C_TABLE_BORDER);

        // 中央毛毡
        int feltM = 50;
        g.fill(feltM, feltM, width - feltM, height - feltM, C_TABLE_FELT);

        // 标题背景
        String title = "麻将 - 等待玩家 (" + countNames() + "/4)";
        int tw = font.width(title);
        g.fill(cx - tw / 2 - 12, cy - 54, cx + tw / 2 + 12, cy - 34, C_NAME_BG);
        g.drawString(font, title, (width - tw) / 2, cy - 50, 0xFFFFFFFF, true);

        // 座位信息
        int y = cy - 24;
        for (int i = 0; i < 4; i++) {
            String name = playerNames[i] != null && !playerNames[i].isEmpty() ? playerNames[i] : "...";
            boolean joined = !name.equals("...");
            String seatLabel = "\u2588\u2588 ";
            String line = seatLabel + (joined ? name : "等待中...");
            int color = joined ? 0xFFFFFFFF : 0xFF888888;
            int lw = font.width(line);
            // 座位背景
            g.fill(cx - lw / 2 - 8, y + i * 20 - 2, cx + lw / 2 + 8, y + i * 20 + 14, joined ? 0x3000FF00 : 0x30FFFFFF);
            g.drawString(font, line, (width - lw) / 2, y + i * 20, color, false);
        }

        // 提示
        String hint = "凑4人后自动开局";
        int hw = font.width(hint);
        g.drawString(font, hint, (width - hw) / 2, y + 90, 0x80FFFFFF, false);
    }

    private int countNames() {
        int c = 0;
        for (String n : playerNames) if (n != null && !n.isEmpty()) c++;
        return c;
    }

    private void renderGame(GuiGraphics g) {
        int cx = width / 2, cy = height / 2;

        // ── 装饰边框 ──
        g.fill(0, 0, width, 4, C_TABLE_BORDER);
        g.fill(0, height - 4, width, height, C_TABLE_BORDER);
        g.fill(0, 0, 4, height, C_TABLE_BORDER);
        g.fill(width - 4, 0, width, height, C_TABLE_BORDER);
        // 内边框线
        g.fill(6, 6, width - 6, 7, 0x40FFFFFF);
        g.fill(6, height - 7, width - 6, height - 6, 0x40FFFFFF);
        g.fill(6, 6, 7, height - 6, 0x40FFFFFF);
        g.fill(width - 7, 6, width - 6, height - 6, 0x40FFFFFF);

        // ── 中央毛毡区 ──
        int feltM = 50;
        g.fill(feltM, feltM, width - feltM, height - feltM, C_TABLE_FELT);
        g.fill(feltM, feltM, width - feltM, feltM + 1, 0x30000000);
        g.fill(feltM, height - feltM - 1, width - feltM, height - feltM, 0x30000000);

        // ── 我的弃牌区（底部偏上）──
        renderDiscardArea(g, playerIndex, cx - 55, cy + 50, "我");

        // ── 我的手牌（底部）──
        renderMyHand(g);

        // ── 我的副露（手牌右侧）──
        int meldStartX = (width + myHand.length * (TW + TS)) / 2 + 8;
        int meldStartY = height - TH - 28;
        for (int[] meld : myMelds) {
            for (int tile : meld) {
                drawTile(g, meldStartX, meldStartY, tile, SW, SH, false);
                meldStartX += SW + 1;
            }
            meldStartX += 4;
        }

        // ── 对家（顶部）──
        renderTopOpponent(g, 0);

        // ── 右侧对手 ──
        renderRightOpponent(g, 1);

        // ── 左侧对手 ──
        renderLeftOpponent(g, 2);

        // ── 中央弃牌区（使用相对座位索引）──
        int oppSeat = (playerIndex + 2) % 4;
        int rightSeat = (playerIndex + 1) % 4;
        int leftSeat = (playerIndex + 3) % 4;
        renderDiscardArea(g, oppSeat, cx - 55, cy - 55, getPlayerNameShort(oppSeat));
        renderDiscardArea(g, rightSeat, cx + 20, cy - 10, getPlayerNameShort(rightSeat));
        renderDiscardArea(g, leftSeat, cx - 85, cy - 10, getPlayerNameShort(leftSeat));

        // ── 最后出牌高亮 ──
        if (lastDiscard >= 0 && lastDiscardBy >= 0) {
            int lx = cx - SW / 2, ly = cy - SH / 2;
            drawTile(g, lx, ly, lastDiscard, SW, SH, true);
            // 显示出牌者名称
            String byName = getPlayerName(lastDiscardBy);
            g.drawString(font, byName, lx - font.width(byName) / 2 + SW / 2, ly - 10, 0xAAFFFFFF, false);
        }

        // ── 信息栏（右下角）──
        int infoX = width - 100, infoY = height - 40;
        g.fill(infoX - 6, infoY - 4, width - 6, infoY + 28, C_NAME_BG);
        g.drawString(font, "剩余: " + wallRemaining + "张", infoX, infoY, 0xFFE0E0E0, false);
        g.drawString(font, "[庄] " + getPlayerName(dealerIndex), infoX, infoY + 12, C_GOLD, false);

        // ── 回合提示（顶部居中）──
        if (phase == 2 || phase == 3) {
            String turnStr;
            int turnColor;
            if (currentTurn == playerIndex) {
                turnStr = phase == 3 ? "[ 选择动作 ]" : "[ 你的回合 - 请出牌 ]";
                turnColor = C_TURN_GLOW;
            } else {
                turnStr = getPlayerName(currentTurn) + " 思考中...";
                turnColor = 0xCCFFFFFF;
            }
            int ttw = font.width(turnStr);
            int ttx = (width - ttw) / 2;
            g.fill(ttx - 8, 36, ttx + ttw + 8, 52, C_NAME_BG);
            g.drawString(font, turnStr, ttx, 38, turnColor, true);
        }

        // ── 动作窗口倒计时 ──
        if (phase == 3 && availableActions != null && availableActions.length > 0 && availableActions[0] != 0) {
            // 仅对有动作选项的玩家显示倒计时
            boolean hasRealAction = false;
            for (byte a : availableActions) {
                if (a != 0 && a != 5) { hasRealAction = true; break; } // 0=NONE, 5=PASS
            }
            if (hasRealAction) {
                long elapsed = System.currentTimeMillis() - actionWindowStartClient;
                int remaining = Math.max(0, 15 - (int)(elapsed / 1000));
                String timer = remaining + "s";
                g.drawString(font, timer, width / 2 - font.width(timer) / 2, 56, remaining <= 5 ? 0xFFFF5252 : 0xFFFFFFFF, false);
            }
        }

        // ── 结束 ──
        if (phase == 4) {
            String result;
            if (winnerIndex >= 0) {
                result = winType == 1 ? getPlayerName(winnerIndex) + " 自摸!" : getPlayerName(winnerIndex) + " 胡了!";
            } else {
                result = "流局!";
            }
            int rw = font.width(result);
            g.fill(cx - rw / 2 - 16, cy - 24, cx + rw / 2 + 16, cy + 14, 0xDD000000);
            g.fill(cx - rw / 2 - 16, cy - 24, cx + rw / 2 + 16, cy - 23, C_GOLD);
            g.fill(cx - rw / 2 - 16, cy + 13, cx + rw / 2 + 16, cy + 14, C_GOLD);
            g.drawString(font, result, (width - rw) / 2, cy - 14, C_GOLD, true);
        }
    }

    // ══════════════════════════════════════════════
    // 牌面绘制（纯代码，无贴图）
    // ══════════════════════════════════════════════

    private void drawTile(GuiGraphics g, int x, int y, int tileId, int w, int h, boolean highlight) {
        // 阴影
        g.fill(x + 1, y + h, x + w + 1, y + h + 2, C_TILE_SHADOW);
        g.fill(x + w, y + 1, x + w + 2, y + h, C_TILE_SHADOW);
        // 背景
        g.fill(x, y, x + w, y + h, C_TILE_BG);
        // 边框
        g.fill(x, y, x + w, y + 1, C_TILE_BORDER);          // 上
        g.fill(x, y + h - 1, x + w, y + h, C_TILE_BORDER);  // 下
        g.fill(x, y, x + 1, y + h, C_TILE_BORDER);           // 左
        g.fill(x + w - 1, y, x + w, y + h, C_TILE_BORDER);   // 右
        // 内发光
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x20FFFFFF);

        if (highlight) {
            g.fill(x - 1, y - 1, x + w + 1, y + h + 1, C_LAST_DISCARD);
            g.fill(x, y, x + w, y + h, C_TILE_BG);
            g.fill(x, y, x + w, y + 1, C_TILE_BORDER);
            g.fill(x, y + h - 1, x + w, y + h, C_TILE_BORDER);
            g.fill(x, y, x + 1, y + h, C_TILE_BORDER);
            g.fill(x + w - 1, y, x + w, y + h, C_TILE_BORDER);
            g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x20FFFFFF);
        }

        int type = MahjongSession.getType(tileId);
        int rank = MahjongSession.suitRank(type);

        if (MahjongSession.isWan(type)) {
            drawWanTile(g, x, y, w, h, rank);
        } else if (MahjongSession.isTiao(type)) {
            drawTiaoTile(g, x, y, w, h, rank);
        } else if (MahjongSession.isBing(type)) {
            drawBingTile(g, x, y, w, h, rank);
        } else if (MahjongSession.isZi(type)) {
            int ziIdx = type - 27;
            drawTileText(g, x, y, w, h, ZI_NAMES[ziIdx], ZI_COLORS[ziIdx]);
        }
    }

    // ── 万子：中文数字 + "万" 文字 ──
    private void drawWanTile(GuiGraphics g, int x, int y, int w, int h, int rank) {
        // 上半：中文数字（红色大号）
        String num = CN_NUM[rank];
        int nw = font.width(num);
        g.drawString(font, num, x + (w - nw) / 2 + 1, y + 4 + 1, 0x30000000, false);
        g.drawString(font, num, x + (w - nw) / 2, y + 4, C_WAN, false);
        // 下半："万"（红色大号）
        String wan = "万";
        int ww = font.width(wan);
        g.drawString(font, wan, x + (w - ww) / 2 + 1, y + h / 2 + 3 + 1, 0x30000000, false);
        g.drawString(font, wan, x + (w - ww) / 2, y + h / 2 + 3, C_WAN, false);
    }

    // ── 条子：绿色竹节图案 + 左上角数字标注 ──
    private void drawTiaoTile(GuiGraphics g, int x, int y, int w, int h, int rank) {
        // 左上角小数字标注（方便识别）
        String label = CN_NUM[rank];
        g.drawString(font, label, x + 2, y + 2, C_TIAO, false);

        // 竹节图形
        int cx = x + w / 2;
        int stickW = 2, stickH = (rank <= 5) ? 22 : 10;
        if (rank <= 5) {
            // 单行竹节
            int[] xOffs = BAM_X[rank];
            int n = xOffs.length;
            int topY = y + (h - stickH) / 2 + 2;
            for (int i = 0; i < n; i++) {
                int sx = cx + xOffs[i] - stickW / 2;
                // 竹节主体（深绿）
                g.fill(sx, topY, sx + stickW, topY + stickH, C_TIAO);
                // 竹节横纹（浅绿，每4px一道）
                for (int j = 3; j < stickH; j += 5) {
                    g.fill(sx - 1, topY + j, sx + stickW + 1, topY + j + 1, 0xFF4CAF50);
                }
            }
        } else {
            // 双行竹节
            int[] xOffs = BAM_X[rank];
            int row1 = (rank == 6) ? 3 : (rank == 7) ? 4 : 4;
            int row2 = rank - row1;
            int topY1 = y + h / 2 - stickH - 1;
            int topY2 = y + h / 2 + 1;
            for (int i = 0; i < row1; i++) {
                int sx = cx + xOffs[i] - stickW / 2;
                g.fill(sx, topY1, sx + stickW, topY1 + stickH, C_TIAO);
                for (int j = 3; j < stickH; j += 5) g.fill(sx - 1, topY1 + j, sx + stickW + 1, topY1 + j + 1, 0xFF4CAF50);
            }
            int off = row1;
            for (int i = 0; i < row2; i++) {
                int sx = cx + xOffs[off + i] - stickW / 2;
                g.fill(sx, topY2, sx + stickW, topY2 + stickH, C_TIAO);
                for (int j = 3; j < stickH; j += 5) g.fill(sx - 1, topY2 + j, sx + stickW + 1, topY2 + j + 1, 0xFF4CAF50);
            }
        }
    }

    // ── 饼子：圆形图案 + 左上角数字标注 ──
    private void drawBingTile(GuiGraphics g, int x, int y, int w, int h, int rank) {
        // 左上角小数字标注
        String label = CN_NUM[rank];
        g.drawString(font, label, x + 2, y + 2, C_BING, false);

        // 圆点图形
        int cx = x + w / 2;
        int cy = y + h / 2 + 2;
        int[][] dots = DOT_P[rank];
        for (int[] dot : dots) {
            int dx = cx + dot[0];
            int dy = cy + dot[1];
            int r = 2; // 圆半径
            // 绿色外圈
            g.fill(dx - r, dy - r, dx + r + 1, dy + r + 1, C_TIAO);
            // 红色内芯
            g.fill(dx - 1, dy - 1, dx + 2, dy + 2, C_WAN);
        }
    }

    private void drawTileText(GuiGraphics g, int x, int y, int w, int h, String text, int color) {
        int tw = font.width(text);
        int tx = x + (w - tw) / 2;
        int ty = y + (h - 9) / 2;
        g.drawString(font, text, tx + 1, ty + 1, 0x40000000, false);
        g.drawString(font, text, tx, ty, color, false);
    }

    private void drawTileBack(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, C_TILE_BACK);
        int m = 2;
        g.fill(x + m, y + m, x + w - m, y + h - m, C_TILE_BACK2);
    }

    // ── 小牌渲染（副露/弃牌用）──
    private void drawTileSmall(GuiGraphics g, int x, int y, int tileId, int w, int h) {
        g.fill(x, y, x + w, y + h, C_TILE_BG);
        g.fill(x, y, x + w, y + 1, 0xFFBCAAA4);
        g.fill(x, y + h - 1, x + w, y + h, 0xFFBCAAA4);
        g.fill(x, y, x + 1, y + h, 0xFFBCAAA4);
        g.fill(x + w - 1, y, x + w, y + h, 0xFFBCAAA4);

        int type = MahjongSession.getType(tileId);
        int rank = MahjongSession.suitRank(type);
        if (MahjongSession.isWan(type)) {
            // 万：红色文字
            String text = CN_NUM[rank] + "万";
            int tw = font.width(text);
            g.drawString(font, text, x + (w - tw) / 2, y + (h - 9) / 2, C_WAN, false);
        } else if (MahjongSession.isTiao(type)) {
            // 条：绿色竖条 + 数字
            int cx = x + w / 2;
            int stickW = 1, stickH = Math.min(h - 4, 14);
            int topY = y + (h - stickH) / 2;
            int[] xOffs = BAM_X[rank];
            int row1 = rank <= 5 ? rank : (rank == 6 ? 3 : 4);
            if (rank <= 5) {
                for (int i = 0; i < rank; i++) {
                    int sx = cx + xOffs[i] - stickW;
                    g.fill(sx, topY, sx + stickW * 2, topY + stickH, C_TIAO);
                }
            } else {
                int row2 = rank - row1;
                int sH = stickH / 2 - 1;
                for (int i = 0; i < row1; i++) {
                    int sx = cx + xOffs[i] - stickW;
                    g.fill(sx, topY, sx + stickW * 2, topY + sH, C_TIAO);
                }
                for (int i = 0; i < row2; i++) {
                    int sx = cx + xOffs[row1 + i] - stickW;
                    g.fill(sx, topY + sH + 2, sx + stickW * 2, topY + sH + 2 + sH, C_TIAO);
                }
            }
        } else if (MahjongSession.isBing(type)) {
            // 饼：圆点 + 数字
            int cx = x + w / 2;
            int cy = y + h / 2 + 1;
            int[][] dots = DOT_P[rank];
            for (int[] dot : dots) {
                int dx = cx + dot[0] / 2;
                int dy = cy + dot[1] / 2;
                g.fill(dx - 1, dy - 1, dx + 2, dy + 2, C_TIAO);
                g.fill(dx, dy, dx + 1, dy + 1, C_WAN);
            }
        } else if (MahjongSession.isZi(type)) {
            int ziIdx = type - 27;
            String text = ZI_NAMES[ziIdx];
            int tw = font.width(text);
            g.drawString(font, text, x + (w - tw) / 2, y + (h - 9) / 2, ZI_COLORS[ziIdx], false);
        }
    }

    // ══════════════════════════════════════════════
    // 我的手牌
    // ══════════════════════════════════════════════

    private void renderMyHand(GuiGraphics g) {
        int count = myHand.length;
        int totalW = count * (TW + TS) - TS;
        int startX = (width - totalW) / 2;
        int baseY = height - TH - 28;

        for (int i = 0; i < count; i++) {
            int x = startX + i * (TW + TS);
            int y = baseY;
            if (i == selectedTileIdx) y -= 10;

            // 选中牌金色光晕
            if (i == selectedTileIdx) {
                g.fill(x - 2, y - 2, x + TW + 2, y + TH + 2, C_GOLD);
            }
            drawTile(g, x, y, myHand[i], TW, TH, false);
        }

        // 出牌提示
        if (phase == 2 && currentTurn == playerIndex) {
            String hint = selectedTileIdx >= 0 ? "双击出牌" : "点击选牌";
            int hw = font.width(hint);
            g.fill((width - hw) / 2 - 6, baseY - 16, (width + hw) / 2 + 6, baseY - 4, C_NAME_BG);
            g.drawString(font, hint, (width - hw) / 2, baseY - 14, C_GOLD, false);
        }
    }

    // ══════════════════════════════════════════════
    // 对手渲染
    // ══════════════════════════════════════════════

    private void renderTopOpponent(GuiGraphics g, int oppIdx) {
        int count = oppCounts[oppIdx];
        int seatIdx = (playerIndex + 1 + oppIdx) % 4;
        String name = getPlayerName(seatIdx);
        boolean isTurn = currentTurn == seatIdx;

        int totalW = Math.min(count, 13) * (SW - 2);
        int startX = (width - totalW) / 2;
        int y = 28;

        // 名称背景面板
        String label = name + " (" + count + "张)" + (isTurn ? " *" : "");
        int labelW = font.width(label) + (seatIdx == dealerIndex ? 26 : 0);
        int lx = (width - labelW) / 2;
        g.fill(lx - 4, y - 16, lx + labelW + 4, y - 2, C_NAME_BG);
        int labelColor = isTurn ? C_TURN_GLOW : 0xFFFFFFFF;
        g.drawString(font, label, lx, y - 14, labelColor, false);
        if (seatIdx == dealerIndex) g.drawString(font, "[庄]", lx - 22, y - 14, C_GOLD, false);

        // 牌背
        for (int i = 0; i < Math.min(count, 13); i++) {
            drawTileBack(g, startX + i * (SW - 2), y, SW - 2, SH - 4);
        }

        // 副露
        int mx = startX;
        int my2 = y + SH - 2;
        if (oppMelds[oppIdx] != null) {
            for (int[] meld : oppMelds[oppIdx]) {
                for (int tile : meld) {
                    drawTileSmall(g, mx, my2, tile, SW - 4, SH - 6);
                    mx += SW - 3;
                }
                mx += 3;
            }
        }
    }

    private void renderRightOpponent(GuiGraphics g, int oppIdx) {
        int count = oppCounts[oppIdx];
        int seatIdx = (playerIndex + 1 + oppIdx) % 4;
        String name = getPlayerName(seatIdx);
        boolean isTurn = currentTurn == seatIdx;

        int startX = width - SW - 20;
        int totalH = Math.min(count, 13) * (SH - 8);
        int startY = (height - totalH) / 2;

        // 名称背景面板
        String label = name + "(" + count + ")" + (isTurn ? "*" : "");
        int labelW = font.width(label) + (seatIdx == dealerIndex ? 26 : 0);
        g.fill(startX - 34, startY - 16, startX - 34 + labelW + 8, startY - 2, C_NAME_BG);
        int labelColor = isTurn ? C_TURN_GLOW : 0xFFFFFFFF;
        g.drawString(font, label, startX - 30, startY - 14, labelColor, false);
        if (seatIdx == dealerIndex) g.drawString(font, "[庄]", startX - 54, startY - 14, C_GOLD, false);

        for (int i = 0; i < Math.min(count, 13); i++) {
            drawTileBack(g, startX, startY + i * (SH - 8), SW - 2, SH - 6);
        }

        // 副露（向下排列）
        int mx = startX - SW - 6;
        int my2 = startY;
        if (oppMelds[oppIdx] != null) {
            for (int[] meld : oppMelds[oppIdx]) {
                for (int tile : meld) {
                    drawTileSmall(g, mx, my2, tile, SW - 4, SH - 6);
                    my2 += SH - 5;
                }
                my2 += 3;
            }
        }
    }

    private void renderLeftOpponent(GuiGraphics g, int oppIdx) {
        int count = oppCounts[oppIdx];
        int seatIdx = (playerIndex + 1 + oppIdx) % 4;
        String name = getPlayerName(seatIdx);
        boolean isTurn = currentTurn == seatIdx;

        int startX = 20;
        int totalH = Math.min(count, 13) * (SH - 8);
        int startY = (height - totalH) / 2;

        // 名称背景面板
        String label = name + "(" + count + ")" + (isTurn ? "*" : "");
        int labelW = font.width(label) + (seatIdx == dealerIndex ? 26 : 0);
        g.fill(startX - 4, startY - 16, startX + labelW + 4, startY - 2, C_NAME_BG);
        int labelColor = isTurn ? C_TURN_GLOW : 0xFFFFFFFF;
        g.drawString(font, label, startX, startY - 14, labelColor, false);
        if (seatIdx == dealerIndex) g.drawString(font, "[庄]", startX + font.width(label) + 4, startY - 14, C_GOLD, false);

        for (int i = 0; i < Math.min(count, 13); i++) {
            drawTileBack(g, startX, startY + i * (SH - 8), SW - 2, SH - 6);
        }

        // 副露
        int mx = startX + SW + 4;
        int my2 = startY;
        if (oppMelds[oppIdx] != null) {
            for (int[] meld : oppMelds[oppIdx]) {
                for (int tile : meld) {
                    drawTileSmall(g, mx, my2, tile, SW - 4, SH - 6);
                    my2 += SH - 5;
                }
                my2 += 3;
            }
        }
    }

    // ══════════════════════════════════════════════
    // 弃牌区
    // ══════════════════════════════════════════════

    private void renderDiscardArea(GuiGraphics g, int playerSeat, int startX, int startY, String label) {
        int[] disc = allDiscards[playerSeat];
        if (disc == null || disc.length == 0) return;

        // 半透明底色
        int cols = 7;
        int rows = (disc.length + cols - 1) / cols;
        int areaW = cols * (DW + 1);
        int areaH = rows * (DH + 1) + 12;
        g.fill(startX - 2, startY - 12, startX + areaW, startY + areaH, C_DISCARD_BG);

        // 标签
        g.drawString(font, label, startX, startY - 11, 0xAAFFFFFF, false);

        for (int i = 0; i < disc.length; i++) {
            int col = i % cols;
            int row = i / cols;
            int dx = startX + col * (DW + 1);
            int dy = startY + row * (DH + 1);
            drawTileSmall(g, dx, dy, disc[i], DW, DH);
        }
    }

    // ══════════════════════════════════════════════
    // 鼠标交互
    // ══════════════════════════════════════════════

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (phase == 2 && currentTurn == playerIndex) {
            int count = myHand.length;
            int totalW = count * (TW + TS) - TS;
            int startX = (width - totalW) / 2;
            int baseY = height - TH - 28;

            for (int i = 0; i < count; i++) {
                int x = startX + i * (TW + TS);
                int y = baseY;
                if (i == selectedTileIdx) y -= 10;
                if (mouseX >= x && mouseX <= x + TW && mouseY >= y && mouseY <= y + TH) {
                    if (selectedTileIdx == i) {
                        // 双击出牌
                        ClientPlayNetworking.send(new MahjongDiscardC2SPacket((byte) myHand[i]));
                        selectedTileIdx = -1;
                    } else {
                        selectedTileIdx = i;
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ══════════════════════════════════════════════
    // 辅助
    // ══════════════════════════════════════════════

    private String getPlayerName(int seatIdx) {
        if (seatIdx < 0 || seatIdx >= 4) return "???";
        return playerNames[seatIdx] != null && !playerNames[seatIdx].isEmpty()
                ? playerNames[seatIdx] : "玩家" + (seatIdx + 1);
    }

    private String getPlayerNameShort(int seatIdx) {
        String name = getPlayerName(seatIdx);
        return name.length() > 6 ? name.substring(0, 6) : name;
    }

    @Override
    public void onClose() { super.onClose(); }

    @Override
    public boolean isPauseScreen() { return false; }
}
