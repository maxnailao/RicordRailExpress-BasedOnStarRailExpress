package io.wifi.starrailexpress.client.gui.endfield;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ColorAlphaType;
import io.github.humbleui.skija.ColorType;
import io.github.humbleui.skija.ImageInfo;
import io.github.humbleui.skija.Pixmap;
import io.github.humbleui.skija.Surface;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.client.gui.endfield.menu.animation.MenuTimeline.Frame;
import io.wifi.starrailexpress.client.gui.endfield.menu.layout.Box;
import io.wifi.starrailexpress.client.gui.endfield.menu.layout.DiscGeometry;
import io.wifi.starrailexpress.client.gui.endfield.menu.layout.MenuLayout;
import io.wifi.starrailexpress.client.gui.endfield.menu.layout.MenuLayout.Style;
import io.wifi.starrailexpress.client.gui.endfield.menu.layout.MenuScene;
import io.wifi.starrailexpress.client.gui.endfield.menu.layout.SceneProjection;
import io.wifi.starrailexpress.client.gui.endfield.menu.layout.SceneProjection.Plane;
import io.wifi.starrailexpress.client.gui.endfield.menu.layout.SceneProjection.Point;
import io.wifi.starrailexpress.client.gui.endfield.menu.model.MenuEntry;
import io.wifi.starrailexpress.client.gui.endfield.menu.model.PlayerCard;
import io.wifi.starrailexpress.client.gui.endfield.render.skija.DiscPainter;
import io.wifi.starrailexpress.client.gui.endfield.render.skija.MapPainter;
import io.wifi.starrailexpress.client.gui.endfield.render.skija.PanelPainter;
import io.wifi.starrailexpress.client.gui.endfield.render.skija.PlayerPainter;
import io.wifi.starrailexpress.client.gui.endfield.render.skija.SkijaResources;
import io.wifi.starrailexpress.client.gui.screen.mapui.MapUiGraphics;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Matrix4f;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** EndfieldCraft's original layered projection renderer, adapted to 1.21.1. */
public final class EndfieldMenuRenderer implements AutoCloseable {
    private static final int FOG_PADDING = 80;
    private static final int WALL_SEGMENTS = 96;
    private static final int[][] CORNERS = {{0, 0}, {0, 1}, {1, 1}, {1, 0}};
    private static final float[] WALL_COS = new float[WALL_SEGMENTS + 1];
    private static final float[] WALL_SIN = new float[WALL_SEGMENTS + 1];
    private static EndfieldMenuRenderer sharedRenderer;
    private static boolean lifecycleRegistered;
    private static volatile boolean invalidateRequested;

    static {
        for (int i = 0; i <= WALL_SEGMENTS; i++) {
            double radians = Math.toRadians(i * 180d / WALL_SEGMENTS);
            WALL_COS[i] = (float) Math.cos(radians);
            WALL_SIN[i] = (float) Math.sin(radians);
        }
    }

    public record Feedback(float hover, float press, boolean focused) {
        public static final Feedback NONE = new Feedback(0, 0, false);
    }

    private record Layer(ResourceLocation id, DynamicTexture texture, int width, int height) {
    }

    private final Minecraft client;
    private final SkijaResources resources;
    private final DiscPainter disc;
    private final PanelPainter panels;
    private final PlayerPainter playerPainter;
    private final MapPainter mapPainter;
    private final Map<String, Layer> layers = new HashMap<>();
    private final float[] projected = new float[2];
    private String showcaseMapId;
    private PlayerCard preparedPlayer;
    private Map<String, String> preparedButtonLabels = Map.of();
    private boolean closed;

    public EndfieldMenuRenderer(Minecraft client) {
        this.client = client;
        resources = new SkijaResources(path -> {
            var resource = client.getResourceManager().getResourceOrThrow(
                    ResourceLocation.fromNamespaceAndPath("endfieldcraft", path));
            try (var stream = resource.open()) {
                return stream.readAllBytes();
            }
        });
        disc = new DiscPainter(resources);
        panels = new PanelPainter(resources);
        playerPainter = new PlayerPainter(resources);
        mapPainter = new MapPainter(resources);
    }

    public static EndfieldMenuRenderer acquire(Minecraft client) {
        registerLifecycle();
        if (invalidateRequested) {
            // Fonts and SVGs also belong to the old resource set, not just baked textures.
            releaseShared();
        }
        if (sharedRenderer == null || sharedRenderer.closed) {
            sharedRenderer = new EndfieldMenuRenderer(client);
        }
        return sharedRenderer;
    }

    public boolean needsReload() {
        return closed || invalidateRequested;
    }

    public static void releaseShared() {
        if (sharedRenderer != null) {
            sharedRenderer.close();
            sharedRenderer = null;
        }
        invalidateRequested = false;
    }

    public static void registerLifecycle() {
        if (lifecycleRegistered) {
            return;
        }
        lifecycleRegistered = true;
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES)
                .registerReloadListener(new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public ResourceLocation getFabricId() {
                        return SRE.id("endfield_menu");
                    }

                    @Override
                    public void onResourceManagerReload(ResourceManager resourceManager) {
                        invalidateRequested = true;
                    }
                });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                client.execute(EndfieldMenuRenderer::releaseShared));
    }

    /**
     * Builds the first frame's layers before the opening timeline starts.
     * Skija rasterization is deliberately kept out of the first animated frame.
     */
    public void prepare(MenuLayout layout, PlayerCard player, List<MenuScene.Button> buttons,
                        String mapId) {
        if (!Objects.equals(showcaseMapId, mapId)) {
            showcaseMapId = mapId;
            removeLayer("map");
        }
        if (preparedPlayer != null && !samePaintedPlayer(preparedPlayer, player)) {
            invalidatePlayerLayers();
        }
        Map<String, String> buttonLabels = new HashMap<>();
        for (MenuScene.Button button : buttons) {
            String id = button.slot().entry().id();
            String label = button.slot().entry().label();
            buttonLabels.put(id, label);
            if (!Objects.equals(preparedButtonLabels.get(id), label)) {
                invalidateButtonLayers(id);
            }
        }
        preparedButtonLabels = Map.copyOf(buttonLabels);
        if (!layout.compact()) {
            layer("backdrop", 1920, 1080, disc::backdrop);
            layer("reticle", 1040, 1040, disc::reticle);
            layer("base", 1040, 1040, disc::base);
            layer("amber", 1040, 1040, disc::amber);
            layer("wall:inside", 1024, 128, canvas -> disc.wall(canvas, true));
            layer("wall:outside", 1024, 128, canvas -> disc.wall(canvas, false));
            layer("well", 920, 920, disc::well);
            layer("map", 920, 920, this::paintShowcaseMap);
            layer("world", 390, 150, canvas -> playerPainter.world(canvas, player));
            layer("footer", 1820, 32, canvas -> playerPainter.footer(canvas, player, 1820));
            layer("readout", 342, 50, canvas -> playerPainter.readout(canvas, player));
        }
        layer("close", 64, 64, panels::close);
        Box profile = layout.profile();
        layer("profile", (int) profile.width(), (int) profile.height(), canvas -> {
            if (layout.compact()) {
                playerPainter.compactProfile(canvas, player);
            } else {
                playerPainter.profile(canvas, player, profile.width(), profile.height());
            }
        });
        for (MenuScene.Button button : buttons) {
            int width = (int) Math.ceil(button.slot().box().width());
            int height = (int) Math.ceil(button.slot().box().height());
            String key = "button:" + button.slot().entry().id();
            layer(key, width, height, canvas -> panels.button(canvas, button.slot().entry(),
                    button.slot().style(), button.slot().box().width(), button.slot().box().height(), false));
            layer(key + ":hover", width, height, canvas -> panels.button(canvas, button.slot().entry(),
                    button.slot().style(), button.slot().box().width(), button.slot().box().height(), true));
            if (button.slot().style() == Style.CARD || button.slot().style() == Style.TILE) {
                Box box = button.slot().box();
                Box glow = new Box(box.x() - FOG_PADDING, box.y() - FOG_PADDING,
                        box.width() + FOG_PADDING * 2, box.height() + FOG_PADDING * 2);
                layer("fog:" + button.slot().entry().id(), (int) Math.ceil(glow.width()),
                        (int) Math.ceil(glow.height()), canvas -> panels.fog(canvas, button.slot().entry(),
                                button.slot().style(), box.width(), box.height(), FOG_PADDING));
            }
        }
        preparedPlayer = player;
    }

    private static boolean samePaintedPlayer(PlayerCard first, PlayerCard second) {
        return first.name().equals(second.name())
                && first.daysPlayed() == second.daysPlayed()
                && Float.compare(first.dayProgress(), second.dayProgress()) == 0
                && first.world().equals(second.world())
                && first.mode().equals(second.mode())
                && first.connection().equals(second.connection())
                && first.daysPlayedLabel().equals(second.daysPlayedLabel())
                && first.hint().equals(second.hint());
    }

    public void render(GuiGraphics graphics, MenuLayout layout, Frame frame, PlayerCard player,
                       List<MenuScene.Button> buttons, Map<String, Feedback> feedback,
                       float scroll, float yaw, float pitch) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        SceneProjection projection = new SceneProjection(layout, yaw, pitch);
        if (!layout.compact()) {
            draw(graphics, layer("backdrop", 1920, 1080, disc::backdrop), projection,
                    new Box(0, 0, 1920, 1080), Plane.at(0), frame.discAlpha(), 4);
        }
        for (MenuScene.Button button : buttons) {
            drawFog(graphics, layout, projection, button);
        }
        float drift = frame.exitDrift();
        float roll = frame.exitRoll();
        Plane infoPlane = new Plane(34, 1, 0, -drift * 0.8f + roll, drift * 0.5f);
        Plane worldPlane = new Plane(72, 1, 0, drift * 0.9f + roll, -drift * 0.7f);
        if (!layout.compact()) {
            drawDisc(graphics, layer("reticle", 1040, 1040, disc::reticle), projection,
                    DiscGeometry.RIM, DiscGeometry.plane(frame, 0),
                    frame.discAlpha() * (1 - frame.contentAlpha()), 12, frame);
            drawDisc(graphics, layer("base", 1040, 1040, disc::base), projection,
                    DiscGeometry.RIM, DiscGeometry.plane(frame, DiscGeometry.BASE_DEPTH),
                    frame.contentAlpha() * frame.discAlpha(), 12, frame);
            drawWall(graphics, projection, frame, false, 180);
            drawDisc(graphics, layer("well", 920, 920, disc::well), projection,
                    DiscGeometry.WELL, DiscGeometry.plane(frame, DiscGeometry.FLOOR_DEPTH),
                    frame.contentAlpha() * frame.discAlpha(), 12, frame);
            drawDisc(graphics, layer("map", 920, 920, this::paintShowcaseMap), projection,
                    DiscGeometry.WELL, DiscGeometry.plane(frame, DiscGeometry.FLOOR_DEPTH),
                    frame.contentAlpha() * frame.discAlpha(), 12, frame);
            for (MenuScene.Button button : buttons) {
                if (button.slot().style() == Style.RESUME_ARC) {
                    drawButton(graphics, layout, projection, button, feedback);
                }
            }
            drawWall(graphics, projection, frame, false, 0);
            drawWall(graphics, projection, frame, true, 180);
            drawWall(graphics, projection, frame, true, 0);
            drawDisc(graphics, layer("amber", 1040, 1040, disc::amber), projection,
                    DiscGeometry.RIM, DiscGeometry.plane(frame, -60), frame.discAlpha(), 12, frame);
            drawDisc(graphics, layer("rim", 1040, 1040, disc::rim), projection,
                    DiscGeometry.RIM, DiscGeometry.plane(frame, DiscGeometry.TOP_DEPTH), frame.discAlpha(), 12, frame);
            draw(graphics, layer("world", 390, 150,
                            canvas -> playerPainter.world(canvas, player)), projection,
                    new Box(1405, 70, 390, 150), worldPlane, frame.controlsAlpha(), 4);
            drawFlat(graphics, layer("footer", 1820, 32,
                            canvas -> playerPainter.footer(canvas, player, 1820)), layout,
                    new Box(50, 1043, 1820, 32), frame.badgeAlpha(), 1);
        }
        for (MenuScene.Button button : buttons) {
            if (!layout.compact() && button.slot().style() == Style.RESUME_ARC) {
                continue;
            }
            drawButton(graphics, layout, projection, button, feedback);
        }
        if (!layout.compact() || layout.height() >= 570) {
            Box profile = layout.profile();
            draw(graphics, layer("profile", (int) profile.width(), (int) profile.height(), canvas -> {
                if (layout.compact()) {
                    playerPainter.compactProfile(canvas, player);
                } else {
                    playerPainter.profile(canvas, player, profile.width(), profile.height());
                }
            }), projection, profile, infoPlane, frame.badgeAlpha(), 4);
            drawSkin(graphics, projection, frame, layout, profile, infoPlane);
            if (!layout.compact()) {
                draw(graphics, layer("readout", 342, 50,
                                canvas -> playerPainter.readout(canvas, player)), projection,
                        layout.daysPlayed(), infoPlane, frame.badgeAlpha(), 4);
            }
        }
        drawFlat(graphics, layer("close", 64, 64, panels::close), layout,
                layout.close(), frame.controlsAlpha(), 1);
        if (layout.maxScroll() > 0) {
            drawScrollBar(graphics, layout, projection, scroll);
        }
        RenderSystem.disableBlend();
    }

    /** Drops baked layers after the menu model or player snapshot changes. */
    public void invalidate() {
        for (Layer layer : layers.values()) {
            client.getTextureManager().release(layer.id());
        }
        layers.clear();
    }

    /** Player metadata changes, while the expensive disc and map layers can stay baked. */
    public void invalidatePlayerLayers() {
        removeLayer("world");
        removeLayer("footer");
        removeLayer("readout");
        removeLayer("profile");
    }

    public void invalidateButton(String id) {
        invalidateButtonLayers(id);
    }

    private void invalidateButtonLayers(String id) {
        removeLayer("button:" + id);
        removeLayer("button:" + id + ":hover");
        removeLayer("fog:" + id);
    }

    public void setShowcaseMap(String mapId) {
        if (java.util.Objects.equals(showcaseMapId, mapId)) {
            return;
        }
        showcaseMapId = mapId;
        removeLayer("map");
    }

    private void paintShowcaseMap(Canvas canvas) {
        ResourceLocation texture = MapUiGraphics.backgroundTexture(showcaseMapId);
        mapPainter.map(canvas, MapUiGraphics.readTextureBytes(texture));
    }

    private void removeLayer(String key) {
        Layer removed = layers.remove(key);
        if (removed != null) {
            client.getTextureManager().release(removed.id());
        }
    }

    private void drawSkin(GuiGraphics graphics, SceneProjection projection, Frame frame,
                          MenuLayout layout, Box profile, Plane infoPlane) {
        if (client.player == null || frame.badgeAlpha() <= 0.001f) {
            return;
        }
        ResourceLocation id = client.player.getSkin().texture();
        Box face = layout.compact() ? new Box(profile.x() + 3, profile.y() + 3, 60, 60)
                : new Box(profile.x() + 20, profile.y() + 20, 70, 70);
        drawTextureRegion(graphics, id, projection, face, infoPlane, frame.badgeAlpha(),
                8 / 64f, 8 / 64f, 16 / 64f, 16 / 64f, 3);
        drawTextureRegion(graphics, id, projection, face, infoPlane, frame.badgeAlpha(),
                40 / 64f, 8 / 64f, 48 / 64f, 16 / 64f, 3);
    }

    /** 圆环图层：discSharpness 不足时以多份轻微放大/偏移的幽灵层叠加，模拟失焦模糊。 */
    private void drawDisc(GuiGraphics graphics, Layer layer, SceneProjection projection, Box box,
                          Plane plane, float alpha, int subdivisions, Frame frame) {
        if (layer == null || alpha <= 0.001f) {
            return;
        }
        float sharp = frame.discSharpness();
        if (sharp >= 0.999f) {
            draw(graphics, layer, projection, box, plane, alpha, subdivisions);
            return;
        }
        float soft = (1 - sharp) * 0.3f;
        draw(graphics, layer, projection, box, ghost(plane, 1.03f, 7, 5), alpha * soft, subdivisions);
        draw(graphics, layer, projection, box, ghost(plane, 0.97f, -7, -5), alpha * soft, subdivisions);
        draw(graphics, layer, projection, box, ghost(plane, 1.0f, 0, 7), alpha * soft, subdivisions);
        draw(graphics, layer, projection, box, plane, alpha * (0.35f + 0.65f * sharp), subdivisions);
    }

    private static Plane ghost(Plane plane, float scaleBoost, float offsetX, float offsetY) {
        return new Plane(plane.depth(), plane.scale() * scaleBoost, plane.tilt(),
                plane.offsetX() + offsetX, plane.offsetY() + offsetY, plane.compensateDepth());
    }

    private void drawButton(GuiGraphics graphics, MenuLayout layout, SceneProjection projection,
                            MenuScene.Button button, Map<String, Feedback> feedback) {
        if (button.alpha() <= 0.001f) {
            return;
        }
        MenuEntry entry = button.slot().entry();
        Feedback interaction = feedback.getOrDefault(entry.id(), Feedback.NONE);
        float lift = interaction.hover() * 4 - interaction.press() * 3;
        float direction = button.slot().style() == Style.TILE && button.box().width() < 320 ? 1 : -1;
        Box visual = button.box().translate(direction * interaction.hover() * 2, -lift)
                .scaleAroundCenter(1 - interaction.press() * 0.03f);
        float alpha = button.alpha() * (entry.enabled() ? 1 : 0.38f);
        String key = "button:" + entry.id();
        int width = (int) Math.ceil(button.slot().box().width());
        int height = (int) Math.ceil(button.slot().box().height());
        boolean clipped = button.slot().scrollable() && !layout.compact();
        if (clipped) {
            enableScrollClip(graphics, layout, projection);
        }
        try {
            draw(graphics, layer(key, width, height, canvas -> panels.button(canvas, entry,
                            button.slot().style(), button.slot().box().width(),
                            button.slot().box().height(), false)), projection, visual,
                    button.plane(), alpha, 3);
            float highlight = Math.max(interaction.hover(), interaction.focused() ? 0.85f : 0);
            if (highlight > 0.001f && entry.enabled()) {
                draw(graphics, layer(key + ":hover", width, height, canvas -> panels.button(canvas, entry,
                                button.slot().style(), button.slot().box().width(),
                                button.slot().box().height(), true)), projection, visual,
                        button.plane(), alpha * highlight, 3);
            }
        } finally {
            if (clipped) {
                graphics.disableScissor();
            }
        }
    }

    private static void enableScrollClip(GuiGraphics graphics, MenuLayout layout, SceneProjection projection) {
        Point[] points = {
                projection.project(layout.scrollArea().x(), layout.scrollArea().y(), Plane.at(28)),
                projection.project(layout.scrollArea().right(), layout.scrollArea().y(), Plane.at(28)),
                projection.project(layout.scrollArea().right(), layout.scrollArea().bottom(), Plane.at(28)),
                projection.project(layout.scrollArea().x(), layout.scrollArea().bottom(), Plane.at(28))
        };
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (Point point : points) {
            minX = Math.min(minX, (int) Math.floor(point.x()));
            minY = Math.min(minY, (int) Math.floor(point.y()));
            maxX = Math.max(maxX, (int) Math.ceil(point.x()));
            maxY = Math.max(maxY, (int) Math.ceil(point.y()));
        }
        graphics.enableScissor(minX, minY, maxX, maxY);
    }

    private void drawScrollBar(GuiGraphics graphics, MenuLayout layout, SceneProjection projection, float scroll) {
        Box area = layout.scrollArea();
        float thumbHeight = Math.max(28, area.height() * area.height() / (area.height() + layout.maxScroll()));
        float y = area.y() + (area.height() - thumbHeight)
                * Math.clamp(scroll / Math.max(layout.maxScroll(), 0.001f), 0, 1);
        Point point = projection.project(area.right() - 5, y, Plane.at(28));
        int width = Math.max(1, Math.round(3 * layout.scale()));
        graphics.fill(Math.round(point.x()), Math.round(point.y()), Math.round(point.x()) + width,
                Math.round(point.y() + thumbHeight * layout.scale()), 0xaaf1ed48);
    }

    private void drawFog(GuiGraphics graphics, MenuLayout layout, SceneProjection projection,
                         MenuScene.Button button) {
        if ((button.slot().style() != Style.CARD && button.slot().style() != Style.TILE)
                || button.alpha() <= 0.001f) {
            return;
        }
        Box box = button.box();
        if (button.slot().scrollable() && (box.bottom() < layout.scrollArea().y()
                || box.y() > layout.scrollArea().bottom())) {
            return;
        }
        Box glow = new Box(box.x() - FOG_PADDING, box.y() - FOG_PADDING,
                box.width() + FOG_PADDING * 2, box.height() + FOG_PADDING * 2);
        draw(graphics, layer("fog:" + button.slot().entry().id(), (int) Math.ceil(glow.width()),
                (int) Math.ceil(glow.height()), canvas -> panels.fog(canvas, button.slot().entry(),
                        button.slot().style(), box.width(), box.height(), FOG_PADDING)), projection, glow,
                button.plane(), button.alpha() * (button.slot().entry().enabled() ? 1 : 0.45f), 3);
    }

    private void drawWall(GuiGraphics graphics, SceneProjection projection, Frame frame,
                          boolean inside, float angle) {
        float alpha = frame.contentAlpha() * frame.discAlpha();
        if (alpha <= 0.001f) {
            return;
        }
        Layer layer = layer(inside ? "wall:inside" : "wall:outside", 1024, 128,
                canvas -> disc.wall(canvas, inside));
        drawCylinder(graphics, layer, projection, frame,
                inside ? DiscGeometry.INNER_RADIUS : DiscGeometry.OUTER_RADIUS,
                inside ? DiscGeometry.FLOOR_DEPTH : DiscGeometry.BASE_DEPTH, angle, inside, alpha);
    }

    private void drawCylinder(GuiGraphics graphics, Layer layer, SceneProjection projection, Frame frame,
                              float radius, float bottomDepth, float startAngle, boolean inside, float alpha) {
        BufferBuilder buffer = beginTexture(layer.id());
        Plane top = DiscGeometry.plane(frame, DiscGeometry.TOP_DEPTH);
        Plane bottom = DiscGeometry.plane(frame, bottomDepth);
        Matrix4f matrix = graphics.pose().last().pose();
        float[] x = new float[4];
        float[] y = new float[4];
        float[] u = new float[4];
        float[] v = new float[4];
        int color = alphaColor(alpha);
        int vertices = 0;
        for (int segment = 0; segment < WALL_SEGMENTS; segment++) {
            for (int i = 0; i < CORNERS.length; i++) {
                int[] corner = CORNERS[i];
                int circleIndex = segment + corner[0];
                projection.project(DiscGeometry.CENTER_X + radius * WALL_COS[circleIndex],
                        DiscGeometry.CENTER_Y + radius * WALL_SIN[circleIndex],
                        corner[1] == 0 ? top : bottom, projected);
                x[i] = projected[0];
                y[i] = projected[1];
                u[i] = (startAngle + circleIndex * 180f / WALL_SEGMENTS) / 360f;
                v[i] = corner[1];
            }
            float winding = (x[1] - x[0]) * (y[2] - y[0]) - (y[1] - y[0]) * (x[2] - x[0]);
            if (inside ? winding >= 0 : winding <= 0) {
                continue;
            }
            if (winding > 0) {
                swap(x, 1, 3); swap(y, 1, 3); swap(u, 1, 3); swap(v, 1, 3);
            }
            for (int i = 0; i < 4; i++) {
                buffer.addVertex(matrix, x[i], y[i], 0).setUv(u[i], v[i]).setColor(color);
            }
            vertices += 4;
        }
        if (vertices > 0) {
            BufferUploader.drawWithShader(buffer.buildOrThrow());
        }
    }

    private void draw(GuiGraphics graphics, Layer layer, SceneProjection projection, Box box,
                      Plane plane, float alpha, int subdivisions) {
        if (layer == null || alpha <= 0.001f) {
            return;
        }
        BufferBuilder buffer = beginTexture(layer.id());
        Matrix4f matrix = graphics.pose().last().pose();
        int color = alphaColor(alpha);
        for (int y = 0; y < subdivisions; y++) {
            for (int x = 0; x < subdivisions; x++) {
                for (int[] corner : CORNERS) {
                    float u = (x + corner[0]) / (float) subdivisions;
                    float v = (y + corner[1]) / (float) subdivisions;
                    projection.project(box.x() + u * box.width(), box.y() + v * box.height(), plane, projected);
                    buffer.addVertex(matrix, projected[0], projected[1], 0).setUv(u, v).setColor(color);
                }
            }
        }
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    private void drawTextureRegion(GuiGraphics graphics, ResourceLocation id, SceneProjection projection,
                                   Box box, Plane plane, float alpha, float u0, float v0,
                                   float u1, float v1, int subdivisions) {
        if (alpha <= 0.001f) {
            return;
        }
        BufferBuilder buffer = beginTexture(id);
        Matrix4f matrix = graphics.pose().last().pose();
        int color = alphaColor(alpha);
        for (int y = 0; y < subdivisions; y++) {
            for (int x = 0; x < subdivisions; x++) {
                for (int[] corner : CORNERS) {
                    float u = (x + corner[0]) / (float) subdivisions;
                    float v = (y + corner[1]) / (float) subdivisions;
                    projection.project(box.x() + u * box.width(), box.y() + v * box.height(), plane, projected);
                    buffer.addVertex(matrix, projected[0], projected[1], 0)
                            .setUv(u0 + (u1 - u0) * u, v0 + (v1 - v0) * v)
                            .setColor(color);
                }
            }
        }
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    private void drawFlat(GuiGraphics graphics, Layer layer, MenuLayout layout, Box box,
                          float alpha, int subdivisions) {
        SceneProjection flat = new SceneProjection(new MenuLayout(true, layout.width(), layout.height(),
                layout.scale(), layout.offsetX(), layout.offsetY(), List.of(), layout.scrollArea(), 0,
                layout.profile(), layout.close()), 0, 0);
        draw(graphics, layer, flat, box, Plane.at(0), alpha, subdivisions);
    }

    private BufferBuilder beginTexture(ResourceLocation id) {
        RenderSystem.setShaderTexture(0, id);
        return Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
    }

    private static int alphaColor(float alpha) {
        return (Math.round(Math.clamp(alpha, 0, 1) * 255) << 24) | 0x00ffffff;
    }

    private Layer layer(String key, int width, int height, Consumer<Canvas> painter) {
        Layer existing = layers.get(key);
        if (existing != null && existing.width() == width && existing.height() == height) {
            return existing;
        }
        if (existing != null) {
            client.getTextureManager().release(existing.id());
        }
        ImageInfo info = new ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL);
        ByteBuffer raster = ByteBuffer.allocateDirect(width * height * 4);
        try (Surface surface = Surface.makeRaster(info);
             Pixmap pixmap = Pixmap.make(info, raster, width * 4)) {
            surface.getCanvas().clear(0);
            painter.accept(surface.getCanvas());
            if (!surface.readPixels(pixmap, 0, 0)) {
                throw new IllegalStateException("Unable to read Endfield menu layer: " + key);
            }
            NativeImage image = new NativeImage(width, height, true);
            raster.rewind();
            byte[] bytes = new byte[width * height * 4];
            raster.get(bytes);
            int index = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int red = bytes[index++] & 255;
                    int green = bytes[index++] & 255;
                    int blue = bytes[index++] & 255;
                    int alpha = bytes[index++] & 255;
                    image.setPixelRGBA(x, y, alpha << 24 | blue << 16 | green << 8 | red);
                }
            }
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("starrailexpress",
                    "endfield/layer_" + key.replaceAll("[^a-zA-Z0-9_]+", "_"));
            DynamicTexture texture = new DynamicTexture(image);
            texture.setFilter(true, false);
            client.getTextureManager().register(id, texture);
            Layer created = new Layer(id, texture, width, height);
            layers.put(key, created);
            return created;
        }
    }

    private static void swap(float[] values, int a, int b) {
        float value = values[a]; values[a] = values[b]; values[b] = value;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        invalidate();
        resources.close();
    }
}
