package org.agmas.noellesroles.content.entity;

import com.mojang.authlib.GameProfile;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.roles.killer.betterkillerghost.BetterKillerGhostComponent;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.game.roles.innocence.role.ModRoles;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * 鬼魅幻影实体
 * 
 * 当鬼魅使用技能时生成的幻影，可以被其他玩家攻击摧毁
 * 完全参照傀儡师PuppeteerBodyEntity实现
 */
public class GhostPhantomEntity extends LivingEntity {

    /** 所有者 UUID（鬼魅玩家） */
    private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID = SynchedEntityData.defineId(
            GhostPhantomEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    /** 皮肤 GameProfile（用于渲染玩家皮肤） */
    private GameProfile skinProfile = null;

    /** 所有者玩家名称 */
    private String ownerName = "";

    /** 最大存活时间（5分钟 = 6000 tick） */
    public static final int MAX_LIFETIME = 6000;

    /** 存活时间计数器 */
    private int lifetime = 0;

    /** 所有者玩家引用（缓存） */
    @Nullable
    private ServerPlayer ownerCache = null;

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

    public GhostPhantomEntity(EntityType<? extends LivingEntity> entityType, Level world) {
        super(entityType, world);
        this.setNoGravity(false); // 有重力
        this.setCustomNameVisible(false);
        this.setHealth(1.0F); // 1点血，任何攻击都会摧毁
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
        if (owner != null) {
            this.entityData.set(OWNER_UUID, Optional.of(owner.getUUID()));
            this.ownerCache = owner;
            this.ownerName = owner.getName().getString();

            // 设置皮肤（获取玩家的 GameProfile）
            this.skinProfile = owner.getGameProfile();

            // 不设置自定义名称，避免显示
            this.setCustomNameVisible(false);
            this.setPose(owner.getPose());
        }
    }

    /**
     * 获取所有者 UUID
     */
    public Optional<UUID> getOwnerUuid() {
        return this.entityData.get(OWNER_UUID);
    }

    /**
     * 获取所有者玩家
     */
    @Nullable
    public ServerPlayer getOwner() {
        if (ownerCache != null && ownerCache.isAlive()) {
            return ownerCache;
        }

        Optional<UUID> ownerUuid = getOwnerUuid();
        if (ownerUuid.isPresent()) {
            ownerCache = (ServerPlayer) level().getPlayerByUUID(ownerUuid.get());
            return ownerCache;
        }
        return null;
    }

    /**
     * 获取皮肤 GameProfile（用于客户端渲染）
     */
    public GameProfile getSkinProfile() {
        return skinProfile;
    }

    /**
     * 获取所有者名称
     */
    public String getOwnerName() {
        return ownerName;
    }

    @Override
    public void tick() {
        super.tick();

        if (level().isClientSide())
            return;

        final var gameWorldComponent = SREGameWorldComponent.KEY.get(level());
        if (gameWorldComponent != null) {
            if (!gameWorldComponent.isRunning()) {
                discard();
            }
        }
        
        // 增加存活时间
        lifetime++;
        if (lifetime > MAX_LIFETIME) {
            this.discard();
            return;
        }

        // 检查所有者是否还存在
        Player owner = getOwner();
        if (owner == null || !owner.isAlive()) {
            this.discard();
            return;
        }
        
        // 检查距离：如果幻影与所有者距离超过20格，销毁幻影
        double distance = this.distanceTo(owner);
        if (distance > 20.0) {
            this.discard();
        }
    }

    public boolean playerHurt(Player player, ResourceLocation deathReason) {
        if (level().isClientSide())
            return false;
        Player owner = getOwner();
        if (owner != null) {
            // 通知鬼魅组件幻影被摧毁
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(level());
            if (gameWorld.isRole(owner, ModRoles.BETTER_KILLER_GHOST)) {
                BetterKillerGhostComponent ghostComp = ModComponents.BETTER_KILLER_GHOST.get(owner);
                if (ghostComp != null && ghostComp.isInShadowMode) {
                    ghostComp.onPhantomDeath(player, deathReason);
                }
            } else {
                owner.teleportTo(owner.getX(), owner.getY(), owner.getZ());
                ModEffects.pierceDeath = true;
                GameUtils.killPlayer(owner, true, player, deathReason);
                ModEffects.pierceDeath = false;
                discard();
            }
        }
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide())
            return false;

        // ⚠️ 关键修复：拦截所有伤害，永远不调用super.hurt()
        // 原版LivingEntity.hurt()会触发完整死亡流程（动画、掉落物、事件），
        // 与组件状态修改冲突导致崩溃。所有伤害都通过playerHurt()路由处理。
        
        // 忽略墙体伤害（防止卡墙时异常）
        if (source.is(DamageTypes.IN_WALL))
            return false;

        net.minecraft.world.entity.Entity attacker = source.getEntity();
        if (attacker instanceof Player player) {
            // 玩家攻击（左键、弹射物等所有来源）- 使用PHANTOM_DESTROYED死亡原因
            return playerHurt(player, GameConstants.DeathReasons.PHANTOM_DESTROYED);
        } else {
            // 非玩家伤害（环境伤害、爆炸、摔落等）：安全销毁幻影并强制退出幽影模式
            Player owner = getOwner();
            if (owner != null) {
                BetterKillerGhostComponent ghostComp = ModComponents.BETTER_KILLER_GHOST.get(owner);
                if (ghostComp != null && ghostComp.isInShadowMode) {
                    ghostComp.exitShadowModeForced();
                }
            }
            this.discard();
            return true;
        }
    }

    @Override
    public void die(DamageSource damageSource) {
        super.die(damageSource);

        // 确保通知鬼魅
        Player owner = getOwner();
        if (owner != null) {
            BetterKillerGhostComponent ghostComp = ModComponents.BETTER_KILLER_GHOST.get(owner);
            if (ghostComp != null && ghostComp.isInShadowMode) {
                ghostComp.exitShadowModeForced();
            }
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);

        if (nbt.contains("OwnerUUID")) {
            this.entityData.set(OWNER_UUID, Optional.of(nbt.getUUID("OwnerUUID")));
        }
        if (nbt.contains("OwnerName")) {
            this.ownerName = nbt.getString("OwnerName");
        }
        this.lifetime = nbt.contains("Lifetime") ? nbt.getInt("Lifetime") : 0;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);

        Optional<UUID> ownerUuid = getOwnerUuid();
        ownerUuid.ifPresent(uuid -> nbt.putUUID("OwnerUUID", uuid));
        nbt.putString("OwnerName", this.ownerName);
        nbt.putInt("Lifetime", this.lifetime);
    }

    @Override
    public boolean isPickable() {
        return true; // 可以被击中
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
    public boolean isAttackable() {
        return true; // 可以被攻击
    }

    @Override
    public boolean canBeHitByProjectile() {
        return true; // 可以被弹射物击中
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        // 完全参照傀儡师：只对虚空伤害不免疫
        if (source.is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD)) {
            return false; // 不免疫虚空伤害，让实体正常死亡
        }
        // 对其他所有伤害都不免疫
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
