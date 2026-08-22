package org.agmas.noellesroles.client.blindness;

/**
 * 轮廓揭示透明度时间线（移植自"失明症"模组 ContactRevealTimeline）
 * <p>
 * 三段式曲线：线性淡入 → 恒定停留 → smoothstep 缓动淡出。
 * 时间线完全在客户端本地计算，服务端只负责下发揭示事件。
 */
public final class ContactRevealTimeline {

    private ContactRevealTimeline() {
    }

    /**
     * 计算当前透明度。
     *
     * @param localAge 自"延迟结束"起经过的纳秒数（负值表示仍在延迟期）
     * @param fadeIn   淡入时长（纳秒）
     * @param hold     停留时长（纳秒）
     * @param fadeOut  淡出时长（纳秒）
     * @param intensity 强度上限（中心块 1.0，邻接块 0.75，声纹更弱）
     */
    public static float alpha(long localAge, long fadeIn, long hold, long fadeOut, float intensity) {
        if (localAge < 0) {
            return 0F;
        }
        if (localAge < fadeIn) {
            return intensity * (float) localAge / fadeIn;
        }
        if (localAge < fadeIn + hold) {
            return intensity;
        }
        long fadeAge = localAge - fadeIn - hold;
        if (fadeAge >= fadeOut) {
            return 0F;
        }
        // smoothstep：比线性淡出更自然
        float progress = (float) fadeAge / fadeOut;
        float smooth = progress * progress * (3F - 2F * progress);
        return intensity * (1F - smooth);
    }
}
