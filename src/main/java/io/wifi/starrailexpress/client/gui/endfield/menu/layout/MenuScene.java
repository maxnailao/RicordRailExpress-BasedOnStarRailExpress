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
        return layout.slots().stream().map(slot -> {
            float alpha = switch (slot.style()) {
                case CARD -> frame.cardAlpha(slot.order());
                case TILE -> frame.tileAlpha(slot.order());
                case POWER -> frame.badgeAlpha();
                case RESUME, RESUME_ARC -> frame.controlsAlpha();
            };
            float dx = slot.style() == Style.CARD ? frame.cardSlide(slot.order())
                    : slot.style() == Style.TILE ? -frame.cardSlide(1) : 0;
            float depth = switch (slot.style()) {
                case CARD -> 34;
                case RESUME, RESUME_ARC -> DiscGeometry.RESUME_DEPTH;
                default -> 28;
            };
            Box box = slot.box().translate(dx, slot.scrollable() ? -scroll : 0);
            Plane plane = slot.style() == Style.RESUME_ARC ? DiscGeometry.plane(frame, depth) : Plane.at(depth);
            return new Button(slot, box, plane, alpha);
        }).toList();
    }
}
