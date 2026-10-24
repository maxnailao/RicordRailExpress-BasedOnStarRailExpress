package io.wifi.starrailexpress.client;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.client.particle.CustomParticleHandlers;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 猩红之爪击杀特效（仅击杀者可见）：服务端击杀判定后发送
 * CustomParticleS2CPayload（id = claw_giant_fist，见 KnifeStabPayload），
 * 客户端时间线——
 *   1) 一只暗红色巨拳（拳面 down、连前臂）从约 10 格高空加速砸向尸体；
 *   2) 落地瞬间扩散猩红冲击环、红屑上溅，拳头驻留并冒红雾；
 *   3) 随后自上而下（前臂→拳面）逐段消散。
 * 与 ShenfaHolySwordKillFx 同款模式：不新增网络包与粒子类型，
 * 形状由 dust 粒子在客户端逐点摆出，全程不进网络循环。
 */
public final class ClawGiantFistKillFx {

    /** 服务端约定同一 id：SRE.id("claw_giant_fist") */
    public static final ResourceLocation FX_ID = SRE.id("claw_giant_fist");

    private static final ParticleOptions DARK = new DustParticleOptions(new Vector3f(0.42F, 0.03F, 0.05F), 1.35F);
    private static final ParticleOptions CRIMSON = new DustParticleOptions(new Vector3f(0.92F, 0.08F, 0.14F), 1.10F);

    private static final int FALL_TICKS = 12;
    private static final int HOLD_TICKS = 12;
    private static final int DISSOLVE_TICKS = 22;
    private static final float FALL_HEIGHT = 10.0F;
    private static final float FIST_MAX_Y = 3.9F;

    private record Point(float x, float y, float z, boolean core) {}

    private static final List<Point> FIST_SHAPE = buildFistShape();

    private static final class Strike {
        final double x, y, z;
        int age = 0;
        Strike(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
    }

    private static final List<Strike> STRIKES = new ArrayList<>();

    private ClawGiantFistKillFx() {}

    public static void register() {
        CustomParticleHandlers.register(FX_ID, (level, origin, durationTicks, params) ->
                STRIKES.add(new Strike(origin.x, origin.y, origin.z)));
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
            drawFist(level, s, lift, Float.MAX_VALUE);
            // 下坠血雾拖尾
            for (int i = 0; i < 5; i++) {
                level.addParticle(i % 2 == 0 ? DARK : CRIMSON,
                        s.x + (level.random.nextFloat() - 0.5F) * 0.5F,
                        s.y + lift + 1.0F + level.random.nextFloat() * 1.8F,
                        s.z + (level.random.nextFloat() - 0.5F) * 0.5F,
                        0.0D, 0.06D, 0.0D);
            }
        } else if (t == FALL_TICKS) {
            drawFist(level, s, 0F, Float.MAX_VALUE);
            spawnImpact(level, s);
        } else if (t < FALL_TICKS + HOLD_TICKS) {
            drawFist(level, s, 0F, Float.MAX_VALUE);
            // 驻留：拳缝冒红雾
            for (int i = 0; i < 2; i++) {
                level.addParticle(level.random.nextBoolean() ? CRIMSON : ParticleTypes.SOUL,
                        s.x + (level.random.nextFloat() - 0.5F) * 0.9F,
                        s.y + 0.4F + level.random.nextFloat() * FIST_MAX_Y,
                        s.z + (level.random.nextFloat() - 0.5F) * 0.9F,
                        0.0D, 0.03D, 0.0D);
            }
        } else {
            float p = (t - FALL_TICKS - HOLD_TICKS) / (float) DISSOLVE_TICKS;
            float cutY = FIST_MAX_Y + 0.3F - p * (FIST_MAX_Y + 0.6F); // 消散线自上而下
            drawFist(level, s, 0F, cutY);
            for (int i = 0; i < 8; i++) {
                double ang = level.random.nextDouble() * Math.PI * 2.0;
                double r = 0.2 + level.random.nextDouble() * 0.7;
                level.addParticle(level.random.nextBoolean() ? CRIMSON : DARK,
                        s.x + Math.cos(ang) * r,
                        s.y + Math.max(cutY, 0.0F) + (level.random.nextDouble() - 0.5) * 0.25,
                        s.z + Math.sin(ang) * r,
                        Math.cos(ang) * 0.04, 0.06 + level.random.nextDouble() * 0.06, Math.sin(ang) * 0.04);
            }
        }
    }

    /** liftY=整体抬升（下坠阶段），cutY=消散线高度（只画 ≤ cutY 的点） */
    private static void drawFist(ClientLevel level, Strike s, float liftY, float cutY) {
        for (Point p : FIST_SHAPE) {
            if (p.y() > cutY) {
                continue;
            }
            level.addParticle(p.core() ? DARK : CRIMSON,
                    s.x + p.x() + (level.random.nextFloat() - 0.5F) * 0.05F,
                    s.y + p.y() + liftY + (level.random.nextFloat() - 0.5F) * 0.05F,
                    s.z + p.z() + (level.random.nextFloat() - 0.5F) * 0.05F,
                    0.0D, liftY > 0.0F ? 0.0D : -0.005D, 0.0D);
        }
    }

    /** 砸中瞬间：水平冲击环 + 红屑上溅 */
    private static void spawnImpact(ClientLevel level, Strike s) {
        for (int i = 0; i < 24; i++) {
            double ang = Math.PI * 2.0 * i / 24.0;
            ParticleOptions o = i % 3 == 0 ? ParticleTypes.END_ROD : (i % 2 == 0 ? CRIMSON : DARK);
            level.addParticle(o,
                    s.x + Math.cos(ang) * 0.25, s.y + 0.05, s.z + Math.sin(ang) * 0.25,
                    Math.cos(ang) * 0.16, 0.04 + level.random.nextDouble() * 0.08, Math.sin(ang) * 0.16);
        }
        for (int i = 0; i < 10; i++) {
            level.addParticle(i % 2 == 0 ? CRIMSON : DARK,
                    s.x + (level.random.nextDouble() - 0.5) * 0.4,
                    s.y + level.random.nextDouble() * 0.5,
                    s.z + (level.random.nextDouble() - 0.5) * 0.4,
                    0.0D, 0.14 + level.random.nextDouble() * 0.18, 0.0D);
        }
    }

    /** 拳心 (0,0.8,0)、半径 0.8 → 拳底 y≈0 正好压在命中点；前臂 y=1.7~3.7 向上延伸 */
    private static List<Point> buildFistShape() {
        List<Point> pts = new ArrayList<>();
        final float r = 0.8F, cy = 0.8F;
        // 拳球：5 个纬圈（含底部拳面）
        for (int la = -60; la <= 60; la += 30) {
            double rad = Math.toRadians(la);
            float yc = cy + r * (float) Math.sin(rad);
            float rr = r * (float) Math.cos(rad);
            for (int i = 0; i < 8; i++) {
                double a = Math.PI * 2.0 * i / 8.0;
                pts.add(new Point(rr * (float) Math.cos(a), yc, rr * (float) Math.sin(a), la == -60 || (i & 1) == 0));
            }
        }
        // 拳核心
        pts.add(new Point(0.0F, cy, 0.0F, true));
        pts.add(new Point(0.3F, cy, 0.0F, true));
        pts.add(new Point(-0.3F, cy, 0.0F, true));
        pts.add(new Point(0.0F, cy, 0.3F, true));
        pts.add(new Point(0.0F, cy, -0.3F, true));
        // 前臂：半径 0.30 的环柱 + 中轴
        for (float y = 1.7F; y <= 3.7F; y += 0.5F) {
            for (int i = 0; i < 8; i++) {
                double a = Math.PI * 2.0 * i / 8.0 + 0.3927 * ((int) ((y - 1.7F) / 0.5F));
                pts.add(new Point(0.30F * (float) Math.cos(a), y, 0.30F * (float) Math.sin(a), false));
            }
            pts.add(new Point(0.0F, y, 0.0F, true));
        }
        return pts;
    }
}