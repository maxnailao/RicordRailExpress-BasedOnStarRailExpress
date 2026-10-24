package io.wifi.starrailexpress.client;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.client.particle.CustomParticleHandlers;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 神罚弓击杀特效（仅击杀者可见）：箭射死玩家后，服务端只向击杀者发送
 * CustomParticleS2CPayload（id = shenfa_holy_sword，见 ShenfaArrowEntity），
 * 客户端在受害者位置播放时间线——
 *   1) 一把白+黄粒子组成的圣剑从约 16 格高空加速砸下，刺入受害者；
 *   2) 落地瞬间扩散冲击环，剑身短暂驻留并冒出火星；
 *   3) 随后从剑柄顶部开始、消散线逐格向下，剑身自上而下消散。
 * 不新增网络包与粒子类型：形状由 dust 粒子在客户端逐点摆出。
 */
public final class ShenfaHolySwordKillFx {

    /** 服务端约定同一 id：SRE.id("shenfa_holy_sword") */
    public static final ResourceLocation FX_ID = SRE.id("shenfa_holy_sword");

    private static final ParticleOptions WHITE = new DustParticleOptions(new Vector3f(1.00F, 1.00F, 0.95F), 0.9F);
    private static final ParticleOptions GOLD = new DustParticleOptions(new Vector3f(1.00F, 0.85F, 0.25F), 1.1F);

    private static final int FALL_TICKS = 16;
    private static final int HOLD_TICKS = 18;
    private static final int DISSOLVE_TICKS = 34;
    private static final float FALL_HEIGHT = 16.0F;

    private record Point(float x, float y, boolean gold) {}

    private static final List<Point> SWORD_SHAPE = buildSwordShape();
    private static final float SWORD_MAX_Y = 3.15F;

    private static final class Strike {
        final double x, y, z;
        int age = 0;
        Strike(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
    }

    private static final List<Strike> STRIKES = new ArrayList<>();

    private ShenfaHolySwordKillFx() {}

    public static void register() {
        CustomParticleHandlers.register(FX_ID, (level, origin, durationTicks, params) -> {
            System.out.println("[ShenfaFx] handler play at " + origin);
            STRIKES.add(new Strike(origin.x, origin.y, origin.z));
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientLevel level = client.level;
            if (level == null) {
                STRIKES.clear();
                return;
            }
            Iterator<Strike> it = STRIKES.iterator();
            while (it.hasNext()) {
                Strike s = it.next();
                if (s.age >= FALL_TICKS + HOLD_TICKS + DISSOLVE_TICKS) {
                    it.remove();
                    continue;
                }
                play(level, s);
                s.age++;
            }
        });
    }

    private static void play(ClientLevel level, Strike s) {
        int t = s.age;
        if (t < FALL_TICKS) {
            float k = t / (float) FALL_TICKS;
            float lift = FALL_HEIGHT * (1F - k * k); // 加速下坠
            drawSword(level, s, lift, Float.MAX_VALUE);
            for (int i = 0; i < 6; i++) {
                level.addParticle(i % 2 == 0 ? WHITE : GOLD,
                        s.x + (level.random.nextFloat() - 0.5F) * 0.3F,
                        s.y + lift + 0.4F + level.random.nextFloat() * 1.6F,
                        s.z + (level.random.nextFloat() - 0.5F) * 0.3F,
                        0.0D, 0.05D, 0.0D);
            }
        } else if (t == FALL_TICKS) {
            drawSword(level, s, 0F, Float.MAX_VALUE);
            spawnImpact(level, s);
        } else if (t < FALL_TICKS + HOLD_TICKS) {
            drawSword(level, s, 0F, Float.MAX_VALUE);
            for (int i = 0; i < 2; i++) {
                level.addParticle(ParticleTypes.END_ROD,
                        s.x + (level.random.nextFloat() - 0.5F) * 0.5F,
                        s.y + level.random.nextFloat() * SWORD_MAX_Y,
                        s.z + (level.random.nextFloat() - 0.5F) * 0.5F,
                        0.0D, 0.02D, 0.0D);
            }
        } else {
            float p = (t - FALL_TICKS - HOLD_TICKS) / (float) DISSOLVE_TICKS;
            float cutY = SWORD_MAX_Y + 0.3F - p * (SWORD_MAX_Y + 0.6F); // 消散线自上而下扫过
            drawSword(level, s, 0F, cutY);
            for (int i = 0; i < 8; i++) {
                double ang = level.random.nextDouble() * Math.PI * 2.0;
                double r = 0.15 + level.random.nextDouble() * 0.55;
                level.addParticle(level.random.nextBoolean() ? GOLD : WHITE,
                        s.x + Math.cos(ang) * r,
                        s.y + Math.max(cutY, 0.0F) + (level.random.nextDouble() - 0.5) * 0.25,
                        s.z + Math.sin(ang) * r,
                        Math.cos(ang) * 0.04, 0.06 + level.random.nextDouble() * 0.06, Math.sin(ang) * 0.04);
            }
        }
    }

    /** liftY=整体抬升（下坠阶段），cutY=消散线高度（只画 ≤ cutY 的点） */
    private static void drawSword(ClientLevel level, Strike s, float liftY, float cutY) {
        for (Point p : SWORD_SHAPE) {
            if (p.y() > cutY) {
                continue;
            }
            level.addParticle(p.gold() ? GOLD : WHITE,
                    s.x + p.x() + (level.random.nextFloat() - 0.5F) * 0.03F,
                    s.y + p.y() + liftY + (level.random.nextFloat() - 0.5F) * 0.03F,
                    s.z + (level.random.nextFloat() - 0.5F) * 0.03F,
                    0.0D, liftY > 0.0F ? 0.0D : -0.005D, 0.0D);
        }
    }

    /** 砸地瞬间：水平冲击环 + 向上溅射 */
    private static void spawnImpact(ClientLevel level, Strike s) {
        for (int i = 0; i < 24; i++) {
            double ang = Math.PI * 2.0 * i / 24.0;
            ParticleOptions o = i % 3 == 0 ? ParticleTypes.END_ROD : (i % 2 == 0 ? GOLD : WHITE);
            level.addParticle(o,
                    s.x + Math.cos(ang) * 0.2, s.y + 0.05, s.z + Math.sin(ang) * 0.2,
                    Math.cos(ang) * 0.14, 0.04 + level.random.nextDouble() * 0.08, Math.sin(ang) * 0.14);
        }
        for (int i = 0; i < 8; i++) {
            level.addParticle(i % 2 == 0 ? WHITE : GOLD,
                    s.x + (level.random.nextDouble() - 0.5) * 0.3,
                    s.y + level.random.nextDouble() * 0.4,
                    s.z + (level.random.nextDouble() - 0.5) * 0.3,
                    0.0D, 0.12 + level.random.nextDouble() * 0.15, 0.0D);
        }
    }

    /** 剑尖在 y=0（刺入点），向上到剑柄；白芯金刃 + 金色护手/剑首、白色剑柄 */
    private static List<Point> buildSwordShape() {
        List<Point> pts = new ArrayList<>();
        for (float y = 0.12F; y <= 2.15F; y += 0.13F) {
            float halfW = 0.045F + 0.055F * Math.min(1.0F, y / 0.6F);
            pts.add(new Point(-halfW, y, true));
            pts.add(new Point(0.0F, y, false));
            pts.add(new Point(halfW, y, true));
        }
        for (float x = -0.55F; x <= 0.55F; x += 0.11F) {
            pts.add(new Point(x, 2.28F, true));
            pts.add(new Point(x, 2.40F, true));
        }
        for (float y = 2.55F; y <= 2.95F; y += 0.13F) {
            pts.add(new Point(-0.06F, y, false));
            pts.add(new Point(0.06F, y, false));
        }
        pts.add(new Point(-0.08F, 3.05F, true));
        pts.add(new Point(0.0F, 3.12F, true));
        pts.add(new Point(0.08F, 3.05F, true));
        return pts;
    }
}