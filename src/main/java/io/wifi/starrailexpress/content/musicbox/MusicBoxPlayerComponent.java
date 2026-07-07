package io.wifi.starrailexpress.content.musicbox;

import io.wifi.starrailexpress.SRE;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

import java.util.*;

/**
 * CCA 组件：持久化玩家的音乐盒拥有列表和当前装备的音乐盒。
 * <p>
 * 默认无音乐盒（equippedBox == null）。
 * </p>
 * <p>回退策略：整个文件删除即可，不影响其他模块。</p>
 */
public class MusicBoxPlayerComponent implements AutoSyncedComponent {

    public static final ComponentKey<MusicBoxPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            SRE.id("music_box"), MusicBoxPlayerComponent.class);

    private final Player player;

    /** 玩家已拥有的音乐盒 ID 集合 */
    private final Set<String> ownedBoxes = new LinkedHashSet<>();

    /** 当前装备的音乐盒 ID，null 表示无音乐盒 */
    @Nullable
    private String equippedBox = null;

    public MusicBoxPlayerComponent(Player player) {
        this.player = player;
    }

    // ── 查询 ──

    public boolean hasMusicBox(String id) {
        return ownedBoxes.contains(id);
    }

    @Nullable
    public String getEquippedBox() {
        return equippedBox;
    }

    public boolean hasEquipped() {
        return equippedBox != null;
    }

    public Set<String> getOwnedBoxes() {
        return Collections.unmodifiableSet(ownedBoxes);
    }

    // ── 修改 ──

    /**
     * 设置装备的音乐盒。传 null 取消装备。
     */
    public void setEquippedBox(@Nullable String id) {
        this.equippedBox = id;
        sync();
    }

    /**
     * 给玩家添加一个音乐盒（重复添加无效）。
     */
    public boolean addMusicBox(String id) {
        boolean added = ownedBoxes.add(id);
        if (added) {
            sync();
        }
        return added;
    }

    /**
     * 移除玩家的一个音乐盒。如果移除的是当前装备的，同时取消装备。
     */
    public boolean removeMusicBox(String id) {
        boolean removed = ownedBoxes.remove(id);
        if (removed) {
            if (id.equals(equippedBox)) {
                equippedBox = null;
            }
            sync();
        }
        return removed;
    }

    // ── 同步 ──

    public void sync() {
        KEY.sync(this.player);
    }

    // ── NBT 持久化 ──

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 拥有列表
        ListTag list = new ListTag();
        for (String id : ownedBoxes) {
            list.add(StringTag.valueOf(id));
        }
        tag.put("OwnedBoxes", list);
        // 装备
        if (equippedBox != null) {
            tag.putString("EquippedBox", equippedBox);
        }
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        ownedBoxes.clear();
        ListTag list = tag.getList("OwnedBoxes", Tag.TAG_STRING);
        for (Tag t : list) {
            ownedBoxes.add(t.getAsString());
        }
        equippedBox = tag.contains("EquippedBox") ? tag.getString("EquippedBox") : null;
    }

    // AutoSyncedComponent 默认使用 writeToNbt/readFromNbt 进行同步
}
