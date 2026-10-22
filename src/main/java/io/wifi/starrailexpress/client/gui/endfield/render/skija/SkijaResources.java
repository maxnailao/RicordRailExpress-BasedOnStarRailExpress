package io.wifi.starrailexpress.client.gui.endfield.render.skija;

import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Data;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontEdging;
import io.github.humbleui.skija.FontMgr;
import io.github.humbleui.skija.FontVariation;
import io.github.humbleui.skija.Typeface;
import io.github.humbleui.skija.Path;
import io.github.humbleui.skija.svg.SVGDOM;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class SkijaResources implements AutoCloseable {
    @FunctionalInterface
    public interface Source {
        byte[] read(String path) throws IOException;
    }

    private record FontKey(float size, boolean display) {
    }

    private record IconKey(String name, int color) {
    }

    private final Source source;
    private final Typeface textFace;
    private final Typeface displayFace;
    private final Map<FontKey, Font> fonts = new HashMap<>();
    private final Map<IconKey, SVGDOM> icons = new HashMap<>();
    private final Map<Integer, Path> cardShapes = new HashMap<>();

    public SkijaResources(Source source) {
        this.source = source;
        try (Typeface variableFace = loadFace("fonts/notosanssc.ttf")) {
            textFace = variableFace.makeClone(new FontVariation("wght", 550));
        }
        try {
            displayFace = loadFace("fonts/rajdhani-semibold.ttf");
        } catch (RuntimeException | LinkageError failure) {
            textFace.close();
            throw failure;
        }
    }

    public Font font(float size, boolean display) {
        return fonts.computeIfAbsent(new FontKey(size, display), key ->
                new Font(display ? displayFace : textFace, size).setEdging(FontEdging.ANTI_ALIAS));
    }

    public Path cardShape(int index) {
        return cardShapes.computeIfAbsent(index, key -> Path.makeFromSVGString(
                new String(read("shapes/card-" + key + ".path"), StandardCharsets.UTF_8).trim()));
    }

    public void icon(Canvas canvas, String name, float x, float y, float size, int color) {
        SVGDOM icon = icons.computeIfAbsent(new IconKey(name, color), key -> {
            String svg = new String(read("icons/" + name + ".svg"), StandardCharsets.UTF_8)
                    .replace("#ffffff", String.format(java.util.Locale.ROOT, "#%06x", color & 0xffffff));
            try (Data data = Data.makeFromBytes(svg.getBytes(StandardCharsets.UTF_8))) {
                return new SVGDOM(data);
            }
        });
        int saved = canvas.save();
        canvas.translate(x, y);
        icon.setContainerSize(size, size).render(canvas);
        canvas.restoreToCount(saved);
    }

    private Typeface loadFace(String path) {
        try (Data data = Data.makeFromBytes(read(path))) {
            Typeface face = FontMgr.getDefault().makeFromData(data);
            if (face == null) {
                throw new IllegalStateException("Could not load font: " + path);
            }
            return face;
        }
    }

    private byte[] read(String path) {
        try {
            return source.read(path);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not load menu resource: " + path, exception);
        }
    }

    @Override
    public void close() {
        icons.values().forEach(SVGDOM::close);
        cardShapes.values().forEach(Path::close);
        fonts.values().forEach(Font::close);
        icons.clear();
        cardShapes.clear();
        fonts.clear();
        displayFace.close();
        textFace.close();
    }
}
