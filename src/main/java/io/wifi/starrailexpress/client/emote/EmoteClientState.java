package io.wifi.starrailexpress.client.emote;

import io.wifi.starrailexpress.emote.EmoteType;
import net.minecraft.client.multiplayer.ClientLevel;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 表情系统客户端状态：记录当前正在播放表情的实体，供玩家模型动画查询。
 * <p>
 * 条目由服务端 {@code EmotePlayS2CPayload} 驱动写入；除服务端停止包外，
 * 本地按表情时长 + 缓冲自动过期，防止停止包丢失导致姿态卡死。
 */
public final class EmoteClientState {

    /** 停止包丢失时的兜底缓冲（tick） */
    private static final long EXPIRE_BUFFER_TICKS = 20;

    /** 实体 ID -> 正在播放的表情 */
    private static final Map<Integer, Entry> ACTIVE = new HashMap<>();

    public record Entry(EmoteType emote, long startGameTick) {
    }

    private EmoteClientState() {
    }

    public static void start(int entityId, EmoteType emote, long startGameTick) {
        ACTIVE.put(entityId, new Entry(emote, startGameTick));
    }

    public static void stop(int entityId) {
        ACTIVE.remove(entityId);
    }

    /**
     * 查询实体当前播放的表情，无则返回 null
     */
    public static Entry get(int entityId) {
        return ACTIVE.get(entityId);
    }

    /**
     * 表情播放进度（tick，含小数部分），用于程序化动画插值；无激活表情返回 -1
     */
    public static float getProgress(int entityId, ClientLevel level, float ageInTicks, int entityTickCount) {
        Entry entry = ACTIVE.get(entityId);
        if (entry == null) {
            return -1.0F;
        }
        // ageInTicks - tickCount ≈ 当前 partialTick，保证渲染平滑
        float partialTick = ageInTicks - entityTickCount;
        return (level.getGameTime() - entry.startGameTick()) + partialTick;
    }

    /**
     * 每客户端刻清理过期条目（兜底）
     */
    public static void tick(ClientLevel level) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        Iterator<Entry> it = ACTIVE.values().iterator();
        while (it.hasNext()) {
            Entry entry = it.next();
            if (now - entry.startGameTick() >= entry.emote().durationTicks() + EXPIRE_BUFFER_TICKS) {
                it.remove();
            }
        }
    }

    /**
     * 断线/退出世界时清空
     */
    public static void clear() {
        ACTIVE.clear();
    }
}
