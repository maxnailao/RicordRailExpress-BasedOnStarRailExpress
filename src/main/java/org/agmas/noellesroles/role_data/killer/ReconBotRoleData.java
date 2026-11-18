package org.agmas.noellesroles.role_data.killer;

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.packet.ReconBotGrappleInputC2SPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 荡索者·钩爪的职业数据（服务端权威的钩爪物理与状态）。
 *
 * <p>阶段：0=空闲，1=勾住方块，2=勾住玩家（双方互相拉近），3=钩爪飞行中（飞出去，超距则收回）。
 *
 * <p>手感对齐 Apex「探路者」：
 * <ul>
 *   <li>勾住方块后持续收绳，把玩家拉向锚点，速度由慢到快，且始终保留真实动量；</li>
 *   <li>拉取中按方向键（WASD，相机相对）可直接改变移动方向与动能；</li>
 *   <li>绳索不可伸长：超出初始绳长时只去掉向外的径向分量，于是可以绕锚点摆动；</li>
 *   <li>按跳跃键发动「绳索起跳」：<b>不断开绳索</b>，只叠加上升冲量与朝锚点的额外拉力，
 *       随后绳索按惯性把人拉起；荡得越快拉得越高（每次钩爪一次）；</li>
 *   <li>再次按技能键断绳，保留动能被甩出去。</li>
 * </ul>
 *
 * <p>输入来自客户端 {@link ReconBotGrappleInputC2SPacket}（方向键=变向与动能，跳跃=绳索起跳）。
 */
public class ReconBotRoleData extends SimpleRoleData {

    /** 钩爪技能 id：与技能注册处共用，避免两处字符串不一致。 */
    public static final ResourceLocation GRAPPLE_SKILL_ID =
            ResourceLocation.fromNamespaceAndPath("starrailexpress", "recon_bot_grapple");
    /** 钩爪冷却：25 秒。注意冷却在「技能结束」后才开始，见 {@link #pendingCooldown}。 */
    private static final int GRAPPLE_COOLDOWN_TICKS = 25 * 20;

    // ===== 可调参数（Apex 探路者钩爪）=====
    private static final double HOOK_RANGE = 15.0D;       // 钩爪最大射程
    private static final double PULL_ACCEL_MAX = 0.12D;   // 收绳“朝锚点”加速度上限（随时间增长但封顶，比重力 0.08 大，能真正把人拉过去）
    private static final double PULL_RAMP = 10.0D;        // 收绳加速度时间常数(tick)：越大起步越平缓(慢→快过渡更柔)
    private static final double MAX_ACCEL = 0.12D;        // 每 tick 总加速度硬上限（收绳 + 方向键合成后）
    private static final double STEER_ACCEL = 0.03D;      // 方向键：相机相对的水平加速度（只做轻微变向）
    private static final double GRAVITY = 0.08D;          // 服务端自积分时补回原版重力
    private static final double MAX_SPEED = 2.2D;         // 钩爪期间总速度硬上限（含方向键）
    private static final double ARRIVE_DIST = 1.4D;       // 到达锚点判定距离
    private static final int MAX_PULL_TICKS = 110;        // 拉取硬超时（防卡死）

    // ===== 绳索起跳（Apex：不断绳，借惯性被绳拉起）=====
    private static final double ROPE_JUMP_UP_BASE = 0.45D;       // 起跳基础上升冲量
    private static final double ROPE_JUMP_UP_PER_SPEED = 0.15D;  // 摆动速度->上升冲量（荡得越快拉得越高）
    private static final double ROPE_JUMP_UP_BONUS_MAX = 0.25D;  // 速度加成上限
    private static final double ROPE_JUMP_PULL_BOOST = 0.08D;    // 起跳瞬间额外朝锚点的拉力（绳子把人拉起）

    // ===== 发射飞行 =====
    private static final double HOOK_FLY_SPEED = 2.0D;  // 钩爪飞行速度（格/tick）
    private static final int HOOK_FLY_MIN_TICKS = 2;    // 最短飞行 tick
    private static final int HOOK_FLY_MAX_TICKS = 12;   // 最长飞行 tick

    private static final int PHASE_NONE = 0;
    private static final int PHASE_BLOCK = 1;
    private static final int PHASE_PLAYER = 2;
    private static final int PHASE_FLYING = 3;

    // ===== 状态字段 =====
    public int phase = PHASE_NONE;
    public double anchorX, anchorY, anchorZ;
    private double ropeLen = 0.0D;    // 勾住瞬间的绳长（不可伸长约束用）
    @Nullable
    public java.util.UUID hookedPlayerUuid = null;
    private int pullTicks = 0;
    // ===== 飞行阶段状态 =====
    private int flightTicks = 0;
    private int flightTotalTicks = 0;
    /** 客户端据此插值渲染飞行中的钩爪尖端（与客户端同关卡的 gameTime 对齐）。 */
    private int flightStartGameTime = 0;
    /** 飞行结束后要进入的阶段：BLOCK / PLAYER，或 NONE 表示没勾到东西要收回。 */
    private int pendingPhase = PHASE_NONE;
    /** 本次钩爪是否已经用绳索起跳（一次钩爪只给一次上升冲量，防止连按起飞）。 */
    private boolean jumpBoostUsed = false;
    /**
     * 钩爪结束的下一 tick 再落冷却。
     * <p>RoleSkill 在 handler 返回 true 后会调用 {@code markSkillUsed}（按 definition 的冷却覆盖状态），
     * 如果在结束瞬间直接写冷却会被它覆盖，因此用「下一 tick 补写」的方式。
     */
    private boolean pendingCooldown = false;
    /** 连续被挡住的可疑 tick 数（单 tick 掉包不算，避免误判导致顿挫）。 */
    private int blockedTicks = 0;
    /** 上一 tick 的服务端位置：用位置差分取真实动量（ServerPlayer 的 getDeltaMovement 不可靠）。 */
    @Nullable
    private Vec3 lastPos = null;
    /** 位置差分得到的真实动量，仅用于起手瞬间给初速度。 */
    private Vec3 lastMomentum = Vec3.ZERO;
    /**
     * 钩爪期间由服务端自行积分的速度。
     * <p>不再每 tick 从位置差分累计：否则客户端 WASD 的输入会被反复算进动量，越按越快。
     * 现在 WASD 只通过 {@link #STEER_ACCEL} 提供有限变向力，速度有硬上限。
     */
    private double simVx, simVy, simVz;

    // ===== 客户端输入镜像（非同步字段，仅服务端 tick 内使用）=====
    private int clientBits = 0;
    private int lastInputTick = -100;
    private boolean prevJump = false; // 跳跃键边沿检测：只有“新按下”才触发绳索起跳

    public ReconBotRoleData(RoleDataContext context) {
        super(context);
    }

    @Nullable
    public static ReconBotRoleData of(Player player) {
        return RoleData.getNullable(ReconBotRoleData.class, player);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer p) {
        return this.player == p;
    }

    /**
     * 被赋予职业时：确保拥有和其他杀手相同的开局金币。
     * <p>标准对局由游戏模式按 {@code canUseKiller()} 统一发放；但从指令 / 中途改职等路径进入时不会发放。
     * 这里只在「余额低于初始金币」时补齐，避免重复发钱。
     */
    @Override
    public void init() {
        if (player instanceof ServerPlayer sp) {
            SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(sp);
            int starting = GameConstants.getMoneyStart();
            if (shop.balance < starting) {
                shop.setBalance(starting);
            }
        }
    }

    /** 客户端据此决定是否发送输入包（同步到本人）。 */
    public boolean isGrapplingClient() {
        return phase != PHASE_NONE;
    }

    public boolean isGrappling() {
        return phase != PHASE_NONE;
    }

    /** 客户端渲染绳索用：是否勾在方块上（勾玩家时不画绳）。 */
    public boolean phaseIsBlock() {
        return phase == PHASE_BLOCK;
    }

    /** 客户端：钩爪是否还在飞出去的过程（此时绳子从手连到飞行中的尖端）。 */
    public boolean isFlyingClient() {
        return phase == PHASE_FLYING;
    }

    /** 客户端：是否已经真正勾住（方块或玩家）。HUD 提示只在勾住后显示。 */
    public boolean isAttachedClient() {
        return phase == PHASE_BLOCK || phase == PHASE_PLAYER;
    }

    public int getFlightStartGameTime() {
        return flightStartGameTime;
    }

    public int getFlightTotalTicks() {
        return flightTotalTicks;
    }

    public Vec3 getAnchor() {
        return new Vec3(anchorX, anchorY, anchorZ);
    }

    /** 供接收器写入每 tick 的客户端输入。 */
    public void applyClientInput(int bits) {
        this.clientBits = bits;
        this.lastInputTick = this.player.level().getGameTime() > 0 ? (int) this.player.level().getGameTime() : 0;
    }

    private boolean inputFresh() {
        return (int) player.level().getGameTime() - lastInputTick <= 2;
    }

    // ==================== 发射 ====================

    /**
     * 发射钩爪：服务端沿视线射线检测方块/玩家，先进入飞行阶段。
     * 飞行结束后才真正勾住；若什么都没勾到（含超出射程），飞完最大距离后收回。
     */
    public boolean fireHook() {
        if (!(player instanceof ServerPlayer sp) || !(sp.level() instanceof ServerLevel level)) {
            return false;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) {
            return false;
        }
        Vec3 from = sp.getEyePosition(1.0F);
        Vec3 look = sp.getViewVector(1.0F);
        Vec3 to = from.add(look.scale(HOOK_RANGE));

        // 方块命中
        BlockHitResult blockHit = level.clip(
                new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, sp));
        double blockDist = blockHit.getType() == BlockHitResult.Type.MISS
                ? Double.MAX_VALUE
                : from.distanceTo(blockHit.getLocation());

        // 玩家命中：手动做「射线 × 玩家包围盒」求交并取最近者。
        // 不依赖 ProjectileUtil（本项目对它做了 mixin 改写，行为不完全可控）。
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(level);
        Vec3 rayVec = to.subtract(from);
        AABB search = sp.getBoundingBox().expandTowards(rayVec).inflate(1.0D);
        Player bestVictim = null;
        double bestDistSq = Double.MAX_VALUE;
        for (Player p : level.getEntitiesOfClass(Player.class, search,
                e -> e != sp && GameUtils.isPlayerAliveAndSurvival(e) && !game.isKillerTeam(e))) {
            java.util.Optional<Vec3> hitPos = p.getBoundingBox().inflate(0.4D).clip(from, to);
            if (hitPos.isPresent()) {
                double d = from.distanceToSqr(hitPos.get());
                if (d < bestDistSq) {
                    bestDistSq = d;
                    bestVictim = p;
                }
            }
        }
        double entDist = bestVictim == null ? Double.MAX_VALUE : Math.sqrt(bestDistSq);

        // 先决定目标点与命中后要进入的阶段
        Vec3 target;
        int nextPhase;
        java.util.UUID victimUuid = null;
        if (bestVictim != null && entDist <= blockDist) {
            target = bestVictim.position().add(0.0D, bestVictim.getBbHeight() * 0.6D, 0.0D);
            nextPhase = PHASE_PLAYER;
            victimUuid = bestVictim.getUUID();
        } else if (blockHit.getType() != BlockHitResult.Type.MISS) {
            target = blockHit.getLocation();
            nextPhase = PHASE_BLOCK;
        } else {
            // 没勾到任何东西（例如超出 15m）：钩爪飞到最大射程后自动收回
            target = to;
            nextPhase = PHASE_NONE;
        }

        // ===== 进入飞行阶段：钩爪先飞出去，到达后才真正勾住 =====
        this.phase = PHASE_FLYING;
        this.pendingPhase = nextPhase;
        this.hookedPlayerUuid = victimUuid;
        this.anchorX = target.x;
        this.anchorY = target.y;
        this.anchorZ = target.z;
        double flyDist = from.distanceTo(target);
        this.flightTicks = 0;
        this.flightTotalTicks = Math.max(HOOK_FLY_MIN_TICKS,
                Math.min(HOOK_FLY_MAX_TICKS, (int) Math.ceil(flyDist / HOOK_FLY_SPEED)));
        this.flightStartGameTime = (int) level.getGameTime();
        this.pullTicks = 0;
        this.ropeLen = 0.0D;
        // 勾住瞬间把跳跃视为“已按住”，必须松开再按才算绳索起跳，避免发射时连跳误触
        this.prevJump = true;
        this.clientBits = 0;
        this.jumpBoostUsed = false;
        this.blockedTicks = 0;
        // 重置位置基准，避免用勾住前的旧位置算出一个巨大的“动量”
        this.lastPos = sp.position();
        // 起手初速度 = 勾住前的真实动量（限幅），之后由服务端自行积分
        Vec3 init = lastMomentum.lengthSqr() > MAX_SPEED * MAX_SPEED
                ? lastMomentum.normalize().scale(MAX_SPEED)
                : lastMomentum;
        this.simVx = init.x;
        this.simVy = init.y;
        this.simVz = init.z;
        // 射出音效
        level.playSound(null, sp.blockPosition(),
                net.minecraft.sounds.SoundEvents.CROSSBOW_SHOOT, net.minecraft.sounds.SoundSource.PLAYERS, 0.7F, 1.2F);
        sync();
        return true;
    }

    /** 断开绳索：保留当前动能（甩出去）。 */
    public void detachKeepMomentum() {
        if (this.phase == PHASE_NONE) {
            return;
        }
        this.phase = PHASE_NONE;
        this.hookedPlayerUuid = null;
        // 不清零速度，让惯性把玩家甩出去；仅归零坠落伤害避免无意义摔伤
        player.fallDistance = 0.0F;
        // 技能结束 → 下一 tick 开始 25 秒冷却
        this.pendingCooldown = true;
        sync();
    }

    private void arrive(Vec3 momentum) {
        this.phase = PHASE_NONE;
        this.hookedPlayerUuid = null;
        Vec3 v = momentum.scale(0.35D); // 到达锚点做一点阻尼
        player.setDeltaMovement(v);
        player.hurtMarked = true;
        sendMotion(player, v);
        // 技能结束 → 下一 tick 开始 25 秒冷却
        this.pendingCooldown = true;
        sync();
    }

    // ==================== 每 tick 物理 ====================

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }

        // ===== 位置差分只用于记录「起手动量」 =====
        // ServerPlayer 的位置由客户端包驱动，getDeltaMovement() 在服务端并不可靠。
        // 钩爪期间改为服务端自行积分速度（simV），这里只把合理位移留作下次起手的初速度，
        // 避免把客户端 WASD 的移动反复算进动量。
        Vec3 pos = sp.position();
        Vec3 measured = lastPos == null ? Vec3.ZERO : pos.subtract(lastPos);
        lastPos = pos;
        if (measured.lengthSqr() <= MAX_SPEED * MAX_SPEED * 4.0D) {
            lastMomentum = measured;
        }

        // 钩爪结束后补写冷却（见 pendingCooldown 注释）
        if (pendingCooldown && phase == PHASE_NONE) {
            pendingCooldown = false;
            applyGrappleCooldown(sp);
        }

        if (phase == PHASE_NONE) {
            return;
        }

        // 被方块挡住时实际位移会远小于模拟速度：按实际比例衰减，避免把动能一直积攒到上限，
        // 否则把挡路的方块挖掉后会被积攒的动能直接弹飞。
        // 需要连续 2 tick 判定为「没动」，避免单 tick 丢包导致误判顿挫。
        if (pullTicks > 0) {
            double expected = Math.sqrt(simVx * simVx + simVy * simVy + simVz * simVz);
            double actual = measured.length();
            if (expected > 0.05D && actual < expected * 0.5D) {
                blockedTicks++;
            } else {
                blockedTicks = 0;
            }
            if (blockedTicks >= 2) {
                double scale = Math.max(0.0D, Math.min(1.0D, actual / expected));
                simVx *= scale;
                simVy *= scale;
                simVz *= scale;
            }
        }
        if (!(sp.level() instanceof ServerLevel level)) {
            phase = PHASE_NONE;
            return;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(sp) || sp.isSpectator()) {
            phase = PHASE_NONE;
            return;
        }

        // 飞行阶段：只推进钩爪位置，玩家不受力
        if (phase == PHASE_FLYING) {
            tickFlight(sp, level);
            return;
        }

        pullTicks++;

        // 勾住玩家时锚点跟随对方胸口，保证双方互相拉近
        Player victim = null;
        if (phase == PHASE_PLAYER && hookedPlayerUuid != null) {
            victim = level.getPlayerByUUID(hookedPlayerUuid);
            if (victim == null || !GameUtils.isPlayerAliveAndSurvival(victim)) {
                phase = PHASE_NONE;
                hookedPlayerUuid = null;
                pendingCooldown = true;
                sync();
                return;
            }
            Vec3 chest = victim.position().add(0.0D, victim.getBbHeight() * 0.6D, 0.0D);
            anchorX = chest.x;
            anchorY = chest.y;
            anchorZ = chest.z;
        }

        Vec3 anchor = new Vec3(anchorX, anchorY, anchorZ);
        Vec3 to = anchor.subtract(pos);
        double dist = to.length();
        Vec3 dir = dist > 1e-3D ? to.scale(1.0D / dist) : Vec3.ZERO; // 指向锚点单位向量

        Vec3 simV = new Vec3(simVx, simVy, simVz);

        // 跳跃键“新按下” = 绳索起跳：不断绳！
        // 只给一个上升冲量 + 额外朝锚点的拉力，之后绳索继续按惯性把人往上拉。
        boolean jumpHeld = inputFresh() && (clientBits & ReconBotGrappleInputC2SPacket.BIT_JUMP) != 0;
        boolean jumpPressed = jumpHeld && !prevJump;
        prevJump = jumpHeld;
        if (jumpPressed && pullTicks > 2 && !jumpBoostUsed) {
            jumpBoostUsed = true;
            double hs = Math.sqrt(simV.x * simV.x + simV.z * simV.z);
            double up = ROPE_JUMP_UP_BASE
                    + Math.min(ROPE_JUMP_UP_BONUS_MAX, hs * ROPE_JUMP_UP_PER_SPEED);
            simV = simV.add(0.0D, up, 0.0D).add(dir.scale(ROPE_JUMP_PULL_BOOST));
            if (simV.lengthSqr() > MAX_SPEED * MAX_SPEED) {
                simV = simV.normalize().scale(MAX_SPEED);
            }
            this.simVx = simV.x;
            this.simVy = simV.y;
            this.simVz = simV.z;
            level.playSound(null, sp.blockPosition(),
                    net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP, net.minecraft.sounds.SoundSource.PLAYERS, 0.5F, 1.6F);
            level.sendParticles(ParticleTypes.END_ROD, sp.getX(), sp.getY() + 0.2D, sp.getZ(),
                    10, 0.3D, 0.2D, 0.3D, 0.03D);
        }

        // 到达 / 超时
        if (dist <= ARRIVE_DIST || pullTicks > MAX_PULL_TICKS) {
            arrive(simV);
            return;
        }
        // 服务端自主积分：先补回重力，再叠加收绳与方向键（客户端 WASD 不再进入状态）
        Vec3 v = new Vec3(simV.x, (simV.y - GRAVITY) * 0.98D, simV.z);
        // 站在地面时不要让重力速度无限累积（否则会积出巨大的幽灵下落速度）
        if (sp.onGround() && v.y < 0.0D) {
            v = new Vec3(v.x, 0.0D, v.z);
        }

        // 收绳加速度：随时间增长，但指数渐近封顶，永远不会超过 PULL_ACCEL_MAX
        double pull = Math.min(PULL_ACCEL_MAX,
                PULL_ACCEL_MAX * (1.0D - Math.exp(-(double) pullTicks / PULL_RAMP)));

        // 方向键：相机相对水平加速度（力度有限，只做变向）
        Vec3 accel = dir.scale(pull);
        if (inputFresh()) {
            accel = accel.add(steerInput(sp));
        }
        // 每 tick 总加速度硬上限：钩爪挂再久，加速度也不会超过 MAX_ACCEL
        if (accel.lengthSqr() > MAX_ACCEL * MAX_ACCEL) {
            accel = accel.normalize().scale(MAX_ACCEL);
        }
        v = v.add(accel);

        // 绳索不可伸长：超出初始绳长时去掉“远离锚点”的径向分量 → 转成绕锚点摆动
        if (dist >= ropeLen) {
            Vec3 outHat = dir.scale(-1.0D);
            double outward = v.dot(outHat);
            if (outward > 0.0D) {
                v = v.subtract(outHat.scale(outward));
            }
        }

        // 限速只削大小不改方向（硬上限，方向键按多久都不会超过）
        if (v.lengthSqr() > MAX_SPEED * MAX_SPEED) {
            v = v.normalize().scale(MAX_SPEED);
        }
        this.simVx = v.x;
        this.simVy = v.y;
        this.simVz = v.z;
        sp.setDeltaMovement(v);
        sp.hurtMarked = true;
        sendMotion(sp, v);
        sp.fallDistance = 0.0F;

        // 命中玩家阶段：把对方也拉向自己（双方互相拉近）
        if (phase == PHASE_PLAYER && victim != null) {
            Vec3 selfChest = sp.position().add(0.0D, sp.getBbHeight() * 0.5D, 0.0D);
            Vec3 victimChest = victim.position().add(0.0D, victim.getBbHeight() * 0.5D, 0.0D);
            Vec3 vTo = selfChest.subtract(victimChest);
            double vDist = vTo.length();
            if (vDist > 0.5D) {
                Vec3 vDir = vTo.scale(1.0D / vDist);
                Vec3 vv = victim.getDeltaMovement().add(vDir.scale(pull));
                if (vv.lengthSqr() > MAX_SPEED * MAX_SPEED) {
                    vv = vv.normalize().scale(MAX_SPEED);
                }
                victim.setDeltaMovement(vv);
                victim.hurtMarked = true;
                sendMotion(victim, vv);
                victim.fallDistance = 0.0F;
            }
        }
    }

    /**
     * 飞行阶段：钩爪从玩家眼睛飞向目标点。
     * <ul>
     *   <li>飞行中玩家不受力，客户端按 FlightStart/FlightTotal 自行插值渲染钩爪尖端；</li>
     *   <li>到达后：有目标 → 进入勾住阶段；没勾到（超出射程/空放）→ 收回并结束，同样进入冷却。</li>
     * </ul>
     */
    private void tickFlight(ServerPlayer sp, ServerLevel level) {
        flightTicks++;
        double progress = Math.min(1.0D, (double) flightTicks / Math.max(1, flightTotalTicks));
        Vec3 from = sp.getEyePosition();
        Vec3 target = new Vec3(anchorX, anchorY, anchorZ);
        Vec3 tip = from.lerp(target, progress);
        // 尖端粒子（一次调用只发一个包）
        level.sendParticles(ParticleTypes.END_ROD, tip.x, tip.y, tip.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);

        if (flightTicks < flightTotalTicks) {
            return; // 客户端自行插值，无需每 tick 同步
        }

        // ===== 飞行结束 =====
        if (pendingPhase == PHASE_NONE) {
            // 没勾到东西：收回，并立即进入冷却（空放也算使用过技能）
            phase = PHASE_NONE;
            hookedPlayerUuid = null;
            pendingCooldown = false;
            applyGrappleCooldown(sp);
            level.playSound(null, sp.blockPosition(),
                    net.minecraft.sounds.SoundEvents.CROSSBOW_SHOOT,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.4F, 0.7F);
            sync();
            return;
        }

        if (pendingPhase == PHASE_PLAYER) {
            Player victim = level.getPlayerByUUID(hookedPlayerUuid);
            if (victim == null || !GameUtils.isPlayerAliveAndSurvival(victim)) {
                phase = PHASE_NONE;
                hookedPlayerUuid = null;
                sync();
                return;
            }
            Vec3 chest = victim.position().add(0.0D, victim.getBbHeight() * 0.6D, 0.0D);
            anchorX = chest.x;
            anchorY = chest.y;
            anchorZ = chest.z;
        }

        phase = pendingPhase;
        pendingPhase = PHASE_NONE;
        // 绳长在真正勾住的瞬间按当前位置计算
        ropeLen = sp.position().distanceTo(new Vec3(anchorX, anchorY, anchorZ));
        pullTicks = 0;
        blockedTicks = 0;
        // 重新取一次起手动量（飞行期间玩家可能还在移动）
        Vec3 init = lastMomentum.lengthSqr() > MAX_SPEED * MAX_SPEED
                ? lastMomentum.normalize().scale(MAX_SPEED)
                : lastMomentum;
        this.simVx = init.x;
        this.simVy = init.y;
        this.simVz = init.z;
        level.playSound(null, sp.blockPosition(),
                net.minecraft.sounds.SoundEvents.CHAIN_HIT, net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 1.4F);
        level.sendParticles(ParticleTypes.END_ROD, anchorX, anchorY, anchorZ, 6, 0.1D, 0.1D, 0.1D, 0.0D);
        if (phase == PHASE_PLAYER) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.recon_bot.hooked_player")
                    .withStyle(ChatFormatting.AQUA), true);
        }
        sync();
    }

    /**
     * 方向键 → 相机相对的水平加速度。W/S 沿视线前后，A/D 沿视线左右，
     * 合成后归一化，保证无论按几个方向力度一致（不会斜着更快）。
     */
    private Vec3 steerInput(ServerPlayer sp) {
        Vec3 inputDir = horizontalInputDir(sp);
        return inputDir == null ? Vec3.ZERO : inputDir.scale(STEER_ACCEL);
    }

    /** 按方向键得到相机相对的水平单位向量；没按方向键返回 null。 */
    @Nullable
    private Vec3 horizontalInputDir(ServerPlayer sp) {
        Vec3 look = sp.getLookAngle();
        Vec3 fwd = new Vec3(look.x, 0.0D, look.z);
        if (fwd.lengthSqr() < 1e-4D) fwd = new Vec3(0.0D, 0.0D, 1.0D);
        fwd = fwd.normalize();
        // 相机右向(水平)：面向南(+Z, yaw=0)时右=西(-X)，故 right=(-fz,0,fx)
        Vec3 right = new Vec3(-fwd.z, 0.0D, fwd.x);
        int bits = this.clientBits;
        Vec3 acc = Vec3.ZERO;
        if ((bits & ReconBotGrappleInputC2SPacket.BIT_FORWARD) != 0) acc = acc.add(fwd);
        if ((bits & ReconBotGrappleInputC2SPacket.BIT_BACK) != 0) acc = acc.subtract(fwd);
        if ((bits & ReconBotGrappleInputC2SPacket.BIT_RIGHT) != 0) acc = acc.add(right);
        if ((bits & ReconBotGrappleInputC2SPacket.BIT_LEFT) != 0) acc = acc.subtract(right);
        if (acc.lengthSqr() < 1e-6D) return null;
        return acc.normalize();
    }

    /** 写入钩爪冷却：同时写技能状态与顶层字段，确保 HUD 与旧逻辑都能读到。 */
    private static void applyGrappleCooldown(ServerPlayer sp) {
        SREAbilityPlayerComponent ability = SREAbilityPlayerComponent.KEY.get(sp);
        ability.setSkillCooldown(GRAPPLE_SKILL_ID, GRAPPLE_COOLDOWN_TICKS);
        ability.cooldown = GRAPPLE_COOLDOWN_TICKS;
        ability.sync();
    }

    /**
     * 显式把速度同步给玩家客户端。
     * <p>只设置 {@code hurtMarked} 在 CCA 的 serverTick 时机下可能不会发包（或发出的值已被改动），
     * 一次性冲量会因此丢失。项目内其它弹飞实现同样使用显式发包。
     */
    private static void sendMotion(Player p, Vec3 motion) {
        if (p instanceof ServerPlayer sp) {
            sp.connection.send(new ClientboundSetEntityMotionPacket(sp.getId(), motion));
        }
    }

    @Override
    public void clear() {
        this.phase = PHASE_NONE;
        this.hookedPlayerUuid = null;
        sync();
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("Phase", phase);
        // 客户端画绳索/插值飞行需要锚点与飞行总时长
        tag.putDouble("Ax", anchorX);
        tag.putDouble("Ay", anchorY);
        tag.putDouble("Az", anchorZ);
        tag.putInt("FlightTotal", flightTotalTicks);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider provider) {
        phase = tag.getInt("Phase");
        anchorX = tag.getDouble("Ax");
        anchorY = tag.getDouble("Ay");
        anchorZ = tag.getDouble("Az");
        flightTotalTicks = tag.getInt("FlightTotal");
        // 客户端以收到同步的时刻作为飞行起点，避免使用服务端时钟造成偏移
        flightStartGameTime = (int) player.level().getGameTime();
    }
}
