package io.wifi.starrailexpress.client.gui.endfield.render.skija;

import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Path;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.types.Rect;

public final class MapPainter {
    private static final float SIZE = 920;
    private static final float CENTER = SIZE / 2;
    private static final float RADIUS = SIZE / 2 - 13;

    private final SkijaResources resources;

    public MapPainter(SkijaResources resources) {
        this.resources = resources;
    }

    public void map(Canvas canvas, byte[] encoded) {
        try (Path circle = Path.makeOval(Rect.makeXYWH(CENTER - RADIUS, CENTER - RADIUS,
                RADIUS * 2, RADIUS * 2))) {
            canvas.save();
            try {
                canvas.clipPath(circle, true);
                if (encoded != null && encoded.length > 0) {
                    drawBackground(canvas, encoded);
                } else {
                    drawFallback(canvas);
                }
                drawAtmosphere(canvas);
            } finally {
                canvas.restore();
            }
        }
    }

    private static void drawBackground(Canvas canvas, byte[] encoded) {
        try (Image image = Image.makeFromEncoded(encoded);
             Paint paint = new Paint().setAntiAlias(true)) {
            int width = image.getImageInfo().getWidth();
            int height = image.getImageInfo().getHeight();
            if (width <= 0 || height <= 0) {
                return;
            }
            float scale = Math.max(SIZE / width, SIZE / height);
            float sourceWidth = SIZE / scale;
            float sourceHeight = SIZE / scale;
            float sourceX = (width - sourceWidth) / 2;
            float sourceY = (height - sourceHeight) / 2;
            Rect source = Rect.makeXYWH(sourceX, sourceY, sourceWidth, sourceHeight);
            Rect destination = Rect.makeXYWH(0, 0, SIZE, SIZE);
            canvas.drawImageRect(image, source, destination, SamplingMode.LINEAR, paint, true);
        }
    }

    private void drawFallback(Canvas canvas) {
        try (ArtCanvas art = new ArtCanvas(canvas, resources)) {
            art.disc(CENTER, CENTER, RADIUS, 0xf20b1012);
            for (int line = 0; line < SIZE; line += 46) {
                art.line(line, 0, line, SIZE, 0x108ac2bb, 1);
                art.line(0, line, SIZE, line, 0x108ac2bb, 1);
            }
        }
    }

    private void drawAtmosphere(Canvas canvas) {
        try (ArtCanvas art = new ArtCanvas(canvas, resources)) {
            art.rect(0, 0, SIZE, SIZE, 0x3203090a);
            for (int y = 0; y < SIZE; y += 4) {
                art.line(0, y, SIZE, y, 0x08d8efe8, 1);
            }
            art.circle(CENTER, CENTER, RADIUS - 1, 0x5a9fe5cf, 2);
        }
    }
}
