package io.wifi.starrailexpress.client.gui.endfield.menu.layout;

import io.wifi.starrailexpress.client.gui.endfield.menu.animation.MenuTimeline.Frame;
import io.wifi.starrailexpress.client.gui.endfield.menu.layout.SceneProjection.Plane;

public final class DiscGeometry {
    public static final float CENTER_X = 960;
    public static final float CENTER_Y = 540;
    public static final float OUTER_RADIUS = 498;
    public static final float INNER_RADIUS = 447;
    public static final float TOP_DEPTH = 50;
    public static final float FLOOR_DEPTH = -130;
    public static final float BASE_DEPTH = -112;
    public static final float RESUME_RADIUS = INNER_RADIUS - 22;
    public static final float RESUME_CHORD = 278;
    public static final float RESUME_DEPTH = 26;
    public static final Box RIM = new Box(440, 20, 1040, 1040);
    public static final Box WELL = new Box(500, 80, 920, 920);

    private DiscGeometry() {
    }

    public static Plane plane(Frame frame, float depth) {
        return new Plane(depth, frame.discScale(), frame.discTilt(), 0, 0, false);
    }

    public static Box resumeBounds() {
        float halfWidth = (float) Math.sqrt(RESUME_RADIUS * RESUME_RADIUS - RESUME_CHORD * RESUME_CHORD);
        return new Box(CENTER_X - halfWidth, CENTER_Y + RESUME_CHORD,
                halfWidth * 2, RESUME_RADIUS - RESUME_CHORD);
    }
}
