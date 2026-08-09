package io.wifi.starrailexpress.content.minigame.mahjong;

import io.wifi.starrailexpress.network.packet.MahjongStateS2CPacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

/**
 * 麻将游戏会话（服务端权威）
 * 4人游戏，136张牌（无花牌），简化胡牌（4面子+1雀头或七对）
 */
public class MahjongSession {

    // ── 常量 ──
    public static final int PLAYER_COUNT = 4;
    public static final int TOTAL_TILES = 136;
    public static final int TILE_TYPES = 34;
    public static final int TILES_PER_PLAYER = 13;

    // 字牌名称: type 27-33
    public static final String[] ZI_NAMES = {"东", "南", "西", "北", "中", "发", "白"};

    public enum Phase { WAITING, DEALING, PLAYING, ACTION_WINDOW, ENDED }

    // 动作类型
    public static final byte ACTION_NONE = 0;
    public static final byte ACTION_CHI = 1;
    public static final byte ACTION_PONG = 2;
    public static final byte ACTION_KONG = 3;
    public static final byte ACTION_HU = 4;
    public static final byte ACTION_PASS = 5;
    public static final byte ACTION_DRAW_WIN = 6; // 自摸

    // ── 玩家 ──
    private final ServerPlayer[] players = new ServerPlayer[4];

    // ── 游戏状态 ──
    private Phase phase = Phase.DEALING;
    private int[] wall;
    private int wallPos = 0;
    private final int[][] hands = new int[4][];
    private final List<int[]>[] melds = new List[4];
    private final List<Integer>[] discards = new List[4];
    private int dealerIndex;
    private int currentTurn;
    private int lastDiscard = -1;
    private int lastDiscardBy = -1;
    private int winnerIndex = -1;
    private byte winType = 0; // 0=none, 1=自摸, 2=点炮

    // ── 动作窗口 ──
    private byte[][] availableActions; // [playerIdx][actionType, tileType, ...]
    private byte[] chosenActions = new byte[4]; // 每个玩家的选择
    private boolean[] hasResponded = new boolean[4];
    private long actionWindowStart = 0;
    private static final long ACTION_TIMEOUT_MS = 15000;

    // ══════════════════════════════════════════════
    // 构造 & 初始化
    // ══════════════════════════════════════════════

    public MahjongSession(ServerPlayer p0, ServerPlayer p1, ServerPlayer p2, ServerPlayer p3) {
        players[0] = p0; players[1] = p1; players[2] = p2; players[3] = p3;
        for (int i = 0; i < 4; i++) {
            melds[i] = new ArrayList<>();
            discards[i] = new ArrayList<>();
        }
        dealerIndex = new Random().nextInt(4);
        currentTurn = dealerIndex;
        initWall();
        dealCards();
        phase = Phase.PLAYING;
        // 庄家先出牌（已有14张）
    }

    private void initWall() {
        wall = new int[TOTAL_TILES];
        for (int i = 0; i < TOTAL_TILES; i++) wall[i] = i;
        // Fisher-Yates shuffle
        Random rng = new Random();
        for (int i = TOTAL_TILES - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int tmp = wall[i]; wall[i] = wall[j]; wall[j] = tmp;
        }
    }

    private void dealCards() {
        for (int p = 0; p < 4; p++) {
            hands[p] = new int[TILES_PER_PLAYER];
            for (int i = 0; i < TILES_PER_PLAYER; i++) {
                hands[p][i] = wall[wallPos++];
            }
            sortHand(hands[p]);
        }
        // 庄家多摸一张
        hands[dealerIndex] = addTile(hands[dealerIndex], wall[wallPos++]);
    }

    // ══════════════════════════════════════════════
    // 麻将牌工具方法
    // ══════════════════════════════════════════════

    public static int getType(int tileId) { return tileId / 4; }
    public static int getCopy(int tileId) { return tileId % 4; }
    public static boolean isWan(int type) { return type >= 0 && type <= 8; }
    public static boolean isTiao(int type) { return type >= 9 && type <= 17; }
    public static boolean isBing(int type) { return type >= 18 && type <= 26; }
    public static boolean isZi(int type) { return type >= 27 && type <= 33; }
    public static int suitRank(int type) {
        if (isWan(type)) return type + 1;
        if (isTiao(type)) return type - 9 + 1;
        if (isBing(type)) return type - 18 + 1;
        return -1;
    }
    public static int suitOf(int type) {
        if (isWan(type)) return 0;
        if (isTiao(type)) return 1;
        if (isBing(type)) return 2;
        return 3; // 字牌
    }

    public static void sortHand(int[] hand) {
        Arrays.sort(hand); // tileId排序即type排序（type=tid/4）
    }

    private static int[] addTile(int[] hand, int tile) {
        int[] nh = Arrays.copyOf(hand, hand.length + 1);
        nh[hand.length] = tile;
        sortHand(nh);
        return nh;
    }

    private static int[] removeTile(int[] hand, int tile) {
        int[] nh = new int[hand.length - 1];
        boolean removed = false;
        int j = 0;
        for (int t : hand) {
            if (!removed && t == tile) { removed = true; continue; }
            nh[j++] = t;
        }
        return nh;
    }

    private static int countType(int[] hand, int type) {
        int c = 0;
        for (int t : hand) if (getType(t) == type) c++;
        return c;
    }

    private static int findTile(int[] hand, int type) {
        for (int t : hand) if (getType(t) == type) return t;
        return -1;
    }

    // ══════════════════════════════════════════════
    // 胡牌检测
    // ══════════════════════════════════════════════

    public static boolean checkHu(int[] hand) {
        if (hand.length % 3 != 2) return false;
        int[] freq = new int[TILE_TYPES];
        for (int t : hand) freq[getType(t)]++;
        // 尝试每种雀头
        for (int i = 0; i < TILE_TYPES; i++) {
            if (freq[i] >= 2) {
                freq[i] -= 2;
                if (checkMelds(freq)) { freq[i] += 2; return true; }
                freq[i] += 2;
            }
        }
        // 七对
        if (hand.length == 14) {
            boolean sevenPairs = true;
            for (int f : freq) {
                if (f != 0 && f != 2 && f != 4) { sevenPairs = false; break; }
                if (f == 1 || f == 3) { sevenPairs = false; break; }
            }
            if (sevenPairs) {
                int pairCount = 0;
                for (int f : freq) pairCount += f / 2;
                if (pairCount == 7) return true;
            }
        }
        return false;
    }

    private static boolean checkMelds(int[] freq) {
        int i = 0;
        while (i < TILE_TYPES && freq[i] == 0) i++;
        if (i == TILE_TYPES) return true;
        // 刻子
        if (freq[i] >= 3) {
            freq[i] -= 3;
            if (checkMelds(freq)) { freq[i] += 3; return true; }
            freq[i] += 3;
        }
        // 顺子（非字牌）
        if (i < 27 && (i % 9) <= 6 && freq[i + 1] > 0 && freq[i + 2] > 0) {
            freq[i]--; freq[i + 1]--; freq[i + 2]--;
            if (checkMelds(freq)) { freq[i]++; freq[i + 1]++; freq[i + 2]++; return true; }
            freq[i]++; freq[i + 1]++; freq[i + 2]++;
        }
        return false;
    }

    // 检测手牌+额外一张是否能胡
    private static boolean checkHuWithExtra(int[] hand, int extraTile) {
        int[] temp = addTile(hand, extraTile);
        return checkHu(temp);
    }

    // ══════════════════════════════════════════════
    // 吃/碰/杠检测
    // ══════════════════════════════════════════════

    /** 获取上家（可以被我吃的玩家） */
    private static int prevPlayer(int pos) { return (pos + 3) % 4; }
    /** 获取下家（可以吃我的玩家） */
    private static int nextPlayer(int pos) { return (pos + 1) % 4; }

    /** 检测玩家是否能吃弃牌，返回可行的[牌1type,牌2type]组合列表 */
    private List<int[]> getChiOptions(int playerIdx, int discardType) {
        List<int[]> options = new ArrayList<>();
        if (isZi(discardType)) return options;
        int suitStart = (discardType / 9) * 9;
        int rank = discardType - suitStart;
        int[] hand = hands[playerIdx];
        // 三种顺子: (r-2,r-1,r), (r-1,r,r+1), (r,r+1,r+2)
        if (rank >= 2 && countType(hand, suitStart + rank - 2) > 0 && countType(hand, suitStart + rank - 1) > 0)
            options.add(new int[]{suitStart + rank - 2, suitStart + rank - 1});
        if (rank >= 1 && rank <= 7 && countType(hand, suitStart + rank - 1) > 0 && countType(hand, suitStart + rank + 1) > 0)
            options.add(new int[]{suitStart + rank - 1, suitStart + rank + 1});
        if (rank <= 6 && countType(hand, suitStart + rank + 1) > 0 && countType(hand, suitStart + rank + 2) > 0)
            options.add(new int[]{suitStart + rank + 1, suitStart + rank + 2});
        return options;
    }

    private boolean canPong(int playerIdx, int discardType) {
        return countType(hands[playerIdx], discardType) >= 2;
    }

    private boolean canKong(int playerIdx, int discardType) {
        return countType(hands[playerIdx], discardType) >= 3;
    }

    /** 检测暗杠：手中有4张相同的牌 */
    private List<Integer> getConcealedKongOptions(int playerIdx) {
        List<Integer> options = new ArrayList<>();
        int[] freq = new int[TILE_TYPES];
        for (int t : hands[playerIdx]) freq[getType(t)]++;
        for (int i = 0; i < TILE_TYPES; i++) {
            if (freq[i] == 4) options.add(i);
        }
        return options;
    }

    /** 检测加杠：碰的明牌+手中第4张 */
    private List<Integer> getAddKongOptions(int playerIdx) {
        List<Integer> options = new ArrayList<>();
        for (int[] meld : melds[playerIdx]) {
            if (meld[0] == ACTION_PONG) {
                int type = getType(meld[1]);
                if (countType(hands[playerIdx], type) > 0) options.add(type);
            }
        }
        return options;
    }

    // ══════════════════════════════════════════════
    // 动作处理
    // ══════════════════════════════════════════════

    /** 玩家出牌 */
    public void handleDiscard(int playerIdx, int tileId) {
        if (phase != Phase.PLAYING || playerIdx != currentTurn) return;
        // 验证牌在手牌中
        boolean found = false;
        for (int t : hands[playerIdx]) if (t == tileId) { found = true; break; }
        if (!found) return;

        // 从手牌移除，加入弃牌区
        hands[playerIdx] = removeTile(hands[playerIdx], tileId);
        discards[playerIdx].add(tileId);
        lastDiscard = tileId;
        lastDiscardBy = playerIdx;

        // 检测其他玩家的动作
        checkActionsAfterDiscard();
    }

    private void checkActionsAfterDiscard() {
        availableActions = new byte[4][];
        boolean anyAction = false;

        for (int i = 0; i < 4; i++) {
            if (i == lastDiscardBy) {
                availableActions[i] = new byte[]{ACTION_NONE};
                continue;
            }
            int discardType = getType(lastDiscard);
            List<Byte> actions = new ArrayList<>();

            // 胡
            if (checkHuWithExtra(hands[i], lastDiscard)) {
                actions.add(ACTION_HU);
                actions.add((byte) discardType);
            }
            // 杠
            if (canKong(i, discardType)) {
                actions.add(ACTION_KONG);
                actions.add((byte) discardType);
            }
            // 碰
            if (canPong(i, discardType)) {
                actions.add(ACTION_PONG);
                actions.add((byte) discardType);
            }
            // 吃（仅下家可以吃）
            if (i == nextPlayer(lastDiscardBy)) {
                List<int[]> chiOpts = getChiOptions(i, discardType);
                if (!chiOpts.isEmpty()) {
                    actions.add(ACTION_CHI);
                    actions.add((byte) discardType);
                }
            }

            if (!actions.isEmpty()) {
                actions.add(ACTION_PASS);
                availableActions[i] = new byte[actions.size()];
                for (int j = 0; j < actions.size(); j++) availableActions[i][j] = actions.get(j);
                anyAction = true;
            } else {
                availableActions[i] = new byte[]{ACTION_NONE};
            }
        }

        if (anyAction) {
            phase = Phase.ACTION_WINDOW;
            actionWindowStart = System.currentTimeMillis();
            Arrays.fill(chosenActions, ACTION_NONE);
            Arrays.fill(hasResponded, false);
            hasResponded[lastDiscardBy] = true; // 出牌者不需要选择
            // 无可用动作的玩家自动标记为已响应，避免等待超时
            for (int i = 0; i < 4; i++) {
                if (availableActions[i][0] == ACTION_NONE) {
                    hasResponded[i] = true;
                    chosenActions[i] = ACTION_PASS;
                }
            }
            broadcastState();
        } else {
            // 无动作，下一位摸牌
            advanceTurn();
        }
    }

    /** 玩家选择动作（动作窗口中） */
    public void handleAction(int playerIdx, byte actionType, byte tileType) {
        handleAction(playerIdx, actionType, tileType, (byte) 0);
    }

    /** 玩家选择动作（动作窗口中），chiOptionIndex用于指定吃牌选项索引 */
    public void handleAction(int playerIdx, byte actionType, byte tileType, byte chiOptionIndex) {
        if (phase != Phase.ACTION_WINDOW) return;
        if (hasResponded[playerIdx]) return;

        chosenActions[playerIdx] = actionType;
        hasResponded[playerIdx] = true;
        // 存储吃牌选项索引供后续使用
        if (actionType == ACTION_CHI) {
            // 在tileType字段中临时存储选项索引（高8位）
            chosenActions[playerIdx] = (byte) ((actionType & 0xFF) | ((chiOptionIndex & 0x0F) << 4));
        }

        // 检查是否所有人都已响应
        boolean allResponded = true;
        for (int i = 0; i < 4; i++) {
            if (!hasResponded[i]) { allResponded = false; break; }
        }
        if (allResponded) resolveActions();
    }

    private void resolveActions() {
        // 优先级：胡 > 杠 > 碰 > 吃
        // 点炮胡的优先级最高，有人点炮即胡牌（不需要等待其他玩家选择）
        
        // 首先检查是否有人要胡牌（点炮）
        for (int i = 0; i < 4; i++) {
            if (i == lastDiscardBy) continue;
            if (chosenActions[i] == ACTION_HU) {
                executeAction(i, ACTION_HU, getType(lastDiscard));
                return;
            }
        }
        
        // 如果没有人胡牌，按正常优先级处理其他动作
        for (byte priority : new byte[]{ACTION_KONG, ACTION_PONG, ACTION_CHI}) {
            for (int i = 0; i < 4; i++) {
                if (i == lastDiscardBy) continue;
                if (chosenActions[i] == priority) {
                    executeAction(i, priority, getType(lastDiscard));
                    return;
                }
            }
        }
        
        // 全部pass，进入下一轮
        advanceTurn();
    }

    private void executeAction(int playerIdx, byte action, int discardType) {
        // 从弃牌区移除被使用的牌
        discards[lastDiscardBy].remove(Integer.valueOf(lastDiscard));

        switch (action) {
            case ACTION_HU -> {
                hands[playerIdx] = addTile(hands[playerIdx], lastDiscard);
                winnerIndex = playerIdx;
                winType = 2; // 点炮
                phase = Phase.ENDED;
                broadcastState();
                return;
            }
            case ACTION_KONG -> {
                int[] used = new int[4];
                used[0] = lastDiscard;
                int idx = 1;
                for (int t : hands[playerIdx]) {
                    if (getType(t) == discardType && idx < 4) {
                        used[idx++] = t;
                        hands[playerIdx] = removeTile(hands[playerIdx], t);
                    }
                }
                melds[playerIdx].add(used);
                // 杠后摸牌
                drawFromWall(playerIdx);
            }
            case ACTION_PONG -> {
                int[] meld = new int[3];
                meld[0] = lastDiscard;
                int idx = 1;
                for (int t : hands[playerIdx]) {
                    if (getType(t) == discardType && idx < 3) {
                        meld[idx++] = t;
                        hands[playerIdx] = removeTile(hands[playerIdx], t);
                    }
                }
                melds[playerIdx].add(meld);
                currentTurn = playerIdx;
                // 碰后需要出牌
                phase = Phase.PLAYING;
            }
            case ACTION_CHI -> {
                List<int[]> chiOpts = getChiOptions(playerIdx, discardType);
                if (chiOpts.isEmpty()) { advanceTurn(); return; }
                
                // 从chosenActions中提取吃牌选项索引（高4位）
                int chiOptionIdx = (chosenActions[playerIdx] >> 4) & 0x0F;
                // 确保索引有效
                if (chiOptionIdx >= chiOpts.size()) chiOptionIdx = 0;
                
                int[] opt = chiOpts.get(chiOptionIdx);
                int[] meld = new int[3];
                meld[0] = lastDiscard;
                int tile1 = findTile(hands[playerIdx], opt[0]);
                hands[playerIdx] = removeTile(hands[playerIdx], tile1);
                meld[1] = tile1;
                int tile2 = findTile(hands[playerIdx], opt[1]);
                hands[playerIdx] = removeTile(hands[playerIdx], tile2);
                meld[2] = tile2;
                // 排序使顺子有序
                Arrays.sort(meld);
                melds[playerIdx].add(meld);
                currentTurn = playerIdx;
                phase = Phase.PLAYING;
            }
        }
        lastDiscard = -1;
        lastDiscardBy = -1;
        broadcastState();
    }

    /** 摸牌后的处理（自摸检测 + 暗杠/加杠） */
    private void afterDraw(int playerIdx) {
        int drawnTile = hands[playerIdx][hands[playerIdx].length - 1]; // 最后加入的牌
        // 自摸检测
        if (checkHu(hands[playerIdx])) {
            // 提供自摸选项
            availableActions = new byte[4][];
            for (int i = 0; i < 4; i++) {
                if (i == playerIdx) {
                    availableActions[i] = new byte[]{ACTION_DRAW_WIN, ACTION_PASS};
                } else {
                    availableActions[i] = new byte[]{ACTION_NONE};
                }
            }
            phase = Phase.ACTION_WINDOW;
            actionWindowStart = System.currentTimeMillis();
            Arrays.fill(chosenActions, ACTION_NONE);
            Arrays.fill(hasResponded, false);
            for (int i = 0; i < 4; i++) {
                if (i != playerIdx) hasResponded[i] = true;
            }
            broadcastState();
            return;
        }
        // 暗杠/加杠检测
        List<Integer> concealedKongs = getConcealedKongOptions(playerIdx);
        List<Integer> addKongs = getAddKongOptions(playerIdx);
        if (!concealedKongs.isEmpty() || !addKongs.isEmpty()) {
            List<Byte> actions = new ArrayList<>();
            for (int type : concealedKongs) {
                actions.add(ACTION_KONG);
                actions.add((byte) type);
            }
            for (int type : addKongs) {
                actions.add(ACTION_KONG);
                actions.add((byte) type);
            }
            actions.add(ACTION_PASS);
            availableActions = new byte[4][];
            availableActions[playerIdx] = new byte[actions.size()];
            for (int i = 0; i < actions.size(); i++) availableActions[playerIdx][i] = actions.get(i);
            for (int i = 0; i < 4; i++) {
                if (i != playerIdx) availableActions[i] = new byte[]{ACTION_NONE};
            }
            phase = Phase.ACTION_WINDOW;
            actionWindowStart = System.currentTimeMillis();
            Arrays.fill(chosenActions, ACTION_NONE);
            Arrays.fill(hasResponded, false);
            for (int i = 0; i < 4; i++) {
                if (i != playerIdx) hasResponded[i] = true;
            }
            broadcastState();
            return;
        }
        // 正常出牌阶段
        phase = Phase.PLAYING;
        broadcastState();
    }

    /** 处理自摸胡牌 */
    private void handleDrawWin(int playerIdx) {
        winnerIndex = playerIdx;
        winType = 1; // 自摸
        phase = Phase.ENDED;
        broadcastState();
    }

    /** 处理暗杠/加杠 */
    private void handleSelfKong(int playerIdx, int kongType) {
        // 暗杠
        if (countType(hands[playerIdx], kongType) == 4) {
            int[] meld = new int[4];
            int idx = 0;
            for (int i = hands[playerIdx].length - 1; i >= 0; i--) {
                if (getType(hands[playerIdx][i]) == kongType && idx < 4) {
                    meld[idx++] = hands[playerIdx][i];
                    hands[playerIdx] = removeTile(hands[playerIdx], hands[playerIdx][i]);
                }
            }
            melds[playerIdx].add(meld);
        } else {
            // 加杠
            for (int[] meld : melds[playerIdx]) {
                if (meld[0] == ACTION_PONG && getType(meld[1]) == kongType) {
                    int tile = findTile(hands[playerIdx], kongType);
                    hands[playerIdx] = removeTile(hands[playerIdx], tile);
                    int[] newMeld = Arrays.copyOf(meld, 4);
                    newMeld[3] = tile;
                    melds[playerIdx].set(melds[playerIdx].indexOf(meld), newMeld);
                    break;
                }
            }
        }
        // 杠后摸牌
        drawFromWall(playerIdx);
    }

    private void advanceTurn() {
        if (wallPos >= TOTAL_TILES) {
            // 流局
            winnerIndex = -1;
            winType = 0;
            phase = Phase.ENDED;
            broadcastState();
            return;
        }
        currentTurn = nextPlayer(currentTurn);
        drawFromWall(currentTurn);
    }

    private void drawFromWall(int playerIdx) {
        if (wallPos >= TOTAL_TILES) {
            winnerIndex = -1; winType = 0;
            phase = Phase.ENDED;
            broadcastState();
            return;
        }
        int tile = wall[wallPos++];
        hands[playerIdx] = addTile(hands[playerIdx], tile);
        currentTurn = playerIdx;
        lastDiscard = -1;
        lastDiscardBy = -1;
        phase = Phase.PLAYING;
        afterDraw(playerIdx);
    }

    // ══════════════════════════════════════════════
    // 动作窗口超时 & 路由
    // ══════════════════════════════════════════════

    /** 由 SessionManager 每 tick 调用 */
    public void tickActionTimeout() {
        if (phase != Phase.ACTION_WINDOW) return;
        if (System.currentTimeMillis() - actionWindowStart > ACTION_TIMEOUT_MS) {
            // 超时自动pass
            for (int i = 0; i < 4; i++) {
                if (!hasResponded[i]) {
                    chosenActions[i] = ACTION_PASS;
                    hasResponded[i] = true;
                }
            }
            resolveActions();
        }
    }

    /** SessionManager 路由：出牌 */
    public void routeDiscard(ServerPlayer player, int tileId) {
        int pi = getPlayerIndex(player);
        if (pi < 0) return;
        handleDiscard(pi, tileId);
    }

    /** SessionManager 路由：动作选择 */
    public void routeAction(ServerPlayer player, byte actionType, byte tileType) {
        routeAction(player, actionType, tileType, (byte) 0);
    }

    public void routeAction(ServerPlayer player, byte actionType, byte tileType, byte chiOptionIndex) {
        int pi = getPlayerIndex(player);
        if (pi < 0) return;
        if (actionType == ACTION_DRAW_WIN) {
            handleDrawWin(pi);
        } else if (actionType == ACTION_KONG && lastDiscardBy < 0) {
            // 自杠（暗杠/加杠）
            handleSelfKong(pi, tileType);
        } else {
            handleAction(pi, actionType, tileType, chiOptionIndex);
        }
    }

    // ══════════════════════════════════════════════
    // 状态广播
    // ══════════════════════════════════════════════

    public void broadcastState() {
        for (int i = 0; i < 4; i++) {
            ServerPlayer p = players[i];
            if (p == null) continue;
            MahjongStateS2CPacket pkt = buildStateFor(i);
            ServerPlayNetworking.send(p, pkt);
        }
    }

    /** 向指定玩家重发当前游戏状态（客户端重连/重开界面时补发） */
    public void resyncTo(ServerPlayer player) {
        int pi = getPlayerIndex(player);
        if (pi < 0) return;
        ServerPlayNetworking.send(player, buildStateFor(pi));
    }

    private MahjongStateS2CPacket buildStateFor(int playerIdx) {
        byte phaseByte = switch (phase) {
            case WAITING -> 0;
            case DEALING -> 1;
            case PLAYING -> 2;
            case ACTION_WINDOW -> 3;
            case ENDED -> 4;
        };

        // 我的手牌 (byte[])
        byte[] myHandBytes = new byte[hands[playerIdx] != null ? hands[playerIdx].length : 0];
        for (int i = 0; i < myHandBytes.length; i++) myHandBytes[i] = (byte) hands[playerIdx][i];

        // 对手手牌数量 (byte[3])
        byte[] oppCounts = new byte[3];
        for (int j = 0; j < 3; j++) {
            int opp = (playerIdx + 1 + j) % 4;
            oppCounts[j] = (byte) (hands[opp] != null ? hands[opp].length : 0);
        }

        // 我的副露 (encoded)
        int[][] myMeldsArr = melds[playerIdx].toArray(new int[0][]);
        byte[] myMeldsData = MahjongStateS2CPacket.encodeMelds(myMeldsArr);

        // 对手的副露 (encoded)
        int[][][] oppMeldsArr = new int[3][][];
        for (int j = 0; j < 3; j++) {
            int opp = (playerIdx + 1 + j) % 4;
            oppMeldsArr[j] = melds[opp].toArray(new int[0][]);
        }
        byte[] oppMeldsData = MahjongStateS2CPacket.encodeOppMelds(oppMeldsArr);

        // 弃牌 (byte[][])
        byte[][] allDiscardsBytes = new byte[4][];
        for (int i = 0; i < 4; i++) {
            List<Integer> d = discards[i];
            allDiscardsBytes[i] = new byte[d.size()];
            for (int k = 0; k < d.size(); k++) allDiscardsBytes[i][k] = (byte)(int) d.get(k);
        }

        // 可用动作（仅自己的）
        byte[] myActions = (availableActions != null && availableActions[playerIdx] != null)
                ? availableActions[playerIdx] : new byte[]{ACTION_NONE};

        // 玩家名称
        String[] names = new String[4];
        for (int i = 0; i < 4; i++) {
            names[i] = players[i] != null ? players[i].getName().getString() : "";
        }

        return new MahjongStateS2CPacket(
                phaseByte, (byte) currentTurn, (byte) dealerIndex, (byte) playerIdx,
                myHandBytes, oppCounts, myMeldsData, oppMeldsData,
                (byte) (TOTAL_TILES - wallPos),
                allDiscardsBytes, (byte) lastDiscard, (byte) lastDiscardBy,
                myActions, names, (byte) winnerIndex, winType
        );
    }

    // ══════════════════════════════════════════════
    // 辅助
    // ══════════════════════════════════════════════

    public int getPlayerIndex(ServerPlayer player) {
        for (int i = 0; i < 4; i++) {
            if (players[i] != null && players[i].getUUID().equals(player.getUUID())) return i;
        }
        return -1;
    }

    public ServerPlayer getPlayer(int idx) { return players[idx]; }
    public boolean isFinished() { return phase == Phase.ENDED; }

    public void handlePlayerLeave(ServerPlayer player) {
        int pi = getPlayerIndex(player);
        if (pi < 0 || isFinished()) return;
        // 有人离开，游戏结束
        winnerIndex = -1; winType = 0;
        phase = Phase.ENDED;
        broadcastState();
    }
}
