package io.wifi.starrailexpress.client.gui.endfield.menu.layout;

public final class SceneProjection {
    private static final float DISTANCE = 1600;
    private final MenuLayout layout;
    private final float yaw;
    private final float pitch;
    private final double sinYaw;
    private final double cosYaw;
    private final double sinPitch;
    private final double cosPitch;
    private final double sinRoll;
    private final double cosRoll;
    private float cachedTilt = Float.NaN;
    private double sinTilt;
    private double cosTilt;

    public record Point(float x, float y) {
    }

    public record Plane(float depth, float scale, float tilt, float offsetX, float offsetY, boolean compensateDepth) {
        public Plane(float depth, float scale, float tilt, float offsetX, float offsetY) {
            this(depth, scale, tilt, offsetX, offsetY, true);
        }

        public static Plane at(float depth) {
            return new Plane(depth, 1, 0, 0, 0);
        }
    }

    public SceneProjection(MenuLayout layout, float yaw, float pitch) {
        this.layout = layout;
        this.yaw = yaw;
        this.pitch = pitch;
        sinYaw = Math.sin(Math.toRadians(yaw));
        cosYaw = Math.cos(Math.toRadians(yaw));
        double pitchRadians = Math.toRadians(20 + pitch);
        sinPitch = Math.sin(pitchRadians);
        cosPitch = Math.cos(pitchRadians);
        double rollRadians = Math.toRadians(-1);
        sinRoll = Math.sin(rollRadians);
        cosRoll = Math.cos(rollRadians);
    }

    public Point project(float x, float y, Plane plane) {
        float[] projected = new float[2];
        project(x, y, plane, projected);
        return new Point(projected[0], projected[1]);
    }

    public void project(float x, float y, Plane plane, float[] output) {
        if (layout.compact()) {
            output[0] = layout.offsetX() + (x + plane.offsetX()) * layout.scale();
            output[1] = layout.offsetY() + (y + plane.offsetY()) * layout.scale();
            return;
        }
        float compensation = plane.compensateDepth() ? 1 - plane.depth() / 1700 : 1;
        double px = (x - 960) * plane.scale() * compensation;
        double py = (y - 540) * plane.scale() * compensation;
        double depth = plane.depth() * (plane.compensateDepth() ? 1 : plane.scale());
        updateTilt(plane.tilt());
        double pz = depth * cosTilt + sinTilt * py;
        py = py * cosTilt - depth * sinTilt;
        double x1 = cosYaw * px + sinYaw * pz;
        double z1 = -sinYaw * px + cosYaw * pz;
        double y1 = cosPitch * py - sinPitch * z1;
        double z2 = sinPitch * py + cosPitch * z1;
        double perspective = DISTANCE / (DISTANCE - z2);
        float sx = (float) ((x1 * cosRoll - y1 * sinRoll) * perspective);
        float sy = (float) ((x1 * sinRoll + y1 * cosRoll) * perspective);
        output[0] = layout.offsetX() + (989 + sx + plane.offsetX()) * layout.scale();
        output[1] = layout.offsetY() + (500 + sy + plane.offsetY()) * layout.scale();
    }

    private void updateTilt(float tilt) {
        if (Float.compare(cachedTilt, tilt) == 0) {
            return;
        }
        cachedTilt = tilt;
        double radians = Math.toRadians(tilt);
        sinTilt = Math.sin(radians);
        cosTilt = Math.cos(radians);
    }

    public boolean contains(Box box, Plane plane, double x, double y) {
        Point[] corners = {
                project(box.x(), box.y(), plane), project(box.right(), box.y(), plane),
                project(box.right(), box.bottom(), plane), project(box.x(), box.bottom(), plane)
        };
        return contains(corners, x, y);
    }

    public boolean containsResume(Plane plane, double x, double y) {
        Point[] points = new Point[65];
        double start = Math.asin(DiscGeometry.RESUME_CHORD / DiscGeometry.RESUME_RADIUS);
        double sweep = Math.PI - 2 * start;
        for (int i = 0; i < points.length; i++) {
            double angle = start + sweep * i / (points.length - 1);
            points[i] = project(DiscGeometry.CENTER_X + (float) Math.cos(angle) * DiscGeometry.RESUME_RADIUS,
                    DiscGeometry.CENTER_Y + (float) Math.sin(angle) * DiscGeometry.RESUME_RADIUS, plane);
        }
        return contains(points, x, y);
    }

    private static boolean contains(Point[] corners, double x, double y) {
        boolean positive = false;
        boolean negative = false;
        for (int i = 0; i < corners.length; i++) {
            Point a = corners[i];
            Point b = corners[(i + 1) % corners.length];
            double cross = (b.x - a.x) * (y - a.y) - (b.y - a.y) * (x - a.x);
            positive |= cross > 0;
            negative |= cross < 0;
        }
        return !(positive && negative);
    }
}
