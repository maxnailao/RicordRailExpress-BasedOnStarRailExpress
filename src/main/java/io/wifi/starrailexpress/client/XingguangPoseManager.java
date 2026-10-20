package io.wifi.starrailexpress.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.wifi.starrailexpress.content.item.KnifeItem;
import io.wifi.starrailexpress.util.ItemSkinManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 星光（传承品质）第一人称持刀姿态状态机。
 * 变换施加在 renderArmWithItem 的 HEAD（相机空间：x=屏幕右，y=屏幕上，z=朝屏幕内），
 * 该点同时包住右臂与刀的渲染，手臂和刀刚性联动、不会脱节。
 *
 * 平滑度：逐渲染帧推进（advance 在每次 apply 时按真实经过时间调用），
 * 采用帧率无关的指数收敛（结束时间 ≈ 半衰期 × 7.6），144/240/360 帧下轨迹完全一致。
 * 挥刀淡出为每帧重算（base * keep），不做逐帧累乘，避免高帧率下衰减速度失真。
 */
public final class XingguangPoseManager {

    private XingguangPoseManager() {}

    // 旋转枢轴：已用"固定 rz=90 单帧标定法"实测钉到【握把】上。
    // 标定读数：rz=90 时握把相对待机态"往上偏、约半个刀刃"(|Δ|≈0.17, 纯 +y)。
    // 由 Δ=(I−R90)·d 解出 d=(grip−pivot)=(−0.085,+0.085)，故新枢轴=(0.32,−0.25)+d≈(0.23,−0.16)。
    // 枢压在握把后，纯 rz 旋转＝握把原地不动、只有刀刃绕腕翻＝真正的翻腕，不再整体公转甩飞。
    // 回退：三次"算枢轴钉握把"均失败(数学换算误差被大角度放大 + rz>90 刚性翻转整条手臂)。
    // 恢复基线枢轴，持刀/拔刀/蓄力围绕它调好、稳定在屏内。
    private static final float PIVOT_X = 0.32F;
    private static final float PIVOT_Y = -0.25F;

    // 标定探针保持关闭。
    private static final boolean CALIB_PROBE = false;

    /** 常规切换收敛半衰期（秒）：配合 snapNear 吸附，约 0.5 秒内彻底结束 */
    private static final float HALF_LIFE = 0.065f;

    public static final class Pose {
        float rx, ry, rz, tx, ty, tz;

        Pose(float rx, float ry, float rz, float tx, float ty, float tz) {
            this.rx = rx; this.ry = ry; this.rz = rz;
            this.tx = tx; this.ty = ty; this.tz = tz;
        }

        boolean isZero() {
            return Math.abs(rx) < 0.01f && Math.abs(ry) < 0.01f && Math.abs(rz) < 0.01f
                    && Math.abs(tx) < 0.001f && Math.abs(ty) < 0.001f && Math.abs(tz) < 0.001f;
        }

        void lerpTo(Pose target, float f) {
            rx += (target.rx - rx) * f;
            ry += (target.ry - ry) * f;
            rz += (target.rz - rz) * f;
            tx += (target.tx - tx) * f;
            ty += (target.ty - ty) * f;
            tz += (target.tz - tz) * f;
        }

        /** 残差小于阈值时直接吸附到目标，给渐近收敛一个明确的终点 */
        void snapNear(Pose target, float epsAngle, float epsPos) {
            if (Math.abs(target.rx - rx) < epsAngle) rx = target.rx;
            if (Math.abs(target.ry - ry) < epsAngle) ry = target.ry;
            if (Math.abs(target.rz - rz) < epsAngle) rz = target.rz;
            if (Math.abs(target.tx - tx) < epsPos) tx = target.tx;
            if (Math.abs(target.ty - ty) < epsPos) ty = target.ty;
            if (Math.abs(target.tz - tz) < epsPos) tz = target.tz;
        }

        Pose copy() {
            return new Pose(rx, ry, rz, tx, ty, tz);
        }
    }

    private static final Pose ZERO = new Pose(0, 0, 0, 0, 0, 0);

    // ============ 预设：rz 正=逆时针（屏幕观感），tx 正=右，ty 正=上，tz 正=向屏幕内 ============
    /**
     * 走动/待机刀：水平横持——刀柄朝左、刀刃朝右(rz=-90，绕屏幕法轴在屏幕平面内转，
     * 刀面仍正对镜头，不会像绕 X 轴 90° 那样侧视消失)。tx 负=左、ty 负=下。
     * 保持非零以绕开 applyKnife() 的 isZero() 提前 return。
     */
    private static final Pose KNIFE_WALK   = new Pose(0f, 0f, -90f,  -0.02f, 0.30f, 0f);
    /** 奔跑刀：与走动同一姿态 */
    private static final Pose KNIFE_SPRINT = KNIFE_WALK;

    // 左臂（空手，走 PlayerRenderer 通道）：竖直持刀配套的轻搭臂
    private static final Pose LARM_WALK   = new Pose(0f, 0f, -12f,  0.10f, 0.06f, 0f);
    // 左臂：奔跑与走动同一姿态
    private static final Pose LARM_SPRINT = LARM_WALK;

    // ============ 刺出预设 ============
    // 蓄力：刀刃向前、刀柄向后。rx 取 -65 而非 -90：
    // -90° 时刀身大面恰好侧对视线，屏幕上只剩刀刃截面（1~2px）＝"看不见"；
    // -65° 是 CS/CF 第一人称刺刀的经典观感——刀尖明显前指、大面仍朝向镜头。
    // 轴向：相机看向 -z，rx 负值把 +y（刀尖）转向 -z（正前方）。
    private static final Pose CHARGE = new Pose(-65f, 0f, 10f, -0.22f, 0.55f, -0.82f);

    // ===== 蓄力多段动作：转一圈 → 转移到右手 → 微微后倾；出刀(释放) → 刀划出去 → 恢复持刀 =====
    // 转一圈后落位到右手(偏右、刀尖朝前)。rx 用 -60(非 -90)避免刀面侧视消失。
    private static final Pose CHARGE_READY = new Pose(-60f, 0f, 8f, 0.18f, 0.50f, -0.70f);
    // 右手微微后倾(引手待发)：在 READY 基础上把刃略抬起(rx 更接近 0)、向身体回收一点(tz 更接近 0)。
    private static final Pose CHARGE_TILT = new Pose(-46f, 0f, 8f, 0.18f, 0.46f, -0.50f);
    private static int chargePhase = 0;        // 0=未蓄力 1=转一圈 2=微微后倾保持
    private static long chargeStartNanos = 0L;
    private static final long CHARGE_SPIN_MS = 450_000_000L; // 转一圈时长

    // ============ 左键劈砍：半圆扫击（translation-only 走弧，rz≤18 仅做手腕轻倾）============
    // 记忆验证：大弧线动作靠 tx/ty 位移走轨迹，rz 保持小角度，规避枢轴误差甩飞。
    // 弧线路径(握把位置)：右下起手→右上抬刃→正上→左侧→左下收刀→过冲回弹→稳定→交回。
    private static final long[] SLASH_T = {0L, 60_000_000L, 110_000_000L, 160_000_000L,
            210_000_000L, 260_000_000L, 360_000_000L, 425_000_000L, 475_000_000L, 625_000_000L};
    // 现在这些 rz 只喂给【物品自转】(绕握把)，手臂停在持刀，故可放心用大角度做真正翻腕。
    // tx/ty=tz=0：纯绕握把自转，握把钉死不动。
    private static final Pose[] SLASH_KF = {
            new Pose(0f, 0f,   8f,  0.00f,  0.00f, 0.00f), // 0 战斗待机：刀尖朝上(=持刀)
            new Pose(0f, 0f,   8f,  0.00f,  0.00f, 0.00f), // 1 待机
            new Pose(0f, 0f, -22f,  0.00f,  0.00f, 0.00f), // 2 外翻蓄力：刀尖引向右下
            new Pose(0f, 0f,  45f,  0.00f,  0.00f, 0.00f), // 3 翻腕起：刀尖扫到右上/顶
            new Pose(0f, 0f, 110f,  0.00f,  0.00f, 0.00f), // 4 刀尖经左中(近水平)
            new Pose(0f, 0f, 175f,  0.00f,  0.00f, 0.00f), // 5 刀尖到左下
            new Pose(0f, 0f, 245f, -0.10f, -0.05f, 0.00f), // 6 收刀：转向水平、开始拉近模型
            new Pose(0f, 0f, 285f, -0.13f, -0.07f, 0.00f), // 7 过冲：顺惯性多走几度
            new Pose(0f, 0f, 270f, -0.13f, -0.07f, 0.00f), // 8 稳定落点：刀柄朝左/刀刃朝右、贴近模型
            new Pose(0f, 0f,   8f,  0.00f,  0.00f, 0.00f), // 9 平滑交回常规持刀(刀尖朝上)
    };
    private static final Pose slashScratch = new Pose(0, 0, 0, 0, 0, 0);
    private static final Pose IDLE_BREATH = new Pose(0, 0, 0, 0, 0, 0);

    // 相位：0=无 1=待机 2=蓄力 3=翻腕 4=收刀 5=过冲回弹 6=稳定低位（1..5 喷刀气）
    private static int slashPhase = 0;
    private static long slashStartNanos = 0L;
    private static boolean wasSwinging = false;

    // ===== 物品自转（真正原地翻腕的核心）=====
    // 挥刀的大角度旋转不再进手臂姿态(knifeShown)，而是单独暴露给 renderItem 注入，
    // 只让【刀】绕手部锚点(≈握把)自转，手臂保持持刀姿态不翻 → 消除"整条手臂甩飞"。
    private static boolean slashSpinActive = false;
    private static float slashSpin = 0f;
    // 挥刀期间刀的额外平移(只作用于刀)：用于收尾把刀拉近角色模型。来自关键帧 tx/ty/tz。
    private static float slashOffX = 0f, slashOffY = 0f, slashOffZ = 0f;

    /** renderItem 注入查询：本帧是否处于"只转刀"的挥刀窗口 */
    public static boolean itemSpinActive() {
        return slashSpinActive;
    }

    /** renderItem 注入查询：刀相对持刀姿态额外要转的角度(度，正=逆时针) */
    public static float itemSpinAngle() {
        return slashSpin;
    }

    /** renderItem 注入查询：刀的额外平移(靠近模型用) */
    public static float itemSpinOffX() { return slashOffX; }
    public static float itemSpinOffY() { return slashOffY; }
    public static float itemSpinOffZ() { return slashOffZ; }

    private static float easeInOut(float u) {
        return u * u * (3f - 2f * u);
    }

    /** 按参考时间轴在关键帧之间平滑插值：段内 smoothstep 缓动＝前段加速、接近终点减速 */
    private static Pose evalSlash(long elapsed) {
        if (CALIB_PROBE) {
            slashScratch.rx = 0f; slashScratch.ry = 0f; slashScratch.rz = 90f;
            slashScratch.tx = 0f; slashScratch.ty = 0f; slashScratch.tz = 0f;
            return slashScratch;
        }
        int i = 0;
        while (i < SLASH_T.length - 1 && elapsed >= SLASH_T[i + 1]) {
            i++;
        }
        if (i >= SLASH_KF.length - 1) {
            return SLASH_KF[SLASH_KF.length - 1];
        }
        long span = SLASH_T[i + 1] - SLASH_T[i];
        float u = span > 0 ? (float) (elapsed - SLASH_T[i]) / (float) span : 1f;
        u = easeInOut(u);
        lerpPose(SLASH_KF[i], SLASH_KF[i + 1], u, slashScratch);
        return slashScratch;
    }

    private static void lerpPose(Pose a, Pose b, float u, Pose out) {
        out.rx = a.rx + (b.rx - a.rx) * u;
        out.ry = a.ry + (b.ry - a.ry) * u;
        out.rz = a.rz + (b.rz - a.rz) * u;
        out.tx = a.tx + (b.tx - a.tx) * u;
        out.ty = a.ty + (b.ty - a.ty) * u;
        out.tz = a.tz + (b.tz - a.tz) * u;
    }

    public static boolean isSlashing() {
        return slashPhase >= 1 && slashPhase <= 5;
    }

    // ============ 切刀：刀播放"拔刀鞘"动作（从屏幕左下角斜划回原位），鞘已去除，改由远处一团黑雾飞来附身 ============
    // 相位：1=把刀收到左下角 2=斜划回原位（拔刀出鞘观感）；整段窗口驱动 XingguangClientEffects 生成飞入黑雾
    private static int drawPhase = 0; // 0=无 1=收向左下 2=斜划出鞘
    private static long drawStartNanos = 0L;

    // 起手收纳姿态：纯平移到屏幕左下（x 负=左，y 负=下），仅带一点倾角，回位时像"抽刀归正"
    private static final Pose DRAW_TUCKED = new Pose(8f, 0f, -22f, -0.48f, -0.40f, 0.10f);
    private static final float HALF_LIFE_DRAW_TUCK = 0.030f;  // 快速把刀收到左下角
    private static final float HALF_LIFE_DRAW_SLIDE = 0.160f; // 缓速斜划回原位＝拔刀出鞘观感
    private static final long DRAW_TUCK_MS = 130_000_000L;   // 在左下角驻留（黑雾开始抵达）
    private static final long DRAW_SLIDE_MS = 520_000_000L;  // 斜划全程（约 0.52s）
    private static final long DRAW_FLIP_MS = 625_000_000L; // 拔刀翻腕全程（复用挥刀关键帧时间轴，已加速）
    private static final long DRAW_MOVE_MS = 600_000_000L;   // 翻腕后整体左移到屏幕左边的时长
    private static final float HALF_LIFE_DRAW_MOVE = 0.16f;  // 左移收敛半衰期（越大越慢越顺）
    // 收尾落位：整把刀(含手臂)平移到屏幕左边。与常驻持刀同为水平横刀(rz=-90，刀柄左/刀刃右)，
    // 不再竖着平移；ty 与持刀同高，tx 负=左。没到最左→tx 调更负(如 -1.1)，冲过头→收小(如 -0.6)。
    private static final Pose DRAW_MOVE_LEFT = new Pose(0f, 0f, -90f, -0.90f, 0.30f, 0.10f);
    // 拔刀/翻腕专用基准：固定为旧的"刀尖朝上"持刀，独立于 KNIFE_WALK，
    // 这样把常驻持刀改成水平(rz=-90)也不会改变切刀动画的轨迹与手臂基准。
    // ty 正=上：整体抬高翻腕位置，仅作用于翻腕阶段，不影响其他姿态。
    private static final Pose DRAW_HOLD = new Pose(0f, 0f, 8f, -0.02f, 0.20f, 0f);

    /** 由 XingguangClientEffects 检测到"刚切到星光刀"时调用：启动拔刀 + 黑雾附身窗口 */
    public static void startPossess() {
        drawPhase = 1;
        drawStartNanos = System.nanoTime();
    }

    /** 拔刀 / 黑雾附身窗口是否仍在进行（特效据此生成飞入黑雾） */
    public static boolean isPossessing() {
        return drawPhase != 0;
    }

    /** 拔刀阶段1＝翻腕扫击窗口：特效据此沿刀身甩出拖尾 */
    public static boolean isFlipping() {
        return drawPhase == 1;
    }

    // 刺出相位（第三人称 Mixin 用于让拳头跟随刀柄前送），仅本地玩家状态机有效
    private static int stabPhase = 0;
    private static long phaseStartNanos = 0L;
    // true=蓄力释放的大刺出(从 CHARGE_TILT 起手)；false=普攻轻刺(从当前持刀起手)
    private static boolean stabFromCharge = true;

    // 各相位收敛半衰期（结束时间 ≈ 半衰期 × 7.6）
    private static final float HALF_LIFE_CHARGE = 0.026f; // ≈0.2s 到位
    private static final float HALF_LIFE_STAB   = 0.013f; // ≈0.1s 刺出到位
    private static final float HALF_LIFE_RETURN = 0.026f; // ≈0.2s 收势回持刀

    /** 第三人称 Mixin 同步用：本地玩家正处于刺出前送窗口 */
    public static boolean isStabOut() {
        return stabPhase == 2;
    }

    /** 第三人称 Mixin 同步用：收势回持刀窗口 */
    public static boolean isStabReturn() {
        return stabPhase == 3;
    }

    // 基础姿态：纯插值状态，不含挥刀衰减
    private static final Pose knifeBase = new Pose(0, 0, 0, 0, 0, 0);
    private static final Pose leftArmBase = new Pose(0, 0, 0, 0, 0, 0);
    // 展示姿态：每帧由 基础姿态 × 挥刀衰减 重算得出
    private static final Pose knifeShown = new Pose(0, 0, 0, 0, 0, 0);
    private static final Pose leftArmShown = new Pose(0, 0, 0, 0, 0, 0);

    private static long lastNanos = 0L;

    /** 由 KnifeItem.releaseUsing 在客户端调用：通知姿态机蓄力已释放
     *  @param hit true=射线刺中人 → 播放 0.1s 刺出 + 0.2s 收势；false=空放 → 直接 0.2s 回持刀 */
    public static void onKnifeReleased(boolean hit) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || !XingguangClientEffects.hasXingguangEquipped(client.player)) {
            return;
        }
        stabPhase = hit ? 2 : 3;
        stabFromCharge = true; // 蓄力释放走"从后倾引手向前划出"的大刺出
        phaseStartNanos = System.nanoTime();
    }

    /** 按真实经过时间推进插值。每渲染帧被 apply* 调用；同帧内多次调用 dt≈0，近似无操作 */
    private static void advance(Minecraft client) {
        long now = System.nanoTime();
        if (lastNanos == 0L) {
            lastNanos = now;
            return;
        }
        float dt = (now - lastNanos) * 1.0E-9f;
        lastNanos = now;
        if (dt <= 0f) return;
        if (dt > 0.25f) dt = 0.25f; // 卡顿/切后台后防跳变

        Player player = client.player;
        Pose targetKnife = ZERO;
        Pose targetLArm = ZERO;
        boolean active = player != null && !player.isSpectator()
                && client.options != null && client.options.getCameraType().isFirstPerson()
                && isXingguangKnife(player.getMainHandItem());

        boolean sprint = player != null && player.isSprinting();
        Pose holdKnife = sprint ? KNIFE_SPRINT : KNIFE_WALK;
        Pose holdLArm = sprint ? LARM_SPRINT : LARM_WALK;
        if (active) {
            targetKnife = holdKnife;
            targetLArm = holdLArm;
        }

        // 左键攻击检测：原版 swing 起手的"沿"触发 → 进入劈砍序列
        // （0.07s 引刀 → 0.11s 落刀 → 0.26s 收势，总时长约 0.44s）
        float swing = player != null ? player.getAttackAnim(1.0F) : 0f;
        boolean swingingNow = swing > 0.02f;
        boolean charging = active && player.isUsingItem()
                && player.getUsedItemHand() == InteractionHand.MAIN_HAND;
        // 普攻(左键)：往前轻刺一下。拔刀/蓄力/已在刺出时不重复触发
        if (swingingNow && !wasSwinging && active && drawPhase == 0 && !charging && stabPhase == 0) {
            stabPhase = 2;
            stabFromCharge = false;
            phaseStartNanos = now;
        }
        wasSwinging = swingingNow;

        // 每帧先清物品自转，只有进入挥刀分支才置位（离开挥刀即自动停转）
        slashSpinActive = false;
        slashSpin = 0f;
        slashOffX = 0f; slashOffY = 0f; slashOffZ = 0f;

        // 相位优先级：蓄力举刀 > 切刀 > 左键劈砍 > 刺出/收势 > 常规持刀
        float halfLife = HALF_LIFE;
        if (!charging) {
            chargePhase = 0; // 松开右键即复位，下次蓄力重新转一圈
        }
        if (charging) {
            // 蓄力优先级最高：转一圈→转移到右手→微微后倾，压过拔刀/劈砍/刺出等一切动作
            if (chargePhase == 0) {
                chargePhase = 1;
                chargeStartNanos = now;
            }
            long ce = now - chargeStartNanos;
            if (chargePhase == 1) {
                targetKnife = CHARGE_READY;
                halfLife = HALF_LIFE_CHARGE;
                float u = ce >= CHARGE_SPIN_MS ? 1f : (float) ce / (float) CHARGE_SPIN_MS;
                slashSpin = 360f * easeInOut(u); // 刀绕握把转一圈(只转刀，手臂不翻)
                slashSpinActive = true;
                if (ce >= CHARGE_SPIN_MS) {
                    chargePhase = 2;
                    chargeStartNanos = now;
                }
            } else {
                targetKnife = CHARGE_TILT; // 右手微微后倾，保持到出刀
                halfLife = HALF_LIFE_CHARGE;
            }
        } else if (drawPhase != 0) {
            slashPhase = 0; // 拔刀期间忽略劈砍
            long e = now - drawStartNanos;
            if (drawPhase == 1) {
                // 阶段1：翻腕。手臂停在"朝上"基准(DRAW_HOLD)，大角度旋转交给 renderItem 只转刀
                targetKnife = DRAW_HOLD;
                Pose kf = evalSlash(e); // 复用挥刀关键帧时间轴
                slashSpin = kf.rz - DRAW_HOLD.rz;
                slashOffX = kf.tx; slashOffY = kf.ty; slashOffZ = kf.tz;
                slashSpinActive = true;
                if (e >= DRAW_FLIP_MS) {
                    drawPhase = 2;
                    drawStartNanos = now; // 进入整体左移
                }
            } else {
                // 阶段2：翻腕走完后，整把刀(含手臂)平移到屏幕左边；刀回到持刀朝向
                targetKnife = DRAW_MOVE_LEFT;
                halfLife = HALF_LIFE_DRAW_MOVE;
                if (now - drawStartNanos >= DRAW_MOVE_MS) {
                    drawPhase = 0; // 结束 → 交回常规持刀，环绕黑雾接管
                }
            }
        } else if (slashPhase != 0) {
            long e = now - slashStartNanos;
            // 手臂+物品基础都停在持刀姿态(不随挥刀翻)；大角度旋转交给 renderItem 只转刀
            targetKnife = holdKnife;
            Pose kf = evalSlash(e);
            slashSpin = kf.rz - holdKnife.rz; // 相对持刀的额外自转量
            slashOffX = kf.tx; slashOffY = kf.ty; slashOffZ = kf.tz; // 收尾拉近模型的平移
            slashSpinActive = true;
            if (e < 120_000_000L) {
                slashPhase = 1; // 战斗待机
            } else if (e < 220_000_000L) {
                slashPhase = 2; // 外翻蓄力
            } else if (e < 520_000_000L) {
                slashPhase = 3; // 快速翻腕·半圆
            } else if (e < 720_000_000L) {
                slashPhase = 4; // 下扫收刀
            } else if (e < 950_000_000L) {
                slashPhase = 5; // 过冲回弹
            } else if (e < 1_250_000_000L) {
                slashPhase = 6; // 稳定低位
            } else {
                slashPhase = 0; // 交回常规持刀
            }
        } else if (stabPhase == 2) {
            // 刺出：普攻把刀身转成朝前(CHARGE_READY)轻刺、蓄力释放从后倾引手(CHARGE_TILT)向前划出，均沿 -z(正前方)推进
            Pose stabTarget = (stabFromCharge ? CHARGE_TILT : CHARGE_READY).copy();
            stabTarget.tz -= stabFromCharge ? 0.55f : 0.50f;
            targetKnife = stabTarget;
            halfLife = HALF_LIFE_STAB;
            if (now - phaseStartNanos >= 100_000_000L) { // 0.1s 刺出窗口结束 → 收势
                stabPhase = 3;
                phaseStartNanos = now;
            }
        } else if (stabPhase == 3) {
            targetKnife = holdKnife; // 收势期间即使玩家在重新蓄力也先回持刀
            halfLife = HALF_LIFE_RETURN;
            targetLArm = holdLArm;
            if (now - phaseStartNanos >= 200_000_000L) { // 0.2s 后回常规
                stabPhase = 0;
            }
        }

        // 帧率无关的指数收敛：f = 1 - 0.5^(dt/半衰期)
        float f = 1f - (float) Math.pow(0.5, dt / halfLife);
        knifeBase.lerpTo(targetKnife, f);
        leftArmBase.lerpTo(targetLArm, f);
        knifeBase.snapNear(targetKnife, 0.5f, 0.005f);
        leftArmBase.snapNear(targetLArm, 0.5f, 0.005f);

        // 挥刀淡出：劈砍/切刀期间不再淡出（姿态已由我们自己接管），其余情况保留淡出
        float keep = (slashPhase != 0 || drawPhase != 0) ? 1f : (swing > 0.01f ? 1f - swing : 1f);
        copyScaled(knifeBase, knifeShown, keep);
        copyScaled(leftArmBase, leftArmShown, keep);
    }

    private static void copyScaled(Pose src, Pose dst, float k) {
        dst.rx = src.rx * k;
        dst.ry = src.ry * k;
        dst.rz = src.rz * k;
        dst.tx = src.tx * k;
        dst.ty = src.ty * k;
        dst.tz = src.tz * k;
    }

    // 守卫条件不能依赖姿态值：姿态推进发生在 apply* 内部（渲染帧驱动），
    // 若初始为零时守卫拦截了 apply，就永远无人推进（自我死锁）。
    // 必须查外部事实：主手物品确实是星光皮肤的刀。
    // 只查"装备了星光皮肤"会把持刀变换泄漏到手里的任何其他物品（手枪显示错乱的根因）。
    public static boolean isXingguangKnife(ItemStack stack) {
        Minecraft client = Minecraft.getInstance();
        return client.player != null && isXingguangKnife(client.player, stack);
    }

    /**
     * 严格按指定玩家和物品栈判断星光刀，避免渲染过渡或第三人称姿态把动作套到其他刀皮肤。
     */
    public static boolean isXingguangKnife(Player player, ItemStack stack) {
        if (player == null || !(stack.getItem() instanceof KnifeItem)) {
            return false;
        }
        return XingguangClientEffects.SKIN_ID.equals(ItemSkinManager.getEquippedSkin(player, stack));
    }

    public static boolean knifeActive() {
        Minecraft client = Minecraft.getInstance();
        return client.player != null && isXingguangKnife(client.player.getMainHandItem());
    }

    public static boolean leftArmActive() {
        Minecraft client = Minecraft.getInstance();
        return client.player != null && isXingguangKnife(client.player.getMainHandItem());
    }

    public static void applyKnife(PoseStack g) {
        advance(Minecraft.getInstance());
        if (knifeShown.isZero()) return;
        apply(g, knifeShown);
    }

    public static void applyLeftArm(PoseStack g) {
        advance(Minecraft.getInstance());
        if (leftArmShown.isZero()) return;
        apply(g, leftArmShown);
    }

    /** 绕右拳枢轴旋转（相机空间：rz 正=逆时针），随后整体平移（平移在旋转之后、屏幕空间所见即所得） */
    private static void apply(PoseStack g, Pose p) {
        g.translate(p.tx, p.ty, p.tz);
        g.translate(PIVOT_X, PIVOT_Y, 0.0F);
        g.mulPose(Axis.XP.rotationDegrees(p.rx));
        g.mulPose(Axis.YP.rotationDegrees(p.ry));
        g.mulPose(Axis.ZP.rotationDegrees(p.rz));
        g.translate(-PIVOT_X, -PIVOT_Y, 0.0F);
    }
}
