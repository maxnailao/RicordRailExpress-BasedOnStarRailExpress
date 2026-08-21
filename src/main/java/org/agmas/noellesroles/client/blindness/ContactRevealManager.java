package org.agmas.noellesroles.client.blindness;

import net.minecraft.core.BlockPos;
import org.agmas.noellesroles.packet.ContactRevealS2CPacket;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户端轮廓揭示仓库（移植自"失明症"模组 ContactRevealManager）
 * <p>
 * 统一存放导盲杖探测与生物声纹两类揭示，以方块坐标为键去重合并。
 * 容量上限 {@link #MAX_ACTIVE_REVEALS}，超出时淘汰"优先级最低 + 最早结束"的条目。
 */
public final class ContactRevealManager {

    /** 同时活跃的揭示方块上限 */
    public static final int MAX_ACTIVE_REVEALS = 128;

    /** 邻接块延迟出现时长（纳秒），制造"先中心后四周"的层次感 */
    private static final long ADJACENT_DELAY_NANOS = 200_000_000L;
    /** 淡入时长（纳秒） */
    private static final long FADE_IN_NANOS = 300_000_000L;
    /** 停留时长（纳秒） */
    private static final long HOLD_NANOS = 5_000_000_000L;
    /** 淡出时长（纳秒） */
    private static final long FADE_OUT_NANOS = 800_000_000L;

    /** 导盲杖中心块强度 */
    private static final float CENTER_INTENSITY = 1.0F;
    /** 导盲杖邻接块强度 */
    private static final float ADJACENT_INTENSITY = 0.75F;

    private static final Map<BlockPos, RevealedBlock> REVEALS = new LinkedHashMap<>();

    private ContactRevealManager() {
    }

    /** 接收导盲杖探测揭示 */
    public static void accept(BlockPos center, List<ContactRevealS2CPacket.Entry> entries) {
        if (entries.isEmpty()) {
            return;
        }
        long now = System.nanoTime();
        for (ContactRevealS2CPacket.Entry entry : entries) {
            if (!entry.isValid()) {
                return;
            }
            BlockPos pos = entry.resolve(center).immutable();
            RevealSource source = entry.center() ? RevealSource.CANE_CENTER : RevealSource.CANE_ADJACENT;
            float intensity = entry.center() ? CENTER_INTENSITY : ADJACENT_INTENSITY;
            RevealedBlock existing = REVEALS.get(pos);
            if (existing == null) {
                REVEALS.put(pos, new RevealedBlock(pos, source, entry.faceMask(), now,
                        ADJACENT_DELAY_NANOS, FADE_IN_NANOS, HOLD_NANOS, FADE_OUT_NANOS, intensity));
            } else {
                existing.refresh(source, entry.faceMask(), now,
                        ADJACENT_DELAY_NANOS, FADE_IN_NANOS, HOLD_NANOS, FADE_OUT_NANOS, intensity);
            }
        }
        trimToLimit();
    }

    /** 接收生物声纹揭示：强度按类别分级，并随声音强度缩放 */
    public static void acceptSound(BlockPos blockCenter, RevealSource source, float strength,
            List<ContactRevealS2CPacket.Entry> entries) {
        if (source == null || entries.isEmpty() || !Float.isFinite(strength) || strength < 0F || strength > 1F) {
            return;
        }
        long now = System.nanoTime();
        float base = switch (source) {
            case ENTITY_FOOTSTEP -> 0.52F;
            case ENTITY_AMBIENT -> 0.62F;
            case ENTITY_DANGER -> 0.70F;
            default -> 0F;
        };
        float intensity = Math.min(0.75F, base * (0.65F + strength * 0.35F));
        // 脚步声停留更短
        long hold = source == RevealSource.ENTITY_FOOTSTEP ? (long) (HOLD_NANOS * 0.72) : HOLD_NANOS;
        for (ContactRevealS2CPacket.Entry entry : entries) {
            if (!entry.isValid()) {
                return;
            }
            // 声纹条目为相对声源 blockCenter 的偏移，按服务端下发的中心解析
            BlockPos pos = entry.resolve(blockCenter).immutable();
            RevealedBlock existing = REVEALS.get(pos);
            if (existing == null) {
                REVEALS.put(pos, new RevealedBlock(pos, source, entry.faceMask(), now,
                        0, FADE_IN_NANOS, hold, FADE_OUT_NANOS, intensity));
            } else {
                existing.refresh(source, entry.faceMask(), now,
                        0, FADE_IN_NANOS, hold, FADE_OUT_NANOS, intensity);
            }
        }
        trimToLimit();
    }

    /** 每客户端 tick 清理到期的揭示 */
    public static void tick(long now) {
        REVEALS.values().removeIf(reveal -> reveal.endNanos() <= now);
    }

    /** 渲染快照，避免遍历期间的并发修改 */
    public static List<RevealedBlock> snapshot() {
        return List.copyOf(new ArrayList<>(REVEALS.values()));
    }

    public static void clear() {
        REVEALS.clear();
    }

    /** 超出容量时按"优先级最低 + 最早结束"淘汰 */
    private static void trimToLimit() {
        while (REVEALS.size() > MAX_ACTIVE_REVEALS) {
            BlockPos victim = REVEALS.entrySet().stream()
                    .min(Comparator.<Map.Entry<BlockPos, RevealedBlock>>comparingInt(entry -> entry.getValue().priority())
                            .thenComparingLong(entry -> entry.getValue().endNanos()))
                    .map(Map.Entry::getKey)
                    .orElse(null);
            if (victim == null) {
                break;
            }
            REVEALS.remove(victim);
        }
    }
}
