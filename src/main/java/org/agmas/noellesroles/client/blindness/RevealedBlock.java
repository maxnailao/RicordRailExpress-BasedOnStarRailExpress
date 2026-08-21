package org.agmas.noellesroles.client.blindness;

import net.minecraft.core.BlockPos;

/**
 * 单个被揭示方块的本地状态（移植自"失明症"模组 RevealedBlock）
 * <p>
 * 重复揭示同一方块时采用"只增不减"的刷新策略：来源只会升级、可见面按位或累积、
 * 结束时间只延长不缩短、强度取最大值——避免连续探测时轮廓闪烁抖动。
 */
public final class RevealedBlock {

    /** 中心块额外停留时间（纳秒） */
    private static final long CENTER_HOLD_BONUS_NANOS = 100_000_000L;

    private final BlockPos pos;
    private RevealSource source;
    private int visibleFaces;
    private long startNanos;
    private long delayNanos;
    private long fadeInNanos;
    private long holdNanos;
    private long fadeOutNanos;
    private float intensity;

    RevealedBlock(BlockPos pos, RevealSource source, int visibleFaces, long startNanos,
            long delayNanos, long fadeInNanos, long holdNanos, long fadeOutNanos, float intensity) {
        this.pos = pos.immutable();
        this.source = source;
        this.visibleFaces = visibleFaces;
        this.startNanos = startNanos;
        this.delayNanos = source == RevealSource.CANE_CENTER ? 0 : delayNanos;
        this.fadeInNanos = fadeInNanos;
        this.holdNanos = holdNanos + (source == RevealSource.CANE_CENTER ? CENTER_HOLD_BONUS_NANOS : 0);
        this.fadeOutNanos = fadeOutNanos;
        this.intensity = source == RevealSource.CANE_CENTER ? 1.0F : intensity;
    }

    void refresh(RevealSource newSource, int newVisibleFaces, long now, long delay, long fadeIn,
            long hold, long fadeOut, float newIntensity) {
        if (this.source == null || newSource.priority() > this.source.priority()) {
            this.source = newSource;
        }
        this.visibleFaces |= newVisibleFaces;
        this.intensity = Math.max(this.intensity, this.source == RevealSource.CANE_CENTER ? 1.0F : newIntensity);
        // "只增不减"：绝不重置 startNanos/delay/fadeIn——重置会让邻接块每次刷新都重走
        // 200ms 延迟与 300ms 淡入，横扫 4 连击/声纹每 10 tick 刷新时轮廓会周期性熄灭再亮起，
        // 表现为剧烈闪烁。新的揭示只通过延长停留时间来延长总时长。
        long requestedEnd = now + fadeIn + hold + fadeOut;
        if (requestedEnd > endNanos()) {
            this.holdNanos = Math.max(this.holdNanos,
                    requestedEnd - this.startNanos - this.delayNanos - this.fadeInNanos - this.fadeOutNanos);
        }
    }

    public BlockPos pos() {
        return this.pos;
    }

    public boolean isCenter() {
        return this.source == RevealSource.CANE_CENTER;
    }

    public RevealSource source() {
        return this.source;
    }

    public int priority() {
        return this.source.priority();
    }

    public int visibleFaces() {
        return this.visibleFaces;
    }

    /** 揭示完全结束的时间点（纳秒），到期后由管理器清理 */
    public long endNanos() {
        return this.startNanos + this.delayNanos + this.fadeInNanos + this.holdNanos + this.fadeOutNanos;
    }

    /** 当前透明度，交给渲染器写入线框颜色 alpha */
    public float alpha(long now) {
        long localAge = now - this.startNanos - this.delayNanos;
        return ContactRevealTimeline.alpha(localAge, this.fadeInNanos, this.holdNanos, this.fadeOutNanos,
                this.intensity);
    }
}
