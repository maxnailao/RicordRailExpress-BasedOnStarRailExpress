package io.wifi.starrailexpress.backpack;

import io.wifi.starrailexpress.progression.ProgressionState.FactionCardType;

import java.util.EnumMap;
import java.util.Map;

/**
 * 场外背包数据模型（Gson POJO）。卡牌以计数形式存储，复用通行证的 {@link FactionCardType}。
 * 序列化格式与 {@code progression} 分区里的 {@code factionCards} 字节兼容，迁移值可直接搬运。
 */
public final class BackpackState {
    public Map<FactionCardType, Integer> cards = new EnumMap<>(FactionCardType.class);
    /** 一次性「移动」迁移守卫：通行证卡牌已搬入背包后置 true。 */
    public boolean migrated = false;
    /** 自选职业卡数量 */
    public int selfSelectCards = 0;
    /** 各阵营卡最近一次成功激活的墙钟时间（epoch ms）。用于同种类使用间隔。 */
    public Map<FactionCardType, Long> cardLastUsedAt = new EnumMap<>(FactionCardType.class);
    /**
     * 每日用量所属日期（本地时区 {@code yyyy-MM-dd}）。
     *
     * <p>与下面两个「今日已用」计数配套：读到旧日期就把计数清零，实现跨天自动重置。
     * 用本地日期而不是 UTC —— 玩家体感的"每天"是本地今天。
     */
    public String dailyUsageDay = "";
    /** 当天各阵营卡已用张数（每种独立计数）。 */
    public Map<FactionCardType, Integer> dailyCardUses = new EnumMap<>(FactionCardType.class);
    /** 当天已用自选职业卡张数。 */
    public int dailySelfSelectUses = 0;
    public long version;

    public static BackpackState createDefault() {
        BackpackState state = new BackpackState();
        for (FactionCardType type : FactionCardType.values()) {
            if (type != FactionCardType.NONE) {
                state.cards.put(type, 0);
            }
        }
        return state;
    }

    public BackpackState normalized() {
        if (cards == null) {
            cards = new EnumMap<>(FactionCardType.class);
        }
        for (FactionCardType type : FactionCardType.values()) {
            if (type != FactionCardType.NONE) {
                cards.putIfAbsent(type, 0);
            }
        }
        // 钳制负值
        cards.replaceAll((type, count) -> count == null ? 0 : Math.max(0, count));
        selfSelectCards = Math.max(0, selfSelectCards);
        cardLastUsedAt = normalizeLastUsedAt(cardLastUsedAt);
        // 每日用量：补全键并钳制负值（跨天重置不在这里做，见 DailyCardUsage）
        if (dailyCardUses == null) {
            dailyCardUses = new EnumMap<>(FactionCardType.class);
        }
        for (FactionCardType type : FactionCardType.values()) {
            if (type != FactionCardType.NONE) {
                dailyCardUses.putIfAbsent(type, 0);
            }
        }
        dailyCardUses.replaceAll((type, count) -> count == null ? 0 : Math.max(0, count));
        dailySelfSelectUses = Math.max(0, dailySelfSelectUses);
        if (dailyUsageDay == null) {
            dailyUsageDay = "";
        }
        return this;
    }

    public long lastUsedAt(FactionCardType type) {
        if (cardLastUsedAt == null || type == null || type == FactionCardType.NONE) {
            return 0L;
        }
        Long value = cardLastUsedAt.get(type);
        return value == null ? 0L : Math.max(0L, value);
    }

    public void markUsed(FactionCardType type, long epochMs) {
        if (type == null || type == FactionCardType.NONE || epochMs <= 0L) {
            return;
        }
        if (cardLastUsedAt == null) {
            cardLastUsedAt = new EnumMap<>(FactionCardType.class);
        }
        cardLastUsedAt.put(type, epochMs);
    }

    public void copyFrom(BackpackState other) {
        this.cards = new EnumMap<>(FactionCardType.class);
        if (other.cards != null) {
            this.cards.putAll(other.cards);
        }
        this.migrated = other.migrated;
        this.selfSelectCards = other.selfSelectCards;
        this.version = other.version;
        this.cardLastUsedAt = other.cardLastUsedAt;
        this.dailyUsageDay = other.dailyUsageDay;
        this.dailyCardUses = other.dailyCardUses;
        this.dailySelfSelectUses = other.dailySelfSelectUses;
        normalized();
    }

    private static Map<FactionCardType, Long> normalizeLastUsedAt(Map<FactionCardType, Long> raw) {
        EnumMap<FactionCardType, Long> used = new EnumMap<>(FactionCardType.class);
        if (raw == null) {
            return used;
        }
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) raw).entrySet()) {
            FactionCardType type = parseType(entry.getKey());
            if (type == FactionCardType.NONE) {
                continue;
            }
            long epoch = toEpoch(entry.getValue());
            if (epoch > 0L) {
                used.put(type, epoch);
            }
        }
        return used;
    }

    private static FactionCardType parseType(Object key) {
        if (key instanceof FactionCardType type) {
            return type;
        }
        if (key == null) {
            return FactionCardType.NONE;
        }
        return FactionCardType.fromString(String.valueOf(key));
    }

    private static long toEpoch(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }
}
