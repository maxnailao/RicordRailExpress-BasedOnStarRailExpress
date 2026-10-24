package io.wifi.starrailexpress.client.gui.endfield.menu.layout;

import io.wifi.starrailexpress.client.gui.endfield.menu.model.MenuAction;
import io.wifi.starrailexpress.client.gui.endfield.menu.model.MenuEntry;
import java.util.ArrayList;
import java.util.List;

public record MenuLayout(boolean compact, float width, float height, float scale,
                         float offsetX, float offsetY, List<Slot> slots, Box scrollArea,
                         float maxScroll, Box profile, Box close) {
    public enum Style { CARD, TILE, RESUME, RESUME_ARC, POWER }

    public record Slot(MenuEntry entry, Box box, Style style, int order, boolean scrollable) {
    }

    public MenuLayout {
        slots = List.copyOf(slots);
    }

    public static MenuLayout create(int guiWidth, int guiHeight, int pixelWidth, int pixelHeight,
                                    List<MenuEntry> entries) {
        boolean compact = pixelWidth < 1000 || pixelHeight < 600 || (float) pixelWidth / pixelHeight < 1.35f;
        return compact ? compact(guiWidth, guiHeight, entries) : desktop(guiWidth, guiHeight, entries);
    }

    private static MenuLayout desktop(int width, int height, List<MenuEntry> entries) {
        float scale = Math.min(width / 1920f, height / 1080f);
        List<Slot> slots = new ArrayList<>();
        int primary = 0;
        int secondary = 0;
        float[] leftX = {260, 205, 185, 210};
        for (MenuEntry entry : entries) {
            if (entry.action() == MenuAction.RESUME) {
                slots.add(new Slot(entry, DiscGeometry.resumeBounds(), Style.RESUME_ARC, 0, false));
            } else if (entry.action() == MenuAction.DISCONNECT) {
                slots.add(new Slot(entry, new Box(92, 866, 76, 76), Style.POWER, 0, false));
            } else if (entry.action().isPrimary() && primary < 4) {
                slots.add(new Slot(entry, new Box(leftX[primary], 224 + primary * 142, 278, 148),
                        Style.CARD, primary++, false));
            } else {
                int row = secondary / 2;
                float y = 250 + row * 140;
                float offsetY = y + 72 - 540;
                float x = 965 + (float) Math.sqrt(Math.max(0, 535 * 535 - offsetY * offsetY));
                slots.add(new Slot(entry, new Box(x + secondary % 2 * 150, y, 154, 144),
                        Style.TILE, secondary++, true));
            }
        }
        return new MenuLayout(false, 1920, 1080, scale, (width - 1920 * scale) / 2,
                (height - 1080 * scale) / 2, slots, new Box(1370, 235, 440, 608),
                Math.max(0, ((secondary + 1) / 2) * 140 + 20 - 608),
                new Box(210, 858, 430, 126), new Box(1794, 43, 64, 64));
    }

    private static MenuLayout compact(int width, int height, List<MenuEntry> entries) {
        float scale = width / 800f;
        float designHeight = height / scale;
        float top = designHeight < 570 ? 96 : 194;
        Box area = new Box(28, top, 744, Math.max(64, designHeight - top - 140));
        List<Slot> slots = new ArrayList<>();
        int index = 0;
        for (MenuEntry entry : entries) {
            if (entry.action() == MenuAction.RESUME) {
                slots.add(new Slot(entry, new Box(36, designHeight - 116, 728, 88), Style.RESUME, 0, false));
            } else {
                slots.add(new Slot(entry, new Box(36 + index % 2 * 370, top + 8 + index / 2 * 116, 364, 112),
                        Style.TILE, index++, true));
            }
        }
        return new MenuLayout(true, 800, designHeight, scale, 0, 0, slots, area,
                Math.max(0, ((index + 1) / 2) * 116 + 16 - area.height()),
                new Box(36, 86, 650, 88), new Box(704, 22, 60, 60));
    }

    public Box daysPlayed() {
        return new Box(profile.right() - 302, profile.y() - 45, 300, 44);
    }

    public float localX(double x) {
        return (float) ((x - offsetX) / scale);
    }

    public float localY(double y) {
        return (float) ((y - offsetY) / scale);
    }
}
