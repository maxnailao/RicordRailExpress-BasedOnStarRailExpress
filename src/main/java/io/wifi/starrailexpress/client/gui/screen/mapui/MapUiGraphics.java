/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.client.gui.screen.mapui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import io.wifi.starrailexpress.SRE;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.Mth;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 地图相关界面（投票 / 轮换）共用的绘制与缓动工具。
 * 建议在使用处 {@code import static ...MapUiGraphics.*;}。
 */
public final class MapUiGraphics {

    /** 地图缩略图：{@code textures/gui/maps/<id>.png}，缺图时画占位符。 */
    private static final String THUMBNAIL_PATH = "textures/gui/maps/%s.png";
    /** 地图投票整屏背景：{@code textures/gui/maps/background/<id>.png}。 */
    private static final String BACKGROUND_PATH = "textures/gui/maps/background/%s.png";
    /** PNG IHDR 宽高缓存；资源包重载后尺寸几乎不变，错了最多裁切偏差。 */
    private static final Map<ResourceLocation, int[]> TEXTURE_SIZE = new HashMap<>();

    private MapUiGraphics() {
    }

    // ------------------------------------------------------------------
    // 颜色 / 缓动
    // ------------------------------------------------------------------

    public static int withAlpha(int color, int alpha) {
        return (Mth.clamp(alpha, 0, 255) << 24) | (color & 0x00FFFFFF);
    }

    /** 只混合 RGB，输出不透明色。 */
    public static int mix(int from, int to, float t) {
        float f = Mth.clamp(t, 0.0f, 1.0f);
        int r = Mth.floor(Mth.lerp(f, (from >> 16) & 0xFF, (to >> 16) & 0xFF));
        int g = Mth.floor(Mth.lerp(f, (from >> 8) & 0xFF, (to >> 8) & 0xFF));
        int b = Mth.floor(Mth.lerp(f, from & 0xFF, to & 0xFF));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public static float easeOutCubic(float t) {
        float f = 1.0f - Mth.clamp(t, 0.0f, 1.0f);
        return 1.0f - f * f * f;
    }

    /** 与帧率无关的指数逼近。 */
    public static float approach(float current, float target, float dt, float speed) {
        return Mth.lerp(approachFactor(dt, speed), current, target);
    }

    public static float approachFactor(float dt, float speed) {
        return 1.0f - (float) Math.exp(-dt * speed);
    }

    /** 稳定的伪随机哈希，用于低多边形网格抖动与占位色。 */
    public static float hash(int a, int b, int salt) {
        int h = a * 374761393 + b * 668265263 + salt * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535.0f;
    }

    // ------------------------------------------------------------------
    // 基础绘制
    // ------------------------------------------------------------------

    public static void drawRectBorder(GuiGraphics g, int x, int y, int w, int h, int thickness, int color) {
        for (int i = 0; i < thickness; i++) {
            g.fill(x + i, y + i, x + w - i, y + i + 1, color);
            g.fill(x + i, y + h - i - 1, x + w - i, y + h - i, color);
            g.fill(x + i, y + i, x + i + 1, y + h - i, color);
            g.fill(x + w - i - 1, y + i, x + w - i, y + h - i, color);
        }
    }

    public static boolean isInRect(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    public static String clip(Font font, String text, int maxWidth) {
        if (maxWidth <= 0 || font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width(ellipsis))) + ellipsis;
    }

    // ------------------------------------------------------------------
    // 贴图
    // ------------------------------------------------------------------

    /**
     * 不做静态缓存：资源包重载后缓存会变陈旧，而每帧实际只有 1~2 次查找（悬停行 + 详情图）。
     * 需要缓存的是全屏背景，由 {@link MapBackdropRenderer} 按屏幕实例缓存。
     */
    public static boolean textureExists(ResourceLocation location) {
        if (location == null) {
            return false;
        }
        try {
            return Minecraft.getInstance().getResourceManager().getResource(location).isPresent();
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * 按模板拼出地图贴图路径，返回第一个真正存在的贴图，都没有则返回 null。
     *
     * <p>
     * 地图 id 允许是 {@code train_maps} 下的相对路径（子文件夹会被枚举成
     * {@code "文件夹名/地图配置名"}），所以这里必须保留 {@code /} 而不是当成扁平文件名。
     *
     * <p>
     * 查找顺序固定为「归一化（小写 + 非法字符换成 {@code _}）优先，原样次之」，不能反过来：
     * 本版本的 {@link ResourceLocation#tryBuild} 并不拒绝大写路径，若先试原样路径，
     * 开着 Windows 目录式资源的开发环境（大小写不敏感）能命中 {@code Polus.png}，
     * 而发布后读的是 jar（zip 大小写敏感），同一张内置贴图 {@code polus.png} 就查不到，
     * 于是 {@code Polus/Skeld/Vineyard/TheWitchPrison/TheWinterPalace/ResearchStation}
     * 这几张文件名带大写的 map 在投票页只剩黑底。
     */
    public static ResourceLocation mapTexture(String pattern, String mapId) {
        if (pattern == null || mapId == null || mapId.isEmpty()) {
            return null;
        }
        String path = String.format(pattern, mapId);
        ResourceLocation sanitized = ResourceLocation.tryBuild(SRE.MOD_ID, sanitizePath(path));
        if (sanitized != null && textureExists(sanitized)) {
            return sanitized;
        }
        ResourceLocation raw = ResourceLocation.tryBuild(SRE.MOD_ID, path);
        if (raw != null && textureExists(raw)) {
            return raw;
        }
        return null;
    }

    /** 归一化路径：小写化，并把可能不被 {@link ResourceLocation} 接受的字符换成 {@code _}，保留目录分隔符。 */
    private static String sanitizePath(String path) {
        StringBuilder out = new StringBuilder(path.length());
        for (int i = 0; i < path.length(); i++) {
            char c = Character.toLowerCase(path.charAt(i));
            boolean valid = c == '/' || c == '_' || c == '-' || c == '.'
                    || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
            out.append(valid ? c : '_');
        }
        return out.toString();
    }

    public static ResourceLocation thumbnailTexture(String mapId) {
        return mapTexture(THUMBNAIL_PATH, mapId);
    }

    public static ResourceLocation backgroundTexture(String mapId) {
        ResourceLocation background = mapTexture(BACKGROUND_PATH, mapId);
        return background != null ? background : thumbnailTexture(mapId);
    }

    /** 读取地图背景原始 PNG，供一次性栅格化使用；每帧调用会重新读盘，不要这样用。 */
    public static byte[] readTextureBytes(ResourceLocation texture) {
        if (texture == null) {
            return null;
        }
        try {
            Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(texture);
            if (resource.isEmpty()) {
                return null;
            }
            try (InputStream stream = resource.get().open()) {
                return stream.readAllBytes();
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 等比放大到铺满目标矩形（cover），超出部分由调用方 scissor 裁掉。
     * 不要把纹理宽高设成目标宽高，那会把非 16:9 的图拉扁。
     */
    public static void drawCover(GuiGraphics g, ResourceLocation texture, int x, int y, int w, int h) {
        if (texture == null || w <= 0 || h <= 0) {
            return;
        }
        int[] size = textureSize(texture);
        int texW = size[0];
        int texH = size[1];
        if (texW <= 0 || texH <= 0) {
            g.blit(texture, x, y, 0.0f, 0.0f, w, h, w, h);
            return;
        }
        float scale = Math.max((float) w / texW, (float) h / texH);
        int destW = Math.max(1, Math.round(texW * scale));
        int destH = Math.max(1, Math.round(texH * scale));
        int destX = x + (w - destW) / 2;
        int destY = y + (h - destH) / 2;
        g.blit(texture, destX, destY, 0.0f, 0.0f, destW, destH, destW, destH);
    }

    /** 读 PNG IHDR，避免整图解码。 */
    private static int[] textureSize(ResourceLocation texture) {
        int[] cached = TEXTURE_SIZE.get(texture);
        if (cached != null) {
            return cached;
        }
        int[] size = new int[] { 0, 0 };
        try {
            Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(texture);
            if (resource.isPresent()) {
                try (InputStream stream = resource.get().open()) {
                    byte[] header = stream.readNBytes(24);
                    if (header.length >= 24 && header[0] == (byte) 0x89 && header[1] == 0x50) {
                        size[0] = ((header[16] & 0xFF) << 24) | ((header[17] & 0xFF) << 16)
                                | ((header[18] & 0xFF) << 8) | (header[19] & 0xFF);
                        size[1] = ((header[20] & 0xFF) << 24) | ((header[21] & 0xFF) << 16)
                                | ((header[22] & 0xFF) << 8) | (header[23] & 0xFF);
                    }
                }
            }
        } catch (Exception ignored) {
            // 读不到尺寸时退回拉伸，总比不画好
        }
        TEXTURE_SIZE.put(texture, size);
        return size;
    }

    public static void drawThumbnail(GuiGraphics g, Font font, String mapId, int x, int y, int w, int h,
            float alpha, int accent) {
        if (w <= 0 || h <= 0 || alpha <= 0.01f) {
            return;
        }
        ResourceLocation texture = thumbnailTexture(mapId);
        if (texture != null) {
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, Mth.clamp(alpha, 0.0f, 1.0f));
            drawCover(g, texture, x, y, w, h);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.disableBlend();
            return;
        }
        drawThumbnailPlaceholder(g, font, mapId, x, y, w, h, alpha, accent);
    }

    public static void drawThumbnailPlaceholder(GuiGraphics g, Font font, String mapId, int x, int y, int w, int h,
            float alpha, int accent) {
        int a = (int) (255.0f * Mth.clamp(alpha, 0.0f, 1.0f));
        g.fillGradient(x, y, x + w, y + h,
                withAlpha(mix(0xFF1E232C, accent, 0.18f), (int) (a * 0.92f)),
                withAlpha(0xFF0C0F15, (int) (a * 0.95f)));

        // 低多边形味的斜向条纹，暗示"占位图"
        int step = Math.max(8, h / 4);
        for (int i = -h; i < w; i += step) {
            g.fill(x + Math.max(0, i), y, x + Math.min(w, i + 2), y + h, withAlpha(accent, (int) (a * 0.06f)));
        }

        String initial = mapId.isEmpty() ? "?" : String.valueOf(Character.toUpperCase(mapId.charAt(0)));
        PoseStack pose = g.pose();
        pose.pushPose();
        float scale = Math.max(1.0f, Math.min(w, h) / 26.0f);
        pose.translate(x + w / 2.0f, y + h / 2.0f, 0.0f);
        pose.scale(scale, scale, 1.0f);
        g.drawString(font, initial, -font.width(initial) / 2, -font.lineHeight / 2,
                withAlpha(mix(0xFF5C636D, accent, 0.4f), (int) (a * 0.75f)), false);
        pose.popPose();
    }

    /** 没有配色信息的地图（如轮换界面）用 id 推一个稳定的冷色调强调色。 */
    public static int accentFromId(String mapId) {
        int h = mapId.hashCode();
        float t = ((h ^ (h >>> 16)) & 0xFF) / 255.0f;
        return mix(0xFF3E5A78, 0xFF6E4A5A, t);
    }
}
