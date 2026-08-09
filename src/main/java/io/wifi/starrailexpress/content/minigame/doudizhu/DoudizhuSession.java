package io.wifi.starrailexpress.content.minigame.doudizhu;

import io.wifi.starrailexpress.network.packet.DoudizhuStateS2CPacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

/**
 * 斗地主游戏会话（服务端权威）
 * <p>
 * 3人游戏：1地主 vs 2农民。54张牌（含大小王）。
 * 阶段：BIDDING → PLAYING → ENDED
 * </p>
 */
public class DoudizhuSession {

    // ── 常量 ──
    public static final int PLAYER_COUNT = 3;
    public static final int CARDS_PER_PLAYER = 17;
    public static final int BOTTOM_CARDS = 3;
    public static final int TOTAL_CARDS = 54;

    // 牌面 rank: 0=3,1=4,...,11=K,12=A,13=2,14=小王,15=大王
    public static final String[] RANK_NAMES = {
        "3","4","5","6","7","8","9","10","J","Q","K","A","2","小","大"
    };
    // suit: 0=黑桃,1=红桃,2=梅花,3=方块
    public static final int SUIT_SPADE = 0, SUIT_HEART = 1, SUIT_CLUB = 2, SUIT_DIAMOND = 3;

    public enum Phase { WAITING, BIDDING, PLAYING, ENDED }
    public enum HandType {
        INVALID, SINGLE, PAIR, TRIPLE, TRIPLE_ONE, TRIPLE_TWO,
        STRAIGHT, DOUBLE_STRAIGHT, AIRPLANE, AIRPLANE_SINGLE, AIRPLANE_PAIR,
        BOMB, ROCKET
    }

    // ── 玩家 ──
    private final ServerPlayer[] players = new ServerPlayer[3];
    private final boolean[] isAI = new boolean[3];

    // ── 游戏状态 ──
    private Phase phase = Phase.BIDDING;
    private final int[][] hands = new int[3][];
    private int[] bottomCards = new int[3];
    private int landlordIndex = -1;
    private int currentTurn;

    // ── 叫地主 ──
    private final int[] bids = {0, 0, 0};
    private int currentBidder;
    private int bidCount;
    private int highestBid;
    private int highestBidder = -1;

    // ── 出牌 ──
    private int[] lastPlayed;
    private int lastPlayedBy = -1;
    private int consecutivePasses;

    // ── 结果 ──
    private byte winnerSide = -1; // 0=地主赢, 1=农民赢

    // ── AI定时器 ──
    private long aiTimerStart = 0;
    private static final long AI_DELAY_MS = 1500;

    // ══════════════════════════════════════════════
    // 构造 & 初始化
    // ══════════════════════════════════════════════

    public DoudizhuSession(ServerPlayer p0, ServerPlayer p1, ServerPlayer p2) {
        players[0] = p0; players[1] = p1; players[2] = p2;
        isAI[0] = p0 == null; isAI[1] = p1 == null; isAI[2] = p2 == null;
        dealCards();
        currentBidder = new Random().nextInt(3);
        currentTurn = currentBidder;
        // 如果首轮叫分者是AI，自动处理
        scheduleAIIfNeeded();
    }

    private void dealCards() {
        List<Integer> deck = new ArrayList<>();
        // 0-51=普通牌(13种rank×4花色), 56=小王, 57=大王
        for (int i = 0; i < 52; i++) deck.add(i);
        deck.add(56); deck.add(57);
        Collections.shuffle(deck);
        for (int p = 0; p < 3; p++) {
            hands[p] = new int[CARDS_PER_PLAYER];
            for (int i = 0; i < CARDS_PER_PLAYER; i++)
                hands[p][i] = deck.get(p * CARDS_PER_PLAYER + i);
            sortHand(hands[p]);
        }
        for (int i = 0; i < 3; i++)
            bottomCards[i] = deck.get(51 + i);
    }

    // ══════════════════════════════════════════════
    // 卡牌工具
    // ══════════════════════════════════════════════

    public static int getRank(int cardId) {
        if (cardId == 57) return 15; // 大王
        if (cardId == 56) return 14; // 小王
        return cardId / 4;
    }

    public static int getSuit(int cardId) {
        if (cardId >= 56) return -1; // 王无花色
        return cardId % 4;
    }

    public static boolean isRedSuit(int cardId) {
        int s = getSuit(cardId);
        return s == SUIT_HEART || s == SUIT_DIAMOND;
    }

    public static void sortHand(int[] hand) {
        Integer[] boxed = new Integer[hand.length];
        for (int i = 0; i < hand.length; i++) boxed[i] = hand[i];
        Arrays.sort(boxed, (a, b) -> {
            int ra = getRank(a), rb = getRank(b);
            if (ra != rb) return Integer.compare(rb, ra); // rank 降序
            return Integer.compare(suitPriority(a), suitPriority(b));
        });
        for (int i = 0; i < hand.length; i++) hand[i] = boxed[i];
    }

    private static int suitPriority(int cardId) {
        int s = getSuit(cardId);
        // ♠ > ♥ > ♦ > ♣
        if (s == 0) return 3; if (s == 1) return 2;
        if (s == 3) return 1; return 0;
    }

    /** 统计每个rank出现次数 */
    private static int[] countByRank(int[] cards) {
        int[] c = new int[16];
        for (int card : cards) c[getRank(card)]++;
        return c;
    }

    // ══════════════════════════════════════════════
    // 叫地主
    // ══════════════════════════════════════════════

    public void handleBid(int playerIndex, int bidScore) {
        if (phase != Phase.BIDDING || playerIndex != currentBidder) return;
        // 校验叫分合法性：bidScore 必须为 0（不叫）或 > highestBid（叫更高分）
        if (bidScore < 0 || bidScore > 3) return;
        if (bidScore > 0 && bidScore <= highestBid) {
            // 叫分不高于当前最高分，视为无效操作，不叫
            bidScore = 0;
        }
        bids[playerIndex] = bidScore;
        if (bidScore > highestBid) {
            highestBid = bidScore;
            highestBidder = playerIndex;
        }
        bidCount++;
        if (highestBid == 3 || bidCount == 3) {
            finalizeBid();
        } else {
            currentBidder = (currentBidder + 1) % 3;
            broadcastState();
            scheduleAIIfNeeded();
        }
    }

    private void finalizeBid() {
        if (highestBidder == -1) {
            // 无人叫分，重新发牌
            dealCards();
            bids[0] = bids[1] = bids[2] = 0;
            bidCount = 0; highestBid = 0; highestBidder = -1;
            currentBidder = new Random().nextInt(3);
            currentTurn = currentBidder;
            broadcastState();
            scheduleAIIfNeeded();
            return;
        }
        landlordIndex = highestBidder;
        // 底牌给地主
        int[] newHand = new int[hands[landlordIndex].length + 3];
        System.arraycopy(hands[landlordIndex], 0, newHand, 0, hands[landlordIndex].length);
        System.arraycopy(bottomCards, 0, newHand, hands[landlordIndex].length, 3);
        hands[landlordIndex] = newHand;
        sortHand(hands[landlordIndex]);
        currentTurn = landlordIndex;
        phase = Phase.PLAYING;
        broadcastState();
        scheduleAIIfNeeded();
    }

    // ══════════════════════════════════════════════
    // 出牌
    // ══════════════════════════════════════════════

    public void handlePlay(int playerIndex, int[] cardIds) {
        if (phase != Phase.PLAYING || playerIndex != currentTurn) return;
        if (cardIds == null || cardIds.length == 0) {
            handlePass(playerIndex);
            return;
        }
        // 验证牌都在手牌中
        if (!cardsInHand(cardIds, hands[playerIndex])) return;

        HandInfo info = detectHandType(cardIds);
        if (info.type == HandType.INVALID) return;

        boolean mustPlay = (lastPlayedBy == -1 || lastPlayedBy == playerIndex || consecutivePasses >= 2);
        if (mustPlay) {
            if (info.type == HandType.INVALID) return;
        } else {
            HandInfo lastInfo = detectHandType(lastPlayed);
            if (!canBeat(info, lastInfo)) return;
        }

        // 从手牌移除
        hands[playerIndex] = removeFromHand(hands[playerIndex], cardIds);
        lastPlayed = cardIds;
        lastPlayedBy = playerIndex;
        consecutivePasses = 0;

        if (hands[playerIndex].length == 0) {
            phase = Phase.ENDED;
            winnerSide = (byte) ((playerIndex == landlordIndex) ? 0 : 1);
            broadcastState();
            return;
        }
        advanceTurn();
        broadcastState();
        scheduleAIIfNeeded();
    }

    private void handlePass(int playerIndex) {
        boolean mustPlay = (lastPlayedBy == -1 || lastPlayedBy == playerIndex || consecutivePasses >= 2);
        if (mustPlay) return;
        consecutivePasses++;
        if (consecutivePasses >= 2) {
            lastPlayed = null;
            lastPlayedBy = -1;
            consecutivePasses = 0;
        }
        advanceTurn();
        broadcastState();
        scheduleAIIfNeeded();
    }

    private void advanceTurn() {
        currentTurn = (currentTurn + 1) % 3;
    }

    // ══════════════════════════════════════════════
    // AI 逻辑
    // ══════════════════════════════════════════════

    private void scheduleAIIfNeeded() {
        if (phase == Phase.ENDED) return;
        if (phase == Phase.BIDDING && isAI[currentBidder]) aiTimerStart = System.currentTimeMillis();
        else if (phase == Phase.PLAYING && isAI[currentTurn]) aiTimerStart = System.currentTimeMillis();
        else aiTimerStart = 0;
    }

    /** 由 SessionManager 每 tick 调用 */
    public void tickAI() {
        if (aiTimerStart == 0) return;
        if (System.currentTimeMillis() - aiTimerStart < AI_DELAY_MS) return;
        aiTimerStart = 0;

        if (phase == Phase.BIDDING && isAI[currentBidder]) {
            aiBid();
        } else if (phase == Phase.PLAYING && isAI[currentTurn]) {
            aiPlay();
        }
    }

    private void aiBid() {
        // 简单策略：随机叫 0-1
        int bid = new Random().nextInt(2); // 0 或 1
        handleBid(currentBidder, bid);
    }

    private void aiPlay() {
        boolean mustPlay = (lastPlayedBy == -1 || lastPlayedBy == currentTurn || consecutivePasses >= 2);
        int[] play = null;

        if (mustPlay) {
            play = findSmallestPlay(hands[currentTurn]);
        } else {
            play = findSmallestBeat(hands[currentTurn], lastPlayed);
        }

        if (play != null) {
            handlePlay(currentTurn, play);
        } else {
            handlePass(currentTurn);
        }
    }

    private int[] findSmallestPlay(int[] hand) {
        if (hand.length == 0) return null;
        // 出最小单牌
        return new int[]{hand[hand.length - 1]};
    }

    private int[] findSmallestBeat(int[] hand, int[] last) {
        HandInfo lastInfo = detectHandType(last);
        if (lastInfo.type == HandType.INVALID) return null;

        int[] rc = countByRank(hand);

        switch (lastInfo.type) {
            case SINGLE: {
                for (int r = lastInfo.rank + 1; r <= 15; r++)
                    if (rc[r] >= 1) return new int[]{findCard(hand, r)};
                break;
            }
            case PAIR: {
                for (int r = lastInfo.rank + 1; r <= 13; r++)
                    if (rc[r] >= 2) return findCardsOfRank(hand, r, 2);
                break;
            }
            case TRIPLE: {
                for (int r = lastInfo.rank + 1; r <= 13; r++)
                    if (rc[r] >= 3) return findCardsOfRank(hand, r, 3);
                break;
            }
            case TRIPLE_ONE: {
                for (int r = lastInfo.rank + 1; r <= 13; r++) {
                    if (rc[r] >= 3) {
                        int[] tri = findCardsOfRank(hand, r, 3);
                        int kicker = findKicker(hand, r, 1);
                        if (kicker >= 0) return concat(tri, new int[]{kicker});
                    }
                }
                break;
            }
            case TRIPLE_TWO: {
                for (int r = lastInfo.rank + 1; r <= 13; r++) {
                    if (rc[r] >= 3) {
                        int[] tri = findCardsOfRank(hand, r, 3);
                        int[] kicker = findPairKicker(hand, r);
                        if (kicker != null) return concat(tri, kicker);
                    }
                }
                break;
            }
            case STRAIGHT: {
                int len = lastInfo.length;
                for (int startR = lastInfo.rank + 1; startR + len - 1 <= 11; startR++) {
                    int[] s = tryStraight(hand, startR, len);
                    if (s != null) return s;
                }
                break;
            }
            case BOMB: {
                for (int r = lastInfo.rank + 1; r <= 13; r++)
                    if (rc[r] >= 4) return findCardsOfRank(hand, r, 4);
                // 王炸
                if (rc[14] >= 1 && rc[15] >= 1) return new int[]{56, 57};
                return null;
            }
            case ROCKET:
                return null;
            default:
                break;
        }

        // 非炸弹牌型，尝试用炸弹压
        if (lastInfo.type != HandType.BOMB && lastInfo.type != HandType.ROCKET) {
            for (int r = 0; r <= 13; r++)
                if (rc[r] >= 4) return findCardsOfRank(hand, r, 4);
            if (rc[14] >= 1 && rc[15] >= 1) return new int[]{56, 57};
        }
        return null;
    }

    private int findCard(int[] hand, int rank) {
        for (int c : hand) if (getRank(c) == rank) return c;
        return -1;
    }

    private int[] findCardsOfRank(int[] hand, int rank, int count) {
        int[] result = new int[count];
        int idx = 0;
        for (int c : hand) {
            if (getRank(c) == rank && idx < count) result[idx++] = c;
        }
        return idx == count ? result : null;
    }

    private int findKicker(int[] hand, int excludeRank, int count) {
        int[] rc = countByRank(hand);
        for (int r = 0; r <= 15; r++) {
            if (r == excludeRank) continue;
            if (rc[r] >= count) return findCard(hand, r);
        }
        return -1;
    }

    private int[] findPairKicker(int[] hand, int excludeRank) {
        int[] rc = countByRank(hand);
        for (int r = 0; r <= 13; r++) {
            if (r == excludeRank) continue;
            if (rc[r] >= 2) return findCardsOfRank(hand, r, 2);
        }
        return null;
    }

    private int[] tryStraight(int[] hand, int startRank, int length) {
        int[] result = new int[length];
        for (int i = 0; i < length; i++) {
            int r = startRank + i;
            if (r > 11) return null;
            int c = findCard(hand, r);
            if (c < 0) return null;
            result[i] = c;
        }
        return result;
    }

    private int[] concat(int[] a, int[] b) {
        int[] r = new int[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    // ══════════════════════════════════════════════
    // 牌型判定
    // ══════════════════════════════════════════════

    public static class HandInfo {
        public final HandType type;
        public final int rank;
        public final int length;
        HandInfo(HandType t, int r, int l) { type = t; rank = r; length = l; }
    }

    public static HandInfo detectHandType(int[] cards) {
        if (cards == null || cards.length == 0)
            return new HandInfo(HandType.INVALID, -1, 0);

        int n = cards.length;
        int[] rc = countByRank(cards);
        int[] ranks = Arrays.stream(cards).map(DoudizhuSession::getRank).distinct().sorted().toArray();
        int uniqueCount = ranks.length;

        // 火箭
        if (n == 2 && rc[14] >= 1 && rc[15] >= 1)
            return new HandInfo(HandType.ROCKET, 15, 1);

        // 单牌
        if (n == 1) return new HandInfo(HandType.SINGLE, ranks[0], 1);
        // 对子
        if (n == 2 && uniqueCount == 1) return new HandInfo(HandType.PAIR, ranks[0], 1);
        // 三张
        if (n == 3 && uniqueCount == 1) return new HandInfo(HandType.TRIPLE, ranks[0], 1);

        // 炸弹 (4张相同)
        if (n == 4 && uniqueCount == 1) return new HandInfo(HandType.BOMB, ranks[0], 1);

        // 三带一
        if (n == 4 && uniqueCount == 2) {
            for (int r : ranks) {
                if (rc[r] == 3) return new HandInfo(HandType.TRIPLE_ONE, r, 1);
            }
        }

        // 三带二
        if (n == 5 && uniqueCount == 2) {
            for (int r : ranks) {
                if (rc[r] == 3) {
                    int otherR = ranks[0] == r ? ranks[1] : ranks[0];
                    if (rc[otherR] == 2 && otherR <= 13)
                        return new HandInfo(HandType.TRIPLE_TWO, r, 1);
                }
            }
        }

        // 顺子 (5+连续单牌，不含2和王)
        if (n >= 5 && uniqueCount == n) {
            int min = ranks[0], max = ranks[n - 1];
            if (max <= 11 && max - min == n - 1)
                return new HandInfo(HandType.STRAIGHT, min, n);
        }

        // 连对 (3+连续对子，不含2和王)
        if (n >= 6 && n % 2 == 0 && uniqueCount == n / 2) {
            boolean allPairs = true;
            for (int r : ranks) if (rc[r] != 2) { allPairs = false; break; }
            if (allPairs) {
                int min = ranks[0], max = ranks[uniqueCount - 1];
                if (max <= 11 && max - min == uniqueCount - 1)
                    return new HandInfo(HandType.DOUBLE_STRAIGHT, min, uniqueCount);
            }
        }

        // 飞机 (2+连续三张)
        HandInfo airplane = checkAirplane(cards, rc, ranks);
        if (airplane != null) return airplane;

        return new HandInfo(HandType.INVALID, -1, 0);
    }

    private static HandInfo checkAirplane(int[] cards, int[] rc, int[] allRanks) {
        int n = cards.length;

        // 找所有rank出现>=3次的（按rank排序）
        List<Integer> tripleRanks = new ArrayList<>();
        for (int r = 0; r <= 15; r++) {
            if (rc[r] >= 3) tripleRanks.add(r);
        }
        if (tripleRanks.size() < 2) return null;

        // 尝试所有可能的连续三张组合（从最长开始）
        for (int len = tripleRanks.size(); len >= 2; len--) {
            for (int start = 0; start + len <= tripleRanks.size(); start++) {
                List<Integer> seq = tripleRanks.subList(start, start + len);
                int min = seq.get(0), max = seq.get(seq.size() - 1);
                if (max > 11) continue; // 不含2和王
                if (max - min != len - 1) continue; // 必须连续

                int tripleCount = len;
                int tripleCards = tripleCount * 3;
                int remaining = n - tripleCards;

                // 飞机不带
                if (remaining == 0 && tripleCount >= 2)
                    return new HandInfo(HandType.AIRPLANE, min, tripleCount);

                // 飞机带单
                if (remaining == tripleCount && tripleCount >= 2) {
                    return new HandInfo(HandType.AIRPLANE_SINGLE, min, tripleCount);
                }

                // 飞机带对
                if (remaining == tripleCount * 2 && tripleCount >= 2) {
                    // 验证剩余牌都是对子（排除三张的rank）
                    Set<Integer> tripleSet = new HashSet<>(seq);
                    int[] remainRC = new int[16];
                    for (int r = 0; r < 16; r++) remainRC[r] = rc[r];
                    for (int r : seq) remainRC[r] -= 3;

                    boolean validKickers = true;
                    int pairCount = 0;
                    for (int r = 0; r < 16; r++) {
                        if (remainRC[r] > 0) {
                            if (remainRC[r] != 2) { validKickers = false; break; }
                            // 王炸不能作为对子附属牌
                            if (r == 14 || r == 15) { validKickers = false; break; }
                            pairCount++;
                        }
                    }
                    if (validKickers && pairCount == tripleCount)
                        return new HandInfo(HandType.AIRPLANE_PAIR, min, tripleCount);
                }
            }
        }
        return null;
    }

    /** 判断 played 能否压过 last */
    public static boolean canBeat(HandInfo played, HandInfo last) {
        if (played.type == HandType.INVALID) return false;
        if (played.type == HandType.ROCKET) return true;
        if (played.type == HandType.BOMB) {
            if (last.type == HandType.BOMB) return played.rank > last.rank;
            return true; // 炸弹压非炸弹
        }
        if (last.type == HandType.ROCKET) return false;
        if (last.type == HandType.BOMB && played.type != HandType.BOMB) return false;
        if (played.type != last.type) return false;
        if (played.length != last.length) return false;
        return played.rank > last.rank;
    }

    // ══════════════════════════════════════════════
    // 手牌操作
    // ══════════════════════════════════════════════

    private boolean cardsInHand(int[] cards, int[] hand) {
        int[] temp = Arrays.copyOf(hand, hand.length);
        for (int c : cards) {
            boolean found = false;
            for (int i = 0; i < temp.length; i++) {
                if (temp[i] == c) { temp[i] = -1; found = true; break; }
            }
            if (!found) return false;
        }
        return true;
    }

    private int[] removeFromHand(int[] hand, int[] toRemove) {
        boolean[] removed = new boolean[hand.length];
        for (int c : toRemove) {
            for (int i = 0; i < hand.length; i++) {
                if (!removed[i] && hand[i] == c) { removed[i] = true; break; }
            }
        }
        int[] result = new int[hand.length - toRemove.length];
        int idx = 0;
        for (int i = 0; i < hand.length; i++) {
            if (!removed[i]) result[idx++] = hand[i];
        }
        return result;
    }



    // ══════════════════════════════════════════════
    // 状态广播
    // ══════════════════════════════════════════════

    public void broadcastState() {
        for (int i = 0; i < 3; i++) {
            if (isAI[i] || players[i] == null) continue;
            DoudizhuStateS2CPacket pkt = buildStateForPlayer(i);
            ServerPlayNetworking.send(players[i], pkt);
        }
    }

    /** 向指定玩家重发当前游戏状态（客户端重连/重开界面时补发） */
    public void resyncTo(ServerPlayer player) {
        int pi = getPlayerIndex(player);
        if (pi < 0 || isAI[pi]) return;
        ServerPlayNetworking.send(player, buildStateForPlayer(pi));
    }

    private DoudizhuStateS2CPacket buildStateForPlayer(int pi) {
        int oppCount1 = hands[(pi + 1) % 3].length;
        int oppCount2 = hands[(pi + 2) % 3].length;
        String[] names = new String[3];
        for (int i = 0; i < 3; i++)
            names[i] = isAI[i] ? "AI" : (players[i] != null ? players[i].getName().getString() : "?");

        // 叫分阶段用 currentBidder 表示当前叫分者，出牌阶段用 currentTurn
        int turnForClient = (phase == Phase.BIDDING) ? currentBidder : currentTurn;

        return DoudizhuStateS2CPacket.create(
            phase.ordinal(), pi, turnForClient, landlordIndex,
            Arrays.copyOf(hands[pi], hands[pi].length),
            oppCount1, oppCount2,
            bottomCards,
            lastPlayed != null ? Arrays.copyOf(lastPlayed, lastPlayed.length) : new int[0],
            lastPlayedBy,
            consecutivePasses,
            Arrays.copyOf(bids, 3),
            names,
            winnerSide
        );
    }

    // ══════════════════════════════════════════════
    // 公共访问器
    // ══════════════════════════════════════════════

    public boolean isFinished() { return phase == Phase.ENDED; }
    public Phase getPhase() { return phase; }
    public ServerPlayer getPlayer(int i) { return players[i]; }
    public boolean isAI(int i) { return isAI[i]; }
    public int getPlayerIndex(ServerPlayer p) {
        for (int i = 0; i < 3; i++)
            if (players[i] != null && players[i].getUUID().equals(p.getUUID())) return i;
        return -1;
    }
    public int getCurrentTurn() { return currentTurn; }
    public int getCurrentBidder() { return currentBidder; }
    public boolean hasAIPlayers() { return isAI[0] || isAI[1] || isAI[2]; }

    public void handleOpponentLeft(ServerPlayer player) {
        phase = Phase.ENDED;
        winnerSide = -1; // 平局
        broadcastState();
    }
}
