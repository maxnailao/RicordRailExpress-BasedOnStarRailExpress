package org.agmas.noellesroles.game.roles.neutral.cipher;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.packet.CipherDroneControlC2SPacket;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import org.jetbrains.annotations.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 密客的侦察无人机：独立、无重力、可飞行的 Mob。
 *
 * <p>不被骑乘——密客本体留在原地，通过 {@code setCameraEntity} 把画面切到本机，
 * 移动/朝向由 {@link CipherDroneControlC2SPacket} 远程驱动（见 {@link #applyControlInput}）。
 * 能穿过并强开木门/铁门、拾取食物饮品，可被打碎（打碎即进入冷却）；
 * 开启自杀式冲锋后无法停止、速度翻倍，撞到墙或玩家即引爆。
 */
public class CipherDroneEntity extends Mob implements GeoEntity {

    /** 常规水平速度（格/tick）；冲锋时为 2 倍 */
    private static final double NORMAL_SPEED = 0.30;
    /** 垂直速度 */
    private static final double VERTICAL_SPEED = 0.26;
    /** 门/物品拾取扫描半径 */
    private static final int INTERACT_RANGE = 2;
    /** 距本体锚点的最大拴绳距离，超出则由服务端回拉，保证客户端持续追踪本机（远程相机不丢） */
    public static final double LEASH_DISTANCE = 1024.0; // 硬边界 1024 格≈64 区块（≥20 区块需求）

    @Nullable
    private UUID owner;

    // ==================== 远程输入缓存（服务端） ====================
    private int inputBits = 0;
    private float inputYaw;
    private float inputPitch;
    private int inputFreshTicks = 0;
    // 仅客户端：上一 tick 的控制朝向——服务端应用本包朝向天然滞后 1 tick，
    // 本地预测用同一滞后值，消除冲锋转向时的轨迹系统性分叉（闪现/橡皮筋的根源）
    private float prevControlYaw;
    private float prevControlPitch;
    private boolean prevControlValid;
    /** 惯性：服务端维护的实际飞行速度（格/tick），向目标速度平滑逼近 */
    private Vec3 flightVel = Vec3.ZERO;
    // 仅服务端：冲锋风声节流计数
    private int rushSoundTick = 0;
    // 仅服务端：连续无人驾驶的 tick 数（滞留机体自我清理用）
    private int unpilotedTicks = 0;

    /** 自杀式冲锋：无法停止，撞墙/撞人即爆 */
    private boolean rush = false;
    /** 已装载 C4：飞行速度下降 40%（服务端权威；驾驶员本地预测每 tick 由同步的组件值喂入） */
    private boolean bombLoaded = false;
    /** 交互(开门)上升沿检测 */
    private boolean useHeldLast = false;

    /** 客户端本地运动预测开关（仅驾驶员本端为 true）：每 tick 用相同运动模型自行推进，消除 20Hz 平移步进 */
    private boolean localPredict = false;

    public CipherDroneEntity(EntityType<? extends Mob> type, net.minecraft.world.level.Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.setInvulnerable(false);
        this.setPersistenceRequired();
    }

    // ==================== GeckoLib 动画 ====================
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    /** 螺旋桨旋转动画（循环），对应 animation.lightweight_quadcopter.rotors */
    private static final RawAnimation ROTORS = RawAnimation.begin().thenLoop("animation.lightweight_quadcopter.rotors");

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "rotors", 0, state -> state.setAndContinue(ROTORS)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }

    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
    }

    @Nullable
    public UUID getOwner() {
        return owner;
    }

    @Nullable
    private ServerPlayer getOwnerPlayer(ServerLevel serverLevel) {
        if (owner == null) return null;
        return serverLevel.getServer().getPlayerList().getPlayer(owner);
    }

    public void setRush(boolean rush) {
        this.rush = rush;
        if (rush) this.rushSoundTick = 0;
    }

    public void setBombLoaded(boolean bombLoaded) {
        this.bombLoaded = bombLoaded;
    }

    public boolean isRush() {
        return rush;
    }

    /** 接收远程驱动输入 */
    public void applyControlInput(int bits, float yaw, float pitch) {
        if (this.level().isClientSide) {
            this.prevControlYaw = this.inputYaw;
            this.prevControlPitch = this.inputPitch;
            this.prevControlValid = true;
        }
        this.inputBits = bits;
        this.inputYaw = yaw;
        this.inputPitch = pitch;
        this.inputFreshTicks = 5;
    }

    // 关闭原生 AI，移动完全由 applyControlInput 驱动
    @Override
    protected void registerGoals() {
    }

    /** 阻断所有原版 despawn 通道（距离/和平难度/随机清空） */
    @Override
    public void checkDespawn() {
    }

    @Override
    public void travel(Vec3 movementInput) {
        // 不走原版行走/重力逻辑，位移在 tick 内手动完成
    }

    @Override
    public void aiStep() {
        // 仅服务端跳过 Mob 默认 aiStep（避免寻路/重力干扰）；
        // 客户端必须放行原版 aiStep，否则网络位置插值（lerpTo→lerpSteps）不推进，
        // 会导致服务端已移动但客户端无人机坐标冻结、相机不跟随。
        if (this.level().isClientSide) {
            super.aiStep();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && this.level() instanceof ServerLevel serverLevel) {
            drive(serverLevel);
        } else if (this.level().isClientSide && this.localPredict) {
            clientPredict();
        }
    }

    /** 驾驶员本端：每 tick 由本地控制调用，开启预测 */
    public void setLocalPredict(boolean v) {
        this.localPredict = v;
    }

    /** 客户端本地预测：镜像服务端运动模型（不含开门/拾取/拴绳），使相机平移平滑 */
    private void clientPredict() {
        boolean fresh = inputFreshTicks > 0;
        double f = 0, l = 0;
        if (fresh) {
            if ((inputBits & CipherDroneControlC2SPacket.BIT_FORWARD) != 0) f += 1;
            if ((inputBits & CipherDroneControlC2SPacket.BIT_BACK) != 0) f -= 1;
            if ((inputBits & CipherDroneControlC2SPacket.BIT_LEFT) != 0) l += 1;
            if ((inputBits & CipherDroneControlC2SPacket.BIT_RIGHT) != 0) l -= 1;
        }
        double speed = NORMAL_SPEED * (rush ? CipherPlayerComponent.RUSH_SPEED_MULT : 1.0) * (this.bombLoaded ? 0.6 : 1.0);
        Vec3 targetVel;
        if (rush) {
            // 冲锋：预测方向用上一 tick 的朝向（与服务端本 tick 正在用的值一致），
            // 而不是本机当前渲染朝向，否则持续转向时两边轨迹必然分叉
            float yaw = this.prevControlValid ? this.prevControlYaw : this.getYRot();
            float pitch = this.prevControlValid ? this.prevControlPitch : this.getXRot();
            double ry = Math.toRadians(yaw);
            double rp = Math.toRadians(pitch);
            Vec3 dir = new Vec3(-Math.sin(ry) * Math.cos(rp), -Math.sin(rp), Math.cos(ry) * Math.cos(rp));
            targetVel = dir.scale(speed);
        } else {
            double rad = Math.toRadians(this.inputYaw);
            Vec3 fwd = new Vec3(-Math.sin(rad), 0, Math.cos(rad));
            Vec3 left = new Vec3(Math.cos(rad), 0, Math.sin(rad));
            Vec3 horizontal = fwd.scale(f).add(left.scale(l));
            if (horizontal.lengthSqr() > 1.0e-4) {
                horizontal = horizontal.normalize().scale(speed);
            } else {
                horizontal = Vec3.ZERO;
            }
            double vy = 0;
            if (fresh) {
                if ((inputBits & CipherDroneControlC2SPacket.BIT_ASCEND) != 0) vy += VERTICAL_SPEED;
                if ((inputBits & CipherDroneControlC2SPacket.BIT_DESCEND) != 0) vy -= VERTICAL_SPEED;
            }
            targetVel = new Vec3(horizontal.x, vy, horizontal.z);
        }
        double accel = rush ? 0.85 : 0.6;
        this.flightVel = this.flightVel.add(targetVel.subtract(this.flightVel).scale(accel));
        if (this.flightVel.lengthSqr() < 1.0e-5) this.flightVel = Vec3.ZERO;
        this.move(MoverType.SELF, this.flightVel);
        this.setDeltaMovement(this.flightVel);
        if (inputFreshTicks > 0) inputFreshTicks--;
    }

    /**
     * 驾驶员本端预测时：服务端同步的位置不再生硬覆盖本地预测，
     * 而是做轻微纠偏吸收漂移；误差过大或按传送处理(steps<=0)则直接吸附（保证权威）。
     * 非预测场景（如其他旁观客户端）走原版插值。
     */
    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps) {
        if (this.level().isClientSide && this.localPredict) {
            Vec3 cur = this.position();
            double errSq = cur.distanceToSqr(x, y, z);
            if (steps <= 0) {
                // 真传送必须立即吸附，保持权威
                this.setPos(x, y, z);
            } else if (this.rush) {
                // 冲锋：预测已与服务端对齐（同用滞后 1 tick 的朝向），误差只剩碰撞差异，
                // 正常纠偏即可；硬吸附只留给 >8 格的真实异常，不再周期性闪现
                if (errSq > 64.0) {
                    this.setPos(x, y, z);
                } else {
                    double k = 0.12;
                    this.setPos(Mth.lerp(k, cur.x, x), Mth.lerp(k, cur.y, y), Mth.lerp(k, cur.z, z));
                }
            } else if (errSq > 16.0) {
                this.setPos(x, y, z);
            } else {
                double k = 0.12;
                this.setPos(Mth.lerp(k, cur.x, x), Mth.lerp(k, cur.y, y), Mth.lerp(k, cur.z, z));
            }
            // 朝向由本地鼠标每 tick 直接设定，忽略服务端旋转插值
        } else {
            super.lerpTo(x, y, z, yaw, pitch, steps);
        }
    }

    /** 服务端：根据远程输入飞行；冲锋时检测碰撞并引爆 */
    private void drive(ServerLevel serverLevel) {
        // 防卡死自救：机体碰撞盒嵌入方块时（如在不完整方块上出生）缓慢上浮脱困，
        // 避免无重力无人机永久卡死导致噪音无限循环
        if (!serverLevel.noCollision(this, this.getBoundingBox().inflate(-0.05D))) {
            this.move(MoverType.SELF, new Vec3(0.0D, 0.15D, 0.0D));
        }

        // 上一局残留 / 本体已下线的滞留机体：连续 2 秒无人驾驶则自我清除，
        // 避免其无声地开灯、偷食物盘并在出生点附近"凭空发声"
        if (isPiloted(serverLevel)) {
            this.unpilotedTicks = 0;
        } else if (++this.unpilotedTicks >= 40) {
            this.discard();
            return;
        }

        boolean fresh = inputFreshTicks > 0;

        // 朝向跟随本体鼠标（本体未 TURN_BANED，yaw/pitch 由客户端转发进来）
        if (fresh) {
            this.setYRot(this.inputYaw);
            this.setXRot(this.inputPitch);
            this.setYHeadRot(this.inputYaw);
        }

        // 水平分量（基于朝向平面）
        double f = 0, l = 0;
        if (fresh) {
            if ((inputBits & CipherDroneControlC2SPacket.BIT_FORWARD) != 0) f += 1;
            if ((inputBits & CipherDroneControlC2SPacket.BIT_BACK) != 0) f -= 1;
            if ((inputBits & CipherDroneControlC2SPacket.BIT_LEFT) != 0) l += 1;
            if ((inputBits & CipherDroneControlC2SPacket.BIT_RIGHT) != 0) l -= 1;
        }

        double speed = NORMAL_SPEED * (rush ? CipherPlayerComponent.RUSH_SPEED_MULT : 1.0) * (this.bombLoaded ? 0.6 : 1.0);
        Vec3 targetVel;
        if (rush) {
            // 冲锋：锁定视线方向，不可停止
            targetVel = this.getLookAngle().scale(speed);
        } else {
            double rad = Math.toRadians(this.inputYaw);
            Vec3 fwd = new Vec3(-Math.sin(rad), 0, Math.cos(rad));
            Vec3 left = new Vec3(Math.cos(rad), 0, Math.sin(rad));
            Vec3 horizontal = fwd.scale(f).add(left.scale(l));
            if (horizontal.lengthSqr() > 1.0e-4) {
                horizontal = horizontal.normalize().scale(speed);
            } else {
                horizontal = Vec3.ZERO;
            }
            double vy = 0;
            if (fresh) {
                if ((inputBits & CipherDroneControlC2SPacket.BIT_ASCEND) != 0) vy += VERTICAL_SPEED;
                if ((inputBits & CipherDroneControlC2SPacket.BIT_DESCEND) != 0) vy -= VERTICAL_SPEED;
            }
            targetVel = new Vec3(horizontal.x, vy, horizontal.z);
        }

        // 惯性：实际速度向目标速度平滑逼近；无输入时目标为 0 → 缓慢滑行停下
        // accel 越大越"跟手"：0.6≈2 tick(0.1s)达到目标速度，保留轻微惯性但不拖沓
        double accel = rush ? 0.85 : 0.6;
        this.flightVel = this.flightVel.add(targetVel.subtract(this.flightVel).scale(accel));
        if (this.flightVel.lengthSqr() < 1.0e-5) this.flightVel = Vec3.ZERO;

        Vec3 before = this.position();
        this.move(MoverType.SELF, this.flightVel);
        this.setDeltaMovement(this.flightVel);
        this.hasImpulse = true;

        Vec3 moved = this.position().subtract(before);

        // 交互(强开门/食物盘等)：上升沿触发一次
        boolean useHeld = fresh && (inputBits & CipherDroneControlC2SPacket.BIT_USE) != 0;
        if (useHeld && !useHeldLast) {
            doInteract();
        }
        useHeldLast = useHeld;

        // 被动：穿过门口即自动强开附近门（铁门也可）
        openDoorsIntersecting();
        // 拾取食物/饮品（冲锋时不做）
        if (!rush) {
            pickupFoodAndDrink();
        }

        // 拴绳：距本体过远回拉，确保客户端持续追踪（远程相机不丢失）
        enforceLeash(serverLevel);

        // 行驶全程播放呼啸/电机声（冲锋更响更急，常态驾驶更轻更缓）
        playRushWind(serverLevel);
        if (rush) {
            double intendedLen = this.flightVel.length();
            // 撞墙/天花板/地板统一判定：沿视线打探测射线（COLLIDER 形状含半砖、楼梯等
            // 不完整方块），命中任何方块即引爆——斜撞滑行不会再漏过旧的位移判据
            Vec3 origin = this.getBoundingBox().getCenter();
            Vec3 lookDir = this.getLookAngle();
            double probe = Math.max(intendedLen, 0.05D) + 0.35D;
            HitResult ray = this.level().clip(new ClipContext(origin,
                    origin.add(lookDir.scale(probe)),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            boolean hitBlock = ray.getType() == HitResult.Type.BLOCK;
            if (hitBlock && ray instanceof BlockHitResult bhr
                    && isDoorLike(this.level().getBlockState(bhr.getBlockPos()))) {
                hitBlock = false; // 门会被被动强开，不算撞击
            }
            // 兜底：被推挤/贴脸卡进方块碰撞盒时射线可能已在体内起始，位移判据补抓
            if (!hitBlock && intendedLen > 0.05D && moved.length() < intendedLen * 0.25D) {
                hitBlock = true;
            }
            if (hitBlock) {
                detonateOnImpact();
                return;
            }
            // 撞玩家判定
            if (hitPlayerNearby()) {
                detonateOnImpact();
                return;
            }
        }

        if (inputFreshTicks > 0) inputFreshTicks--;
    }

    private void enforceLeash(ServerLevel serverLevel) {
        ServerPlayer ownerPlayer = owner != null ? serverLevel.getServer().getPlayerList().getPlayer(owner) : null;
        if (ownerPlayer == null) return;
        double dx = this.getX() - ownerPlayer.getX();
        double dy = this.getY() - ownerPlayer.getY();
        double dz = this.getZ() - ownerPlayer.getZ();
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist > LEASH_DISTANCE) {
            double k = LEASH_DISTANCE / dist;
            this.teleportTo(ownerPlayer.getX() + dx * k, ownerPlayer.getY() + dy * k, ownerPlayer.getZ() + dz * k);
        }
    }

    /** 冲锋撞墙/撞人 → 交给组件引爆 */
    private void detonateOnImpact() {
        // 撞击即引爆：先清实体侧视觉标记；组件侧状态由 detonate()→closeDrone() 统一清理
        this.bombLoaded = false;
        if (this.level() instanceof ServerLevel serverLevel && owner != null) {
            ServerPlayer ownerPlayer = serverLevel.getServer().getPlayerList().getPlayer(owner);
            if (ownerPlayer != null) {
                CipherPlayerComponent.KEY.get(ownerPlayer).detonate();
            }
        }
    }

    /** 冲锋：检测附近是否有玩家（排除本体） */
    private boolean hitPlayerNearby() {
        AABB box = this.getBoundingBox().inflate(0.6);
        List<ServerPlayer> players = this.level().getEntitiesOfClass(ServerPlayer.class, box,
                p -> p.getUUID().equals(owner));
        if (!players.isEmpty()) return true;
        List<Entity> hits = this.level().getEntities(this, box,
                e -> (e instanceof ServerPlayer) && !e.getUUID().equals(owner));
        return !hits.isEmpty();
    }

    // ==================== 开门 ====================

    /** 射线前方的门（交互键） */
    private void tryOpenDoorsNearby() {
        Vec3 eye = this.getEyePosition();
        Vec3 look = this.getViewVector(1.0f);
        Vec3 end = eye.add(look.scale(4.0));
        HitResult hit = this.level().clip(new ClipContext(eye, end,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, this));
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            tryOpenDoorAt(blockHit.getBlockPos());
        }
    }

    /** 被动：机身所在/相邻格若为门则强开，使无人机穿门而过 */
    private void openDoorsIntersecting() {
        BlockPos center = BlockPos.containing(this.position());
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++)
                    tryOpenDoorAt(center.offset(dx, dy, dz));
    }

    private void tryOpenDoorAt(BlockPos pos) {
        BlockState state = this.level().getBlockState(pos);
        if (isDoorLike(state)) {
            this.level().setBlock(pos, state.setValue(BlockStateProperties.OPEN, true), 3 /* UPDATE_ALL */);
            // 门为上下两半，需同步另一半
            BlockPos pair = pairOf(pos, state);
            if (pair != null) {
                BlockState pairState = this.level().getBlockState(pair);
                if (isDoorLike(pairState) && !pairState.getValue(BlockStateProperties.OPEN)) {
                    this.level().setBlock(pair, pairState.setValue(BlockStateProperties.OPEN, true), 3 /* UPDATE_ALL */);
                }
            }
        }
    }

    private static boolean isDoorLike(BlockState state) {
        return (state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock)
                && state.hasProperty(BlockStateProperties.OPEN);
    }

    @Nullable
    private static BlockPos pairOf(BlockPos pos, BlockState state) {
        if (!state.hasProperty(DoorBlock.HALF)) return null;
        boolean bottom = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER;
        return bottom ? pos.above() : pos.below();
    }

    // ==================== 拾取食物/饮品 ====================

    private void pickupFoodAndDrink() {
        if (!(this.level() instanceof ServerLevel serverLevel) || owner == null) return;
        ServerPlayer ownerPlayer = serverLevel.getServer().getPlayerList().getPlayer(owner);
        if (ownerPlayer == null) return;
        AABB box = this.getBoundingBox().inflate(INTERACT_RANGE);
        List<net.minecraft.world.entity.item.ItemEntity> items =
                serverLevel.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box);
        for (net.minecraft.world.entity.item.ItemEntity item : items) {
            ItemStack stack = item.getItem();
            if (stack.isEmpty()) continue;
            if (isFoodOrDrink(stack)) {
                ItemStack toGive = stack.copy();
                if (ownerPlayer.getInventory().add(toGive)) {
                    stack.shrink(toGive.getCount());
                    if (stack.isEmpty()) item.discard();
                    item.getItem().shrink(0);
                    ownerPlayer.containerMenu.sendAllDataToRemote();
                }
            }
        }
    }

    private static boolean isFoodOrDrink(ItemStack stack) {
        return stack.has(net.minecraft.core.component.DataComponents.FOOD)
                || stack.has(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
    }

    // ==================== 受击 / 打碎 ====================

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // 常态防坠毁：只允许其他玩家的直接攻击（枪/近战/投射物）打碎无人机，
        // 溺水、火焰、摔落、爆炸余波、方块挤压等环境伤害全部免疫
        if (!(source.getEntity() instanceof Player attacker)) return false;
        // 本体不能误伤自己的无人机（例如挥刀/开枪）
        if (owner != null && attacker.getUUID().equals(owner)) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource cause) {
        super.die(cause);
        if (!this.level().isClientSide && this.level() instanceof ServerLevel serverLevel && owner != null) {
            ServerPlayer ownerPlayer = serverLevel.getServer().getPlayerList().getPlayer(owner);
            if (ownerPlayer != null) {
                CipherPlayerComponent comp = CipherPlayerComponent.KEY.get(ownerPlayer);
                if (this.rush) {
                    // 已装弹冲锋被打碎 → 在死亡点掉落携带剩余引信的已激活落地 C4
                    comp.onDroneRushDestroyed(serverLevel, this.position());
                } else {
                    comp.onDroneBroken();
                }
            }
        }
    }

    @Override
    public boolean shouldShowName() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putBoolean("Rush", rush);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Owner")) owner = tag.getUUID("Owner");
        rush = tag.getBoolean("Rush");
    }

    public static net.minecraft.world.entity.ai.attributes.AttributeSupplier createAttributes() {
        return Mob.createMobAttributes()
                .add(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH, 10.0)
                .add(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, 0.30)
                .build();
    }

    /** 悍匪手持 C4 右键无人机 → 装载。返回 CONSUME 阻止其它交互 */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!this.level().isClientSide && this.level() instanceof ServerLevel serverLevel
                && player instanceof ServerPlayer sp && owner != null) {
            io.wifi.starrailexpress.cca.SREGameWorldComponent gw =
                    io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(serverLevel);
            ItemStack stack = sp.getItemInHand(hand);
            if (gw != null
                    && gw.isRole(sp, org.agmas.noellesroles.role.ModRoles.GANGSTERS)
                    && stack.is(org.agmas.noellesroles.init.ModItems.C4)) {
                ServerPlayer cipher = serverLevel.getServer().getPlayerList().getPlayer(owner);
                if (cipher != null) {
                    CipherPlayerComponent comp = CipherPlayerComponent.KEY.get(cipher);
                    if (comp.loadBombFromGangster(sp, hand, this.position())) {
                        return InteractionResult.CONSUME;
                    }
                    return InteractionResult.FAIL;
                }
            }
        }
        return super.mobInteract(player, hand);
    }

    /** 交互键：优先给看向的玩家贴悍匪的C4；否则射线前方一定距离内的互动方块（门直接开；食物盘/按钮等调用其 use） */
    private void doInteract() {
        // 优先：正看向玩家 → 为其贴上悍匪的 C4（成功则不再处理方块）
        if (this.level() instanceof ServerLevel serverLevel && owner != null) {
            Player lookPlayer = pickLookedPlayer(serverLevel, 8.0);
            if (lookPlayer instanceof ServerPlayer spTarget) {
                ServerPlayer op = getOwnerPlayer(serverLevel);
                if (op != null) {
                    CipherPlayerComponent.KEY.get(op).plantDroneC4(serverLevel, spTarget);
                    return;
                }
            }
        }
        Vec3 eye = this.getEyePosition();
        Vec3 look = this.getViewVector(1.0f);
        Vec3 end = eye.add(look.scale(8.0)); // 8 格互动射程，解决"太远的方块无法互动"
        HitResult hit = this.level().clip(new ClipContext(eye, end,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, this));
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) return;
        BlockPos pos = blockHit.getBlockPos();
        BlockState state = this.level().getBlockState(pos);
        if (isDoorLike(state)) {
            tryOpenDoorAt(pos);
            return;
        }
        // 其它互动方块（食物盘/按钮/活板门等）：以本体为使用者直接触发 use，绕过原版距离校验
        if (this.level() instanceof ServerLevel serverLevel) {
            ServerPlayer ownerPlayer = getOwnerPlayer(serverLevel);
            if (ownerPlayer != null) {
                ((io.wifi.starrailexpress.mixin.block.AbstractBlockInvoker) state.getBlock())
                        .tmm$invokeOnUse(state, serverLevel, pos, ownerPlayer, blockHit);
                ownerPlayer.containerMenu.sendAllDataToRemote();
            }
        }
    }

    /** 射线内正看向的玩家（排除本体/旁观），用于贴弹目标选择 */
    @Nullable
    private Player pickLookedPlayer(ServerLevel level, double reach) {
        Vec3 start = this.getEyePosition();
        Vec3 dir = this.getViewVector(1.0f);
        Vec3 end = start.add(dir.scale(reach));
        AABB box = this.getBoundingBox().expandTowards(dir.scale(reach)).inflate(1.0D);
        net.minecraft.world.phys.EntityHitResult hit =
                net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                        this, start, end, box,
                        e -> e instanceof ServerPlayer p
                                && (owner == null || !p.getUUID().equals(owner))
                                && !p.isSpectator()
                                && p.canBeHitByProjectile(),
                        reach * reach);
        if (hit != null && hit.getEntity() instanceof Player pl) return pl;
        return null;
    }

    /** 该机体当前是否正被密客驾驶：组件 droneActive 开启且指向本实体 */
    private boolean isPiloted(ServerLevel serverLevel) {
        if (owner == null) return false;
        ServerPlayer ownerPlayer = serverLevel.getServer().getPlayerList().getPlayer(owner);
        if (ownerPlayer == null) return false;
        CipherPlayerComponent comp = CipherPlayerComponent.KEY.get(ownerPlayer);
        return comp.droneActive && comp.droneId != null && comp.droneId.equals(this.getUUID());
    }

    /** 螺旋桨/电机声：仅驾驶时、且只在无人机所在位置发出；volume 越大越响且传得越远（可闻半径=16×volume） */
    private void playRushWind(ServerLevel serverLevel) {
        if (!isPiloted(serverLevel)) return;
        if (this.rush) {
            if (this.rushSoundTick++ % 2 != 0) return;
        } else {
            if (this.rushSoundTick++ % 5 != 0) return;
        }
        float pitch = this.rush
                ? 1.15F + this.random.nextFloat() * 0.15F
                : 0.9F + this.random.nextFloat() * 0.10F;
        // 可闻半径 = 16 × volume；常态约 48 格、冲锋约 56 格，整体比之前更响
        float vol = this.rush ? 3.5F : 3.0F;
        // 统一以无人机坐标发声
        serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.CONDUIT_AMBIENT,
                net.minecraft.sounds.SoundSource.PLAYERS, vol, pitch);
    }
}