package io.wifi.starrailexpress.client;

import com.mojang.blaze3d.systems.RenderSystem;
import io.wifi.StarRailExpressID;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.client.particle.HandParticle;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.util.ItemSkinManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;

import java.awt.Color;
import java.util.Random;

/**
 * 星光（传承品质）第一人称专属特效：黑灰双色手部粒子环绕刀刃。
 * 使用 HPManager 手部粒子系统（枪械枪口火光同源）：粒子绘制在第一人称
 * 手持模型的渲染矩阵内，因此始终贴合刀身、跟随摆臂与挥刀动画，
 * 不会悬空在视角前。纯本地生成、不发网络包，其他玩家完全看不到。
 *
 * 坐标推导：刀刃模型坐标 c_y=-15(刀尖)~9(刃根)，经 knife_xingguang_in_hand.json
 * 的 display 变换（scale 0.5 → 绕Z转180° → 平移+0.125）映射到手部空间：
 * q_y = 0.125 - 0.5 * ((c_y - 8) / 16)，即刀尖 0.844、刃根 0.094。
 */
public final class XingguangClientEffects {

    private XingguangClientEffects() {}

    /** 与 SRESkinRegistry 注册的皮肤 ID 一致 */
    public static final String SKIN_ID = "knife_xingguang";

    // 环绕参数（手部空间单位）
    private static final float BLADE_TOP = 0.84f;     // 刀尖高度
    private static final float BLADE_BOTTOM = 0.09f;  // 刃根高度
    private static final float ORBIT_RADIUS = 0.10f;  // 环绕半径（离刀轴多远）
    private static final int PARTICLES_PER_TICK = 3;  // 每 tick 生成数

    // 星光音效播放音量：源素材偏小，这里整体放大（MC 允许 >1，每 +1 档约 +6dB）。
    // 若仍偏小可继续加大；若嫌底噪也一起变大，改用 ffmpeg 把 ogg 本身归一化更干净。
    private static final float SOUND_VOLUME = 4.0f;

    private static final Random SLASH_RANDOM = new Random();
    private static final ResourceLocation QI_TEXTURE =
            StarRailExpressID.watheId("textures/particle/gunshot.png");

    // ============ 黑雾附身：一团黑雾从屏幕左侧飞到刀刃，抵达时爆开，随后环绕黑雾接管 ============
    private static final int MIST_BLOB_COUNT = 14;        // 一团包含的粒子数（紧凑堆叠＝看起来是一团）
    private static final float MIST_SPAWN_X = -3.2f;      // 团出生点在屏幕左侧（x 负=左）
    private static final float MIST_BLOB_SPREAD = 0.35f;  // 团的紧凑半径（越小越像一团）
    private static final float MIST_TARGET_Y = 0.45f;     // 汇聚到刀刃中部高度
    private static final float MIST_SPEED = 0.26f;        // 整团飞行速度：配合 MIST_MAX_AGE 刚好抵达刀刃
    private static final int MIST_MAX_AGE = 13;           // 一团飞行存活（＝飞行 tick 数，约 0.65s，与拔刀窗口对齐）
    // 抵达爆开：飞行结束后往四周炸开，强度 1→0 平滑衰减＝顺的雾爆；这几 tick 内仍不出现环绕
    private static final int MIST_BURST_TICKS = 8;        // 爆开持续 tick（更长＝更顺）
    private static final int MIST_BURST_COUNT = 16;       // 爆开首帧粒子数（随后按强度递减）
    private static final float MIST_BURST_SPEED = 0.34f;  // 炸开初速（按强度衰减＝扩散逐渐减速）
    private static final int MIST_BURST_MAX_AGE = 10;     // 爆开粒子存活（>持续tick＝叠成连续雾云）
    private static final Random MIST_RANDOM = new Random();

    private static boolean prevHolding = false;
    private static boolean prevUsing = false;
    private static boolean prevHoldingClaw = false;
    private static boolean prevClawCharging = false;
    private static boolean prevClawAttack = false;
    // 黑雾时序（纯 tick 驱动，独立于渲染帧推进的拔刀窗口，确保抵达爆开后环绕必定回归）
    // stage：0=常驻环绕 1=一团飞行中 2=抵达爆开中（1、2 期间都不出现环绕）
    private static int mistStage = 0;
    private static int mistTimer = 0;
    private static boolean mistSpawned = false;

    // ============ 刀死人后追加的深蓝黑影数量：每击杀一人累加一颗，对局结束由服务端广播清零 ============
    private static int tickCounter = 0;
    private static int darkBlueOrbCount = 0;

    // ============ 击杀后全屏黑灰滤镜（仅击杀者客户端，随 onBladeKill 触发）============
    // 颜色（黑灰，可改 0x000000 纯黑 / 0x2A2A30 更浅）、持续时长、峰值不透明度
    private static final int KILL_TINT_COLOR_RGB = 0x1B1B1F;
    private static final long KILL_TINT_DURATION_MS = 2800L;
    private static final float KILL_TINT_MAX_ALPHA = 0.38f;
    private static long killTintStartMs = 0L;

    /** 击杀者客户端收到通知后调用：刀刃环绕粒子每杀一人多一颗深蓝色黑影 */
    public static void onBladeKill() {
        if (darkBlueOrbCount < 9) {
            darkBlueOrbCount++;
        }
        killTintStartMs = System.currentTimeMillis(); // 触发全屏黑灰滤镜
        // 星光·命中音效：本方法仅由服务端单发给击杀者的 XingguangKillFxS2CPayload 触发，天然只击杀者可闻
        Minecraft mcKill = Minecraft.getInstance();
        if (mcKill.player != null) {
            mcKill.player.playSound(TMMSounds.ITEM_KNIFE_XINGGUANG_KILL, 1.0f, 1.0f);
        }
    }

    /** 收到服务端对局结束广播后调用：清除所有深蓝黑影（下一局从 0 重新累加） */
    public static void clearDarkBlueOrbs() {
        darkBlueOrbCount = 0;
    }

    // ============ 猩红之爪击杀后全屏暗红滤镜（仅击杀者客户端，随 claw 通知触发）============
    // 击杀瞬间立刻全屏满浓度、线性淡出（动作反馈不做淡入）；颜色/时长/浓度可调
    private static final int CLAW_TINT_COLOR_RGB = 0x7A0F1A;
    private static final long CLAW_TINT_DURATION_MS = 2400L;
    private static final float CLAW_TINT_MAX_ALPHA = 0.42f;
    private static long clawTintStartMs = 0L;

    /** 击杀者客户端收到爪子击杀通知后调用：触发全屏暗红滤镜 */
    public static void onClawKill() {
        clawTintStartMs = System.currentTimeMillis();
    }

    /** 渲染爪子击杀后的全屏暗红滤镜：由 SREClient 的 HudRenderCallback 每帧调用 */
    public static void renderClawKillTint(GuiGraphics g) {
        if (clawTintStartMs == 0L) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        long elapsed = System.currentTimeMillis() - clawTintStartMs;
        if (elapsed >= CLAW_TINT_DURATION_MS) {
            clawTintStartMs = 0L;
            return;
        }
        float t = (float) elapsed / CLAW_TINT_DURATION_MS; // 0..1
        float alpha = (1f - t) * CLAW_TINT_MAX_ALPHA;      // 无淡入、直接全屏后淡出
        if (alpha <= 0.003f) {
            return;
        }
        int a = (int) (alpha * 255f);
        int color = (a << 24) | (CLAW_TINT_COLOR_RGB & 0xFFFFFF);
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.fill(0, 0, w, h, color);
        RenderSystem.disableBlend();
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null || client.player == null || client.options == null) {
                return;
            }
            // 刚换到星光刀：刀播放拔刀动作，并启动"一团黑雾从左侧飞来 → 抵达爆开 → 环绕回归"时序
            boolean holding = XingguangPoseManager.knifeActive();
            if (holding && !prevHolding) {
                XingguangPoseManager.startPossess();
                mistStage = 1;
                mistTimer = MIST_MAX_AGE;
                mistSpawned = false;
                // 星光·切刀音效：换到星光刀的那一 tick，仅持刀者本地可闻
                client.player.playSound(TMMSounds.ITEM_KNIFE_XINGGUANG_DRAW, 1.0f, 1.0f);
            }
            prevHolding = holding;
            // 刚切到猩红之爪：播放第一人称切刀(field-flip)。放在第一人称 return 之前，保证状态跟踪不断。
            boolean holdingClaw = client.options.getCameraType().isFirstPerson()
                    && XingguangPoseManager.isXinghongClaw(client.player.getMainHandItem());
            if (holdingClaw && !prevHoldingClaw) {
                ClawFlipManager.start();
            }
            prevHoldingClaw = holdingClaw;
            ClawFlipManager.validateAgainstHand();
            // 爪子蓄力窗口(右键按住 use)：优先级最高，打断切刀/空挥/检视
            boolean clawCharging = holdingClaw && client.player.isUsingItem();
            if (clawCharging && !prevClawCharging) {
                ClawFlipManager.notifyChargeStarted();
            } else if (!clawCharging && prevClawCharging) {
                ClawFlipManager.notifyChargeEnded();
            }
            prevClawCharging = clawCharging;
            // 持爪+左键 → 空挥前划一刀：仅"新按下"边沿触发。
            // 之前用 isDown() 每 tick 轮询，按住左键时每个 tick 都重起空挥，
            // 会把刚按 H 启动的检视瞬间掐死（表现为"检视没了"）。
            boolean clawAttack = holdingClaw && client.options.keyAttack.isDown();
            if (clawAttack && !prevClawAttack) {
                ClawFlipManager.tryStartSwing();
            }
            prevClawAttack = clawAttack;
            // 猩红之爪·转刀特效：蓄力转刀/补转/收刀转/奔跑切换转刀期间，
            // 沿环刃边缘甩出猩红刀气，顺旋转切向飞散成环状拖尾（纯本地，仅自己可见）
            if (holdingClaw && ClawFlipManager.spinFxActive()) {
                float phase = ClawFlipManager.spinFxPhaseDeg();
                for (int i = 0; i < CLAW_QI_PER_TICK; i++) {
                    spawnClawSpinQi(phase + i * (360f / CLAW_QI_PER_TICK));
                }
            }
            // 仅第一人称：手部粒子本来也只在自己视角渲染，这里再省一层空转
            if (!client.options.getCameraType().isFirstPerson()) {
                return;
            }
            if (!hasXingguangEquipped(client.player)) {
                return;
            }

            // 星光·举刀音效：按住右键开始蓄力(举刀)的上升沿触发一次，仅自己可闻。
            // 与举刀动画读同一 isUsingItem 信号，天然同步；挥刀(左键)沿用原版刀音效，不在此处理。
            boolean using = client.player.isUsingItem();
            if (using && !prevUsing) {
                client.player.playSound(TMMSounds.ITEM_KNIFE_XINGGUANG_RAISE, 1.0f, 1.0f);
            }
            prevUsing = using;

            // 纯 tick 驱动的黑雾时序（不依赖渲染帧推进的拔刀窗口，确保爆开后环绕必定回归）
            if (mistStage == 1) {
                // 一团飞行：只在起始发一次，靠粒子存活飞向刀刃；飞行期间不出现环绕
                if (!mistSpawned) {
                    spawnPossessMist();
                    mistSpawned = true;
                }
                if (--mistTimer <= 0) {
                    mistStage = 2; // 抵达 → 爆开
                    mistTimer = MIST_BURST_TICKS;
                }
            } else if (mistStage == 2) {
                // 抵达爆开：强度从 1 平滑衰减到 0，边炸边减速＝顺的雾爆，仍不出现环绕
                float intensity = (float) mistTimer / (float) MIST_BURST_TICKS;
                spawnBurst(intensity);
                if (--mistTimer <= 0) {
                    mistStage = 0; // 爆完 → 环绕回归
                }
            } else {
                tickCounter++;
                int count = 3 + darkBlueOrbCount;
                for (int i = 0; i < count; i++) {
                    spawnOrbitParticle(i, count);
                }
            }

            // 翻腕(拔刀阶段1)或左键劈砍窗口：沿刀刃甩出黑色拖尾（跟刀走，纯本地）
            if (XingguangPoseManager.isSlashing() || XingguangPoseManager.isFlipping()) {
                for (int i = 0; i < 6; i++) {
                    spawnSlashQi();
                }
            }
        });
    }

    private static void spawnOrbitParticle(int index, int total) {
        // 相位均分，绕刀轴旋转
        float a = tickCounter * 0.28f + index * (float) Math.PI * 2f / total;
        // 高度在刃根~刀尖之间正弦扫动，三粒错开相位，让整条刀刃都被环绕
        float sweep = (float) (Math.sin(tickCounter * 0.10f + index * 2.1f) + 1.0) * 0.5f;
        float y = BLADE_BOTTOM + (BLADE_TOP - BLADE_BOTTOM) * sweep;
        float x = (float) Math.cos(a) * ORBIT_RADIUS;
        float z = (float) Math.sin(a) * ORBIT_RADIUS;

        // 黑灰混合：每第 3 粒为灰色，其余近黑；第 4 粒起（每次击杀解锁一颗）为深蓝
        boolean gray = (tickCounter + index) % 3 == 0;
        Color base;
        if (index >= 3) {
            base = new Color(18, 32, 130, 225);
        } else {
            base = gray ? new Color(122, 122, 128, 200) : new Color(20, 20, 24, 220);
        }
        Color fade = new Color(base.getRed(), base.getGreen(), base.getBlue(), 0);

        HandParticle p = new HandParticle()
                .setTexture(StarRailExpressID.watheId("textures/particle/gunshot.png"))
                .setPos(x, y, z)
                .setMaxAge(5)
                .setSize(0.07f + (index % 3) * 0.02f)
                .setVelocity(0f, 0.012f, 0f)
                .setLight(15, 15)
                .setColor(base, fade);
        SREClient.handParticleManager.spawn(p);
    }

    /** 黑色刀气：劈砍时从刃面甩出，朝刀尖方向 + 四周扩散，快速淡出 */
    private static void spawnSlashQi() {
        float r = SLASH_RANDOM.nextFloat();
        float y = BLADE_BOTTOM + (BLADE_TOP - BLADE_BOTTOM) * r;
        float ang = SLASH_RANDOM.nextFloat() * (float) Math.PI * 2f;
        float vx = (float) Math.cos(ang) * 0.03f;
        float vz = (float) Math.sin(ang) * 0.03f;

        Color base = new Color(14, 14, 20, 205);
        Color fade = new Color(14, 14, 20, 0);

        HandParticle p = new HandParticle()
                .setTexture(QI_TEXTURE)
                .setPos((SLASH_RANDOM.nextFloat() - 0.5f) * 0.06f, y, 0f)
                .setMaxAge(6)
                .setSize(0.09f + SLASH_RANDOM.nextFloat() * 0.07f)
                .setVelocity(vx, 0.035f, vz)
                .setLight(15, 15)
                .setColor(base, fade);
        SREClient.handParticleManager.spawn(p);
    }

    /** 黑雾附身：发射一团紧凑黑雾，整团同向从屏幕左侧飞向刀刃中部，抵达即淡出 */
    private static void spawnPossessMist() {
        float ox = MIST_SPAWN_X;
        float oy = MIST_TARGET_Y;
        float oz = 0f;
        // 团中心 → 汇聚点（刀刃中部）的单位方向，整团共用＝飞过来是一团而非散射
        float dx = -ox;
        float dy = MIST_TARGET_Y - oy;
        float dz = -oz;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz) + 1.0E-4f;
        float ux = dx / len;
        float uy = dy / len;
        float uz = dz / len;

        Color base = new Color(10, 10, 16, 235);
        Color fade = new Color(10, 10, 16, 0);

        for (int i = 0; i < MIST_BLOB_COUNT; i++) {
            // 粒子紧凑地堆在团中心周围，形成"一团"
            float jx = (float) (MIST_RANDOM.nextDouble() - 0.5) * MIST_BLOB_SPREAD;
            float jy = (float) (MIST_RANDOM.nextDouble() - 0.5) * MIST_BLOB_SPREAD;
            float jz = (float) (MIST_RANDOM.nextDouble() - 0.5) * MIST_BLOB_SPREAD;
            HandParticle p = new HandParticle()
                    .setTexture(QI_TEXTURE)
                    .setPos(ox + jx, oy + jy, oz + jz)
                    .setMaxAge(MIST_MAX_AGE)
                    .setSize(0.22f + MIST_RANDOM.nextFloat() * 0.12f)
                    .setVelocity(ux * MIST_SPEED, uy * MIST_SPEED, uz * MIST_SPEED)
                    .setLight(6, 6)
                    .setColor(base, fade);
            SREClient.handParticleManager.spawn(p);
        }
    }

    /** 抵达爆开：一团黑雾扑到刀刃中部后向四周炸开，强度由 1→0 平滑衰减，爆完后常驻环绕接管 */
    private static void spawnBurst(float intensity) {
        if (intensity <= 0f) {
            return;
        }
        int n = Math.max(1, (int) Math.round(MIST_BURST_COUNT * intensity));
        float sp = MIST_BURST_SPEED * (0.35f + 0.65f * intensity);
        Color base = new Color(10, 10, 16, (int) (235f * (0.4f + 0.6f * intensity)));
        Color fade = new Color(10, 10, 16, 0);

        for (int i = 0; i < n; i++) {
            double ang = MIST_RANDOM.nextFloat() * (float) Math.PI * 2.0;
            double elev = (MIST_RANDOM.nextDouble() - 0.5) * Math.PI;
            float r = sp * (0.6f + MIST_RANDOM.nextFloat() * 0.4f);
            float vx = (float) (Math.cos(ang) * Math.cos(elev)) * r;
            float vy = (float) Math.sin(elev) * r;
            float vz = (float) (Math.sin(ang) * Math.cos(elev)) * r;

            HandParticle p = new HandParticle()
                    .setTexture(QI_TEXTURE)
                    .setPos(
                            (float) ((MIST_RANDOM.nextDouble() - 0.5) * 0.12),
                            MIST_TARGET_Y + (float) ((MIST_RANDOM.nextDouble() - 0.5) * 0.12),
                            (float) ((MIST_RANDOM.nextDouble() - 0.5) * 0.12))
                    .setMaxAge(MIST_BURST_MAX_AGE)
                    .setSize(0.16f + MIST_RANDOM.nextFloat() * 0.10f)
                    .setVelocity(vx, vy, vz)
                    .setLight(6, 6)
                    .setColor(base, fade);
            SREClient.handParticleManager.spawn(p);
        }
    }

    /**
     * 渲染击杀后的全屏黑灰滤镜：击杀瞬间立刻全屏满浓度，之后线性淡出，持续 {@link #KILL_TINT_DURATION_MS}。
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
        float alpha = (1f - t) * KILL_TINT_MAX_ALPHA;      // 无淡入、直接全屏后淡出
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

    /** 判定与恶魔之刃/暗星统一模式一致：先看物品栈 SKIN 组件，过期组件不否决，回退装备系统实时查询 */
    public static boolean hasXingguangEquipped(Player player) {
        ItemStack main = player.getMainHandItem();
        if (!main.is(TMMItems.KNIFE)) {
            return false;
        }
        if (SKIN_ID.equals(main.get(SREDataComponentTypes.SKIN))) {
            return true;
        }
        return SKIN_ID.equals(ItemSkinManager.getEquippedSkin(player, main));
    }

    // ============ 猩红之爪·转刀刀气 ============
    // 环刃环口朝镜头＝环面平行屏幕，粒子在物品空间 x-y 平面按相位角布点；
    // 出生半径必须大于模型半宽，否则被刀身几何深度测试遮挡（同星光教训）。
    private static final float CLAW_RING_R = 0.24f;      // 环刃外缘半径（手部空间）
    private static final int CLAW_QI_PER_TICK = 4;       // 每 tick 刀气粒数
    private static final Random CLAW_QI_RANDOM = new Random();

    private static void spawnClawSpinQi(float deg) {
        double rad = Math.toRadians(deg + (CLAW_QI_RANDOM.nextFloat() - 0.5f) * 30.0);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);
        float r = CLAW_RING_R + (CLAW_QI_RANDOM.nextFloat() - 0.5f) * 0.05f;
        Color base = new Color(225, 32, 74, 215);   // 猩红
        Color fade = new Color(150, 10, 40, 0);
        HandParticle p = new HandParticle()
                .setTexture(QI_TEXTURE)
                .setPos(cos * r, sin * r, (CLAW_QI_RANDOM.nextFloat() - 0.5f) * 0.06f)
                .setMaxAge(7)
                .setSize(0.08f + CLAW_QI_RANDOM.nextFloat() * 0.06f)
                .setVelocity(-sin * 0.05f, cos * 0.05f, 0f) // 顺旋转切向甩出
                .setLight(15, 15)
                .setColor(base, fade);
        SREClient.handParticleManager.spawn(p);
    }
}