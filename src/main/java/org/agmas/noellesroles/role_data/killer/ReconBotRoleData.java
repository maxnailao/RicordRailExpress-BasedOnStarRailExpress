package org.agmas.noellesroles.role_data.killer;

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.packet.ReconBotGrappleInputC2SPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 探路机器·钩爪的职业数据（服务端权威的拉取物理与状态）。
 *
 * <p>阶段：0=空闲，1=向方块锚点拉取，2=命中玩家→双方被拉到中点。
 * <p>手感（速度从慢到快 + 惯性）：每 tick 用 {@code Vec3.lerp} 把当前速度平滑逼近
 * 「朝锚点、逐渐加速」的目标速度，因此起步慢、越拉越快、断开后带惯性甩出。
 * <p>输入来自客户端 {@link ReconBotGrappleInputC2SPacket}（潜行/技能=断开保动能；
 * 触块后 0.3s 内跳跃=上甩；方向键=轻微获得该方向的力）。
 */
public class ReconBotRoleData extends SimpleRoleData {

    // ===== 可调参数（Apex 风格：钟摆甩动 + 径向收绳 + 90°脱手 + 撒杆跳）=====
    private static final double HOOK_RANGE = 21.0D;      // 钩爪最大射程（对齐 Apex）
    private static final double ROPE_ACCEL_MAX = 0.16D;  // 收绳“向内加速度”上限(渐近强度, 不再直接设速度)
    private static final double ROPE_ACCEL_RAMP = 14.0D; // 收绳加速度时间常数(tick)：越大起步越平缓(慢→快过渡更柔)
    private static final double SWING_MAX_SPEED = 2.6D;  // 摆动/甩飞总速度上限(调大=能甩得更远)
    private static final double ARRIVE_DIST = 1.1D;      // 到达锚点判定距离
    private static final double FLING_BOOST = 1.35D;     // 跳跃抛射时把当前摆动动量整体放大的倍数(=被绳甩出去)
    private static final double FLING_LIFT_RATIO = 0.5D; // 随水平速度附加的上浮, 把横向甩变成斜上抛
    private static final double FLING_LIFT_MAX = 0.85D;  // 附加上浮上限
    private static final double FLING_MAX = 3.0D;        // 抛射合成速度上限(调大=甩得更远)
    private static final double SWING_STRAFE = 0.11D;    // A/D：垂直于绳索的切向变向加速度(荡绳侧身摆)
    private static final double REEL_BOOST = 0.10D;      // W/S：额外收绳/放绳的径向加速度
    private static final int MAX_PULL_TICKS = 90;        // 拉取硬超时（防卡死）

    private static final int PHASE_NONE = 0;
    private static final int PHASE_BLOCK = 1;
    private static final int PHASE_PLAYER = 2;

    // ===== 状态字段 =====
    public int phase = PHASE_NONE;
    public double anchorX, anchorY, anchorZ;
    // 初始绳索方向（玩家→锚点，单位向量）：用于 Apex 90° 脱手判定
    private double ropeInitX, ropeInitY, ropeInitZ;
    @Nullable
    public java.util.UUID hookedPlayerUuid = null;
    private int attachGameTick = 0;   // 触块/命中时刻（游戏 tick）
    private int pullTicks = 0;

    // ===== 客户端输入镜像（非同步字段，仅服务端 tick 内使用）=====
    private int clientBits = 0;
    private int lastInputTick = -100;

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

    /** 客户端据此决定是否发送输入包（同步到本人）。 */
    public boolean isGrapplingClient() {
        return phase != PHASE_NONE;
    }

    public boolean isGrappling() {
        return phase != PHASE_NONE;
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

    /** 发射钩爪：服务端沿视线射线检测方块/玩家。返回 true 才消耗冷却。 */
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

        // 玩家命中（非杀手阵营、存活、非自己）
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(level);
        AABB box = sp.getBoundingBox().expandTowards(look.scale(HOOK_RANGE)).inflate(1.0D);
        EntityHitResult entHit = ProjectileUtil.getEntityHitResult(level, sp, from, to, box,
                e -> e instanceof Player p && p != sp && GameUtils.isPlayerAliveAndSurvival(p)
                        && !game.isKillerTeam(p));
        double entDist = entHit == null ? Double.MAX_VALUE : from.distanceTo(entHit.getLocation());

        int now = (int) level.getGameTime();

        if (entHit != null && entDist <= blockDist) {
            // 命中玩家 → 双方拉到中点
            Entity victim = entHit.getEntity();
            this.phase = PHASE_PLAYER;
            this.hookedPlayerUuid = victim.getUUID();
            Vec3 mid = sp.position().add(victim.position()).scale(0.5D);
            this.anchorX = mid.x;
            this.anchorY = mid.y;
            this.anchorZ = mid.z;
            this.attachGameTick = now;
            this.pullTicks = 0;
            sp.displayClientMessage(Component.translatable("message.noellesroles.recon_bot.hooked_player")
                    .withStyle(ChatFormatting.AQUA), true);
        } else if (blockHit.getType() != BlockHitResult.Type.MISS) {
            // 命中方块 → 向锚点拉取
            Vec3 anchor = blockHit.getLocation();
            this.phase = PHASE_BLOCK;
            this.hookedPlayerUuid = null;
            this.anchorX = anchor.x;
            this.anchorY = anchor.y;
            this.anchorZ = anchor.z;
            this.attachGameTick = now;
            this.pullTicks = 0;
            // 命中音效 + 触块反馈
            level.playSound(null, sp.blockPosition(),
                    net.minecraft.sounds.SoundEvents.CHAIN_HIT, net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 1.4F);
            level.sendParticles(ParticleTypes.END_ROD, anchor.x, anchor.y, anchor.z, 6, 0.1, 0.1, 0.1, 0.0);
        } else {
            // 空放，不消耗冷却
            return false;
        }
        // 记录初始绳索方向，用于「转过90°自动脱手」
        Vec3 originPos = sp.position();
        Vec3 ropeVec = new Vec3(anchorX - originPos.x, anchorY - originPos.y, anchorZ - originPos.z);
        double ropeLen = ropeVec.length();
        if (ropeLen > 1e-3D) {
            this.ropeInitX = ropeVec.x / ropeLen;
            this.ropeInitY = ropeVec.y / ropeLen;
            this.ropeInitZ = ropeVec.z / ropeLen;
        }
        this.clientBits = 0;
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
        sync();
    }

    private void arrive() {
        this.phase = PHASE_NONE;
        this.hookedPlayerUuid = null;
        Vec3 v = player.getDeltaMovement().scale(0.35D); // 到达锚点做一点阻尼
        player.setDeltaMovement(v);
        player.hurtMarked = true;
        sync();
    }

    // ==================== 每 tick 物理 ====================

    @Override
    public void serverTick() {
        if (phase == PHASE_NONE) {
            return;
        }
        if (!(player instanceof ServerPlayer sp) || !(sp.level() instanceof ServerLevel level)) {
            phase = PHASE_NONE;
            return;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(sp) || sp.isSpectator()) {
            phase = PHASE_NONE;
            return;
        }

        int now = (int) level.getGameTime();
        pullTicks++;

        // 断开绳索仅由 G 键(技能键)触发，见 ReconBotRole.triggerGrapple → detachKeepMomentum；此处不再监听 S/Shift

        Vec3 pos = sp.position();
        Vec3 anchor = new Vec3(anchorX, anchorY, anchorZ);
        Vec3 to = anchor.subtract(pos);
        double dist = to.length();

        // 跳跃 = 顺着绳索摆动把“人”沿动量抛出去：不注入准星方向，只放大当前摆动动量并随速附加一点上浮
        if (phase == PHASE_BLOCK && inputFresh()
                && (clientBits & ReconBotGrappleInputC2SPacket.BIT_JUMP) != 0) {
            Vec3 cur = sp.getDeltaMovement(); // 这就是荡起来累积的真实动量(含左右方向)
            double hSpeed = Math.sqrt(cur.x * cur.x + cur.z * cur.z);
            Vec3 fling = cur.scale(FLING_BOOST); // 整体放大=被绳甩得更远，方向沿用摆动方向
            fling = fling.add(0.0D, Math.min(FLING_LIFT_MAX, hSpeed * FLING_LIFT_RATIO), 0.0D); // 斜上抛
            if (fling.lengthSqr() > FLING_MAX * FLING_MAX) {
                fling = fling.normalize().scale(FLING_MAX);
            }
            sp.setDeltaMovement(fling);
            sp.hurtMarked = true;
            sp.fallDistance = 0.0F;
            level.playSound(null, sp.blockPosition(),
                    net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP, net.minecraft.sounds.SoundSource.PLAYERS, 0.5F, 1.6F);
            phase = PHASE_NONE;
            hookedPlayerUuid = null;
            sync();
            return;
        }

        // 到达 / 超时
        if (dist <= ARRIVE_DIST || pullTicks > MAX_PULL_TICKS) {
            arrive();
            return;
        }

        // ===== 钟摆物理(动量守恒)：把当前速度当真实动量累积，只叠加“沿绳向内收绳”与“左右荡绳切向” =====
        Vec3 dir = dist > 1e-3D ? to.scale(1.0D / dist) : Vec3.ZERO; // 指向锚点单位向量
        Vec3 ropeInit = new Vec3(ropeInitX, ropeInitY, ropeInitZ);
        // Apex 90° 规则：绳向相对初始转过90°即脱手，带着累积的切向动能被甩飞(仅方块摆动阶段)
        if (phase == PHASE_BLOCK && pullTicks > 3 && ropeInit.dot(dir) <= 0.0D) {
            detachKeepMomentum();
            return;
        }
        // 当前动量(含每 tick 重力累积)——不重建、只叠加
        Vec3 cur = sp.getDeltaMovement();
        // 平滑“慢→快”的收绳向内加速度(指数渐近：起步极缓，越接近锚点收得越急)
        double pull = ROPE_ACCEL_MAX * (1.0D - Math.exp(-(double) pullTicks / ROPE_ACCEL_RAMP));
        Vec3 v = cur.add(dir.scale(pull));
        // 左右荡绳切向输入：决定甩飞的方向与距离
        if (inputFresh() && phase == PHASE_BLOCK) {
            v = v.add(swingInput(sp, dir));
        }
        // 绷紧约束：绳索不可伸长 → 去掉“远离锚点”的径向分量，转化为绕锚点的切向摆动(动能守恒)
        Vec3 outHat = dir.scale(-1.0D);
        double outward = v.dot(outHat);
        if (outward > 0.0D) {
            v = v.subtract(outHat.scale(outward));
        }
        // 限速只削大小不改方向(上限放宽以便甩远)
        if (v.lengthSqr() > SWING_MAX_SPEED * SWING_MAX_SPEED) {
            v = v.normalize().scale(SWING_MAX_SPEED);
        }
        sp.setDeltaMovement(v);
        sp.hurtMarked = true;
        sp.fallDistance = 0.0F;

        // 命中玩家阶段：双方都被拉向中点(同样用渐近向内加速度)
        if (phase == PHASE_PLAYER && hookedPlayerUuid != null) {
            Player victim = level.getPlayerByUUID(hookedPlayerUuid);
            if (victim == null || !GameUtils.isPlayerAliveAndSurvival(victim)) {
                phase = PHASE_NONE;
                hookedPlayerUuid = null;
                sync();
                return;
            }
            Vec3 vAnchor = sp.position().add(victim.position()).scale(0.5D);
            Vec3 vTo = vAnchor.subtract(victim.position());
            double vDist = vTo.length();
            if (vDist > 0.5D) {
                Vec3 vDir = vTo.scale(1.0D / vDist);
                double vPull = ROPE_ACCEL_MAX * (1.0D - Math.exp(-(double) pullTicks / ROPE_ACCEL_RAMP));
                Vec3 vv = victim.getDeltaMovement().add(vDir.scale(vPull));
                if (vv.lengthSqr() > SWING_MAX_SPEED * SWING_MAX_SPEED) {
                    vv = vv.normalize().scale(SWING_MAX_SPEED);
                }
                victim.setDeltaMovement(vv);
                victim.hurtMarked = true;
                victim.fallDistance = 0.0F;
            }
        }
    }

    /**
     * Apex 荡绳变向输入。关键：A/D 的力投影到「垂直于绳索」的切线方向上，
     * 且切线的左右由准星朝向(camera yaw)决定 —— 看向哪侧就往哪侧荡开，把直线拉取
     * 转成绕锚点的越摆越快；W/S 沿绳方向收绳/放绳(径向)。
     */
    private Vec3 swingInput(ServerPlayer sp, Vec3 ropeDir) {
        int bits = this.clientBits;
        Vec3 look = sp.getLookAngle();
        Vec3 fwd = new Vec3(look.x, 0.0D, look.z);
        if (fwd.lengthSqr() < 1e-4D) fwd = new Vec3(0.0D, 0.0D, 1.0D);
        fwd = fwd.normalize();
        // 相机右向(水平)：面向南(+Z, yaw=0)时右=西(-X)，故 right=(-fz,0,fx)
        Vec3 camRight = new Vec3(-fwd.z, 0.0D, fwd.x);
        Vec3 result = Vec3.ZERO;
        boolean left = (bits & ReconBotGrappleInputC2SPacket.BIT_LEFT) != 0;
        boolean right = (bits & ReconBotGrappleInputC2SPacket.BIT_RIGHT) != 0;
        if (left || right) {
            // 把相机右向投影到垂直于绳索的平面 → 得到真正的摆动切线
            Vec3 tangent = camRight.subtract(ropeDir.scale(camRight.dot(ropeDir)));
            if (tangent.lengthSqr() > 1e-6D) {
                tangent = tangent.normalize();
                double sgn = (right ? 1.0D : 0.0D) - (left ? 1.0D : 0.0D);
                result = result.add(tangent.scale(sgn * SWING_STRAFE));
            }
        }
        // W 收绳更快
        if ((bits & ReconBotGrappleInputC2SPacket.BIT_FORWARD) != 0) {
            result = result.add(ropeDir.scale(REEL_BOOST));
        }
        // S 放绳（减小径向、拉开放幅）
        if ((bits & ReconBotGrappleInputC2SPacket.BIT_BACK) != 0) {
            result = result.add(ropeDir.scale(-REEL_BOOST));
        }
        return result;
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
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider provider) {
        phase = tag.getInt("Phase");
    }
}