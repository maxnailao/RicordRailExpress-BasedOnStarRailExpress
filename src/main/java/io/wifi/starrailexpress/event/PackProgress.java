package io.wifi.starrailexpress.event;

/** 单玩家单任务包进度：counts[i] 对应 EventPack.conditions()[i]，全部达标才 claimed 发奖。 */
public final class PackProgress {
    public boolean claimed;
    public boolean claimable;
    public int[] counts;

    public PackProgress(boolean claimed, boolean claimable, int[] counts) {
        this.claimed = claimed;
        this.claimable = claimable;
        this.counts = counts == null ? new int[0] : counts;
    }

    public static PackProgress ofSize(int n) {
        return new PackProgress(false, false, new int[Math.max(0, n)]);
    }
}