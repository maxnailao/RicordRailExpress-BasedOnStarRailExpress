package net.exmo.sre.loading;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import io.wifi.starrailexpress.SRE;
import net.exmo.sre.EXSREClient;
import net.exmo.sre.loading.texture.ConfigTexture;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 帧序列动画渲染器 —— 从资源包加载 PNG / JPG 帧序列，以指定帧率播放，
 * 并在相邻帧之间做 Alpha 交叉淡入淡出（动态补帧），用作背景。
 * <p>
 * 帧文件在首次启动时解压到游戏根目录 {@code video/} 下，
 * 并由 ConfigTexture 从磁盘加载（如 frame_0000.png, frame_0001.jpg ...）。
 * 支持 .png / .jpg / .jpeg —— JPG 内存占用更小、画质更高，推荐使用。
 */
@Environment(EnvType.CLIENT)
public class FrameAnimationRenderer {

    private static final Logger LOGGER = LogUtils.getLogger();
     private static volatile boolean inWorld = false;

    private final List<ResourceLocation> frames = new ArrayList<>();
    private final float fps;
    private final Path frameDir;
    /** true：进入世界后 loadFrames() 会清空帧（标题背景用）；false：世界中也可播放（菜单背景用） */
    private final boolean clearWhenInWorld;
    private long startTimeMs = -1;
    private boolean loaded = false;

    /**
     * @param fps 播放帧率（例如 24.0F 或 30.0F）
     */
    public FrameAnimationRenderer(float fps) {
        this(fps, EXSREClient.GAME_VIDEO_DIR, true);
    }

    /**
     * @param fps              播放帧率
     * @param frameDir         帧文件目录（必须位于 {@code run/video/} 之下，与 ConfigTexture 的磁盘映射一致）
     * @param clearWhenInWorld 进入世界后是否清空帧序列（标题背景传 true；游戏内 GUI 背景传 false）
     */
    public FrameAnimationRenderer(float fps, Path frameDir, boolean clearWhenInWorld) {
        this.fps = fps;
        this.frameDir = frameDir;
        this.clearWhenInWorld = clearWhenInWorld;
    }

    public static void setInWorld(boolean value) {
        inWorld = value;
    }

    // ─── 加载 ────────────────────────────────────────────────────────

    /**
     * 从帧目录扫描帧序列。应在 {@code Screen.init()} 或首次渲染时调用。
     */
    public void loadFrames() {
        if (clearWhenInWorld && inWorld) {
            this.frames.clear();
            this.loaded = false;
            this.startTimeMs = -1;
            return;
        }

        frames.clear();
        Minecraft mc = Minecraft.getInstance();

        if (!Files.isDirectory(frameDir)) {
            loaded = true;
            LOGGER.warn("[SRE] Video frame directory missing: {}", frameDir);
            return;
        }

        // ConfigTexture 把 "video/..." 路径映射到 run/video/ 磁盘目录，子目录同样有效
        String prefix = "video/";
        try {
            String rel = EXSREClient.GAME_VIDEO_DIR.relativize(frameDir).toString().replace("\\", "/");
            if (!rel.isEmpty()) prefix = "video/" + rel + "/";
        } catch (IllegalArgumentException ignored) {
        }

        try (Stream<Path> stream = Files.list(frameDir)) {
            List<Path> frameFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString().toLowerCase();
                        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg");
                    })
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();

            for (Path frame : frameFiles) {
                String fileName = frame.getFileName().toString();
                ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(SRE.MOD_ID, prefix + fileName);
                mc.getTextureManager().register(loc, new ConfigTexture(loc));
                frames.add(loc);
            }
        } catch (IOException e) {
            LOGGER.error("[SRE] Failed to load video frames from {}", frameDir, e);
        }

        loaded = true;
        startTimeMs = -1;

        if (frames.isEmpty()) {
            LOGGER.warn("[SRE] No video frames found in {}", frameDir);
        } else {
            LOGGER.info("[SRE] Loaded {} video frames from {}", frames.size(), frameDir);
        }
    }

    public boolean hasFrames() {
        return loaded && !frames.isEmpty();
    }

    public int getFrameCount() {
        return frames.size();
    }

    // ─── 渲染 ────────────────────────────────────────────────────────

    /**
     * 渲染动画帧到全屏，带帧间交叉淡入淡出补帧效果。
     *
     * @param g            GuiGraphics
     * @param screenWidth  屏幕宽度（GUI 坐标）
     * @param screenHeight 屏幕高度（GUI 坐标）
     * @param delta         partial tick
     */
    public void render(GuiGraphics g, int screenWidth, int screenHeight, float delta) {
        if (!hasFrames()) return;

        long now = Util.getMillis();
        if (startTimeMs < 0) startTimeMs = now;

        float elapsedSec = (now - startTimeMs) / 1000.0F;
        float totalFrames = frames.size();

        // 当前帧位置（浮点），整数部分 = 帧索引，小数部分 = 补帧插值因子
        float framePos = (elapsedSec * fps) % totalFrames;
        int frameIndex = (int) framePos;
        float blend = framePos - frameIndex; // 0.0 ~ 1.0

        int nextIndex = (frameIndex + 1) % frames.size();
        ResourceLocation currentFrame = frames.get(frameIndex);
        ResourceLocation nextFrame = frames.get(nextIndex);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 渲染当前帧（完全不透明）
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        g.blit(currentFrame, 0, 0, 0, 0,
                screenWidth, screenHeight, screenWidth, screenHeight);

        // 渲染下一帧（用 blend 因子作为 alpha 实现交叉淡入淡出补帧）
        if (blend > 0.005F) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, blend);
            g.blit(nextFrame, 0, 0, 0, 0,
                    screenWidth, screenHeight, screenWidth, screenHeight);
        }

        // 重置
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    /**
     * 渲染动画帧到全屏，整体带额外透明度（用于淡入淡出过渡）。
     */
    public void render(GuiGraphics g, int screenWidth, int screenHeight,
                       float delta, float overallAlpha) {
        if (!hasFrames()) return;

        long now = Util.getMillis();
        if (startTimeMs < 0) startTimeMs = now;

        float elapsedSec = (now - startTimeMs) / 1000.0F;
        float totalFrames = frames.size();

        float framePos = (elapsedSec * fps) % totalFrames;
        int frameIndex = (int) framePos;
        float blend = framePos - frameIndex;
        int nextIndex = (frameIndex + 1) % frames.size();

        ResourceLocation currentFrame = frames.get(frameIndex);
        ResourceLocation nextFrame = frames.get(nextIndex);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 当前帧叠加整体 alpha
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, overallAlpha);
        g.blit(currentFrame, 0, 0, 0, 0,
                screenWidth, screenHeight, screenWidth, screenHeight);

        // 下一帧：alpha = overallAlpha * blend
        if (blend > 0.005F) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, overallAlpha * blend);
            g.blit(nextFrame, 0, 0, 0, 0,
                    screenWidth, screenHeight, screenWidth, screenHeight);
        }

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    /**
     * 渲染动画帧到指定矩形区域（拉伸铺满），带帧间交叉淡入淡出补帧效果。
     * 供游戏内 GUI 面板（如 E 键主菜单）作为动态背景使用。
     */
    public void render(GuiGraphics g, int x, int y, int width, int height) {
        render(g, x, y, width, height, 1.0F);
    }

    /**
     * 渲染动画帧到指定矩形区域，整体带额外透明度。
     */
    public void render(GuiGraphics g, int x, int y, int width, int height, float overallAlpha) {
        if (!hasFrames() || width <= 0 || height <= 0) return;

        long now = Util.getMillis();
        if (startTimeMs < 0) startTimeMs = now;

        float elapsedSec = (now - startTimeMs) / 1000.0F;
        float totalFrames = frames.size();

        float framePos = (elapsedSec * fps) % totalFrames;
        int frameIndex = (int) framePos;
        float blend = framePos - frameIndex;
        int nextIndex = (frameIndex + 1) % frames.size();

        ResourceLocation currentFrame = frames.get(frameIndex);
        ResourceLocation nextFrame = frames.get(nextIndex);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, overallAlpha);
        g.blit(currentFrame, x, y, 0, 0, width, height, width, height);

        if (blend > 0.005F) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, overallAlpha * blend);
            g.blit(nextFrame, x, y, 0, 0, width, height, width, height);
        }

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    /**
     * 重置动画到起始帧。
     */
    public void reset() {
        startTimeMs = -1;
    }
}
