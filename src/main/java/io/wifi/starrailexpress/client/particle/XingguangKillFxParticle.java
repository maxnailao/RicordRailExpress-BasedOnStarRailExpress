package io.wifi.starrailexpress.client.particle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 星光（传承）刀击杀特效：受害者位置长出一只由黑灰粒子组成的张开手掌，
 * 掌心浮现深蓝+白色粒子球，随后手收拢把球捏爆。世界空间特效，所有人可见。
 * 时间线（tick）：0-6 手长出张开 | 6-14 托球 | 14-20 收指捏球 | 20 爆散 | ~28 淡出。
 */
@Environment(EnvType.CLIENT)
public final class XingguangKillFxParticle {

    /** 全局尺度：粒子手 ≈ 人物体积的 3 倍（约 5~6 格高）。
     *  手、球、所有结构偏移与粒子方块尺寸均乘 S，改这一个数即可整体缩放。 */
    private static final float S = 2.75F;

    private XingguangKillFxParticle() {}

    private static float clamp01(float v) { return Mth.clamp(v, 0f, 1f); }

    /** 手掌锚点偏移：球心（相对 spawn 锚点，已乘 S） */
    private static final double ORB_X = 0.0, ORB_Y = 0.16 * S, ORB_Z = 0.0;

    // ============ 手 ============
    /** 五指：{展开角(度), 指长} */
    private static final float[][] FINGERS = {
            {-58f, 0.36f}, {-29f, 0.48f}, {0f, 0.54f}, {29f, 0.46f}, {58f, 0.34f}
    };
    private static final int HAND_COUNT = 26; // 5 指 x 4 点 + 掌心 6 点
    private static final AtomicInteger HAND_SEQ = new AtomicInteger();

    public static class Hand extends TextureSheetParticle {
        private final int idx; // 0..25 角色编号
        private final double ax, ay, az; // 生成锚点：每帧绝对定位用，避免偏移逐帧累加

        Hand(ClientLevel level, double x, double y, double z, int idx) {
            super(level, x, y, z);
            this.idx = idx;
            this.ax = x;
            this.ay = y;
            this.az = z;
            this.lifetime = 40;
            boolean palm = idx >= 20;
            this.quadSize = (palm ? 0.24f : 0.15f) * S;
            this.setSize(0.05f * S, 0.05f * S);
            float gray = level.random.nextIntBetweenInclusive(12, 55) / 255f;
            this.setColor(gray, gray, gray + 0.02f);
            this.alpha = 0.95f;
        }

        /** 张开状态下该点相对锚点的位置；extend=手指伸展比例（长出过程 0→1），全部乘 S */
        private double[] openPos(float extend) {
            if (idx >= 20) { // 掌心椭圆 6 点
                double b = (idx - 20) * Math.PI / 3.0;
                return new double[]{Math.cos(b) * 0.11 * S, (-0.02 + Math.sin(b) * 0.05) * S, Math.sin(b) * 0.07 * S};
            }
            int f = idx / 4, k = idx % 4;
            double a = Math.toRadians(FINGERS[f][0]);
            double len = FINGERS[f][1] * (k + 1) / 4.0 * extend * S;
            return new double[]{Math.sin(a) * (0.09 * S + len * 0.30), 0.04 * S + len, Math.cos(a) * 0.02 * S};
        }

        @Override
        public void tick() {
            this.xo = this.x; this.yo = this.y; this.zo = this.z;
            if (this.age++ >= this.lifetime) { this.remove(); return; }
            float riseT = clamp01(this.age / 6f);            // 从地下长出
            float closeT = clamp01((this.age - 14) / 6f);    // 收指捏球
            double[] open = openPos(riseT);
            double px = open[0], py = open[1], pz = open[2];
            if (this.age >= 6 && this.age < 14) {
                py += Math.sin((this.age + this.idx) * 0.35) * 0.012 * S; // 悬浮微动
            }
            if (closeT > 0f) { // 所有点朝球心收拢，停在球面小圈上
                double gx = ORB_X + (this.idx % 5 - 2) * 0.012 * S;
                double gy = ORB_Y + 0.01 * S;
                double gz = ORB_Z + (this.idx % 3 - 1) * 0.012 * S;
                px = Mth.lerp(closeT, px, gx);
                py = Mth.lerp(closeT, py - (1f - riseT) * 1.5f, gy);
                pz = Mth.lerp(closeT, pz, gz);
            } else {
                py -= (1f - riseT) * 1.5f; // 长出：升起距离随整体再次缩小同步减半
            }
            this.setPos(ax + px, ay + py, az + pz);
            if (this.age >= 26) {
                this.alpha -= 0.12f;
                if (this.alpha <= 0.01f) this.remove();
            }
        }

        @Override
        public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }
    }

    public static class HandFactory implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public HandFactory(SpriteSet sprites) { this.sprites = sprites; }
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                double x, double y, double z, double vx, double vy, double vz) {
            int idx = Math.floorMod(HAND_SEQ.getAndIncrement(), HAND_COUNT);
            Hand p = new Hand(level, x, y, z, idx);
            p.pickSprite(this.sprites);
            return p;
        }
    }

    // ============ 球 ============
    private static final int ORB_COUNT = 14;
    private static final AtomicInteger ORB_SEQ = new AtomicInteger();
    private static final double GOLDEN = Math.PI * (3 - Math.sqrt(5)); // 斐波那契均匀球面点

    public static class Orb extends TextureSheetParticle {
        private final int idx;
        private final double ax, ay, az; // 生成锚点
        private boolean burst = false;

        Orb(ClientLevel level, double x, double y, double z, int idx) {
            super(level, x, y, z);
            this.idx = idx;
            this.ax = x;
            this.ay = y;
            this.az = z;
            this.lifetime = 40;
            this.quadSize = 0.10f * S;
            this.setSize(0.02f * S, 0.02f * S);
            this.setColor(1.0f, 1.0f, 1.0f);                     // 纯白粒子球
            this.alpha = 0f;
        }

        /** 第 idx 点在半径 r 球面（绕 y 自旋 rot）上的偏移 */
        private double[] shellPos(float r, float rot) {
            double t = (idx + 0.5) / ORB_COUNT;
            double ph = Math.acos(1 - 2 * t);
            double th = GOLDEN * idx + rot;
            return new double[]{r * Math.sin(ph) * Math.cos(th), r * Math.cos(ph), r * Math.sin(ph) * Math.sin(th)};
        }

        @Override
        public void tick() {
            this.xo = this.x; this.yo = this.y; this.zo = this.z;
            if (this.age++ >= this.lifetime) { this.remove(); return; }
            if (this.age < 20) {
                float r;
                if (this.age < 6) { r = 0.16f * S; this.alpha = 0f; }
                else if (this.age < 8) { r = 0.16f * S; this.alpha = (this.age - 6) / 2f * 0.95f; }
                else if (this.age < 14) { r = 0.16f * S; this.alpha = 0.95f; }
                else { r = (0.16f - clamp01((this.age - 14) / 6f) * 0.115f) * S; this.alpha = 0.95f; } // 被捏收缩
                double[] p = shellPos(r, this.age * 0.22f);
                this.setPos(ax + ORB_X + p[0], ay + ORB_Y + p[1], az + ORB_Z + p[2]);
            } else if (!this.burst) {
                this.burst = true; // 捏爆：转为径向外抛速度
                double[] dir = shellPos(1f, this.age * 0.22f);
                this.xd = dir[0] * 0.16 * S + random.nextGaussian() * 0.01 * S;
                this.yd = dir[1] * 0.16 * S + 0.03 * S;
                this.zd = dir[2] * 0.16 * S + random.nextGaussian() * 0.01 * S;
            } else {
                this.x += this.xd; this.y += this.yd; this.z += this.zd;
                this.yd *= 0.93; this.xd *= 0.96; this.zd *= 0.96;
                this.alpha -= 0.07f;
                if (this.alpha <= 0.01f) this.remove();
            }
        }

        @Override
        public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }
    }

    public static class OrbFactory implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public OrbFactory(SpriteSet sprites) { this.sprites = sprites; }
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                double x, double y, double z, double vx, double vy, double vz) {
            int idx = Math.floorMod(ORB_SEQ.getAndIncrement(), ORB_COUNT);
            Orb p = new Orb(level, x, y, z, idx);
            p.pickSprite(this.sprites);
            return p;
        }
    }
}