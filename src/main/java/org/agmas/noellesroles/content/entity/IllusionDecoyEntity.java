package org.agmas.noellesroles.content.entity;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.init.ModEntities;

import java.util.Optional;
import java.util.UUID;

/**
 * 幻术师假人实体。
 *
 * <p>三种行为模式：
 * <ul>
 *   <li>CHASE (0)：向最近的平民玩家移动（技能一）</li>
 *   <li>FOLLOW (1)：跟随所有者移动（技能二）</li>
 *   <li>STATIONARY (2)：原地不动（技能三）</li>
 * </ul>
 *
 * <p>被击中后释放闪光弹效果并消散。
 */
public class IllusionDecoyEntity extends PathfinderMob {

    /** 行为模式 */
    public static final int MODE_CHASE = 0;
    public static final int MODE_FOLLOW = 1;
    public static final int MODE_STATIONARY = 2;

    /** 皮肤所属玩家 UUID */
    private static final EntityDataAccessor<Optional<UUID>> SKIN_UUID = SynchedEntityData.defineId(
            IllusionDecoyEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    /** 行为模式 */
    private static final EntityDataAccessor<Integer> BEHAVIOR_MODE = SynchedEntityData.defineId(
            IllusionDecoyEntity.class, EntityDataSerializers.INT);

    /** 手持物品（从所有者同步） */
    private static final EntityDataAccessor<ItemStack> HELD_ITEM = SynchedEntityData.defineId(
            IllusionDecoyEntity.class, EntityDataSerializers.ITEM_STACK);

    /** 副手物品（从所有者同步） */
    private static final EntityDataAccessor<ItemStack> OFFHAND_ITEM = SynchedEntityData.defineId(
            IllusionDecoyEntity.class, EntityDataSerializers.ITEM_STACK);

    /** 物品是否锁定（技能一/三为true，技能二为false） */
    private static final EntityDataAccessor<Boolean> ITEM_LOCKED = SynchedEntityData.defineId(
            IllusionDecoyEntity.class, EntityDataSerializers.BOOLEAN);

    /**
     * 姿态标志位：
     * bit0=举刀/使用物品，bit1=疾跑，bit2=蹲下，bit3=坐着，bit4=左手持物。
     * <p><b>只在放置瞬间取样一次</b>（{@link #setup}），之后固定不变：
     * 蹲着放出来就是蹲着的分身，放开蹲键也不会跟着站起来。
     */
    private static final EntityDataAccessor<Integer> POSE_FLAGS = SynchedEntityData.defineId(
            IllusionDecoyEntity.class, EntityDataSerializers.INT);

    /** 姿态标志位定义 */
    public static final int POSE_USING_ITEM = 1 << 0;
    public static final int POSE_SPRINTING = 1 << 1;
    public static final int POSE_CROUCHING = 1 << 2;
    public static final int POSE_SITTING = 1 << 3;
    public static final int POSE_OFFHAND_ITEM = 1 << 4;

    private static final double BASE_SPEED = 0.25D;

    /** 所有者（幻术师）UUID */
    private UUID ownerUuid = null;
    private Player ownerCache = null;

    /** 剩余存活时间 (tick) */
    private int remainingLifetime = 200;

    /** 是否已被击中（防止多次触发） */
    private boolean hasBeenHit = false;

    /** 跟随偏移量（技能二中每个假人的偏移角度） */
    private float followOffsetAngle = 0.0F;
    private static final double FOLLOW_DISTANCE = 2.0D;

    /** 寻路重计算计时器 */
    private int repathTimer = 0;

    /**
     * 上一 tick 的本体位置，用于取本体的**真实位移**。
     * <p>技能二跟随靠复刻这个位移实现「和本体一样快」；用 {@code getDeltaMovement()} 会被重力
     * 与客户端输入不同步影响（跳跃时速度突变，会让分身后退再追上）。
     */
    private double lastOwnerX;
    private double lastOwnerY;
    private double lastOwnerZ;
    /** 是否已经记录了上一 tick 的本体位置 */
    private boolean hasLastOwnerPos = false;

    /** 技能二：分身与本体允许的最大位置误差，超过就直接吸附到队列位（防止被卡在方块里落下） */
    private static final double FOLLOW_MAX_ERROR = 3.0D;
    /** 技能二：每 tick 向队列位收敛的最大距离（格） */
    private static final double FOLLOW_CATCHUP_PER_TICK = 0.35D;

    public IllusionDecoyEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        this.setHealth(2.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 2.0)
                .add(Attributes.MOVEMENT_SPEED, BASE_SPEED)
                .add(Attributes.ATTACK_DAMAGE, 0.0);
    }

    @Override
    protected void registerGoals() {
        // 移动逻辑在 tick() 中手动处理
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SKIN_UUID, Optional.empty());
        builder.define(BEHAVIOR_MODE, MODE_CHASE);
        builder.define(HELD_ITEM, ItemStack.EMPTY);
        builder.define(OFFHAND_ITEM, ItemStack.EMPTY);
        builder.define(ITEM_LOCKED, false);
        builder.define(POSE_FLAGS, 0);
    }

    /**
     * 初始化假人
     *
     * @param owner       所有者（幻术师）
     * @param skinUuid    皮肤 UUID（通常与所有者相同）
     * @param mode        行为模式
     * @param lifetime    存活时间 (tick)
     * @param offsetAngle 跟随偏移角度（技能二用）
     * @param itemLocked  物品是否锁定（技能一/三为true，技能二为false）
     */
    public void setup(Player owner, UUID skinUuid, int mode, int lifetime, float offsetAngle, boolean itemLocked) {
        this.ownerUuid = owner.getUUID();
        this.ownerCache = owner;
        this.entityData.set(SKIN_UUID, Optional.ofNullable(skinUuid));
        this.entityData.set(BEHAVIOR_MODE, mode);
        this.entityData.set(HELD_ITEM, owner.getMainHandItem().copy());
        this.entityData.set(OFFHAND_ITEM, owner.getOffhandItem().copy());
        this.entityData.set(ITEM_LOCKED, itemLocked);
        this.remainingLifetime = lifetime;
        this.followOffsetAngle = offsetAngle;
        this.setYRot(owner.getYRot());
        this.yRotO = owner.getYRot();
        this.setYBodyRot(owner.getYRot());
        this.setYHeadRot(owner.getYRot());
        // 记录放置瞬间的本体位置，作为技能二位移复刻的起点
        lastOwnerX = owner.getX();
        lastOwnerY = owner.getY();
        lastOwnerZ = owner.getZ();
        hasLastOwnerPos = true;
        // 姿态取「放置瞬间」所有者的动作（蹲下/坐着/举刀/疾跑）
        syncPoseFrom(owner);
    }

    /**
     * 把所有者的当前动作取样成姿态标志位（**只在放置瞬间调用一次**，之后姿态固定）。
     * <p>坐下在这里表示为「玩家正骑乘座位实体」——原版玩家没有 SITTING 姿态枚举，
     * 座位系统（{@code SeatEntity}/{@code MountableBlock}）就是通过骑乘表达的，
     * 客户端渲染时据此把模型切成坐姿。
     */
    private void syncPoseFrom(Player owner) {
        int flags = 0;
        if (owner.isUsingItem()) flags |= POSE_USING_ITEM;
        if (owner.isSprinting()) flags |= POSE_SPRINTING;
        if (owner.isCrouching()) flags |= POSE_CROUCHING;
        if (owner.isPassenger()) flags |= POSE_SITTING;
        if (!owner.getOffhandItem().isEmpty()) flags |= POSE_OFFHAND_ITEM;
        setPoseFlags(flags);
    }

    public UUID getSkinUuid() {
        return this.entityData.get(SKIN_UUID).orElse(null);
    }

    public int getBehaviorMode() {
        return this.entityData.get(BEHAVIOR_MODE);
    }

    public ItemStack getHeldItem() {
        return this.entityData.get(HELD_ITEM);
    }

    public ItemStack getOffhandItem() {
        return this.entityData.get(OFFHAND_ITEM);
    }

    public boolean isItemLocked() {
        return this.entityData.get(ITEM_LOCKED);
    }

    public int getPoseFlags() {
        return this.entityData.get(POSE_FLAGS);
    }

    public void setPoseFlags(int flags) {
        this.entityData.set(POSE_FLAGS, flags);
    }

    @Override
    public ItemStack getItemInHand(InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND) {
            return getHeldItem();
        }
        return getOffhandItem();
    }

    public Player getOwner() {
        if (ownerCache != null && ownerCache.isAlive()) {
            return ownerCache;
        }
        if (ownerUuid != null) {
            try {
                ownerCache = level().getPlayerByUUID(ownerUuid);
            } catch (Exception e) {
                ownerCache = null;
            }
        }
        return ownerCache;
    }

    @Override
    public void tick() {
        super.tick();

        // 客户端：根据姿态标志设置疾跑状态
        if (level().isClientSide) {
            int flags = getPoseFlags();
            setSprinting((flags & 2) != 0);
            return;
        }

        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        // 检查游戏是否运行中
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(level());
        if (gameWorld == null || !gameWorld.isRunning()) {
            vanish(serverLevel);
            return;
        }

        Player owner = getOwner();
        if (owner == null || !owner.isAlive() || owner.isSpectator()) {
            vanish(serverLevel);
            return;
        }

        // 存活时间递减
        remainingLifetime--;
        if (remainingLifetime <= 0) {
            vanish(serverLevel);
            return;
        }

        // 物品在锁定模式下保持放置瞬间的手持物，不锁定时持续跟随所有者（技能二）
        if (!this.entityData.get(ITEM_LOCKED)) {
            this.entityData.set(HELD_ITEM, owner.getMainHandItem().copy());
            this.entityData.set(OFFHAND_ITEM, owner.getOffhandItem().copy());
        }
        // 姿态**只在 setup() 取样一次**（放置瞬间），之后固定不变：
        // 蹲下放出来就是蹲着的分身、坐着放就是坐着的，放开按键也不会跟着站起来。

        int mode = getBehaviorMode();
        switch (mode) {
            case MODE_CHASE -> tickChase(serverLevel, owner);
            case MODE_FOLLOW -> tickFollow(serverLevel, owner);
            case MODE_STATIONARY -> { /* 原地不动 */ }
        }
    }

    /**
     * 技能一：向最近的平民玩家移动
     */
    private void tickChase(ServerLevel serverLevel, Player owner) {
        if (--repathTimer <= 0 || getNavigation().isDone()) {
            repathTimer = 20;
            Player nearest = findNearestInnocentPlayer(serverLevel, owner);
            if (nearest != null) {
                getNavigation().moveTo(nearest, 1.0D);
            } else {
                // 没有目标时随机移动
                double dx = getX() + random.nextDouble() * 6 - 3;
                double dz = getZ() + random.nextDouble() * 6 - 3;
                getNavigation().moveTo(dx, getY(), dz, 0.8D);
            }
        }
    }

    /**
     * 技能二：跟随所有者移动。
     *
     * <p>不用寻路（{@code getNavigation().moveTo}）：那样走的是分身自己的
     * {@code MOVEMENT_SPEED}（0.25）× 速度系数，实际约 0.3 格/tick，
     * 而玩家走路约 0.1、疾跑约 0.13 格/tick —— 分身会比本体快 2~3 倍，一眼就假。
     *
     * <p>改为直接复刻本体的**真实位移**，并持续收敛到队列位：
     * <ol>
     * <li>加上本体这一 tick 的位移增量 → 与本体同速同向，跳跃/走位都跟得上；</li>
     * <li>再加一个小幅收敛量（每 tick 最多 {@link #FOLLOW_CATCHUP_PER_TICK} 格）→ 逐渐回到队列位；</li>
     * <li>误差超过 {@link #FOLLOW_MAX_ERROR} 格时直接吸附（被方块卡住、传送后追上）。</li>
     * </ol>
     */
    private void tickFollow(ServerLevel serverLevel, Player owner) {
        if (!hasLastOwnerPos) {
            lastOwnerX = owner.getX();
            lastOwnerY = owner.getY();
            lastOwnerZ = owner.getZ();
            hasLastOwnerPos = true;
        }
        double ownerDx = owner.getX() - lastOwnerX;
        double ownerDy = owner.getY() - lastOwnerY;
        double ownerDz = owner.getZ() - lastOwnerZ;
        lastOwnerX = owner.getX();
        lastOwnerY = owner.getY();
        lastOwnerZ = owner.getZ();

        // 队列位：本体周围按 followOffsetAngle 分布的固定偏移
        double offsetRad = Math.toRadians(owner.getYRot() + followOffsetAngle);
        double slotX = owner.getX() + Math.sin(offsetRad) * FOLLOW_DISTANCE;
        double slotZ = owner.getZ() + Math.cos(offsetRad) * FOLLOW_DISTANCE;

        // 1) 同速：跟本体走一样的位移
        double x = getX() + ownerDx;
        double y = getY() + ownerDy;
        double z = getZ() + ownerDz;

        // 2) 归位：只做小幅收敛，避免"贴脸—落后"来回摆
        double errX = slotX - x;
        double errZ = slotZ - z;
        double err = Math.sqrt(errX * errX + errZ * errZ);
        if (err > FOLLOW_MAX_ERROR) {
            // 3) 被卡住/被传送：直接吸附回队列位
            x = slotX;
            z = slotZ;
        } else if (err > 0.01D) {
            double step = Math.min(FOLLOW_CATCHUP_PER_TICK, err);
            x += errX / err * step;
            z += errZ / err * step;
        }

        // setPos 会写 xOld/yOld/zOld，客户端据此做位置插值 —— 跟随才平滑、不一顿一顿
        this.setPos(x, y, z);

        // 同步朝向（与本体完全一致，包括抬头/低头与身体朝向）
        this.setYRot(owner.getYRot());
        this.setXRot(owner.getXRot());
        this.setYBodyRot(owner.getYRot());
        this.setYHeadRot(owner.getYRot());
    }

    /**
     * 查找最近的平民（非杀手）玩家
     */
    private Player findNearestInnocentPlayer(ServerLevel serverLevel, Player owner) {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(level());
        Player nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (Player p : serverLevel.players()) {
            if (p == owner) continue;
            if (!p.isAlive() || p.isSpectator()) continue;
            // 只追击平民阵营的玩家
            if (!gameWorld.isInnocent(p)) continue;

            double dist = p.distanceToSqr(this);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = p;
            }
        }
        return nearest;
    }

    /**
     * 被击中时的闪光弹效果
     */
    public void onHitByPlayer(ServerLevel serverLevel, Player attacker) {
        if (hasBeenHit) return;
        hasBeenHit = true;

        int mode = getBehaviorMode();

        // 播放闪光音效
        serverLevel.playSound(null, blockPosition(), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST,
                SoundSource.PLAYERS, 1.5f, 1.0f);

        // 闪光粒子
        serverLevel.sendParticles(ParticleTypes.FLASH, getX(), getY() + 1.0D, getZ(),
                5, 0.3D, 0.3D, 0.3D, 0.0D);
        serverLevel.sendParticles(ParticleTypes.POOF, getX(), getY() + 1.0D, getZ(),
                20, 0.5D, 0.5D, 0.5D, 0.05D);

        if (mode == MODE_STATIONARY) {
            // 技能三：半径10格内玩家受到黑暗I+失明I+缓慢I 8s，扣除25%理智值
            applySkill3Effects(serverLevel, attacker);
        } else {
            // 技能一/二：击中者受到失明10s（不对杀手阵营生效）
            if (attacker != null && attacker.isAlive()) {
                SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(level());
                if (!gameWorld.isKillerTeam(attacker)) {
                    attacker.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 200, 0));
                }
            }
        }

        // 通知组件假人被击中
        Player owner = getOwner();
        if (owner != null) {
            var comp = ModComponents.HUANSHUSHI.maybeGet(owner).orElse(null);
            if (comp != null) {
                comp.onDecoyHit(mode);
            }
        }

        // 召唤闪光弹（参照变形者举刀假人 burstIntoFlash）
        FlashGrenadeEntity flash = new FlashGrenadeEntity(ModEntities.FLASH_GRENADE, serverLevel);
        if (owner != null) {
            flash.setOwner(owner);
        }
        flash.setPosRaw(getX(), getY() + 0.5D, getZ());
        flash.setDeltaMovement(0.0D, -0.2D, 0.0D);
        serverLevel.addFreshEntity(flash);

        discard();
    }

    /**
     * 技能三特殊效果：黑暗+失明+缓慢+扣理智
     */
    private void applySkill3Effects(ServerLevel serverLevel, Player attacker) {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(level());
        double radius = 10.0;
        for (Player p : serverLevel.players()) {
            if (!p.isAlive() || p.isSpectator()) continue;
            // 不对杀手阵营生效
            if (gameWorld.isKillerTeam(p)) continue;
            if (p.distanceToSqr(this) <= radius * radius) {
                // 黑暗I + 失明I + 缓慢I 8秒 = 160 tick
                p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 160, 0));
                p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 160, 0));
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 160, 0));

                // 扣除25%理智值
                var psychoComp = io.wifi.starrailexpress.cca.SREPlayerPsychoComponent.KEY.maybeGet(p).orElse(null);
                if (psychoComp != null) {
                    int penalty = io.wifi.starrailexpress.game.GameConstants.getPsychoTimer() / 4;
                    int current = Math.max(0, psychoComp.psychoTicks);
                    psychoComp.setPsychoTicks(current + penalty);
                }
            }
        }
    }

    /**
     * 静默消散
     */
    private void vanish(ServerLevel serverLevel) {
        serverLevel.sendParticles(ParticleTypes.POOF,
                getX(), getY() + 1.0D, getZ(), 10, 0.3D, 0.5D, 0.3D, 0.02D);
        discard();
    }

    /**
     * 被玩家枪击时直接调用（参照 GhostPhantomEntity.playerHurt / PuppeteerBodyEntity.playerHurt）
     * 不走原版 hurt() 流程，避免触发完整死亡动画/事件。
     */
    public boolean playerHurt(Player attacker) {
        if (level().isClientSide())
            return false;
        if (hasBeenHit)
            return false;
        if (level() instanceof ServerLevel serverLevel) {
            onHitByPlayer(serverLevel, attacker);
        }
        return true;
    }

    @Override
    public boolean hurt(DamageSource damageSource, float amount) {
        if (level().isClientSide())
            return false;
        if (hasBeenHit)
            return false;

        // 拦截所有伤害，永远不调用 super.hurt()
        // 原版 LivingEntity.hurt() 会触发完整死亡流程，与组件状态冲突
        Entity attacker = damageSource.getEntity();
        if (attacker instanceof Player player) {
            return playerHurt(player);
        }

        // 非玩家伤害直接消散
        if (level() instanceof ServerLevel serverLevel) {
            vanish(serverLevel);
        }
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected boolean canRide(Entity entity) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isAttackable() {
        return true;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerUuid != null) {
            tag.putUUID("OwnerUUID", ownerUuid);
        }
        UUID skin = getSkinUuid();
        if (skin != null) {
            tag.putUUID("SkinUUID", skin);
        }
        tag.putInt("RemainingLifetime", remainingLifetime);
        tag.putInt("BehaviorMode", getBehaviorMode());
        tag.putFloat("FollowOffsetAngle", followOffsetAngle);
        tag.putBoolean("HasBeenHit", hasBeenHit);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("OwnerUUID")) {
            ownerUuid = tag.getUUID("OwnerUUID");
        }
        if (tag.hasUUID("SkinUUID")) {
            this.entityData.set(SKIN_UUID, Optional.of(tag.getUUID("SkinUUID")));
        }
        remainingLifetime = tag.contains("RemainingLifetime") ? tag.getInt("RemainingLifetime") : 200;
        this.entityData.set(BEHAVIOR_MODE, tag.contains("BehaviorMode") ? tag.getInt("BehaviorMode") : MODE_CHASE);
        followOffsetAngle = tag.contains("FollowOffsetAngle") ? tag.getFloat("FollowOffsetAngle") : 0.0F;
        hasBeenHit = tag.contains("HasBeenHit") && tag.getBoolean("HasBeenHit");
    }
}
