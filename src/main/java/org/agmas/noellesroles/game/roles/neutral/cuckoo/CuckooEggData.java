package org.agmas.noellesroles.game.roles.neutral.cuckoo;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 布谷鸟蛋数据管理，替代 Fabric 中不可用的 getPersistentData()。
 * 服务端和客户端各自维护一份蛋 UUID 映射。
 */
public class CuckooEggData {

    /** 蛋的交互实体 tag：给蛋提供可被枪/刀/棒子命中的碰撞箱 */
    public static final String EGG_INTERACTION_TAG = "cuckoo_egg_interaction";

    public static class EggInfo {
        public final UUID ownerUuid;
        /** 破坏进度（tick），达到100时蛋被打碎 */
        public int breakProgress;
        /** 缓存的蛋实体引用，避免每tick全量搜索 */
        public transient Entity eggEntity;
        /** 绑定的交互实体（服务端用，供命中判定） */
        public transient Entity interactionEntity;

        public EggInfo(UUID ownerUuid) {
            this.ownerUuid = ownerUuid;
            this.breakProgress = 0;
            this.eggEntity = null;
            this.interactionEntity = null;
        }
    }

    /** 实体 UUID -> 蛋信息（服务端用） */
    private static final Map<UUID, EggInfo> EGGS = new java.util.concurrent.ConcurrentHashMap<>();

    /** 交互实体 UUID -> 蛋实体 UUID（服务端权威映射，防止客户端伪造目标） */
    private static final Map<UUID, UUID> INTERACTION_TO_EGG = new java.util.concurrent.ConcurrentHashMap<>();

    /** 客户端已知的蛋 UUID 集合 */
    private static final Set<UUID> CLIENT_EGGS = new HashSet<>();

    /** 客户端：当前玩家拥有的蛋 UUID 集合 */
    private static final Set<UUID> CLIENT_OWNED_EGGS = new HashSet<>();

    // === 服务端方法 ===

    public static void registerEgg(Entity eggEntity, UUID ownerUuid) {
        registerEgg(eggEntity, null, ownerUuid);
    }

    public static void registerEgg(Entity eggEntity, Entity interactionEntity, UUID ownerUuid) {
        EggInfo info = new EggInfo(ownerUuid);
        info.eggEntity = eggEntity;
        info.interactionEntity = interactionEntity;
        EGGS.put(eggEntity.getUUID(), info);
        if (interactionEntity != null) {
            INTERACTION_TO_EGG.put(interactionEntity.getUUID(), eggEntity.getUUID());
        }
    }

    public static boolean isCuckooEgg(Entity entity) {
        if (entity == null) return false;
        UUID uuid = entity.getUUID();
        return EGGS.containsKey(uuid) || INTERACTION_TO_EGG.containsKey(uuid);
    }

    /** 由蛋实体或其交互实体解析出蛋信息（服务端权威） */
    public static EggInfo resolveEggInfo(Entity any) {
        if (any == null) return null;
        EggInfo info = EGGS.get(any.getUUID());
        if (info != null) return info;
        UUID eggUuid = INTERACTION_TO_EGG.get(any.getUUID());
        return eggUuid == null ? null : EGGS.get(eggUuid);
    }

    /** 注销蛋及其交互实体映射 */
    public static void unregisterEgg(UUID eggUuid) {
        EGGS.remove(eggUuid);
        INTERACTION_TO_EGG.values().removeIf(eggUuid::equals);
    }

    /** 获取所有已注册的蛋（用于tick遍历） */
    public static Map<UUID, EggInfo> getAllEggs() {
        return EGGS;
    }

    public static void reset() {
        EGGS.clear();
        INTERACTION_TO_EGG.clear();
        CLIENT_EGGS.clear();
        CLIENT_OWNED_EGGS.clear();
    }

    public static boolean hasNearbyEgg(net.minecraft.world.level.Level level, AABB aabb) {
        for (Entity e : level.getEntities(null, aabb)) {
            if (isCuckooEgg(e)) return true;
        }
        return false;
    }

    // === 客户端方法 ===

    /** 客户端：判断实体是否为自己下的蛋 */
    public static boolean isOwnEggClient(Entity entity) {
        return entity != null && CLIENT_OWNED_EGGS.contains(entity.getUUID());
    }

    /** 客户端：从 sync NBT 更新蛋数据 */
    public static void readClientSync(CompoundTag tag) {
        CLIENT_EGGS.clear();
        CLIENT_OWNED_EGGS.clear();
        ListTag eggList = tag.getList("CuckooEggs", CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < eggList.size(); i++) {
            CompoundTag eggTag = eggList.getCompound(i);
            UUID eggUuid = eggTag.getUUID("UUID");
            boolean owned = eggTag.getBoolean("Owned");
            CLIENT_EGGS.add(eggUuid);
            if (owned) CLIENT_OWNED_EGGS.add(eggUuid);
        }
    }

    /** 服务端：写入 sync NBT 中的蛋数据 */
    public static void writeServerSync(CompoundTag tag, UUID ownerUuid) {
        ListTag eggList = new ListTag();
        for (Map.Entry<UUID, EggInfo> entry : EGGS.entrySet()) {
            CompoundTag eggTag = new CompoundTag();
            eggTag.putUUID("UUID", entry.getKey());
            eggTag.putBoolean("Owned", entry.getValue().ownerUuid.equals(ownerUuid));
            eggList.add(eggTag);
        }
        tag.put("CuckooEggs", eggList);
    }
}
