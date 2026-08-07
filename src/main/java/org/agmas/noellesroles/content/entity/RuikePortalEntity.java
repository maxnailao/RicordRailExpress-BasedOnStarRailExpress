package org.agmas.noellesroles.content.entity;

import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.agmas.noellesroles.game.roles.innocence.ruike.RuikePlayerComponent;
import org.agmas.noellesroles.init.ModEntities;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 时空旅者传送门实体
 *
 * 外观类似原版地狱传送门，玩家碰撞时传送到配对传送门。
 * 传送后有8秒冷却，防止重复传送。
 */
public class RuikePortalEntity extends Entity {

    /** 传送冷却时间（tick） */
    public static final int TELEPORT_COOLDOWN_TICKS = 160; // 8秒

    /** 音效截断延迟（tick）：PORTAL_TRAVEL约70tick，保留前40% */
    private static final int SOUND_STOP_DELAY_TICKS = 28;

    /** 传送冷却追踪：玩家UUID -> 冷却结束tick */
    private static final Map<UUID, Integer> teleportCooldowns = new HashMap<>();

    /** 待截断音效追踪：玩家UUID -> 停止tick */
    private static final Map<UUID, Integer> pendingSoundStops = new HashMap<>();

    // 同步数据：放置者UUID
    private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID =
            SynchedEntityData.defineId(RuikePortalEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    // 配对传送门UUID
    private static final EntityDataAccessor<Optional<UUID>> PAIR_UUID =
            SynchedEntityData.defineId(RuikePortalEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    public RuikePortalEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER_UUID, Optional.empty());
        builder.define(PAIR_UUID, Optional.empty());
    }

    // ==================== 数据访问 ====================

    public void setOwnerUUID(UUID uuid) {
        this.entityData.set(OWNER_UUID, Optional.ofNullable(uuid));
    }

    @Nullable
    public UUID getOwnerUUID() {
        return this.entityData.get(OWNER_UUID).orElse(null);
    }

    public void setPairUUID(@Nullable UUID uuid) {
        this.entityData.set(PAIR_UUID, Optional.ofNullable(uuid));
    }

    @Nullable
    public UUID getPairUUID() {
        return this.entityData.get(PAIR_UUID).orElse(null);
    }

    // ==================== Tick 逻辑 ====================

    @Override
    public void tick() {
        super.tick();

        if (level().isClientSide()) {
            return;
        }

        // 服务端：检查待截断的音效
        if (!pendingSoundStops.isEmpty()) {
            int currentTick = ((ServerLevel) level()).getServer().getTickCount();
            Iterator<Map.Entry<UUID, Integer>> iter = pendingSoundStops.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<UUID, Integer> entry = iter.next();
                if (currentTick >= entry.getValue()) {
                    UUID uuid = entry.getKey();
                    iter.remove();
                    ServerPlayer p = ((ServerLevel) level()).getServer().getPlayerList().getPlayer(uuid);
                    if (p != null) {
                        p.connection.send(new ClientboundStopSoundPacket(
                                SoundEvents.PORTAL_TRAVEL.getLocation(), SoundSource.PLAYERS));
                    }
                }
            }
        }

        // 服务端：检测玩家碰撞
        ServerLevel serverLevel = (ServerLevel) level();
        AABB portalBox = getBoundingBox().inflate(0.2); // 稍微扩大检测范围

        for (ServerPlayer player : serverLevel.getEntitiesOfClass(ServerPlayer.class, portalBox)) {
            if (!GameUtils.isPlayerAliveAndSurvival(player)) {
                continue;
            }
            // 必须蹲下才能被传送
            if (!player.isShiftKeyDown()) {
                continue;
            }
            // 检查传送冷却
            if (isOnTeleportCooldown(player)) {
                continue;
            }
            tryTeleport(player, serverLevel);
        }
    }

    /**
     * 尝试传送玩家到配对传送门
     */
    private void tryTeleport(ServerPlayer player, ServerLevel serverLevel) {
        UUID pairUuid = getPairUUID();

        if (pairUuid == null) {
            // 没有配对传送门，提醒玩家
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.ruike.no_paired_portal")
                            .withStyle(ChatFormatting.YELLOW),
                    true);
            // 给予短冷却避免消息刷屏
            setTeleportCooldown(player, 20); // 1秒
            return;
        }

        // 查找配对传送门实体
        Entity pairedEntity = serverLevel.getEntity(pairUuid);
        if (!(pairedEntity instanceof RuikePortalEntity pairedPortal) || pairedPortal.isRemoved()) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.ruike.paired_portal_missing")
                            .withStyle(ChatFormatting.RED),
                    true);
            setTeleportCooldown(player, 20);
            return;
        }

        // 传送玩家到配对传送门位置
        double targetX = pairedPortal.getX();
        double targetY = pairedPortal.getY();
        double targetZ = pairedPortal.getZ();

        player.teleportTo(targetX, targetY, targetZ);

        // 播放传送音效（音量0.1F，仅保留前40%后截断）
        serverLevel.playSound(null, getX(), getY(), getZ(),
                SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.1F, 1.0F);
        serverLevel.playSound(null, targetX, targetY, targetZ,
                SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.1F, 1.0F);
        // 记录音效截断时间（28tick后由tick逻辑发送停止包）
        int nowTick = serverLevel.getServer().getTickCount();
        pendingSoundStops.put(player.getUUID(), nowTick + SOUND_STOP_DELAY_TICKS);

        // 设置8秒传送冷却
        setTeleportCooldown(player, TELEPORT_COOLDOWN_TICKS);

        player.displayClientMessage(
                Component.translatable("message.noellesroles.ruike.teleported")
                        .withStyle(ChatFormatting.AQUA),
                true);
    }

    // ==================== 传送冷却管理 ====================

    private static void setTeleportCooldown(Player player, int ticks) {
        int currentTick = player.level().getServer() != null
                ? player.level().getServer().getTickCount()
                : (int) player.level().getGameTime();
        teleportCooldowns.put(player.getUUID(), currentTick + ticks);
    }

    private static boolean isOnTeleportCooldown(Player player) {
        Integer endTick = teleportCooldowns.get(player.getUUID());
        if (endTick == null) {
            return false;
        }
        int currentTick = player.level().getServer() != null
                ? player.level().getServer().getTickCount()
                : (int) player.level().getGameTime();
        if (currentTick >= endTick) {
            teleportCooldowns.remove(player.getUUID());
            return false;
        }
        return true;
    }

    /**
     * 清除所有传送冷却和待截断音效（游戏结束时调用）
     */
    public static void clearAllTeleportCooldowns() {
        teleportCooldowns.clear();
        pendingSoundStops.clear();
    }

    /**
     * 获取玩家传送冷却剩余 tick（用于 HUD 显示，客户端估算）
     */
    public static int getPlayerTeleportCooldownRemaining(Player player) {
        Integer endTick = teleportCooldowns.get(player.getUUID());
        if (endTick == null) return 0;
        int currentTick = player.level().getServer() != null
                ? player.level().getServer().getTickCount()
                : (int) player.level().getGameTime();
        int remaining = endTick - currentTick;
        return Math.max(0, remaining);
    }

    // ==================== 移除处理 ====================

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide()) {
            // 从玩家组件中移除此传送门
            UUID ownerUuid = getOwnerUUID();
            if (ownerUuid != null) {
                Player owner = level().getPlayerByUUID(ownerUuid);
                if (owner != null) {
                    RuikePlayerComponent comp = RuikePlayerComponent.KEY.get(owner);
                    if (comp != null) {
                        comp.removePortal(this.getUUID());
                    }
                }
            }

            // 清除配对传送门的配对引用
            UUID pairUuid = getPairUUID();
            if (pairUuid != null) {
                Entity paired = ((ServerLevel) level()).getEntity(pairUuid);
                if (paired instanceof RuikePortalEntity pairedPortal) {
                    pairedPortal.setPairUUID(null);
                }
            }
        }
        super.remove(reason);
    }

    // ==================== NBT 持久化 ====================

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("OwnerUUID")) {
            setOwnerUUID(tag.getUUID("OwnerUUID"));
        }
        if (tag.hasUUID("PairUUID")) {
            setPairUUID(tag.getUUID("PairUUID"));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        UUID owner = getOwnerUUID();
        if (owner != null) {
            tag.putUUID("OwnerUUID", owner);
        }
        UUID pair = getPairUUID();
        if (pair != null) {
            tag.putUUID("PairUUID", pair);
        }
    }
}
