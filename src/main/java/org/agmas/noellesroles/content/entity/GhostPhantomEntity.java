package org.agmas.noellesroles.content.entity;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.roles.killer.betterkillerghost.BetterKillerGhostComponent;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;
/**
 * 鬼魅幻影实体
 * 
 * 当鬼魅使用技能时生成的幻影，可以被其他玩家攻击摧毁
 * 参照傀儡师机制：幻影被摧毁时，鬼魅玩家直接死亡
 */
public class GhostPhantomEntity extends LivingEntity {

    /** 所有者 UUID（鬼魅玩家） */
    private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID = SynchedEntityData.defineId(
            GhostPhantomEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    /** 最大存活时间（防止无限存在） */
    public static final int MAX_LIFETIME = 6000; // 5分钟

    /** 存活时间计数器 */
    private int lifetime = 0;

    /** 所有者玩家引用（缓存） */
    @Nullable
    private ServerPlayer ownerCache = null;

    public GhostPhantomEntity(EntityType<? extends LivingEntity> entityType, Level world) {
        super(entityType, world);
        this.setHealth(1.0F); // 1点血，任何攻击都会摧毁
        this.setInvulnerable(false); // 可以被攻击
    }

    /**
     * 处理幻影被攻击的逻辑（与傀儡师PuppeteerBodyEntity.playerHurt完全一致）
     * @param attacker 攻击者玩家
     * @param deathReason 死亡原因
     * @return 是否成功处理
     */
    public boolean playerHurt(Player attacker, ResourceLocation deathReason) {
        if (level().isClientSide())
            return false;
        
        ServerPlayer owner = getOwner();
        if (owner != null) {
            // 通知鬼魅组件幻影被摧毁
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(level());
            if (gameWorld.isRole(owner, ModRoles.BETTER_KILLER_GHOST)) {
                BetterKillerGhostComponent ghostComp = ModComponents.BETTER_KILLER_GHOST.get(owner);
                if (ghostComp != null && ghostComp.isInShadowMode) {
                    // 参照傀儡师机制：幻影被摧毁时，鬼魅玩家直接死亡
                    GameUtils.killPlayer(owner, true, attacker instanceof ServerPlayer ? (ServerPlayer) attacker : null, GameConstants.DeathReasons.PHANTOM_DESTROYED);
                    
                    // 强制退出幽影模式（在死亡之后清理状态）
                    ghostComp.exitShadowModeForced();
                }
            }
        }
        return true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER_UUID, Optional.empty());
    }

    /**
     * 设置所有者（鬼魅玩家）
     */
    public void setOwner(ServerPlayer owner) {
        this.entityData.set(OWNER_UUID, Optional.of(owner.getUUID()));
        this.ownerCache = owner;
    }

    /**
     * 获取所有者 UUID
     */
    public UUID getOwnerUuid() {
        return this.entityData.get(OWNER_UUID).orElse(null);
    }

    /**
     * 获取所有者玩家
     */
    @Nullable
    public ServerPlayer getOwner() {
        if (this.ownerCache != null && !this.ownerCache.isRemoved()) {
            return this.ownerCache;
        }
        
        UUID ownerUuid = this.getOwnerUuid();
        if (ownerUuid != null && this.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            net.minecraft.world.entity.player.Player player = serverLevel.getPlayerByUUID(ownerUuid);
            if (player instanceof ServerPlayer) {
                this.ownerCache = (ServerPlayer) player;
                return this.ownerCache;
            }
        }
        
        return null;
    }

    @Override
    public void tick() {
        super.tick();
        
        // 增加存活时间
        this.lifetime++;
        
        // 检查是否超过最大存活时间
        if (this.lifetime > MAX_LIFETIME) {
            this.discard();
            return;
        }
        
        // 检查所有者是否存在
        ServerPlayer owner = this.getOwner();
        if (owner == null || !GameUtils.isPlayerAliveAndSurvival(owner)) {
            this.discard();
            return;
        }
        
        // 检查距离：如果幻影与所有者距离超过20格，销毁幻影
        double distance = this.distanceTo(owner);
        if (distance > 20.0) {
            this.discard();
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide())
            return false;

        // 获取攻击者
        Player attacker = source.getEntity() instanceof Player ? (Player) source.getEntity() : null;
        
        // 如果幻影死亡，通知鬼魅组件
        if (this.isDeadOrDying()) {
            ServerPlayer owner = this.getOwner();
            if (owner != null) {
                BetterKillerGhostComponent ghostComp = ModComponents.BETTER_KILLER_GHOST.get(owner);
                if (ghostComp != null && ghostComp.isInShadowMode) {
                    // 幻影被非玩家因素摧毁（如距离过远），也导致鬼魅死亡
                    GameUtils.killPlayer(owner, true, null, GameConstants.DeathReasons.PHANTOM_DESTROYED);
                    ghostComp.exitShadowModeForced();
                }
            }
        }
        
        // 无论如何都销毁实体
        this.discard();
        
        return true;
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.hasUUID("Owner")) {
            this.entityData.set(OWNER_UUID, Optional.of(compound.getUUID("Owner")));
        }
        this.lifetime = compound.getInt("Lifetime");
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        UUID ownerUuid = this.getOwnerUuid();
        if (ownerUuid != null) {
            compound.putUUID("Owner", ownerUuid);
        }
        compound.putInt("Lifetime", this.lifetime);
    }

    @Override
    public boolean isPushable() {
        return false; // 不能被推动
    }

    @Override
    protected void doPush(net.minecraft.world.entity.Entity entity) {
        // 不与其他实体碰撞
    }

    @Override
    public boolean shouldShowName() {
        return false; // 不显示名称
    }

    @Override
    public boolean hasCustomName() {
        return false;
    }

    @Override
    public Iterable<net.minecraft.world.item.ItemStack> getArmorSlots() {
        return java.util.Collections.emptyList();
    }

    @Override
    public net.minecraft.world.item.ItemStack getItemBySlot(net.minecraft.world.entity.EquipmentSlot slot) {
        return net.minecraft.world.item.ItemStack.EMPTY;
    }

    @Override
    public void setItemSlot(net.minecraft.world.entity.EquipmentSlot slot, net.minecraft.world.item.ItemStack stack) {
        // 不装备任何物品
    }

    @Override
    public net.minecraft.world.entity.HumanoidArm getMainArm() {
        return net.minecraft.world.entity.HumanoidArm.RIGHT;
    }
}
