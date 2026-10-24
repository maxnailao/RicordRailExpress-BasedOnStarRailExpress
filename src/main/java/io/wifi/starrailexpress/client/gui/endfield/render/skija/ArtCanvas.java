package io.wifi.starrailexpress.client.gui.endfield.render.skija;

import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.Path;
import io.github.humbleui.skija.Shader;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;

public final class ArtCanvas implements AutoCloseable {
    public static final int INK = 0xff303531;
    public static final int PAPER = 0xffeeeee6;
    public static final int YELLOW = 0xfff1ed48;
    public static final int MINT = 0xff9fe5cf;

    private final Canvas canvas;
    private final SkijaResources resources;
    private final Paint paint = new Paint().setAntiAlias(true);

    public ArtCanvas(Canvas canvas, SkijaResources resources) {
        this.canvas = canvas;
        this.resources = resources;
    }

    public void rect(float x, float y, float width, float height, int color) {
        canvas.drawRect(Rect.makeXYWH(x, y, width, height), fill(color));
    }

    public void round(float x, float y, float width, float height, float radius, int color) {
        canvas.drawRRect(RRect.makeXYWH(x, y, width, height, radius), fill(color));
    }

    public void rim(float x, float y, float width, float height, float radius, int color, float stroke) {
        canvas.drawRRect(RRect.makeXYWH(x, y, width, height, radius), stroke(color, stroke));
    }

    public void gradient(float x, float y, float width, float height, float radius, int top, int bottom) {
        try (Shader shader = Shader.makeLinearGradient(x, y, x + width, y + height, new int[]{top, bottom})) {
            canvas.drawRRect(RRect.makeXYWH(x, y, width, height, radius), fill(top).setShader(shader));
            paint.setShader(null);
        }
    }

    public void line(float x0, float y0, float x1, float y1, int color, float width) {
        canvas.drawLine(x0, y0, x1, y1, stroke(color, width));
    }

    public void circle(float x, float y, float radius, int color, float width) {
        canvas.drawCircle(x, y, radius, stroke(color, width));
    }

    public void disc(float x, float y, float radius, int color) {
        canvas.drawCircle(x, y, radius, fill(color));
    }

    public void arc(float x, float y, float radius, float start, float sweep, int color, float width) {
        canvas.drawArc(x - radius, y - radius, x + radius, y + radius, start, sweep, false, stroke(color, width));
    }

    public void text(String text, float x, float baseline, float size, int color, boolean display, float maxWidth) {
        Font font = resources.font(size, display);
        String fitted = fit(text, font, maxWidth);
        canvas.drawString(fitted, x, baseline, font, fill(color));
    }

    public void centered(String text, float x, float baseline, float size, int color, boolean display, float maxWidth) {
        Font font = resources.font(size, display);
        String fitted = fit(text, font, maxWidth);
        canvas.drawString(fitted, x - font.measureTextWidth(fitted) / 2, baseline, font, fill(color));
    }

    public Rect textBounds(String text, float size, boolean display, float maxWidth) {
        Font font = resources.font(size, display);
        return font.measureText(fit(text, font, maxWidth));
    }

    public void centeredAt(String text, float x, float y, float size, int color, boolean display, float maxWidth) {
        Font font = resources.font(size, display);
        String fitted = fit(text, font, maxWidth);
        Rect bounds = font.measureText(fitted);
        canvas.drawString(fitted, x - (bounds.getLeft() + bounds.getRight()) / 2,
                y - (bounds.getTop() + bounds.getBottom()) / 2, font, fill(color));
    }

    public void icon(String name, float x, float y, float size, int color) {
        resources.icon(canvas, name, x, y, size, color);
    }

    public void path(Path path, int color, float strokeWidth) {
        canvas.drawPath(path, strokeWidth <= 0 ? fill(color) : stroke(color, strokeWidth));
    }

    private String fit(String text, Font font, float width) {
        if (font.measureTextWidth(text) <= width) {
            return text;
        }
        int end = text.length();
        while (end > 0) {
            end = text.offsetByCodePoints(end, -1);
            if (font.measureTextWidth(text.substring(0, end) + "…") <= width) {
                break;
            }
        }
        return text.substring(0, end) + "…";
    }

    private Paint fill(int color) {
        return paint.setMode(PaintMode.FILL).setColor(color).setShader(null);
    }

    private Paint stroke(int color, float width) {
        return paint.setMode(PaintMode.STROKE).setStrokeWidth(width).setColor(color).setShader(null);
    }

    @Override
    public void close() {
        paint.close();
    }
}
