package io.wifi.starrailexpress.client.gui.endfield.menu.layout;

import io.wifi.starrailexpress.client.gui.endfield.menu.animation.MenuTimeline.Frame;
import io.wifi.starrailexpress.client.gui.endfield.menu.layout.MenuLayout.Slot;
import io.wifi.starrailexpress.client.gui.endfield.menu.layout.MenuLayout.Style;
import io.wifi.starrailexpress.client.gui.endfield.menu.layout.SceneProjection.Plane;
import java.util.List;

public final class MenuScene {
    public record Button(Slot slot, Box box, Plane plane, float alpha) {
    }

    private MenuScene() {
    }

    public static List<Button> buttons(MenuLayout layout, Frame frame, float scroll) {
        float drift = frame.exitDrift();
        float roll = frame.exitRoll();
        return layout.slots().stream().map(slot -> {
            float alpha = switch (slot.style()) {
                case CARD -> frame.cardAlpha(slot.order());
                case TILE -> frame.tileAlpha(slot.order());
                case POWER -> frame.badgeAlpha();
                case RESUME, RESUME_ARC -> frame.resumeAlpha();
            };
            float dx = switch (slot.style()) {
                case CARD -> frame.cardSlide(slot.order()) + roll * 0.6f;
                case TILE -> frame.tileSlide(slot.order()) + roll * 0.8f;
                case POWER -> -drift + roll;
                default -> roll;
            };
            float dy = switch (slot.style()) {
                case POWER -> drift * 0.7f;
                case RESUME -> frame.resumeSlide();
                default -> 0f;
            };
            float depth = switch (slot.style()) {
                case CARD -> 34;
                case RESUME, RESUME_ARC -> DiscGeometry.RESUME_DEPTH;
                default -> 28;
            };
            Box box = slot.box().translate(dx, dy + (slot.scrollable() ? -scroll : 0));
            Plane plane;
            if (slot.style() == Style.RESUME_ARC) {
                Plane disc = DiscGeometry.plane(frame, depth);
                plane = new Plane(disc.depth(), disc.scale(), disc.tilt(), disc.offsetX(),
                        frame.resumeSlide(), disc.compensateDepth());
            } else {
                plane = Plane.at(depth);
            }
            return new Button(slot, box, plane, alpha);
        }).toList();
    }
}
