package io.wifi.starrailexpress.emote;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 表情类型。所有表情默认全部解锁，玩家可在表情装配界面自由装配到罗盘的 8 个槽位中。
 *
 * @param durationTicks 表情播放时长（tick），到期后自动结束
 */
public enum EmoteType {
    WAVE("wave", 60),
    CLAP("clap", 80),
    SCRATCH_HEAD("scratch_head", 60),
    THINK("think", 100),
    POINT("point", 50),
    CONFUSED("confused", 60);

    private static final Map<String, EmoteType> BY_ID = new HashMap<>();

    static {
        for (EmoteType emote : values()) {
            BY_ID.put(emote.id, emote);
        }
    }

    private final String id;
    private final int durationTicks;

    EmoteType(String id, int durationTicks) {
        this.id = id;
        this.durationTicks = durationTicks;
    }

    public String id() {
        return id;
    }

    public int durationTicks() {
        return durationTicks;
    }

    public String translationKey() {
        return "emote.starrailexpress." + id;
    }

    /**
     * 按网络传输的字符串 ID 查找表情，未知 ID 返回 null
     */
    public static EmoteType byId(String id) {
        return id == null ? null : BY_ID.get(id.toLowerCase(Locale.ROOT));
    }
}
