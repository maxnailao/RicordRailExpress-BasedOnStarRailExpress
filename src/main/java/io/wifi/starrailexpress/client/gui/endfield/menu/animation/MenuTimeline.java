package io.wifi.starrailexpress.client.gui.endfield.menu.animation;

public final class MenuTimeline {
    public static final float SPEED = 1.2f;
    private final long openedAt;
    private final boolean reducedMotion;
    private long closedAt = -1;

    public MenuTimeline(long now, boolean reducedMotion) {
        openedAt = now;
        this.reducedMotion = reducedMotion;
    }

    public void close(long now) {
        if (!closing()) {
            closedAt = now;
        }
    }

    public boolean closing() {
        return closedAt >= 0;
    }

    public boolean finished(long now) {
        return closing() && (reducedMotion || elapsed(now, closedAt) >= 500);
    }

    public Frame sample(long now) {
        float opening = reducedMotion ? 900 : elapsed(closing() ? closedAt : now, openedAt);
        float exit = !closing() ? -1 : reducedMotion ? 500 : elapsed(now, closedAt);
        return new Frame(opening, exit);
    }

    private static float elapsed(long now, long origin) {
        return (float) ((now - origin) / 1_000_000d * SPEED);
    }

    public record Frame(float opening, float exit) {
        public float backdrop() {
            return exit < 0 ? 1 : 1 - range(exit, 380, 500);
        }

        public float discScale() {
            float scale = 0.02f + 0.98f * easeOut(range(opening, 58, 558));
            return exit < 0 ? scale : scale * (1 - 0.98f * easeInOut(range(exit, 150, 340)));
        }

        public float discTilt() {
            return exit < 0 ? 40 * (1 - easeInOut(range(opening, 234, 382)))
                    : 51.5f * easeInOut(range(exit, 150, 340));
        }

        public float discAlpha() {
            return range(opening, 58, 115) * (exit < 0 ? 1 : 1 - range(exit, 290, 350));
        }

        public float contentAlpha() {
            return range(opening, 355, 510) * (exit < 0 ? 1 : 1 - range(exit, 175, 285));
        }

        public float cardAlpha(int order) {
            float start = switch (order) { case 0 -> 340; case 1 -> 355; case 2 -> 457; default -> 505; };
            return easeOut(range(opening, start, start + 150))
                    * (exit < 0 ? 1 : 1 - range(exit, 50, 150 + Math.min(order, 2) * 50));
        }

        public float cardSlide(int order) {
            return exit < 0 ? (1 - easeOut(range(opening, 340 + order * 45, 890))) * 36
                    : -80 * easeOut(range(exit, 50, 220));
        }

        public float tileAlpha(int order) {
            return range(opening, 370 + Math.min(order, 8) * 25, 470 + Math.min(order, 8) * 25)
                    * (exit < 0 ? 1 : 1 - range(exit, Math.max(35, 140 - order * 18), 250 - Math.min(order, 8) * 15));
        }

        public float badgeAlpha() {
            float alpha = opening < 625 ? range(opening, 565, 605) : opening < 670 ? 0 : range(opening, 670, 710);
            return alpha * (exit < 0 ? 1 : 1 - range(exit, 75, 150));
        }

        public float controlsAlpha() {
            return range(opening, 450, 620) * (exit < 0 ? 1 : 1 - range(exit, 60, 160));
        }
    }

    public static float range(float value, float start, float end) {
        return Math.clamp((value - start) / (end - start), 0, 1);
    }

    public static float easeOut(float value) {
        return 1 - (float) Math.pow(1 - value, 3);
    }

    public static float easeInOut(float value) {
        return value * value * (3 - 2 * value);
    }
}
