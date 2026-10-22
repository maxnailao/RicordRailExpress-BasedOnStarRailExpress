package io.wifi.starrailexpress.client.gui.endfield.menu.layout;

public record Box(float x, float y, float width, float height) {
    public float right() {
        return x + width;
    }

    public float bottom() {
        return y + height;
    }

    public float centerX() {
        return x + width / 2;
    }

    public float centerY() {
        return y + height / 2;
    }

    public boolean contains(double px, double py) {
        return px >= x && px < right() && py >= y && py < bottom();
    }

    public Box translate(float dx, float dy) {
        return new Box(x + dx, y + dy, width, height);
    }

    public Box scaleAroundCenter(float scale) {
        return new Box(centerX() - width * scale / 2, centerY() - height * scale / 2,
                width * scale, height * scale);
    }
}
