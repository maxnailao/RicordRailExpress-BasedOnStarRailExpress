package io.wifi.starrailexpress.client.gui.endfield.render.skija;

import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Path;
import io.github.humbleui.skija.PathBuilder;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Shader;
import io.github.humbleui.types.Rect;

import static io.wifi.starrailexpress.client.gui.endfield.render.skija.ArtCanvas.*;

public final class DiscPainter {
    private final SkijaResources resources;

    public DiscPainter(SkijaResources resources) {
        this.resources = resources;
    }

    public void rim(Canvas canvas) {
        try (ArtCanvas art = new ArtCanvas(canvas, resources)) {
            art.circle(520, 520, 473, 0xff303a38, 50);
            art.circle(520, 520, 497, 0xff67716a, 1.3f);
            art.circle(520, 520, 448, 0xff58675e, 1.4f);
            for (int ring = 0; ring < 8; ring++) {
                art.circle(520, 520, 463 + ring * 3, 0x20757c72, 1);
            }
            float[][] segments = {{-40.5f, 30.5f}, {-4, 48}, {138, 30}, {174, 30}, {210, 24}};
            for (float[] segment : segments) {
                art.arc(520, 520, 480, segment[0], segment[1], 0xf2eef0e6, 8);
            }
            for (int i = 0; i < 120; i++) {
                double angle = i * Math.PI / 60;
                float radius = i % 5 == 0 ? 437 : 441;
                art.line(520 + (float) Math.cos(angle) * radius, 520 + (float) Math.sin(angle) * radius,
                        520 + (float) Math.cos(angle) * 445, 520 + (float) Math.sin(angle) * 445,
                        i % 5 == 0 ? 0x787b897c : 0x284d6257, 1);
            }
            for (int i = 0; i < 3; i++) {
                double angle = Math.toRadians(-121 + i * 122);
                float x = 520 + (float) Math.cos(angle) * 495;
                float y = 520 + (float) Math.sin(angle) * 495;
                try (PathBuilder builder = new PathBuilder();
                     Path path = builder.moveTo(x, y - 16).lineTo(x - 8, y + 14).lineTo(x + 8, y + 14).closePath().detach()) {
                    art.path(path, YELLOW, 1.5f);
                }
            }
        }
    }

    public void base(Canvas canvas) {
        try (ArtCanvas art = new ArtCanvas(canvas, resources)) {
            art.disc(520, 520, 508, 0xb6090d0f);
            art.circle(520, 520, 498, 0xff242c2d, 2);
        }
    }

    public void amber(Canvas canvas) {
        try (ArtCanvas art = new ArtCanvas(canvas, resources)) {
            art.circle(520, 520, 466, 0xddc48b4f, 1.3f);
            art.circle(520, 520, 468, 0x406e744b, 1);
        }
    }

    public void wall(Canvas canvas, boolean inside) {
        int[] colors = inside ? new int[]{0xff62706b, 0xff35433f, 0xff182221, 0xff0b1113}
                : new int[]{0xff57615b, 0xff34423e, 0xff202c2a, 0xff101719};
        try (Paint paint = new Paint().setAntiAlias(true);
             Shader vertical = Shader.makeLinearGradient(0, 0, 0, 128, colors);
             Shader light = Shader.makeLinearGradient(0, 0, 1024, 0,
                     new int[]{0x00121d19, 0x2ce1f0dc, 0x00121d19, 0x1dc0dccf, 0x00121d19})) {
            canvas.drawRect(Rect.makeWH(1024, 128), paint.setShader(vertical));
            canvas.drawRect(Rect.makeWH(1024, 128), paint.setShader(light));
        }
        try (ArtCanvas art = new ArtCanvas(canvas, resources)) {
            for (int i = 12; i < 128; i += 16) {
                art.line(0, i, 1024, i, 0x196c8077, 0.7f);
            }
        }
    }

    public void well(Canvas canvas) {
        try (ArtCanvas art = new ArtCanvas(canvas, resources); PathBuilder builder = new PathBuilder();
             Path clip = builder.addCircle(460, 460, 447).detach()) {
            canvas.save();
            canvas.clipPath(clip, true);
            art.disc(460, 460, 447, 0xf20b1012);
            for (int x = 0; x < 920; x += 58) {
                art.line(x, 0, x, 920, 0x0b9abbb9, 1);
                art.line(0, x, 920, x, 0x0b9abbb9, 1);
            }
            for (int y = 0; y < 920; y += 4) {
                art.line(0, y, 920, y, 0x06ffffff, 1);
            }
            art.circle(460, 460, 210, 0x103f766e, 1);
            art.circle(460, 460, 280, 0x103f766e, 1);
            canvas.restore();
        }
    }

    public void reticle(Canvas canvas) {
        try (ArtCanvas art = new ArtCanvas(canvas, resources)) {
            for (int i = 0; i < 96; i++) {
                double angle = i * Math.PI / 48;
                art.disc(520 + (float) Math.cos(angle) * 420, 520 + (float) Math.sin(angle) * 420, 2, 0x90757d76);
            }
            art.arc(520, 520, 300, 0, 82, MINT, 2);
            art.arc(520, 520, 300, 180, 82, MINT, 2);
            art.line(490, 520, 550, 520, MINT, 2);
            art.line(520, 490, 520, 550, MINT, 2);
        }
    }

    public void backdrop(Canvas canvas) {
        try (ArtCanvas art = new ArtCanvas(canvas, resources)) {
            art.circle(960, 540, 735, 0x244b6e64, 1);
            art.circle(960, 540, 855, 0x244b6e64, 1);
            art.line(155, 970, 1580, 40, 0x1aafc0ad, 1);
            int[][] crosses = {{200, 116}, {512, 96}, {1260, 150}, {1760, 330}, {130, 800}, {1410, 952}, {1170, 260}};
            for (int[] point : crosses) {
                art.line(point[0] - 9, point[1], point[0] + 9, point[1], 0x65e9eee1, 1.3f);
                art.line(point[0], point[1] - 9, point[0], point[1] + 9, 0x65e9eee1, 1.3f);
            }
            for (int card = 0; card < 4; card++) {
                float y = 284 + card * 142;
                for (int strand = 0; strand < 4; strand++) {
                    float offset = strand * 5;
                    try (PathBuilder builder = new PathBuilder();
                         Path cable = builder.moveTo(385, y + offset).cubicTo(440, y + offset, 445, 330 + card * 124 + offset,
                                524, 330 + card * 124 + offset).detach()) {
                        art.path(cable, 0x708c9b91, 1.7f);
                    }
                }
            }
        }
    }
}
