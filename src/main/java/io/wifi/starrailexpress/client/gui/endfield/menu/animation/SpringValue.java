package io.wifi.starrailexpress.client.gui.endfield.menu.animation;

public final class SpringValue {
    private float value;
    private float velocity;

    public float update(float target, float seconds, float frequency) {
        float dt = Math.clamp(seconds, 0, 0.05f);
        float offset = value - target;
        float decay = (float) Math.exp(-frequency * dt);
        float intermediate = velocity + frequency * offset;
        value = target + (offset + intermediate * dt) * decay;
        velocity = (velocity - frequency * intermediate * dt) * decay;
        if (Math.abs(value - target) < 0.0001f && Math.abs(velocity) < 0.0001f) {
            value = target;
            velocity = 0;
        }
        return value;
    }

    public float value() {
        return value;
    }
}
