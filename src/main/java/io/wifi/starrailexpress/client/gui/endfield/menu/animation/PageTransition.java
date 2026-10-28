package io.wifi.starrailexpress.client.gui.endfield.menu.animation;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 子页面转场：进入时 920ms CRT 显像管闪烁；返回主菜单时 380ms 全屏淡出。
 * 用法：init() 里 beginCrt()；返回按钮回调里 startReturn(runnable)；
 * render() 最末尾调用 render(graphics, width, height)。
 */
public final class PageTransition {
    public static final long CRT_TOTAL_MS = 920;
    public static final long RETURN_MS = 380;
    /** CRT 黑场窗口：窗口内纯黑，窗口间页面亮起（0–64 为开场纯黑）。 */
    private static final long[][] BLACK_WINDOWS = {
            {0, 64}, {120, 166}, {230, 285}, {350, 405}, {478, 524}, {598, 644}
    };

    private long crtStart = -1;
    private long returnStart = -1;
    private Runnable returnAction;
    private boolean crtShown;

    public void beginCrt() {
        crtStart = System.nanoTime();
    }

    /** 仅界面"第一次打开"播放 CRT：从子弹窗回到原界面时跳过；动画开关关闭时跳过。 */
    public void beginCrtOnce(net.minecraft.client.Minecraft client) {
        if (crtShown || crtStart >= 0) {
            return;
        }
        if (client != null && client.options.screenEffectScale().get() == 0
                && !io.wifi.starrailexpress.SREClientConfig.menuAnimationsEnabled()) {
            return;
        }
        crtShown = true;
        beginCrt();
    }

    public void startReturn(Runnable action) {
        if (returnStart < 0) {
            returnStart = System.nanoTime();
            returnAction = action;
        }
    }

    public boolean returning() {
        return returnStart >= 0;
    }

    /** CRT 播放期间为 true，页面可据此屏蔽点击输入。 */
    public boolean crtActive() {
        return crtStart >= 0 && elapsed(crtStart) < CRT_TOTAL_MS;
    }

    /** 在界面 render() 的最末尾调用：绘制覆盖层并在淡出结束时执行返回。 */
    public void render(GuiGraphics graphics, int width, int height) {
        if (returnStart >= 0) {
            float t = elapsed(returnStart);
            if (t >= RETURN_MS) {
                Runnable action = returnAction;
                returnStart = -1;
                returnAction = null;
                crtStart = -1;
                if (action != null) {
                    action.run();
                }
                return;
            }
            graphics.fill(0, 0, width, height, Math.round(t / RETURN_MS * 255f) << 24);
            return;
        }
        if (crtStart < 0) {
            return;
        }
        float t = elapsed(crtStart);
        if (t >= CRT_TOTAL_MS) {
            crtStart = -1;
            return;
        }
        renderCrt(graphics, width, height, t);
    }

    private void renderCrt(GuiGraphics graphics, int width, int height, float t) {
        if (t < 260) {
            float p = t / 260f;
            int half = Math.max(1, Math.round(2f + 26f * easeOut(p)));
            float fade = t < 140 ? 0.85f : 0.85f * Math.clamp((260 - t) / 120f, 0, 1);
            int cy = height / 2;
            graphics.fill(0, cy - half, width, cy + half, Math.round(fade * 255f) << 24 | 0x00FFFFFF);
        }
        if (t >= 40) {
            float p = Math.clamp((t - 40) / 870f, 0, 1);
            int band = Math.max(10, height / 7);
            int y = Math.round(-band + p * (height + band * 2f));
            graphics.fill(0, y, width, y + band, 0x14FFFFFF);
        }
        float black = blackAlpha(t);
        if (black > 0.001f) {
            graphics.fill(0, 0, width, height, Math.round(black * 255f) << 24);
        }
    }

    private static float blackAlpha(float t) {
        float value = 0;
        for (long[] window : BLACK_WINDOWS) {
            float in = window[0] == 0 ? 1 : Math.clamp((t - window[0]) / 8f, 0, 1);
            float out = Math.clamp((window[1] - t) / 8f, 0, 1);
            value = Math.max(value, Math.min(in, out));
        }
        return value;
    }

    private static float elapsed(long origin) {
        return (float) ((System.nanoTime() - origin) / 1_000_000d);
    }

    private static float easeOut(float value) {
        float inv = 1 - value;
        return 1 - inv * inv * inv;
    }
}