package io.wifi.starrailexpress.client.gui.endfield.menu.input;

public final class PointerGesture {
    private String captured;
    private double startX;
    private double startY;
    private boolean dragging;
    private boolean down;

    public void begin(String id, double x, double y) {
        captured = id;
        startX = x;
        startY = y;
        dragging = false;
        down = true;
    }

    public boolean move(double x, double y, double threshold) {
        if (down && Math.hypot(x - startX, y - startY) > threshold) {
            dragging = true;
        }
        return dragging;
    }

    public String release(String hovered) {
        String result = down && !dragging && captured != null && captured.equals(hovered) ? captured : null;
        cancel();
        return result;
    }

    public void cancel() {
        down = false;
        dragging = false;
        captured = null;
    }

    public boolean pressed(String id) {
        return down && !dragging && id.equals(captured);
    }

    public boolean down() {
        return down;
    }
}
