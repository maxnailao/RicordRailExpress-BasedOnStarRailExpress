package io.wifi.starrailexpress.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.wifi.starrailexpress.client.XingguangPoseManager.Pose;
import net.minecraft.client.Minecraft;

/**
 * 猩红之爪"切刀"(field-flip)第一人称专属动画，总时长 2.10s。
 * 绝对时间轴关键帧驱动（纯时间函数，任意帧率轨迹一致），分镜对应：
 *  0.00-0.30 空手(整链停在屏幕外右下+轻微呼吸)
 *  0.30-0.45 方块手+武器从右下快速入画，侧刃(tiltX=72)窄轮廓
 *  0.45-0.65 手腕上抬外翻，刀转正面，移到中下偏右
 *  0.65-0.88 翻刀：spin 逆时针 150° 半圆扫过(绕环心=物品空间原点)，手掌短暂打开、拇指张开
 *  0.88-1.05 水平展示停顿(刀身伸向左侧)，镜头轻推进
 *  1.05-1.25 手指逐根收拢，手腕带刀回落右下
 *  1.25-1.55 前臂回收低位，侧刃投影变窄
 *  1.55-2.10 减速、小回弹，收敛到 0＝交回常驻 display 姿态
 * 三层通道：
 *  相机层 applyCam  (renderArmWithItem HEAD)：臂+刀+手整体走位与小角度腕滚。
 *  物品层 applyItem(renderItem HEAD)       ：只转刀+手——侧刃俯仰 tiltX 与大角度翻腕 spin。
 *  手部   curl()/thumbSpread()             ：手指握紧/张开(0=摊开 1=攥拳；spread 1=拇指张开)。
 */
public final class ClawFlipManager {

    private ClawFlipManager() {}

    private static final long TOTAL_NS = 1_900_000_000L; // 完整剧本时长（视频 2.0s，含 0.1s 缓冲）

    // 相机层枢轴：沿用星光实测拳位（爪子小角度旋转，枢轴误差不放大）
    private static final float CAM_PIVOT_X = 0.32F;
    private static final float CAM_PIVOT_Y = -0.25F;
    // 物品层翻刀枢轴：center_pivot 后环心≈模型中心≈此空间原点。
    // 若实测翻刀时握把整体画小圆漂移，按漂移反方向调这两个值。
    private static final float SPIN_PIVOT_X = 0.0F;
    private static final float SPIN_PIVOT_Y = 0.0F;

    private static boolean running = false;
    private static long startNanos = 0L;
    // 奔跑切刀分流：start() 时玩家在奔跑 → 播"拇指转"剧本，
    // 其关键帧是叠加在 SPRINT 奔跑持刀姿态之上的增量，末帧全零无缝交回。
    private static boolean sprintFlip = false;
    // 第 33 轮：片头空手期压缩，总时长 2.6s→2.2s
    private static final long RUN_TOTAL_NS = 2_200_000_000L;

    // ===== 空挥(左键前划)独立通道：0.45s，按住左键按最小间隔连挥 =====
    private static final long SWING_TOTAL_NS = 450_000_000L;
    private static final long SWING_MIN_GAP_NS = 220_000_000L;
    private static boolean swingRunning = false;
    private static long swingStartNanos = 0L;

    // ===== 优先级：蓄力 > 检视 > 空挥 > 切刀 =====
    // 切刀可被检视/空挥/蓄力打断；空挥与蓄力可打断检视；蓄力窗口期禁止空挥/切刀起手。
    // 检视/蓄力的爪子动画后续接入时，直接复用这些窗口标志，打断规则已就位。
    private static boolean inspecting = false;
    private static boolean charging = false;

    // 检视动画状态：7.5s 剧本（托掌亮刀→绕指连旋7圈·手随刀走→左上举刀展示→回落定格）
    private static final long INSP_TOTAL_NS = 7_500_000_000L;
    private static final float INSP_SPEED = 1.5f; // 播放倍速：1.0 实测偏慢，提到 1.5（实际 5.0s）
    private static boolean inspRunning = false;
    private static long inspStartNanos = 0L;

    public static boolean isInspecting() {
        return inspecting;
    }

    public static boolean camActive() {
        return running || inspRunning || swingRunning || outRunning;
    }

    /** 由 XingguangClientEffects 检测到"刚切到猩红之爪"时调用。
     *  已在播切刀时重复上升沿（皮肤同步闪断导致的假换槽）不再重启，避免动画被截断循环。 */
    public static void start() {
        if (running) {
            return;
        }
        System.out.println("[ClawFlip] start() prev charging=" + charging
                + " inspecting=" + inspecting + " swing=" + swingRunning);
        charging = false;
        inspecting = false;
        inspRunning = false;
        swingRunning = false;
        Minecraft client = Minecraft.getInstance();
        sprintFlip = client.player != null && client.player.isSprinting();
        running = true;
        startNanos = System.nanoTime();
    }

    /** 左键攻击时调用：打断切刀与检视；蓄力期不响应；连挥按最小间隔节流 */
    public static void tryStartSwing() {
        if (charging) {
            return;
        }
        long now = System.nanoTime();
        if (swingRunning && now - swingStartNanos < SWING_MIN_GAP_NS) {
            return;
        }
        running = false;      // 空挥打断切刀
        inspecting = false;   // 空挥打断检视
        inspRunning = false;
        outRunning = false;   // 空挥打断出击剧本
        swingRunning = true;
        swingStartNanos = now;
    }

    /** H 键检视起手：打断切刀与空挥，启动 8.7s 检视剧本 */
    public static void notifyInspectStarted() {
        System.out.println("[ClawFlip] inspect start charging=" + charging);
        if (charging) {
            return; // 蓄力窗口期 H 不起检视（优先级：蓄力 > 检视）
        }
        inspecting = true;
        running = false;
        swingRunning = false;
        inspRunning = true;
        inspStartNanos = System.nanoTime();
    }

    /** 再按 H / 被蓄力打断 / 自然结束：退出检视（末帧≈常驻位，无跳变） */
    public static void notifyInspectEnded() {
        inspecting = false;
        inspRunning = false;
    }

    /** 蓄力上升/下降沿：蓄力打断切刀/检视/空挥，且窗口期禁止二者起手 */
    public static void notifyChargeStarted() {
        charging = true;
        inspecting = false;
        running = false;
        swingRunning = false;
        inspRunning = false;
        outRunning = false;
    }

    public static void notifyChargeEnded() {
        charging = false;
    }

    /** 蓄力释放（由 KnifeItem.releaseUsing 客户端调用，携带真实命中判定）：
     *  命中 → 立即停转刀，快速播放"出击"剧本；未命中 → 什么都不做，
     *  updateChargeSpin 检测到 charging==false 自动补转整圈后恢复持刀。
     *  幂等：charging 已为 false（下降沿先处理过）则忽略。 */
    public static void notifyChargeReleased(boolean hit) {
        if (!charging) {
            return;
        }
        charging = false;
        if (hit) {
            chargeSpin = 0f;
            chargeSpinSlowing = false;
            running = false;
            swingRunning = false;
            inspecting = false;
            inspRunning = false;
            outRunning = true;
            outStartNanos = System.nanoTime();
        }
    }

    /** 打断：直接结束并交回常驻姿态（各通道末帧≈0，跳变极小） */
    public static void cancel() {
        running = false;
        swingRunning = false;
    }

    /** 判定闪断宽限：isXinghongClaw 依赖 getEquippedSkin，组件同步/stack 重写瞬间会闪 false，
     *  连续 4 tick(200ms) 判定失败才认定真离手，防止窗口被同步闪断集体击杀 */
    private static int clawMissTicks = 0;

    /** 由 tick 事件调用：切槽/离开第一人称/主手已不是爪 → 立即结束，防状态僵留 */
    public static void validateAgainstHand() {
        if (!running && !swingRunning && !inspecting && !charging && !inspRunning) {
            clawMissTicks = 0;
            return;
        }
        Minecraft client = Minecraft.getInstance();
        boolean handOk = client.player != null && client.options != null
                && client.options.getCameraType().isFirstPerson()
                && XingguangPoseManager.isXinghongClaw(client.player.getMainHandItem());
        if (handOk) {
            clawMissTicks = 0;
            return;
        }
        if (++clawMissTicks < 4) {
            return; // 闪断容忍窗口：等皮肤数据同步落地，不杀动画
        }
        System.out.println("[ClawFlip] validate cleared windows: running=" + running
                + " insp=" + inspRunning + " swing=" + swingRunning + " charge=" + charging);
        running = false;
        swingRunning = false;
        inspecting = false;
        inspRunning = false;
        charging = false;
        outRunning = false;
    }

    /** 推进并读取本帧经过时间；末帧评完自动结束 */
    private static long elapsed() {
        long total = sprintFlip ? RUN_TOTAL_NS : TOTAL_NS;
        long e = System.nanoTime() - startNanos;
        if (e < 0L) {
            return 0L;
        }
        if (e >= total) {
            running = false;
            return total - 1L;
        }
        return e;
    }

    private static long swingElapsed() {
        long e = System.nanoTime() - swingStartNanos;
        if (e < 0L) {
            return 0L;
        }
        if (e >= SWING_TOTAL_NS) {
            swingRunning = false;
            return SWING_TOTAL_NS - 1L;
        }
        return e;
    }

    private static long inspElapsed() {
        long e = (long) ((System.nanoTime() - inspStartNanos) * INSP_SPEED);
        if (e < 0L) {
            return 0L;
        }
        if (e >= INSP_TOTAL_NS) {
            inspRunning = false;
            inspecting = false; // 剧本播完自动退出检视窗口
            return INSP_TOTAL_NS - 1L;
        }
        return e;
    }

    /** 出击剧本（命中释放）推进：末帧自动结束，增量表全零＝零跳变交回持刀位；
     *  结束时甩一整圈收刀（借蓄力转刀通道 spin 0→360≡0），转完回到当前持刀状态 */
    private static long outElapsed() {
        long e = System.nanoTime() - outStartNanos;
        if (e < 0L) {
            return 0L;
        }
        if (e >= OUT_TOTAL_NS) {
            outRunning = false;
            startPostOutSpin(); // 出刀动作收尾：旋转一周交回持刀姿态
            return OUT_TOTAL_NS - 1L;
        }
        return e;
    }

    /** 出击剧本结束后：刀绕环心完整转一圈再定格（smoothstep 首尾速度 0，末点 360≡0 无跳变）。
     *  奔跑中转完后 SPRINT 通道自行继续切换/保持，两种持刀态都自然接回。 */
    private static void startPostOutSpin() {
        chargeSpin = 0f;
        chargeSpinSlowing = true;
        chargeSlowStartNanos = System.nanoTime();
        chargeSlowStartSpin = 0f;
        chargeSlowRemain = 360f;
        chargeSlowSpanNs = CHARGE_SLOW_NS;
    }

    /** 渲染帧驱动（applyItem 内部调用）：蓄力恒速累积；松手补转整圈后归零 */
    private static float updateChargeSpin() {
        long now = System.nanoTime();
        if (chargeLastNanos == 0L) {
            chargeLastNanos = now;
        }
        float dt = Math.min((now - chargeLastNanos) / 1_000_000_000f, 0.1f);
        chargeLastNanos = now;
        if (charging) {
            chargeSpinSlowing = false;
            chargeSpin += CHARGE_SPIN_DPS * dt;
            if (chargeSpin >= 360_000f) {
                chargeSpin -= 360_000f; // 防长期蓄力浮点精度丢失，保留千圈等效
            }
            return chargeSpin;
        }
        if (!chargeSpinSlowing && chargeSpin == 0f) {
            return 0f; // 空闲；出击收刀转(startPostOutSpin)已置 slowing，不受此拦截
        }
        if (!chargeSpinSlowing) {
            chargeSpinSlowing = true;
            chargeSlowStartNanos = now;
            chargeSlowStartSpin = chargeSpin;
            float mod = chargeSpin % 360f;
            // 至少一整圈 + 恰好落在整圈边界：mod≈0 时正好 360°，否则 720°-mod ∈(360,720)
            chargeSlowRemain = mod < 1f ? 360f : 720f - mod;
            chargeSlowSpanNs = (long) (CHARGE_SLOW_NS * chargeSlowRemain / 360f);
        }
        float u = Math.min((now - chargeSlowStartNanos) / (float) chargeSlowSpanNs, 1f);
        float s = u * u * (3f - 2f * u);
        chargeSpin = chargeSlowStartSpin + chargeSlowRemain * s;
        if (u >= 1f) {
            chargeSpin = 0f; // 整圈 ≡ 0，归零无残留
            chargeSpinSlowing = false;
        }
        return chargeSpin;
    }

    // ===== 时间轴(纳秒)：按参考视频 12 帧(6fps)逐帧还原；中段(翻刀扫掠)压缩提速约 30% =====
    // ===== 切刀关键帧（视频 claw_ref165 165 帧 @82fps 还原：入画→抬腕→甩臂抛爪→空中旋3圈→掌心收拢→定格）=====
    private static final long[] FLIP_T = {
            0L, 250_000_000L, 350_000_000L, 450_000_000L, 550_000_000L,
            700_000_000L, 850_000_000L, 1_000_000_000L, 1_150_000_000L,
            1_350_000_000L, 1_550_000_000L, 1_700_000_000L, TOTAL_NS,
    };
    // 相机层（手+臂）：0.25s 前屏幕外停放→右下水平入画→抬腕→左下伸展甩抛→
    // 0.7-1.15s 手回右下张开等待(爪在空中)→1.35s 接爪归位
    // 注意 Pose 构造器签名＝(rx, ry, rz, tx, ty, tz)：前三旋转(度)，后三平移(格)！
    private static final Pose[] CAM_KF = {
            new Pose(0f, 0f, 0f, 0.62f, -0.80f, 0.20f),     // 0.00 屏幕外停放(平移)
            new Pose(0f, 0f, 0f, 0.62f, -0.80f, 0.20f),     // 0.25 屏幕外(真空手期)
            new Pose(25f, 0f, -8f, -0.02f, -0.12f, 0.05f),  // 0.35 右下水平入画(帧036)
            new Pose(0f, 0f, -4f, 0.05f, 0.08f, -0.02f),    // 0.45 抬腕立起(帧043-050)
            new Pose(5f, 0f, 10f, -0.18f, 0.02f, -0.15f),   // 0.55 左下伸展·甩抛(帧057)
            new Pose(-5f, 0f, 0f, 0.02f, -0.10f, -0.05f),   // 0.70 手回右下张开等待(帧064)
            new Pose(0f, 0f, -3f, 0.05f, -0.08f, -0.03f),   // 0.85 等待(帧071-078)
            new Pose(0f, 0f, -5f, 0.03f, -0.06f, -0.05f),   // 1.00 等待(帧085-092)
            new Pose(0f, 0f, -3f, 0.02f, -0.04f, -0.03f),   // 1.15 准备接爪(帧099)
            new Pose(0f, 0f, 0f, 0f, 0f, 0f),               // 1.35 接爪收拢(帧106-113)
            new Pose(0f, 0f, 0f, 0f, 0f, 0f),               // 1.55 持爪
            new Pose(0f, 0f, 0f, 0f, 0f, 0f),               // 1.70 归位
            new Pose(0f, 0f, 0f, 0f, 0f, 0f),               // 1.90 定格
    };
    // 物品层（爪）：tiltX=rx(第1参)，spin=rz(第3参)；tx/ty 平移让爪飞离手部空转，
    // spin 90→1080(3圈≡0)，1.15s tiltX 立起=穿掌，末帧零跳变交回常驻
    private static final Pose[] ITM_KF = {
            new Pose(55f, 0f, 90f, 0f, 0f, 0f),        // 0.00 屏幕外(水平角度待入画)
            new Pose(55f, 0f, 90f, 0f, 0f, 0f),        // 0.25 屏幕外
            new Pose(50f, 0f, 90f, 0f, 0f, 0f),        // 0.35 入画：水平端入爪尖左
            new Pose(5f, 0f, 100f, 0f, 0f, 0f),        // 0.45 立起竖直
            new Pose(0f, 0f, 120f, -0.05f, 0.10f, 0f), // 0.55 开始离手
            new Pose(15f, 0f, 280f, -0.02f, 0.28f, 0f),// 0.70 空中右上(0.8圈)
            new Pose(0f, 0f, 440f, -0.12f, 0.12f, 0f), // 0.85 空中(1.2圈)
            new Pose(15f, 0f, 600f, -0.22f, 0.22f, 0f),// 1.00 空中左侧(1.7圈)
            new Pose(40f, 0f, 760f, -0.10f, 0.02f, 0f),// 1.15 掌下穿掌(2.1圈)
            new Pose(10f, 0f, 920f, -0.02f, 0.10f, 0f),// 1.35 掌上方(2.6圈)
            new Pose(0f, 0f, 1040f, 0f, 0.03f, 0f),    // 1.55 减速(2.9圈)
            new Pose(0f, 0f, 1080f, 0f, 0f, 0f),       // 1.70 停转竖直(3圈整)
            new Pose(0f, 0f, 1080f, 0f, 0f, 0f),       // 1.90 1080≡0 交回常驻
    };
    // curl：握持→甩抛瞬间松指→空中全程张开等爪→1.35s 收拢抓爪
    private static final float[] CURL_KF = {
            0.95f, 0.95f, 0.90f, 0.85f, 0.30f, 0.10f, 0.08f, 0.10f, 0.15f, 0.70f, 0.85f, 0.60f, 0.55f,
    };
    private static final float[] THUMB_KF = {
            0.00f, 0.00f, 0.10f, 0.30f, 0.90f, 1.00f, 1.00f, 1.00f, 0.90f, 0.40f, 0.10f, 0.00f, 0.00f,
    };

    // ===== 奔跑切刀关键帧（视频 run_flip_ref 164 帧全还原，标题"#10 拇指转"，剧本 2.2s）=====
    // 分镜：0-0.10 空手(屏幕外)→0.10-0.30 大臂入画→0.30-0.45 手张开爪放拇指根→
    // 0.45-1.05 第一段拇指转(逆时针~2.5圈)→1.05-1.20 低位回收(爪近出屏)→
    // 1.20-1.70 第二段再旋~1.5圈→1.70-1.90 抓停→末帧全零＝交回 SPRINT 奔跑持刀姿态
    // 第 33 轮：前段节点大幅提前（片头 0.25/0.50/0.70 → 0.10/0.30/0.45），整条时间轴收紧
    // 注意：本表是 SPRINT 通道之上的【增量】，Pose＝(rx,ry,rz,tx,ty,tz) 前三旋转后三平移
    private static final long[] RUN_T = {
            0L, 100_000_000L, 300_000_000L, 450_000_000L, 650_000_000L,
            850_000_000L, 1_050_000_000L, 1_200_000_000L, 1_450_000_000L,
            1_700_000_000L, 1_900_000_000L, 2_050_000_000L, RUN_TOTAL_NS,
    };
    // 相机层（手）：屏幕外停放→大臂入画→中右开掌→跟旋上抬→右下压→低位回收→
    // 再举跟旋→2.1s 抓停→归零(＝奔跑持刀位)
    private static final Pose[] RUN_CAM_KF = {
            new Pose(0f, 0f, 0f, 0.55f, -0.75f, 0.15f),    // 0.00 屏幕外右下(空手奔跑)
            new Pose(0f, 0f, 0f, 0.55f, -0.75f, 0.15f),    // 0.25 屏幕外(真空手期)
            new Pose(10f, 0f, -6f, -0.05f, -0.15f, 0.05f), // 0.50 大臂入画占右下(帧025-031)
            new Pose(0f, 0f, -4f, 0.02f, 0.05f, 0.00f),    // 0.70 手到中右·张开接爪(帧037-043)
            new Pose(-5f, 0f, 6f, -0.02f, 0.10f, -0.04f),  // 0.95 首旋·手跟抬(帧049-055)
            new Pose(5f, 0f, -4f, 0.04f, -0.02f, 0.00f),   // 1.20 旋·手右移下压(帧061-067)
            new Pose(0f, 0f, 0f, 0.00f, -0.25f, 0.05f),    // 1.45 低位回收·爪近出屏(帧097-103)
            new Pose(-4f, 0f, 4f, -0.02f, 0.02f, 0.00f),   // 1.60 爪回流入画(帧109)
            new Pose(-8f, 0f, 8f, 0.05f, 0.14f, -0.06f),   // 1.85 二段旋·手高举(帧121-133)
            new Pose(0f, 0f, 0f, 0.02f, 0.02f, 0.00f),     // 2.10 抓停(帧139)
            new Pose(0f, 0f, 0f, 0f, 0f, 0f),              // 2.30 归零＝奔跑持刀(帧145-164)
            new Pose(0f, 0f, 0f, 0f, 0f, 0f),              // 2.45 定格
            new Pose(0f, 0f, 0f, 0f, 0f, 0f),              // 2.60 交回 SPRINT 通道
    };
    // 物品层（爪）：spin 逆时针单调累积 1440°(4圈整≡0)；tiltX 全程 45~65°＝拇指转时刀近水平盘转；
    // tx/ty 小幅离掌漂移，末帧零跳变交回奔跑持刀
    private static final Pose[] RUN_ITM_KF = {
            new Pose(60f, 0f, 0f, 0f, 0f, 0f),             // 0.00 屏幕外水平角
            new Pose(60f, 0f, 0f, 0f, 0f, 0f),             // 0.25 屏幕外
            new Pose(55f, 0f, 20f, 0f, 0f, 0f),            // 0.50 入画
            new Pose(45f, 0f, 90f, 0.02f, -0.02f, 0f),     // 0.70 放到拇指根
            new Pose(60f, 0f, 420f, -0.02f, 0.06f, 0f),    // 0.95 掌面上旋(1.2圈)
            new Pose(50f, 0f, 780f, 0.03f, 0.02f, 0f),     // 1.20 左侧下侧(2.2圈)
            new Pose(65f, 0f, 900f, 0f, -0.10f, 0f),       // 1.45 低位(2.5圈)
            new Pose(55f, 0f, 1020f, -0.02f, 0.04f, 0f),   // 1.60 回流入画(2.8圈)
            new Pose(60f, 0f, 1300f, 0.02f, 0.06f, 0f),    // 1.85 二段高举(3.6圈)
            new Pose(20f, 0f, 1410f, 0f, 0.02f, 0f),       // 2.10 抓停(3.9圈)
            new Pose(0f, 0f, 1440f, 0f, 0f, 0f),           // 2.30 立定(4圈整≡0)
            new Pose(0f, 0f, 1440f, 0f, 0f, 0f),           // 2.45
            new Pose(0f, 0f, 1440f, 0f, 0f, 0f),           // 2.60 交回常驻
    };
    // curl：空手握→入画半开→两段旋转全程五指张开→2.1s 收拢抓爪→奔跑持刀握位 0.70
    private static final float[] RUN_CURL = {
            0.95f, 0.95f, 0.85f, 0.40f, 0.10f, 0.12f, 0.10f, 0.15f,
            0.10f, 0.85f, 0.70f, 0.70f, 0.70f,
    };
    private static final float[] RUN_THUMB = {
            0.00f, 0.00f, 0.20f, 0.80f, 1.00f, 1.00f, 1.00f, 1.00f,
            1.00f, 0.50f, 0.30f, 0.30f, 0.30f,
    };
    private static final float[][] RCAM_Y = compsOf(RUN_CAM_KF);
    private static final float[][] RCAM_D = tangentsOf(RCAM_Y, RUN_T);
    private static final float[][] RITM_Y = compsOf(RUN_ITM_KF);
    private static final float[][] RITM_D = tangentsOf(RITM_Y, RUN_T);
    private static final float[] RCURL_D = tangents(RUN_CURL, RUN_T);
    private static final float[] RTHUMB_D = tangents(RUN_THUMB, RUN_T);

    // ===== 样条缓存：Fritsch-Carlson 单调三次插值，逐点切线预计算 =====
    // 相比逐段 easeInOut：段与段之间速度连续，144+ 帧下无"停一下再冲一下"的分段感；
    // 单调钳制(局部极值切线归零)保证驻留段(空手停泊/结尾定格)不过冲回弹。
    private static final float[][] CAM_Y = compsOf(CAM_KF);
    private static final float[][] CAM_D = tangentsOf(CAM_Y, FLIP_T);
    private static final float[][] ITM_Y = compsOf(ITM_KF);
    private static final float[][] ITM_D = tangentsOf(ITM_Y, FLIP_T);
    private static final float[] CURL_D = tangents(CURL_KF, FLIP_T);
    private static final float[] THUMB_D = tangents(THUMB_KF, FLIP_T);

    // ===== 空挥关键帧：0 蓄力回拽 → 100ms 后拉到位 → 200ms 猛力前划 → 300ms 过冲 → 450ms 归位 =====
    private static final long[] SWING_T = {
            0L, 100_000_000L, 200_000_000L, 300_000_000L, SWING_TOTAL_NS,
    };
    // 相机层：tz 负=朝屏幕外(前刺)，rz 正=逆时针腕扫，前划轨迹走右下→左下弧线
    private static final Pose[] SWING_CAM_KF = {
            new Pose(0f, 0f, 0f, 0f, 0f, 0f),     // 0.00 常驻位
            new Pose(0f, 0f, -8f, 0.05f, 0.08f, 0.12f),    // 0.10 蓄力：抬腕后拽(藏刀)
            new Pose(0f, 0f, 25f, -0.12f, -0.10f, -0.38f), // 0.20 猛力前划：冲向屏幕外+左扫
            new Pose(0f, 0f, 30f, -0.16f, -0.16f, -0.30f), // 0.30 顺势过冲(左下)
            new Pose(0f, 0f, 0f, 0f, 0f, 0f),     // 0.45 归位
    };
    // 物品层：tiltX 俯仰让刃面斜切，spin 小角度腕扫加强划痕
    private static final Pose[] SWING_ITM_KF = {
            new Pose(0f, 0f, 0f, 0f, 0f, 0f),    // 0.00
            new Pose(15f, 0f, -15f, 0f, 0f, 0f), // 0.10 后仰藏刀，刃面朝上
            new Pose(-20f, 0f, 35f, 0f, 0f, 0f), // 0.20 下切+逆时针划
            new Pose(-10f, 0f, 45f, 0f, 0f, 0f), // 0.30 划痕到底
            new Pose(0f, 0f, 0f, 0f, 0f, 0f),    // 0.45
    };
    private static final float[][] SCAM_Y = compsOf(SWING_CAM_KF);
    private static final float[][] SCAM_D = tangentsOf(SCAM_Y, SWING_T);
    private static final float[][] SITM_Y = compsOf(SWING_ITM_KF);
    private static final float[][] SITM_D = tangentsOf(SITM_Y, SWING_T);

    // ===== 检视关键帧（视频 inspect_ref3 165 帧 @19fps 逐帧还原：托掌亮刀→两段绕指连旋→左上展示→归位）=====
    // 剧本时间 = 视频时间 - 0.6s（跳过片头 idle）
    private static final long[] INSP_T = {
            0L, 400_000_000L, 900_000_000L, 1_400_000_000L, 1_900_000_000L,
            2_400_000_000L, 3_000_000_000L, 3_600_000_000L, 4_200_000_000L,
            4_800_000_000L, 5_400_000_000L, 6_000_000_000L, 6_400_000_000L,
            6_800_000_000L, INSP_TOTAL_NS,
    };
    // 相机层（手）：全程"手随刀走"——中下托掌(0.4)→右侧跟旋(1.4)→回中(1.9-3.0)→
    // 右侧绕指高举(3.6)→手背朝向(4.2)→中左连旋(4.8-5.4)→右下减速(6.0)→左上举刀展示(6.4)→回落定格
    // 注意 Pose 构造器签名＝(rx, ry, rz, tx, ty, tz)：前三旋转(度)，后三平移(格)；tx 负=左，ty 正=上
    private static final Pose[] INSP_CAM_KF = {
            new Pose(0f, 0f, 0f, 0f, 0f, 0f),            // 0.00 常驻
            new Pose(0f, 0f, -6f, -0.16f, -0.02f, 0.02f), // 0.40 移到中下·托掌亮刀(帧017-021)
            new Pose(2f, 0f, -4f, -0.10f, 0.00f, 0.00f),  // 0.90 首抛起旋·手略回右(帧025-029)
            new Pose(-4f, 0f, 6f, -0.06f, 0.08f, -0.04f), // 1.40 抬手指右上·刀绕指(帧037-041)
            new Pose(0f, 0f, -2f, -0.12f, 0.02f, 0.00f),  // 1.90 回落中下接旋(帧049-053)
            new Pose(-2f, 0f, 2f, -0.14f, 0.04f, -0.02f), // 2.40 掌心朝上托刀(帧057-061)
            new Pose(0f, 4f, 0f, -0.08f, 0.04f, 0.00f),   // 3.00 手位微右移(帧065-069)
            new Pose(-6f, 0f, 8f, 0.04f, 0.12f, -0.06f),  // 3.60 右侧高举·绕指(帧073-077)
            new Pose(0f, -4f, -4f, -0.02f, 0.06f, -0.02f),// 4.20 手背朝向镜头·刀过掌背(帧085-089)
            new Pose(-4f, 0f, 4f, -0.10f, 0.08f, -0.04f), // 4.80 回中·指间夹旋(帧097-101)
            new Pose(-6f, 0f, 6f, 0.02f, 0.12f, -0.06f),  // 5.40 再举起·刀掌面水平旋(帧109-117)
            new Pose(2f, 0f, -4f, -0.12f, -0.02f, 0.00f), // 6.00 落右下·减速(帧125-129)
            new Pose(-10f, 0f, 6f, -0.22f, 0.24f, -0.08f),// 6.40 左上举刀展示(帧133)★视频标志动作
            new Pose(0f, 0f, 0f, -0.04f, 0.02f, 0.00f),   // 6.80 回右下·爪立起(帧141)
            new Pose(0f, 0f, 0f, 0f, 0f, 0f),             // 7.50 定格交回常驻
    };
    // 物品层（爪）：tiltX=rx 全程 55~85°＝刀近水平绕竖轴转（视频绕指转刀形态），
    // 收尾 6.4s 展示后立起归 0；spin=rz 单调累积 2520°(7圈整≡0)零跳变交回常驻；
    // tx/ty 只做小幅"离掌"漂移（刀贴掌心转，不飞远）
    private static final Pose[] INSP_ITM_KF = {
            new Pose(0f, 0f, 0f, 0f, 0f, 0f),             // 0.00
            new Pose(75f, 0f, 30f, 0f, -0.02f, 0f),       // 0.40 托掌放平(帧017)
            new Pose(70f, 0f, 250f, 0.02f, -0.04f, 0f),   // 0.90 首抛(0.7圈)
            new Pose(60f, 0f, 520f, -0.02f, 0.06f, 0f),   // 1.40 绕指上旋(1.4圈)
            new Pose(75f, 0f, 760f, 0.02f, 0.02f, 0f),    // 1.90 过掌背(2.1圈)
            new Pose(65f, 0f, 980f, -0.02f, 0.05f, 0f),   // 2.40 掌心续旋(2.7圈)
            new Pose(80f, 0f, 1200f, 0.02f, 0.03f, 0f),   // 3.00 水平盘转(3.3圈)
            new Pose(60f, 0f, 1440f, -0.02f, 0.08f, 0f),  // 3.60 高举绕指(4.0圈)
            new Pose(85f, 0f, 1680f, 0.02f, 0.02f, 0f),   // 4.20 手背侧盘转(4.7圈)
            new Pose(65f, 0f, 1920f, -0.02f, 0.05f, 0f),  // 4.80 指根夹旋(5.3圈)
            new Pose(80f, 0f, 2160f, 0.02f, 0.03f, 0f),   // 5.40 掌面大盘(6.0圈)
            new Pose(55f, 0f, 2380f, -0.02f, 0.04f, 0f),  // 6.00 减速(6.6圈)
            new Pose(35f, 0f, 2500f, 0f, 0.05f, 0f),      // 6.40 抓停·随举刀斜指左上
            new Pose(0f, 0f, 2520f, 0f, 0.02f, 0f),       // 6.80 立起(7圈整)
            new Pose(0f, 0f, 2520f, 0f, 0f, 0f),          // 7.50 2520≡0 交回常驻
    };
    // curl：起手即摊开手掌，全程手指微张绕刀旋转，6.0s 后才收拢抓停
    private static final float[] INSP_CURL = {
            0.55f, 0.10f, 0.05f, 0.12f, 0.05f, 0.10f, 0.05f, 0.12f,
            0.05f, 0.10f, 0.08f, 0.20f, 0.85f, 0.60f, 0.55f,
    };
    private static final float[] INSP_THUMB = {
            0.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f,
            1.00f, 1.00f, 1.00f, 0.90f, 0.30f, 0.00f, 0.00f,
    };
    private static final float[][] ICAM_Y = compsOf(INSP_CAM_KF);
    private static final float[][] ICAM_D = tangentsOf(ICAM_Y, INSP_T);
    private static final float[][] IITM_Y = compsOf(INSP_ITM_KF);
    private static final float[][] IITM_D = tangentsOf(IITM_Y, INSP_T);
    private static final float[] ICURL_D = tangents(INSP_CURL, INSP_T);
    private static final float[] ITHUMB_D = tangents(INSP_THUMB, INSP_T);

    // ===== 出击剧本（蓄力命中释放，快速 ~0.42s）=====
    // ★占位分镜：回拽蓄腕 → 猛力前刺(tz 向屏幕内 -Z) → 顺势过冲 → 归位。
    // 待按 出击.mp4 逐帧还原精确关键帧（参考 run_flip_ref 提帧流程）。
    // 增量表末帧全零：常态持刀/奔跑持刀两种基底上都能零跳变收尾。
    private static final long OUT_TOTAL_NS = 420_000_000L;
    private static boolean outRunning = false;
    private static long outStartNanos = 0L;
    private static final long[] OUT_T = {
            0L, 90_000_000L, 190_000_000L, 280_000_000L, OUT_TOTAL_NS,
    };
    private static final Pose[] OUT_CAM_KF = {
            new Pose(0f, 0f, 0f, 0f, 0f, 0f),               // 0.00 持刀位
            new Pose(0f, 0f, -8f, 0.08f, 0.06f, 0.12f),     // 0.09 回拽右下蓄腕
            new Pose(0f, 0f, 14f, -0.10f, -0.04f, -0.42f),  // 0.19 猛力前刺(-Z 向屏幕内)
            new Pose(0f, 0f, 18f, -0.13f, -0.08f, -0.34f),  // 0.28 顺势过冲
            new Pose(0f, 0f, 0f, 0f, 0f, 0f),               // 0.42 归位＝交回持刀位
    };
    private static final Pose[] OUT_ITM_KF = {
            new Pose(0f, 0f, 0f, 0f, 0f, 0f),             // 0.00
            new Pose(18f, 0f, -25f, 0f, 0.02f, 0f),       // 0.09 后拽仰刃
            new Pose(-30f, 0f, 60f, 0f, 0f, -0.05f),      // 0.19 压刃前甩
            new Pose(-15f, 0f, 80f, 0f, 0f, 0f),          // 0.28 顺转到底
            new Pose(0f, 0f, 0f, 0f, 0f, 0f),             // 0.42 归零
    };
    private static final float[][] OCAM_Y = compsOf(OUT_CAM_KF);
    private static final float[][] OCAM_D = tangentsOf(OCAM_Y, OUT_T);
    private static final float[][] OITM_Y = compsOf(OUT_ITM_KF);
    private static final float[][] OITM_D = tangentsOf(OITM_Y, OUT_T);

    private static float[][] compsOf(Pose[] kfs) {
        int n = kfs.length;
        float[][] y = new float[6][n];
        for (int i = 0; i < n; i++) {
            Pose p = kfs[i];
            y[0][i] = p.rx;
            y[1][i] = p.ry;
            y[2][i] = p.rz;
            y[3][i] = p.tx;
            y[4][i] = p.ty;
            y[5][i] = p.tz;
        }
        return y;
    }

    private static float[][] tangentsOf(float[][] comps, long[] ts) {
        float[][] d = new float[comps.length][];
        for (int c = 0; c < comps.length; c++) {
            d[c] = tangents(comps[c], ts);
        }
        return d;
    }

    /** 按非均匀时间轴求每个关键点的中心差分切线，并做单调性钳制 */
    private static float[] tangents(float[] ys, long[] ts) {
        int n = ys.length;
        float[] m = new float[n - 1];
        for (int i = 0; i < n - 1; i++) {
            m[i] = (ys[i + 1] - ys[i]) / (float) (ts[i + 1] - ts[i]);
        }
        float[] d = new float[n];
        d[0] = m[0];
        d[n - 1] = 0f; // 末帧静止：平滑停在常驻姿态
        for (int i = 1; i < n - 1; i++) {
            if (m[i - 1] * m[i] <= 0f) {
                d[i] = 0f; // 局部极值/驻留段：切线归零防过冲
            } else {
                float avg = 0.5f * (m[i - 1] + m[i]);
                float lim = 3f * Math.min(Math.abs(m[i - 1]), Math.abs(m[i]));
                d[i] = Math.copySign(Math.min(Math.abs(avg), lim), avg);
            }
        }
        return d;
    }

    /** Hermite 基求值：定位所在段后按 (值, 切线) 插值，时间参数化，帧率无关 */
    private static float evalAt(float[] ys, float[] ds, long e, long[] ts) {
        int n = ys.length;
        int i = 0;
        while (i < n - 1 && e >= ts[i + 1]) {
            i++;
        }
        if (i >= n - 1) {
            return ys[n - 1];
        }
        float span = (float) (ts[i + 1] - ts[i]);
        float u = span > 0f ? (float) (e - ts[i]) / span : 1f;
        float u2 = u * u;
        float u3 = u2 * u;
        return (2f * u3 - 3f * u2 + 1f) * ys[i]
                + (u3 - 2f * u2 + u) * span * ds[i]
                + (-2f * u3 + 3f * u2) * ys[i + 1]
                + (u3 - u2) * span * ds[i + 1];
    }

    private static Pose evalPose(float[][] ys, float[][] ds, long e, long[] ts, Pose out) {
        out.rx = evalAt(ys[0], ds[0], e, ts);
        out.ry = evalAt(ys[1], ds[1], e, ts);
        out.rz = evalAt(ys[2], ds[2], e, ts);
        out.tx = evalAt(ys[3], ds[3], e, ts);
        out.ty = evalAt(ys[4], ds[4], e, ts);
        out.tz = evalAt(ys[5], ds[5], e, ts);
        return out;
    }

    private static final Pose camScratch = new Pose(0, 0, 0, 0, 0, 0);
    private static final Pose itmScratch = new Pose(0, 0, 0, 0, 0, 0);
    private static int debugFrame = 0;

    /** renderArmWithItem HEAD：臂+刀+手整链走位。空手段叠加极轻呼吸。 */
    public static void applyCam(PoseStack g) {
        if (outRunning) {
            Pose kf = evalPose(OCAM_Y, OCAM_D, outElapsed(), OUT_T, camScratch);
            g.translate(kf.tx, kf.ty, kf.tz);
            g.translate(CAM_PIVOT_X, CAM_PIVOT_Y, 0f);
            g.mulPose(Axis.XP.rotationDegrees(kf.rx));
            g.mulPose(Axis.YP.rotationDegrees(kf.ry));
            g.mulPose(Axis.ZP.rotationDegrees(kf.rz));
            g.translate(-CAM_PIVOT_X, -CAM_PIVOT_Y, 0f);
            return;
        }
        if (running) {
            long e = elapsed();
            Pose kf = sprintFlip
                    ? evalPose(RCAM_Y, RCAM_D, e, RUN_T, camScratch)
                    : evalPose(CAM_Y, CAM_D, e, FLIP_T, camScratch);
            float breath = (!sprintFlip && e < 250_000_000L)
                    ? 0.006f * (float) Math.sin(e * 1.2566e-8) : 0f;
            g.translate(kf.tx, kf.ty + breath, kf.tz);
            g.translate(CAM_PIVOT_X, CAM_PIVOT_Y, 0f);
            g.mulPose(Axis.XP.rotationDegrees(kf.rx));
            g.mulPose(Axis.YP.rotationDegrees(kf.ry));
            g.mulPose(Axis.ZP.rotationDegrees(kf.rz));
            g.translate(-CAM_PIVOT_X, -CAM_PIVOT_Y, 0f);
            return;
        }
        if (inspRunning) {
            long e = inspElapsed();
            Pose kf = evalPose(ICAM_Y, ICAM_D, e, INSP_T, camScratch);
            if ((debugFrame++ & 63) == 0) {
                System.out.println("[ClawFlip] insp applyCam e=" + (e / 1_000_000L)
                        + "ms t=" + kf.tx + "," + kf.ty + "," + kf.tz
                        + " r=" + kf.rx + "," + kf.rz);
            }
            g.translate(kf.tx, kf.ty, kf.tz);
            g.translate(CAM_PIVOT_X, CAM_PIVOT_Y, 0f);
            g.mulPose(Axis.XP.rotationDegrees(kf.rx));
            g.mulPose(Axis.YP.rotationDegrees(kf.ry));
            g.mulPose(Axis.ZP.rotationDegrees(kf.rz));
            g.translate(-CAM_PIVOT_X, -CAM_PIVOT_Y, 0f);
            return;
        }
        if (!swingRunning) {
            return;
        }
        Pose kf = evalPose(SCAM_Y, SCAM_D, swingElapsed(), SWING_T, camScratch);
        g.translate(kf.tx, kf.ty, kf.tz);
        g.translate(CAM_PIVOT_X, CAM_PIVOT_Y, 0f);
        g.mulPose(Axis.XP.rotationDegrees(kf.rx));
        g.mulPose(Axis.YP.rotationDegrees(kf.ry));
        g.mulPose(Axis.ZP.rotationDegrees(kf.rz));
        g.translate(-CAM_PIVOT_X, -CAM_PIVOT_Y, 0f);
    }

    /** renderItem HEAD：只转刀+手——侧刃俯仰与绕环大角度翻腕，手臂不跟着甩。 */
    public static void applyItem(PoseStack g) {
        // 蓄力无限转刀：右键按住期间恒速绕环心旋转，窗口期其他剧本已被打断清空
        float cs = updateChargeSpin();
        if (cs != 0f) {
            g.translate(SPIN_PIVOT_X, SPIN_PIVOT_Y, 0f);
            g.mulPose(Axis.ZP.rotationDegrees(cs));
            g.translate(-SPIN_PIVOT_X, -SPIN_PIVOT_Y, 0f);
        }
        if (outRunning) {
            Pose kf = evalPose(OITM_Y, OITM_D, outElapsed(), OUT_T, itmScratch);
            g.translate(kf.tx, kf.ty, kf.tz);
            g.translate(SPIN_PIVOT_X, SPIN_PIVOT_Y, 0f);
            g.mulPose(Axis.XP.rotationDegrees(kf.rx));
            g.mulPose(Axis.YP.rotationDegrees(kf.ry));
            g.mulPose(Axis.ZP.rotationDegrees(kf.rz));
            g.translate(-SPIN_PIVOT_X, -SPIN_PIVOT_Y, 0f);
            return;
        }
        if (running) {
            long e = elapsed();
            Pose kf = sprintFlip
                    ? evalPose(RITM_Y, RITM_D, e, RUN_T, itmScratch)
                    : evalPose(ITM_Y, ITM_D, e, FLIP_T, itmScratch);
            g.translate(kf.tx, kf.ty, kf.tz);
            g.translate(SPIN_PIVOT_X, SPIN_PIVOT_Y, 0f);
            g.mulPose(Axis.XP.rotationDegrees(kf.rx));
            g.mulPose(Axis.YP.rotationDegrees(kf.ry));
            g.mulPose(Axis.ZP.rotationDegrees(kf.rz));
            g.translate(-SPIN_PIVOT_X, -SPIN_PIVOT_Y, 0f);
            return;
        }
        if (inspRunning) {
            Pose kf = evalPose(IITM_Y, IITM_D, inspElapsed(), INSP_T, itmScratch);
            g.translate(kf.tx, kf.ty, kf.tz);
            g.translate(SPIN_PIVOT_X, SPIN_PIVOT_Y, 0f);
            g.mulPose(Axis.XP.rotationDegrees(kf.rx));
            g.mulPose(Axis.YP.rotationDegrees(kf.ry));
            g.mulPose(Axis.ZP.rotationDegrees(kf.rz));
            g.translate(-SPIN_PIVOT_X, -SPIN_PIVOT_Y, 0f);
            return;
        }
        if (!swingRunning) {
            return;
        }
        Pose kf = evalPose(SITM_Y, SITM_D, swingElapsed(), SWING_T, itmScratch);
        g.translate(kf.tx, kf.ty, kf.tz);
        g.translate(SPIN_PIVOT_X, SPIN_PIVOT_Y, 0f);
        g.mulPose(Axis.XP.rotationDegrees(kf.rx));
        g.mulPose(Axis.YP.rotationDegrees(kf.ry));
        g.mulPose(Axis.ZP.rotationDegrees(kf.rz));
        g.translate(-SPIN_PIVOT_X, -SPIN_PIVOT_Y, 0f);
    }

    /** 手指弯曲度；返回 <0 表示不在动画窗口(手部走常规逻辑，空挥也走常规攥拳)。 */
    public static float curl() {
        if (running) {
            return sprintFlip
                    ? evalAt(RUN_CURL, RCURL_D, elapsed(), RUN_T)
                    : evalAt(CURL_KF, CURL_D, elapsed(), FLIP_T);
        }
        if (inspRunning) {
            return evalAt(INSP_CURL, ICURL_D, inspElapsed(), INSP_T);
        }
        return -1f;
    }

    /** 拇指张开度 0~1。 */
    public static float thumbSpread() {
        if (running) {
            return sprintFlip
                    ? evalAt(RUN_THUMB, RTHUMB_D, elapsed(), RUN_T)
                    : evalAt(THUMB_KF, THUMB_D, elapsed(), FLIP_T);
        }
        if (inspRunning) {
            return evalAt(INSP_THUMB, ITHUMB_D, inspElapsed(), INSP_T);
        }
        return 0f;
    }

    /** 特效采样：当前是否有转刀在进行（蓄力转刀/补转/收刀转/切换转刀） */
    public static boolean spinFxActive() {
        return chargeSpin != 0f || sprintSpin != 0f;
    }

    /** 特效采样：当前合成转刀相位(度)。粒子按此沿环刃边缘布点，拖尾跟着刀走 */
    public static float spinFxPhaseDeg() {
        return (chargeSpin + sprintSpin) % 360f;
    }

    // ===== 蓄力转刀通道：右键按住(isUsingItem)期间刀绕环心恒速无限旋转 =====
    // 未命中松手：沿当前方向补转【至少一整圈】后停在整圈边界(≡0°)，零跳变恢复持刀；
    // 命中松手由 notifyChargeReleased(true) 直接接管为出击剧本，不走这里的减速。
    // 调转速改 CHARGE_SPIN_DPS(度/秒)；调停刀快慢改 CHARGE_SLOW_NS(每圈时长)
    // 第 32 轮：定为每秒 2.5 圈 → 900°/s
    private static final float CHARGE_SPIN_DPS = 900f;
    private static final long CHARGE_SLOW_NS = 350_000_000L;
    private static float chargeSpin = 0f;
    private static boolean chargeSpinSlowing = false;
    private static float chargeSlowStartSpin = 0f;
    private static float chargeSlowRemain = 0f;
    private static long chargeSlowStartNanos = 0L;
    private static long chargeSlowSpanNs = 0L;
    private static long chargeLastNanos = 0L;

    // ===== 奔跑持刀通道（参考 奔跑持刀动作.jpg：环口在拳上朝镜头、爪尖朝下垂、前臂内收弓起）=====
    // 附加式叠加：不与切刀/检视/空挥互斥，奔跑切刀从奔跑姿态起手，动画结束后仍保持奔跑持刀。
    // 指数收敛过渡（半衰期 0.12s），开始/停止奔跑均平滑进出，帧率无关。
    // 实测校准十一轮：(65,180,-40) 形态已正确（环口朝镜头+刀刃下垂），仅刀身斜 30°
    // （柄右上/刃左下），rz -40→-20 顺时针回正 → 柄正上、刃正下完全垂直
    // 重构思路（第 12 轮）：ITM(65,180,-20) 的刀+自定义手形态已正确，问题在原版手臂
    // 没跟上——物品层 180° 带着自定义手转走，手臂仍停在挥动位，形成"双手脱节"。
    // 改为：手臂大位移去够刀手（tx 左移、ty 下移），rz 负值顺时针转正手腕，rx 前送
    // 第 13 轮：撤销上轮 CAM 误改（手臂在 15.31.40 已正确，恢复 tx/ty/rx）。
    // 不垂直的真因：CAM rz=+14 把整链逆时针滚了 14°，刀跟着斜挂。
    // rz 14→-6：整体顺时针回正 20°，让"环在上、刃在下"落在同一竖直线上
    // 第 14 轮：角度已定版（垂直下垂+花纹朝镜头）。改前后层：刀当前浮在拳头之前，
    // 参考图应为拳在前、环套在手指后。tz 向屏幕内(-Z)推刀，旋转参数冻结
    // 第 15 轮：前后镜像（翻页）。对当前姿态做绕竖轴 Ry(180) 复合，代数化简
    // Ry180·Rx(65)·Ry(180)·Rz(-20) = Rx(-65)·Rz(-20)：刀刃保持下垂，左右+前后同时翻转
    // 第 16 轮：镜像成功（花纹保留、钩向已翻）。翻页连带整链逆时针歪 ~45°，
    // CAM rz 纯屏幕滚转 1:1，-6→-50 顺时针回正，刀+手臂一起转竖直
    // 第 17 轮：姿态定版，仅平移：整体右移（tx 负左正右，-0.20→+0.05，净右移 0.25 格）
    // 第 18 轮：整体向右（顺时针）再转 20°：CAM rz -50→-70
    // 第 19 轮：整体再右移 0.20 格（tx 0.05→0.25）
    // 第 20 轮：整体绕竖轴向左微偏航：CAM ry 0→+10（负方向则改 -10）
    // 第 21 轮：整体再右移 0.15 格（tx 0.25→0.40）
    // 第 22 轮：继续右移 0.15 格（tx 0.40→0.55）
    // 第 23 轮：整体向前俯仰一点：CAM rx 15→30（绕水平轴前倾）
    // 第 24 轮：整体下移（ty 0.16→-0.05，下移 0.21 格）+ 继续向前俯仰（rx 30→45）
    // 第 25 轮：继续下移 0.20 格（ty -0.05→-0.25）
    private static final Pose SPRINT_CAM = new Pose(45f, 10f, -70f, 0.55f, -0.25f, -0.02f);
    private static final Pose SPRINT_ITM = new Pose(-65f, 0f, -20f, 0f, 0f, -0.15f);
    private static float sprintBlend = 0f;
    private static long sprintLastNanos = 0L;

    // ===== 转刀切换：刀先在静止的手上转一圈换好姿态，定像后手臂再随位 =====
    // 阶段1 SPIN_NS（只动刀）：刀绕环心转整圈 360°≡0，物品层基底姿态随转同步滑移，
    //   相机层(手臂)完全冻结——转刀期间手不动；本阶段锁定输入，切换奔跑/行走无效。
    // 阶段2 CAM_NS（手动刀不动）：刀已停在目标姿态且不转，手臂 smoothstep 滑向目标位；
    //   本阶段可被打断，want 再变则从当前值起新一轮转刀。
    // 第 27 轮：转速翻倍——SPIN_NS 360→180ms；第 31 轮：实测过快，回退一圈 360ms
    private static final long SPRINT_SPIN_NS = 360_000_000L;
    private static final long SPRINT_CAM_NS = 240_000_000L;
    private static float sprintTarget = 0f;
    private static float sprintItmStart = 0f;
    private static float sprintCamStart = 0f;
    private static float sprintItmBlend = 0f;
    private static float sprintCamBlend = 0f;
    private static float sprintSpin = 0f;
    private static long sprintTransStartNanos = 0L;
    private static boolean sprintTransiting = false;

    /** 守卫用：正在奔跑 或 转刀切换仍在进行 */
    public static boolean sprintHoldActive() {
        Minecraft client = Minecraft.getInstance();
        return (client.player != null && client.player.isSprinting())
                || sprintItmBlend > 0.001f || sprintTransiting;
    }

    /** 推进两段式切换（渲染帧驱动，applySprintCam 内部调用）。
     *  检视窗口期目标强制为 0：奔跑中检视会转刀回正常姿态检视，结束后再转刀进奔跑持刀。 */
    private static float updateSprintBlend() {
        Minecraft client = Minecraft.getInstance();
        float want = (client.player != null && client.player.isSprinting()
                && client.options != null && client.options.getCameraType().isFirstPerson()
                && !inspecting && !inspRunning) ? 1f : 0f;
        long now = System.nanoTime();
        if (sprintLastNanos == 0L) {
            sprintLastNanos = now;
        }
        sprintLastNanos = now;
        if (want != sprintTarget) {
            // 转刀阶段锁定输入；转完进入手臂随位阶段后才接受新切换
            if (!sprintTransiting || now - sprintTransStartNanos >= SPRINT_SPIN_NS) {
                sprintTarget = want;
                sprintItmStart = sprintItmBlend;
                sprintCamStart = sprintCamBlend;
                sprintTransStartNanos = now;
                sprintTransiting = true;
            }
        }
        if (sprintTransiting) {
            long t = now - sprintTransStartNanos;
            if (t < SPRINT_SPIN_NS) {
                // 阶段1：只动刀——整圈旋转 + 物品层基底滑移，CAM 冻结
                float u = t / (float) SPRINT_SPIN_NS;
                float s = u * u * (3f - 2f * u);
                sprintSpin = 360f * s;
                sprintItmBlend = sprintItmStart + (sprintTarget - sprintItmStart) * s;
            } else {
                // 阶段2：刀定像停转，手臂滑向目标位
                sprintSpin = 0f; // 整圈 360≡0，归零无残留
                sprintItmBlend = sprintTarget;
                float u = Math.min((t - SPRINT_SPIN_NS) / (float) SPRINT_CAM_NS, 1f);
                float s = u * u * (3f - 2f * u);
                sprintCamBlend = sprintCamStart + (sprintTarget - sprintCamStart) * s;
                if (u >= 1f) {
                    sprintTransiting = false;
                    sprintCamBlend = sprintTarget;
                }
            }
        }
        return sprintCamBlend;
    }

    /** renderArmWithItem HEAD：奔跑姿态先于动画窗口施加（整链抬高手腕）；转刀期间冻结 */
    public static void applySprintCam(PoseStack g) {
        float b = updateSprintBlend();
        if (b <= 0f) {
            return;
        }
        Pose p = SPRINT_CAM;
        g.translate(p.tx * b, p.ty * b, p.tz * b);
        g.translate(CAM_PIVOT_X, CAM_PIVOT_Y, 0f);
        g.mulPose(Axis.XP.rotationDegrees(p.rx * b));
        g.mulPose(Axis.YP.rotationDegrees(p.ry * b));
        g.mulPose(Axis.ZP.rotationDegrees(p.rz * b));
        g.translate(-CAM_PIVOT_X, -CAM_PIVOT_Y, 0f);
    }

    /** renderItem HEAD：刀基底姿态随转刀进度滑移 + 叠加绕环心整圈 */
    public static void applySprintItem(PoseStack g) {
        float b = sprintItmBlend; // 已由 applySprintCam 推进；单独调用也安全
        if (b <= 0f && sprintSpin == 0f) {
            return;
        }
        Pose p = SPRINT_ITM;
        g.translate(p.tx * b, p.ty * b, p.tz * b);
        g.translate(SPIN_PIVOT_X, SPIN_PIVOT_Y, 0f);
        g.mulPose(Axis.XP.rotationDegrees(p.rx * b));
        g.mulPose(Axis.YP.rotationDegrees(p.ry * b));
        g.mulPose(Axis.ZP.rotationDegrees(p.rz * b));
        g.mulPose(Axis.ZP.rotationDegrees(sprintSpin)); // 转刀切换：绕环心整圈，末点≡0
        g.translate(-SPIN_PIVOT_X, -SPIN_PIVOT_Y, 0f);
    }

}
