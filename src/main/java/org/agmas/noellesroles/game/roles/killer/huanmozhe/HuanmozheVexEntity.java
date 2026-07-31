package org.agmas.noellesroles.game.roles.killer.huanmozhe;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;

/**
 * 幻魔者自定义恼鬼实体
 *
 * 建模、贴图同原版恼鬼一样，但不会攻击玩家。
 * 仅作为视觉效果存在，实际理智扣减和缓慢效果由 HuanmozhePlayerComponent 处理。
 * 存在时间由组件控制（15秒后由组件 discard）。
 */
public class HuanmozheVexEntity extends Vex {

    private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID =
            SynchedEntityData.defineId(HuanmozheVexEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    /** 随机漂浮目标偏移 */
    private float wanderAngle;

    public HuanmozheVexEntity(EntityType<? extends Vex> entityType, Level level) {
        super(entityType, level);
        this.setNoGravity(true);
        this.wanderAngle = this.random.nextFloat() * (float) (Math.PI * 2);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Vex.createAttributes();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER_UUID, Optional.empty());
    }

    public void setOwnerUuid(UUID uuid) {
        this.entityData.set(OWNER_UUID, Optional.of(uuid));
    }

    public Optional<UUID> getOwnerUuid() {
        return this.entityData.get(OWNER_UUID);
    }

    @Override
    public void tick() {
        super.tick();

        // 无重力漂浮
        this.setNoGravity(true);

        // 随机漂浮移动（模拟恼鬼的飘动）
        if (!this.level().isClientSide) {
            wanderAngle += (this.random.nextFloat() - 0.5f) * 0.3f;
            double speed = 0.02;
            Vec3 motion = new Vec3(
                    Math.cos(wanderAngle) * speed,
                    (this.random.nextFloat() - 0.5f) * 0.01,
                    Math.sin(wanderAngle) * speed
            );
            this.setDeltaMovement(motion);
            this.hasImpulse = true;
        }
    }

    /**
     * 不攻击玩家 - 重写以移除所有攻击行为
     */
    @Override
    protected void customServerAiStep() {
        // 空实现 - 不执行任何AI逻辑
    }

    /**
     * 不会自然消失
     */
    @Override
    public boolean removeWhenFarAway(double distanceSquared) {
        return false;
    }

    /**
     * 和平模式下不消失（关键！否则和平模式会立即移除敌对生物）
     */
    @Override
    public boolean shouldDespawnInPeaceful() {
        return false;
    }

    /**
     * 始终持久化，不被刷新规则移除
     */
    @Override
    public boolean isPersistenceRequired() {
        return true;
    }

    /**
     * 不掉落任何物品
     */
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource damageSource, boolean recentlyHit) {
        // 无掉落
    }

    /**
     * 不播放死亡音效（避免干扰）
     */
    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.VEX_DEATH;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.VEX_AMBIENT;
    }

    /**
     * 免疫击退
     */
    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(net.minecraft.world.entity.Entity entity) {
        // 不推动其他实体
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        getOwnerUuid().ifPresent(uuid -> tag.putUUID("OwnerUUID", uuid));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("OwnerUUID")) {
            setOwnerUuid(tag.getUUID("OwnerUUID"));
        }
    }
}
