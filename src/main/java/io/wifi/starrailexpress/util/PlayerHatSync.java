package io.wifi.starrailexpress.util;

import io.wifi.starrailexpress.data.PlayerEconomyManager;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.player.Player;

/**
 * 帽子皮肤实体数据同步
 * <p>
 * 帽子皮肤没有物品载体（不像刀/枪可以通过手持物品堆栈的 SKIN 组件跨客户端同步），
 * 因此通过玩家的 SynchedEntityData 同步当前装备的帽子皮肤名，
 * 保证其他玩家的客户端也能看到头顶的帽子。
 * </p>
 */
public final class PlayerHatSync {

    /** 未装备帽子时的默认值 */
    public static final String DEFAULT = "default";

    public static final EntityDataAccessor<String> EQUIPPED_HAT =
            SynchedEntityData.defineId(Player.class, EntityDataSerializers.STRING);

    private PlayerHatSync() {
    }

    /**
     * 强制提前完成类初始化，必须在模组初始化阶段（任何实体构造之前）调用。
     * <p>
     * 实体数据 ID 由全局计数器在类加载时递增分配；若等到第一次玩家构造
     * （defineSynchedData 被调用）时才触发本类静态初始化，分配到的 ID 会与
     * {@code SynchedEntityData.Builder} 创建时的定长数组容量相等，
     * 导致 define 时数组越界崩溃（首个被构造的玩家，如 FakePlayer）。
     * </p>
     */
    public static void ensureInitialized() {
        // 引用静态字段以触发 <clinit>，在任何实体构造前完成 EQUIPPED_HAT 的 ID 分配
        if (EQUIPPED_HAT == null) {
            throw new IllegalStateException("PlayerHatSync accessor failed to initialize");
        }
        io.wifi.starrailexpress.SRE.LOGGER.info("[HatSync] EQUIPPED_HAT accessor id = {}",
                EQUIPPED_HAT.id());
    }

    /** 在 Player.defineSynchedData 中注册数据项（通过 Mixin 调用） */
    public static void define(SynchedEntityData.Builder builder) {
        builder.define(EQUIPPED_HAT, DEFAULT);
    }

    /** 读取玩家当前装备的帽子皮肤（客户端/服务端均可用） */
    public static String getHat(Player player) {
        return player.getEntityData().get(EQUIPPED_HAT);
    }

    /** 服务端设置玩家装备的帽子皮肤，自动同步给所有追踪客户端 */
    public static void setHat(Player player, String hatSkin) {
        if (player.level().isClientSide()) {
            return;
        }
        String value = hatSkin == null || hatSkin.isBlank() ? DEFAULT : hatSkin;
        player.getEntityData().set(EQUIPPED_HAT, value);
        io.wifi.starrailexpress.SRE.LOGGER.info("[HatSync] setHat {} -> {} (clientSide={})",
                player.getName().getString(), value, player.level().isClientSide());
    }

    /**
     * 当装备皮肤类型为帽子时同步实体数据。
     * 供 ItemSkinManager.setEquippedSkinForItemType 统一调用。
     */
    public static void onSkinEquipped(Player player, String itemTypeName, String skinName) {
        if (!"hat".equals(PlayerEconomyManager.normalizeItemName(itemTypeName))) {
            return;
        }
        setHat(player, skinName);
    }
}
