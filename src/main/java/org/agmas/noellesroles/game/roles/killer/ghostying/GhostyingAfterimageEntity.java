package org.agmas.noellesroles.game.roles.killer.ghostying;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

/**
 * 鬼影残影实体
 *
 * 鬼影释放"鬼影步"时留在原地的假人诱饵：
 * - 纯视觉欺骗，被攻击只会让残影消散，不会伤害鬼影本体
 * - 本体现身时由组件调用 discard 移除
 * - 内置安全寿命，所有者不存在或死亡时自动消散
 */
public class GhostyingAfterimageEntity extends LivingEntity {

    /** 所有者 UUID（鬼影玩家） */
    private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID = SynchedEntityData.defineId(
            GhostyingAfterimageEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    /** 安全存活时间上限（5秒 = 100 tick），正常情况下本体现身即移除 */
    public static final int MAX_LIFETIME = 100;

    /** 存活时间计数器 */
    private int lifetime = 0;

    public GhostyingAfterimageEntity(EntityType<? extends LivingEntity> entityType, Level world) {
        super(entityType, world);
        this.setNoGravity(false);
        this.setCustomNameVisible(false);
        this.setHealth(1.0F); // 1点血，任何攻击都会使其消散
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER_UUID, Optional.empty());
    }

    /** 设置所有者（鬼影玩家） */
    public void setOwnerUuid(UUID uuid) {
        this.entityData.set(OWNER_UUID, Optional.ofNullable(uuid));
    }

    /** 获取所有者 UUID */
    public Optional<UUID> getOwnerUuid() {
        return this.entityData.get(OWNER_UUID);
    }

    @Override
    public boolean hasCustomName() {
        return false;
    }

    @Override
    public void setCustomName(@Nullable Component component) {
        // 不设置自定义名称
    }

    @Override
    public boolean isCustomNameVisible() {
        return false;
    }

    @Override
    public boolean shouldShowName() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();

        if (level().isClientSide())
            return;

        final var gameWorldComponent = SREGameWorldComponent.KEY.get(level());
        if (gameWorldComponent != null && !gameWorldComponent.isRunning()) {
            discard();
            return;
        }

        // 安全寿命兜底
        lifetime++;
        if (lifetime > MAX_LIFETIME) {
            this.discard();
            return;
        }

        // 所有者不存在（离线/死亡）时自动消散
        UUID ownerUuid = getOwnerUuid().orElse(null);
        if (ownerUuid == null || level().getPlayerByUUID(ownerUuid) == null) {
            this.discard();
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide())
            return false;

        // ⚠️ 拦截所有伤害，永远不调用 super.hurt()：
        // 残影只是幻象，被攻击只会消散，不影响鬼影本体
        if (source.is(DamageTypes.IN_WALL))
            return false;

        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                    getX(), getY() + 0.9, getZ(),
                    8, 0.3, 0.8, 0.3, 0.02);
        }
        this.discard();
        return true;
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        if (nbt.contains("OwnerUUID")) {
            this.entityData.set(OWNER_UUID, Optional.of(nbt.getUUID("OwnerUUID")));
        }
        this.lifetime = nbt.contains("Lifetime") ? nbt.getInt("Lifetime") : 0;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        getOwnerUuid().ifPresent(uuid -> nbt.putUUID("OwnerUUID", uuid));
        nbt.putInt("Lifetime", this.lifetime);
    }

    @Override
    public boolean isPickable() {
        return true; // 可以被选中攻击（诱导误伤）
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(net.minecraft.world.entity.Entity entity) {
        // 不与其他实体碰撞
    }

    @Override
    public boolean isAttackable() {
        return true;
    }

    @Override
    public boolean canBeHitByProjectile() {
        return true;
    }

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return Collections.emptyList();
    }

    @Override
    public ItemStack getItemBySlot(net.minecraft.world.entity.EquipmentSlot slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItemSlot(net.minecraft.world.entity.EquipmentSlot slot, ItemStack stack) {
        // 不装备任何物品
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }
}
