package io.wifi.starrailexpress.client.gui.endfield.menu.animation;

public final class MenuTimeline {
    public static final float SPEED = 1.0f;
    public static final float CLOSE_TOTAL_MS = 720f;
    public static final float DETAIL_TOTAL_MS = 960f;
    private final long openedAt;
    private final boolean reducedMotion;
    private long closedAt = -1;
    private boolean toDetail;

    public MenuTimeline(long now, boolean reducedMotion) {
        openedAt = now;
        this.reducedMotion = reducedMotion;
    }

    public void close(long now) {
        close(now, false);
    }

    /** 进入子页面：圆环铺满视口 + 黑场，960ms 后切换页面。 */
    public void transitionToDetail(long now) {
        close(now, true);
    }

    private void close(long now, boolean detail) {
        if (!closing()) {
            closedAt = now;
            toDetail = detail;
        }
    }

    public boolean closing() {
        return closedAt >= 0;
    }

    public boolean isToDetail() {
        return toDetail;
    }

    public boolean finished(long now) {
        return closing() && (reducedMotion
                || elapsed(now, closedAt) >= (toDetail ? DETAIL_TOTAL_MS : CLOSE_TOTAL_MS));
    }

    public Frame sample(long now) {
        float opening = reducedMotion ? 9999 : elapsed(closing() ? closedAt : now, openedAt);
        float exit = !closing() ? -1 : reducedMotion ? 9999 : elapsed(now, closedAt);
        return new Frame(opening, exit, toDetail);
    }

    private static float elapsed(long now, long origin) {
        return (float) ((now - origin) / 1_000_000d * SPEED);
    }

    public record Frame(float opening, float exit, boolean toDetail) {
        private static final int LEFT_START = 105;
        private static final int RIGHT_START = 120;
        private static final int STAGGER = 55;
        private static final int CARD_RISE = 380;
        /** 对角线倍率：RIM 层宽 1040，1040×2.4=2496 > 1920×1080 对角线 2203，可铺满四角。 */
        public static final float COVER_SCALE = 2.4f;

        public float backdrop() {
            if (exit < 0) return easeOut(range(opening, 0, 430));
            return toDetail ? 1 : 1 - easeInOut(range(exit, 0, CLOSE_TOTAL_MS));
        }

        public float blur() {
            if (exit < 0) return easeInOut(range(opening, 0, 430));
            return toDetail ? 1 : 1 - easeInOut(range(exit, 0, CLOSE_TOTAL_MS));
        }

        public float discScale() {
            if (exit < 0) {
                return 0.78f + 0.22f * easeOut(range(opening, 0, 760));
            }
            if (toDetail) {
                float charge = easeOut(range(exit, 0, 140));
                float fill = easeIn(range(exit, 140, 860));
                return 1 + 0.06f * charge + (COVER_SCALE - 1f) * fill;
            }
            float grow = easeOut(range(exit, 0, 140));
            float shrink = easeIn(range(exit, 140, 520));
            return 1 + 0.06f * grow - 0.45f * shrink;
        }

        public float discTilt() {
            if (exit < 0) {
                return 40 * (1 - easeInOut(range(opening, 0, 760)));
            }
            return toDetail ? 0 : 52 * easeInOut(range(exit, 140, 520));
        }

        public float discAlpha() {
            if (exit < 0) return easeOut(range(opening, 0, 140));
            return toDetail ? 1 : 1 - easeIn(range(exit, 300, 560));
        }

        public float discSharpness() {
            if (exit < 0) return easeInOut(range(opening, 120, 760));
            return toDetail ? 1 : 1 - easeInOut(range(exit, 140, 520));
        }

        public float contentAlpha() {
            if (exit < 0) return easeInOut(range(opening, 200, 700));
            return toDetail ? 1 : 1 - range(exit, 140, 420);
        }

        /** 转场黑场：860ms 前压满，杜绝四角外露；普通关闭不启用。 */
        public float blackout() {
            return exit >= 0 && toDetail ? easeInOut(range(exit, 620, 860)) : 0;
        }

        public float cardAlpha(int order) {
            if (exit >= 0 && toDetail) return 1 - easeOut(range(exit, 0, 260));
            float start = LEFT_START + order * STAGGER;
            return easeOut(range(opening, start, start + CARD_RISE))
                    * (exit < 0 ? 1 : 1 - easeIn(range(exit, 40 + order * 30, 240 + order * 30)));
        }

        public float cardSlide(int order) {
            if (exit >= 0 && toDetail) return 0;
            float start = LEFT_START + order * STAGGER;
            return exit < 0 ? (1 - easeOut(range(opening, start, start + CARD_RISE))) * 300
                    : easeIn(range(exit, 40 + order * 30, 260 + order * 30)) * 320;
        }

        public float tileAlpha(int order) {
            if (exit >= 0 && toDetail) return 1 - easeOut(range(exit, 0, 260));
            int capped = Math.min(order, 8);
            float start = RIGHT_START + capped * STAGGER;
            return easeOut(range(opening, start, start + CARD_RISE))
                    * (exit < 0 ? 1 : 1 - easeIn(range(exit, 55 + capped * 30, 255 + capped * 30)));
        }

        public float tileSlide(int order) {
            if (exit >= 0 && toDetail) return 0;
            int capped = Math.min(order, 8);
            float start = RIGHT_START + capped * STAGGER;
            return exit < 0 ? -(1 - easeOut(range(opening, start, start + CARD_RISE))) * 260
                    : -easeIn(range(exit, 55 + capped * 30, 275 + capped * 30)) * 300;
        }

        public float badgeAlpha() {
            return controlsAlpha();
        }

        public float controlsAlpha() {
            if (exit < 0) return easeOut(range(opening, 180, 540));
            if (toDetail) return 1 - easeOut(range(exit, 0, 260));
            return 1 - easeIn(range(exit, 0, 300));
        }

        public float exitDrift() {
            if (exit < 0 || toDetail) return 0;
            return 170 * easeIn(range(exit, 0, 320));
        }

        /** 完全关闭：整体向右侧回卷（120–720ms 滑出一个屏宽）。 */
        public float exitRoll() {
            if (exit < 0 || toDetail) return 0;
            return 1920 * easeIn(range(exit, 120, CLOSE_TOTAL_MS));
        }

        public float resumeAlpha() {
            if (exit < 0) return range(opening, 235, 430);
            if (toDetail) return 1 - easeOut(range(exit, 0, 260));
            return 1 - easeIn(range(exit, 0, 300));
        }

        public float resumeSlide() {
            if (exit < 0) return (1 - easeOut(range(opening, 235, 825))) * 150;
            if (toDetail) return 0;
            return easeIn(range(exit, 0, 320)) * 220;
        }
    }

    public static float range(float value, float start, float end) {
        return Math.clamp((value - start) / (end - start), 0, 1);
    }

    public static float easeOut(float value) {
        return 1 - (float) Math.pow(1 - value, 3);
    }

    public static float easeIn(float value) {
        return value * value * value;
    }

    public static float easeInOut(float value) {
        return value * value * (3 - 2 * value);
    }
}
