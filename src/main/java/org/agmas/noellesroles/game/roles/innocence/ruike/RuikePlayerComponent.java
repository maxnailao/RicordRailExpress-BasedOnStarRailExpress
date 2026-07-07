package org.agmas.noellesroles.game.roles.innocence.ruike;

import io.wifi.starrailexpress.api.RoleComponent;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.component.ModComponents;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 时空旅者组件
 *
 * 跟踪该玩家放置的传送门 UUID（最多2个，用于配对）
 */
public class RuikePlayerComponent implements RoleComponent {

    /** 组件键 */
    public static final ComponentKey<RuikePlayerComponent> KEY = ModComponents.RUIKE;

    private final Player player;

    /** 该玩家放置的传送门 UUID 列表（最多2个） */
    public final List<UUID> portalUuids = new ArrayList<>();

    public RuikePlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean shouldSyncWith(net.minecraft.server.level.ServerPlayer p) {
        return p == this.player;
    }

    public void sync() {
        if (player != null && !player.level().isClientSide()) {
            ModComponents.RUIKE.sync(this.player);
        }
    }

    @Override
    public void init() {
        portalUuids.clear();
        sync();
    }

    @Override
    public void clear() {
        portalUuids.clear();
        sync();
    }

    /**
     * 添加传送门 UUID（无上限，由技能逻辑管理淘汰）
     */
    public void addPortal(UUID uuid) {
        portalUuids.add(uuid);
        sync();
    }

    /**
     * 移除传送门 UUID
     */
    public void removePortal(UUID uuid) {
        portalUuids.remove(uuid);
        sync();
    }

    /**
     * 获取当前传送门数量
     */
    public int getPortalCount() {
        return portalUuids.size();
    }

    // ==================== NBT 序列化 ====================

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("portalCount", portalUuids.size());
        for (int i = 0; i < portalUuids.size(); i++) {
            tag.putUUID("portal_" + i, portalUuids.get(i));
        }
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        portalUuids.clear();
        int count = tag.contains("portalCount") ? tag.getInt("portalCount") : 0;
        for (int i = 0; i < count; i++) {
            if (tag.hasUUID("portal_" + i)) {
                portalUuids.add(tag.getUUID("portal_" + i));
            }
        }
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {}

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {}
}
