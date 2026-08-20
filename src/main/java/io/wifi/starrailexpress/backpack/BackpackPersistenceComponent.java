package io.wifi.starrailexpress.backpack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.wifi.starrailexpress.SRE;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

/**
 * 场外背包（职业卡）本地 NBT 持久化组件。
 * <p>
 * 与 CS 仓库（CS2InventoryComponent）相同思路：数据随玩家存档保存在服务端，
 * 重进/重启不丢失。MySQL 未启用时作为主存储，启用时作为本地镜像备份。
 * </p>
 */
public class BackpackPersistenceComponent implements AutoSyncedComponent {

    public static final ComponentKey<BackpackPersistenceComponent> KEY = ComponentRegistry.getOrCreate(
            SRE.id("backpack_persistence"), BackpackPersistenceComponent.class);

    private static final Gson GSON = new GsonBuilder().create();

    private final Player player;
    private BackpackState state = BackpackState.createDefault();

    public BackpackPersistenceComponent(Player player) {
        this.player = player;
    }

    public BackpackState getState() {
        return state;
    }

    public void setState(BackpackState newState) {
        if (newState == null) {
            state = BackpackState.createDefault();
            return;
        }
        state.copyFrom(newState);
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putString("StateJson", GSON.toJson(state.normalized()));
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        if (!tag.contains("StateJson", Tag.TAG_STRING) || tag.getString("StateJson").isBlank()) {
            return;
        }
        try {
            BackpackState loaded = GSON.fromJson(tag.getString("StateJson"), BackpackState.class);
            state = loaded == null ? BackpackState.createDefault() : loaded.normalized();
        } catch (RuntimeException exception) {
            state = BackpackState.createDefault();
        }
    }
}