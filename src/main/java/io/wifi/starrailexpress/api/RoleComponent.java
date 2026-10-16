package io.wifi.starrailexpress.api;

import net.fabricmc.api.EnvType;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.util.CheckEnvironment;

/**
 * @author canyuesama
 */
public interface RoleComponent extends AutoSyncedComponent {
    Player getPlayer();

    void init();

    void clear();

    @Override
    default boolean shouldSyncWith(ServerPlayer player) {
        return this.getPlayer() == player;
    }

    void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup);

    void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup);

    default void writeToSyncNbtWithPlayer(CompoundTag tag, HolderLookup.Provider registryLookup,
            ServerPlayer recipient) {
        // if (!SREGameWorldComponent.KEY.get(recipient.level()).isRunning()) {
        //     return;
        // }
        writeToSyncNbt(tag, registryLookup);
    }

    @Override
    default void writeSyncPacket(RegistryFriendlyByteBuf buf, ServerPlayer recipient) {
        CompoundTag tag = new CompoundTag();
        this.writeToSyncNbtWithPlayer(tag, buf.registryAccess(), recipient);
        buf.writeNbt(tag);
    }

    @Override
    @CheckEnvironment(EnvType.CLIENT)
    default void applySyncPacket(RegistryFriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        if (tag != null) {
            this.readFromSyncNbt(tag, buf.registryAccess());
        }
    }

    // ==================== NBT 读取工具 ====================
    // 供 RoleComponent 与 RoleData 共用：类型不匹配时回退到默认值，避免读到脏数据直接抛异常。

    /**
     * 一般情况下请不要使用这个方法。这个方法会让玩家NBT长度暴增，极有可能导致玩家无法进入游戏。更建议使用writeToSyncNbt。
     */
    @Override
    default void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    /**
     * 一般情况下无需用到此方法。
     */
    @Override
    default void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    default String getStringTag(CompoundTag tag, String name, String defaultValue) {
        return getStringTagOrDefault(tag, name, defaultValue);
    }

    default int getIntTag(CompoundTag tag, String name, int defaultValue) {
        return getIntTagOrDefault(tag, name, defaultValue);
    }

    default Byte getByteTag(CompoundTag tag, String name, Byte defaultValue) {
        return getByteTagOrDefault(tag, name, defaultValue);
    }

    default short getShortTag(CompoundTag tag, String name, short defaultValue) {
        return getShortTagOrDefault(tag, name, defaultValue);
    }

    default long getLongTag(CompoundTag tag, String name, long defaultValue) {
        return getLongTagOrDefault(tag, name, defaultValue);
    }

    default double getDoubleTag(CompoundTag tag, String name, double defaultValue) {
        return getDoubleTagOrDefault(tag, name, defaultValue);
    }

    default float getFloatTag(CompoundTag tag, String name, float defaultValue) {
        return getFloatTagOrDefault(tag, name, defaultValue);
    }

    default boolean getBooleanTag(CompoundTag tag, String name, boolean defaultValue) {
        return getBooleanTagOrDefault(tag, name, defaultValue);
    }

    public static String getStringTagOrDefault(CompoundTag tag, String name, String defaultValue) {
        if (tag.contains(name, Tag.TAG_STRING)) {
            return tag.getString(name);
        }
        return defaultValue;
    }

    public static int getIntTagOrDefault(CompoundTag tag, String name, int defaultValue) {
        if (tag.contains(name, Tag.TAG_INT)) {
            return tag.getInt(name);
        }
        return defaultValue;
    }

    public static Byte getByteTagOrDefault(CompoundTag tag, String name, Byte defaultValue) {
        if (tag.contains(name, Tag.TAG_BYTE)) {
            return tag.getByte(name);
        }
        return defaultValue;
    }

    public static short getShortTagOrDefault(CompoundTag tag, String name, short defaultValue) {
        if (tag.contains(name, Tag.TAG_SHORT)) {
            return tag.getShort(name);
        }
        return defaultValue;
    }

    public static long getLongTagOrDefault(CompoundTag tag, String name, long defaultValue) {
        if (tag.contains(name, Tag.TAG_LONG)) {
            return tag.getLong(name);
        }
        return defaultValue;
    }

    public static double getDoubleTagOrDefault(CompoundTag tag, String name, double defaultValue) {
        if (tag.contains(name, Tag.TAG_DOUBLE)) {
            return tag.getDouble(name);
        }
        return defaultValue;
    }

    public static float getFloatTagOrDefault(CompoundTag tag, String name, float defaultValue) {
        if (tag.contains(name, Tag.TAG_FLOAT)) {
            return tag.getFloat(name);
        }
        return defaultValue;
    }

    public static boolean getBooleanTagOrDefault(CompoundTag tag, String name, boolean defaultValue) {
        if (tag.contains(name)) {
            return tag.getBoolean(name);
        }
        return defaultValue;
    }
}
