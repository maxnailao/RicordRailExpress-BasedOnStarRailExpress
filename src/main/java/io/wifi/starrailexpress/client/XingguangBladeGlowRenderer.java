package io.wifi.starrailexpress.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.wifi.starrailexpress.client.render.TMMRenderLayers;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * 星光刀刃自发光：加法混合光壳贴着刀刃（手部粒子同一空间：刃根 y=0.09、刀尖 y=0.84）
 * 交叉双平面绘制，挥刀/姿态变换全程跟随，光从刃面透出并越过刃缘向外衰减。
 */
@Environment(EnvType.CLIENT)
public final class XingguangBladeGlowRenderer {

    private XingguangBladeGlowRenderer() {}

    private static final ResourceLocation GLOW_TEX =
            ResourceLocation.parse("starrailexpress:textures/particle/xingguang_blade_glow.png");
    private static RenderType layer = null;

    private static final float Y0 = 0.00f;   // 覆盖刃根以下一点
    private static final float Y1 = 0.96f;   // 越过刀尖
    private static final float HALF = 0.11f; // 光壳横向溢出量（刀半宽约 0.02）

    public static void render(PoseStack stack, MultiBufferSource buffers) {
        if (layer == null) layer = TMMRenderLayers.additiveFullbright(GLOW_TEX);
        float pulse = 0.72f + 0.28f * Mth.sin(System.currentTimeMillis() * 0.004f);
        float i = 0.30f * pulse;
        float r = 0.42f * i, g = 0.42f * i, b = 0.52f * i;
        VertexConsumer vc = buffers.getBuffer(layer);
        Matrix4f m = stack.last().pose();
        // 交叉平面一：XY（正对视角时铺满刃面并溢出刃缘）
        put(vc, m, -HALF, Y0, 0.05f, 0f, 1f, r, g, b);
        put(vc, m,  HALF, Y0, 0.05f, 1f, 1f, r, g, b);
        put(vc, m,  HALF, Y1, 0.05f, 1f, 0f, r, g, b);
        put(vc, m, -HALF, Y1, 0.05f, 0f, 0f, r, g, b);
        // 交叉平面二：ZY（侧转视角时保持发光连续）
        put(vc, m, 0f, Y0, -HALF, 0f, 1f, r, g, b);
        put(vc, m, 0f, Y0,  HALF, 1f, 1f, r, g, b);
        put(vc, m, 0f, Y1,  HALF, 1f, 0f, r, g, b);
        put(vc, m, 0f, Y1, -HALF, 0f, 0f, r, g, b);
    }

    private static void put(VertexConsumer vc, Matrix4f m,
                            float x, float y, float z, float u, float v,
                            float r, float g, float b) {
        vc.addVertex(m, x, y, z).setColor(r, g, b, 1f).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0)
                .setNormal(0f, 1f, 0f);
    }
}