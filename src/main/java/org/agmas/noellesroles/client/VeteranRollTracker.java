package org.agmas.noellesroles.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.phys.Vec3;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 退伍军人冲刺翻滚动画客户端状态追踪器
 * 记录正在播放翻滚动画的玩家实体 ID、起始 tick 与冲刺方向，
 * 由 {@link org.agmas.noellesroles.mixin.client.roles.veteran.VeteranRollRenderMixin} 在渲染时读取。
 */
@Environment(EnvType.CLIENT)
public final class VeteranRollTracker {

    /** 翻滚动画时长（tick），约 0.6 秒，与冲刺位移时间对齐 */
    public static final int ROLL_DURATION_TICKS = 12;

    private record RollState(long startTick, Vec3 direction) {
    }

    private static final Map<Integer, RollState> ROLLS = new ConcurrentHashMap<>();

    private VeteranRollTracker() {
    }

    /**
     * 开始播放翻滚动画
     */
    public static void startRoll(int entityId, Vec3 direction, long currentTick) {
        ROLLS.put(entityId, new RollState(currentTick, direction));
    }

    /**
     * 获取翻滚进度，范围 [0, 1]；不在翻滚状态或已结束时返回 -1
     */
    public static float getRollProgress(int entityId, long currentTick, float partialTick) {
        RollState state = ROLLS.get(entityId);
        if (state == null) {
            return -1.0F;
        }
        float progress = (currentTick - state.startTick() + partialTick) / (float) ROLL_DURATION_TICKS;
        if (progress >= 1.0F) {
            ROLLS.remove(entityId);
            return -1.0F;
        }
        return Math.max(progress, 0.0F);
    }

    /**
     * 获取翻滚冲刺方向（水平单位向量）
     */
    public static Vec3 getRollDirection(int entityId) {
        RollState state = ROLLS.get(entityId);
        return state != null ? state.direction() : Vec3.ZERO;
    }

    /**
     * 清理过期的翻滚状态，由客户端 tick 事件调用
     */
    public static void cleanup(long currentTick) {
        Iterator<Map.Entry<Integer, RollState>> it = ROLLS.entrySet().iterator();
        while (it.hasNext()) {
            if (currentTick - it.next().getValue().startTick() >= ROLL_DURATION_TICKS + 20) {
                it.remove();
            }
        }
    }

    public static void clear() {
        ROLLS.clear();
    }
}
