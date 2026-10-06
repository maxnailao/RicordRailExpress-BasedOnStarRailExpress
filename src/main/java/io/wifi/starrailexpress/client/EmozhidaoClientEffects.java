package io.wifi.starrailexpress.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * 恶魔之刃击杀特效：只在击杀者自己的客户端生成紫粉色粒子 + 全屏粉红滤镜。
 * 粒子从第一人称手持刀的位置（视线前方、略偏下）向外喷发。
 */
public final class EmozhidaoClientEffects {

    private EmozhidaoClientEffects() {}

    private static final Vector3f PURPLE = new Vector3f(0.62f, 0.25f, 1.0f);
    private static final Vector3f PINK = new Vector3f(1.0f, 0.45f, 0.85f);

    // 击杀全屏粉红滤镜：颜色（可改 0xE066FF 更偏紫）、持续时长、峰值不透明度
    private static final int KILL_TINT_COLOR_RGB = 0xFF6ECC;
    private static final long KILL_TINT_DURATION_MS = 3000L;
    private static final float KILL_TINT_MAX_ALPHA = 0.30f;
    private static long killTintStartMs = 0L;

    public static void spawnKillFx() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (mc.level == null || player == null) {
            return;
        }

        // 触发全屏粉红滤镜
        killTintStartMs = System.currentTimeMillis();

        // 估算手持刀的世界坐标：眼睛位置沿视线向前、略微向下
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        Vec3 base = eye.add(look.scale(0.7)).add(0.0, -0.22, 0.0);

        RandomSource rnd = player.getRandom();

        // 紫→粉渐变尘埃粒子爆发
        for (int i = 0; i < 70; i++) {
            DustColorTransitionOptions options =
                    new DustColorTransitionOptions(PURPLE, PINK, 0.8f + rnd.nextFloat() * 0.8f);
            double ox = (rnd.nextDouble() - 0.5) * 0.5;
            double oy = (rnd.nextDouble() - 0.5) * 0.5;
            double oz = (rnd.nextDouble() - 0.5) * 0.5;
            double vx = (rnd.nextDouble() - 0.5) * 0.18;
            double vy = rnd.nextDouble() * 0.14 + 0.02;
            double vz = (rnd.nextDouble() - 0.5) * 0.18;
            mc.level.addParticle(options, base.x + ox, base.y + oy, base.z + oz, vx, vy, vz);
        }

        // 少量高光粒子，增强「特效」观感
        for (int i = 0; i < 14; i++) {
            mc.level.addParticle(ParticleTypes.END_ROD,
                    base.x + (rnd.nextDouble() - 0.5) * 0.5,
                    base.y + (rnd.nextDouble() - 0.5) * 0.5,
                    base.z + (rnd.nextDouble() - 0.5) * 0.5,
                    (rnd.nextDouble() - 0.5) * 0.12,
                    rnd.nextDouble() * 0.1,
                    (rnd.nextDouble() - 0.5) * 0.12);
        }
    }

    /**
     * 渲染击杀后的全屏粉红滤镜：快速到峰值再淡出，持续 {@link #KILL_TINT_DURATION_MS}。
     * 由 SREClient 的 HudRenderCallback 每帧调用。
     */
    public static void renderKillTint(GuiGraphics g) {
        if (killTintStartMs == 0L) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        long elapsed = System.currentTimeMillis() - killTintStartMs;
        if (elapsed >= KILL_TINT_DURATION_MS) {
            killTintStartMs = 0L;
            return;
        }
        float t = (float) elapsed / KILL_TINT_DURATION_MS; // 0..1
        // 击杀瞬间立刻全屏满浓度，之后线性淡出（无淡入）
        float alpha = (1f - t) * KILL_TINT_MAX_ALPHA;
        if (alpha <= 0.003f) {
            return;
        }
        int a = (int) (alpha * 255f);
        int color = (a << 24) | (KILL_TINT_COLOR_RGB & 0xFFFFFF);
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.fill(0, 0, w, h, color);
        RenderSystem.disableBlend();
    }
}