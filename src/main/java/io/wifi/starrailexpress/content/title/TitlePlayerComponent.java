package io.wifi.starrailexpress.content.title;

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

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * CCA 组件：玩家拥有的称号 + 当前装备的称号。
 *
 * <p>与音乐盒同一套做法（{@code AutoSyncedComponent} + NBT 持久化），
 * 所以仓库界面在客户端能直接读到，不需要额外做 S2C 包。
 *
 * <p>玩家**同时只能装备一个**称号。
 */
public class TitlePlayerComponent implements AutoSyncedComponent {

    public static final ComponentKey<TitlePlayerComponent> KEY = ComponentRegistry.getOrCreate(
            SRE.id("player_titles"), TitlePlayerComponent.class);

    private final Player player;

    /** 已拥有的称号 id（有序，方便仓库稳定显示） */
    private final Set<String> owned = new LinkedHashSet<>();

    /** 当前装备的称号 id；null = 不显示称号 */
    @Nullable
    private String equipped = null;

    public TitlePlayerComponent(Player player) {
        this.player = player;
    }

    // ── 查询 ──

    public Set<String> getOwned() {
        return Collections.unmodifiableSet(owned);
    }

    public boolean owns(String id) {
        return id != null && owned.contains(id);
    }

    @Nullable
    public String getEquipped() {
        return equipped;
    }

    public boolean isEquipped(String id) {
        return id != null && id.equals(equipped);
    }

    public void sync() {
        KEY.sync(this.player);
    }

    // ── 写入 ──

    /** 发放称号。返回 true 表示是新得到的（之前没有） */
    public boolean grant(String id) {
        boolean added = owned.add(id);
        if (added) {
            sync();
        }
        return added;
    }

    /** 收回称号；如果正装备着会被一并卸下 */
    public boolean revoke(String id) {
        boolean removed = owned.remove(id);
        if (removed) {
            if (id.equals(equipped)) {
                equipped = null;
            }
            sync();
        }
        return removed;
    }

    /**
     * 装备 / 卸下称号。
     *
     * <p>重复装备同一个称号会**卸下**（和音乐盒的点击行为一致）。
     *
     * @return 操作后是否处于装备状态
     */
    public boolean toggleEquip(String id) {
        if (id == null || !owned.contains(id)) {
            return false;
        }
        if (id.equals(equipped)) {
            equipped = null;
            sync();
            return false;
        }
        equipped = id;
        sync();
        return true;
    }

    /** 直接卸下（收回称号、或称号定义被删除时用） */
    public void clearEquipped() {
        if (equipped != null) {
            equipped = null;
            sync();
        }
    }

    // ── NBT ──

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        ListTag list = new ListTag();
        for (String id : owned) {
            list.add(StringTag.valueOf(id));
        }
        tag.put("Titles", list);
        tag.putString("EquippedTitle", equipped == null ? "" : equipped);
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        owned.clear();
        if (tag.contains("Titles", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Titles", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                String id = list.getString(i);
                if (!id.isEmpty()) {
                    owned.add(id);
                }
            }
        }
        String eq = tag.getString("EquippedTitle");
        equipped = eq.isEmpty() ? null : eq;
        // 防御：装备的称号必须是自己拥有的
        if (equipped != null && !owned.contains(equipped)) {
            equipped = null;
        }
    }
}
