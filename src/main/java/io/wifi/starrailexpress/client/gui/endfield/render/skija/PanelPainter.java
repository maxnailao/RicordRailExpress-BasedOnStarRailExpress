package io.wifi.starrailexpress.client.gui.endfield.render.skija;

import io.wifi.starrailexpress.client.gui.endfield.menu.layout.MenuLayout.Style;
import io.wifi.starrailexpress.client.gui.endfield.menu.layout.DiscGeometry;
import io.wifi.starrailexpress.client.gui.endfield.menu.model.MenuAction;
import io.wifi.starrailexpress.client.gui.endfield.menu.model.MenuEntry;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Matrix33;
import io.github.humbleui.skija.Path;
import io.github.humbleui.skija.PathBuilder;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.MaskFilter;
import io.github.humbleui.skija.FilterBlurMode;
import io.github.humbleui.types.Rect;
import io.github.humbleui.types.RRect;

import static io.wifi.starrailexpress.client.gui.endfield.render.skija.ArtCanvas.*;

public final class PanelPainter {
    private final SkijaResources resources;

    public PanelPainter(SkijaResources resources) {
        this.resources = resources;
    }

    public void button(Canvas canvas, MenuEntry entry, Style style, float width, float height, boolean highlighted) {
        boolean resume = style == Style.RESUME || style == Style.RESUME_ARC;
        boolean curved = style == Style.RESUME_ARC;
        boolean power = entry.action() == MenuAction.DISCONNECT;
        try (ArtCanvas art = new ArtCanvas(canvas, resources);
             Path face = face(entry, style, width, height);
             Path shadow = face.makeOffset(curved ? 0 : style == Style.TILE && width < 320 ? -12 : 8, curved ? 5 : 10);
             Path inset = face.makeTransform(new Matrix33(0.92f, 0, width * 0.04f, 0, 0.87f, height * 0.065f, 0, 0, 1))) {
            canvas.save();
            if (curved) {
                canvas.clipPath(face, true);
            }
            art.path(shadow, 0x3ae5ede4, 0);
            art.path(shadow, 0x70d8ded4, 1.5f);
            canvas.restore();
            int base = resume ? (highlighted ? 0xfffbfa8d : YELLOW)
                    : power ? (highlighted ? 0xff633c3b : 0xff382c2b)
                    : highlighted ? 0xfff5f6ec : 0xffbbbeb4;
            canvas.save();
            canvas.clipPath(face, true);
            art.gradient(0, 0, width, height, 0, base, resume ? 0xffb5a62f : power ? 0xff221e1e : 0xff8b9389);
            if (resume || power) {
                for (int x = -100; x < width + height; x += 16) {
                    art.line(x, height, x + height, 0, power ? 0x42fa6e62 : 0x18525320, power ? 5 : 1);
                }
            } else {
                for (int line = 0; line < 12; line++) {
                    art.arc(width * 0.78f, -40, 55 + line * 13, 45, 170, 0x13404739, 1);
                }
                art.path(inset, 0x55f9f9ef, 2);
                art.line(20, height - 30, width - 34, height - 30, 0x22404b3c, 1);
            }
            canvas.restore();
            art.path(face, highlighted ? 0xfff5f7d2 : 0x99f4f5e9, highlighted ? 2.5f : 1.3f);
            int ink = power ? 0xfff8ddd4 : INK;
            Rect content = face.computeTightBounds();
            float centerX = (content.getLeft() + content.getRight()) / 2;
            float centerY = (content.getTop() + content.getBottom()) / 2;
            if (style == Style.POWER) {
                art.icon(entry.action().icon(), centerX - 22, centerY - 22, 44, ink);
            } else if (resume) {
                if (curved) {
                    centerY = content.getTop() + content.getHeight() * 0.42f;
                }
                float textSize = curved ? 36 : 32;
                float iconSize = curved ? 34 : 30;
                float gap = 18;
                float textWidth = art.textBounds(entry.label(), textSize, false, width - 180).getWidth();
                float groupWidth = iconSize + gap + textWidth;
                float startX = centerX - groupWidth / 2;
                art.icon("play", startX, centerY - iconSize / 2, iconSize, ink);
                art.centeredAt(entry.label(), startX + iconSize + gap + textWidth / 2, centerY, textSize, ink, false, width - 180);
                float shortcutX = curved ? centerX + 208 : content.getRight() - 44;
                art.centeredAt("ESC", shortcutX, centerY, 21, 0x99545424, true, 55);
            } else if (width > 320) {
                art.icon(entry.action().icon(), content.getLeft() + 20, centerY - 21.5f, 43, ink);
                Rect text = art.textBounds(entry.label(), 23, false, width - 112);
                art.centeredAt(entry.label(), content.getLeft() + 83 + text.getWidth() / 2, centerY,
                        23, ink, false, width - 112);
            } else {
                float size = style == Style.CARD ? 55 : 48;
                float textSize = style == Style.CARD ? 24 : 20;
                float textHeight = art.textBounds(entry.label(), textSize, false, width - 32).getHeight();
                float top = centerY - (size + 11 + textHeight) / 2;
                art.icon(entry.action().icon(), centerX - size / 2, top, size, ink);
                art.centeredAt(entry.label(), centerX, top + size + 11 + textHeight / 2,
                        textSize, ink, false, width - 32);
            }
            if (highlighted) {
                art.rect(0, height / 2 - 14, 4, 28, resume ? INK : YELLOW);
            }
        }
    }

    public void fog(Canvas canvas, MenuEntry entry, Style style, float width, float height, int padding) {
        canvas.save();
        canvas.translate(padding, padding);
        try (Path shape = face(entry, style, width, height);
             MaskFilter blur = MaskFilter.makeBlur(FilterBlurMode.NORMAL, style == Style.CARD ? 24 : 28);
             Paint paint = new Paint().setAntiAlias(true).setColor(0x58ffffff).setMaskFilter(blur)) {
            canvas.drawPath(shape, paint);
        }
        canvas.restore();
    }

    private Path face(MenuEntry entry, Style style, float width, float height) {
        if (style == Style.RESUME_ARC) {
            float radius = DiscGeometry.RESUME_RADIUS;
            float chord = DiscGeometry.RESUME_CHORD;
            float start = (float) Math.toDegrees(Math.asin(chord / radius));
            try (PathBuilder builder = new PathBuilder()) {
                return builder.addArc(Rect.makeXYWH(width / 2 - radius, -chord - radius, radius * 2, radius * 2),
                        start, 180 - start * 2).closePath().detach();
            }
        }
        if (style != Style.CARD) {
            float left = style == Style.TILE && width < 320 ? 15 : 3;
            return Path.makeRRect(RRect.makeXYWH(left, 1, width - 18, height - 18, 10));
        }
        int index = switch (entry.action()) {
            case ADVANCEMENTS -> 0;
            case STATISTICS -> 1;
            case OPTIONS -> 2;
            default -> 3;
        };
        Path source = resources.cardShape(index);
        Rect bounds = source.getBounds();
        float sx = (width - 16) / bounds.getWidth();
        float sy = (height - 18) / bounds.getHeight();
        return source.makeTransform(new Matrix33(sx, 0, 3 - bounds.getLeft() * sx,
                0, sy, 1 - bounds.getTop() * sy, 0, 0, 1));
    }

    public void close(Canvas canvas) {
        try (ArtCanvas art = new ArtCanvas(canvas, resources)) {
            art.disc(32, 32, 26, 0xa6d8dfd3);
            art.circle(32, 32, 30, 0x70ecf3e7, 1.5f);
            art.icon("x", 18, 18, 28, INK);
        }
    }
}
