package io.wifi.starrailexpress.client.gui.endfield.menu.input;

public final class ScrollMotion {
    private float position;
    private float velocity;
    private float limit;
    private float springTarget = Float.NaN;

    public void setLimit(float limit) {
        this.limit = limit;
        position = Math.clamp(position, 0, limit);
        velocity = 0;
        springTarget = Float.NaN;
    }

    public void wheel(double amount) {
        springTarget = Float.NaN;
        velocity -= (float) amount * 720;
    }

    public void drag(float delta, float seconds) {
        springTarget = Float.NaN;
        float resistance = position < 0 || position > limit ? 0.3f : 1;
        position -= delta * resistance;
        velocity = Math.clamp(-delta / Math.max(seconds, 0.008f) * resistance, -2400, 2400);
    }

    public void update(float seconds, boolean dragging) {
        if (dragging || limit == 0) {
            return;
        }
        float dt = Math.clamp(seconds, 0, 0.033f);
        if (Float.isNaN(springTarget) && (position < 0 || position > limit)) {
            springTarget = Math.clamp(position, 0, limit);
        }
        if (!Float.isNaN(springTarget)) {
            float offset = position - springTarget;
            float decay = (float) Math.exp(-16 * dt);
            float intermediate = velocity + 16 * offset;
            position = springTarget + (offset + intermediate * dt) * decay;
            velocity = (velocity - 16 * intermediate * dt) * decay;
            if (Math.abs(velocity) < 0.2f && Math.abs(position - springTarget) < 0.2f) {
                position = springTarget;
                velocity = 0;
                springTarget = Float.NaN;
            }
        } else {
            velocity *= (float) Math.exp(-7 * dt);
            position += velocity * dt;
        }
    }

    public void reveal(float top, float bottom, float viewportTop, float viewportHeight) {
        if (top - position < viewportTop) {
            position = Math.max(0, top - viewportTop);
        } else if (bottom - position > viewportTop + viewportHeight) {
            position = Math.min(limit, bottom - viewportTop - viewportHeight);
        }
        velocity = 0;
        springTarget = Float.NaN;
    }

    public void stop() {
        velocity = 0;
    }

    public float position() {
        return position;
    }
}
