package org.agmas.noellesroles.content.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 棺材实体 —— 纯装饰道具，没有碰撞体积：玩家与其他实体可直接穿过。
 * 生成后受重力下落，落地后静止不动；玩家攻击可将其移除。
 */
public class CoffinEntity extends Entity {

    public CoffinEntity(EntityType<? extends Entity> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
    }

    // ===== 无碰撞体积：不阻挡玩家与其他实体 =====
    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    // ===== 重力下落，落地后转为静止 =====
    @Override
    public void tick() {
        super.tick();
        if (this.isNoGravity()) {
            return;
        }
        // 防卡地：若生成在实心方块内部（如指令放错位置），向上抬升到最近的空处再落地，避免卡在地里
        BlockPos here = this.blockPosition();
        if (this.level().getBlockState(here).isCollisionShapeFullBlock(this.level(), here)) {
            BlockPos target = here;
            int maxScan = 128;
            while (maxScan-- > 0 && target.getY() < this.level().getMaxBuildHeight()
                    && this.level().getBlockState(target).isCollisionShapeFullBlock(this.level(), target)) {
                target = target.above();
            }
            this.setPos(this.getX(), target.getY(), this.getZ());
            this.setDeltaMovement(Vec3.ZERO);
        }
        if (this.onGround()) {
            this.setDeltaMovement(Vec3.ZERO);
            this.setNoGravity(true);
            return;
        }
        this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -0.04D, 0.0D));
        this.move(MoverType.SELF, this.getDeltaMovement());
        this.setDeltaMovement(this.getDeltaMovement().scale(0.98D));
    }

    // ===== 允许玩家攻击/指令清除 =====
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide || this.isRemoved()) {
            return false;
        }
        if (source.getEntity() instanceof Player || source.isCreativePlayer()
                || source.is(DamageTypes.GENERIC_KILL)) {
            this.discard();
            return true;
        }
        return false;
    }

    @Override
    public boolean shouldShowName() {
        return false;
    }
}
