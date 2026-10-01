package io.wifi.starrailexpress.cca;

import io.wifi.starrailexpress.SRE;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CCA 组件：CS2 风格仓库系统
 * <p>
 * 存储玩家的箱子、钥匙、货币和箱子掉落累积概率。
 * </p>
 * <p>回退策略：整个文件删除即可，不影响其他模块。</p>
 */
public class CS2InventoryComponent implements AutoSyncedComponent {

    public static final ComponentKey<CS2InventoryComponent> KEY = ComponentRegistry.getOrCreate(
            SRE.id("cs2_inventory"), CS2InventoryComponent.class);

    private final Player player;

    /** 玩家拥有的箱子 {boxId: count} */
    private final Map<String, Integer> boxes = new ConcurrentHashMap<>();

    /** 玩家拥有的钥匙 {keyId: count} */
    private final Map<String, Integer> keys = new ConcurrentHashMap<>();

    /** 玩家拥有的皮肤 {skinId(itemType/skinName): count} */
    private final Map<String, Integer> skins = new ConcurrentHashMap<>();

    /** 玩家拥有的音乐盒 {musicBoxId: count} */
    private final Map<String, Integer> musicBoxes = new ConcurrentHashMap<>();

    /** 箱子掉落累积概率（百分比，初始10，每次未掉落+5） */
    private int boxDropChance = 10;

    public CS2InventoryComponent(Player player) {
        this.player = player;
    }

    // ── 箱子操作 ──

    public Map<String, Integer> getBoxes() {
        return Collections.unmodifiableMap(boxes);
    }

    public int getBoxCount(String boxId) {
        return boxes.getOrDefault(boxId, 0);
    }

    public void addBox(String boxId, int count) {
        if (count <= 0) return;
        boxes.merge(boxId, count, Integer::sum);
        sync();
    }

    public boolean removeBox(String boxId, int count) {
        int current = boxes.getOrDefault(boxId, 0);
        if (current < count) return false;
        int remaining = current - count;
        if (remaining <= 0) {
            boxes.remove(boxId);
        } else {
            boxes.put(boxId, remaining);
        }
        sync();
        return true;
    }

    // ── 钥匙操作 ──

    public Map<String, Integer> getKeys() {
        return Collections.unmodifiableMap(keys);
    }

    public int getKeyCount(String keyId) {
        return keys.getOrDefault(keyId, 0);
    }

    public void addKey(String keyId, int count) {
        if (count <= 0) return;
        keys.merge(keyId, count, Integer::sum);
        sync();
    }

    public boolean removeKey(String keyId, int count) {
        int current = keys.getOrDefault(keyId, 0);
        if (current < count) return false;
        int remaining = current - count;
        if (remaining <= 0) {
            keys.remove(keyId);
        } else {
            keys.put(keyId, remaining);
        }
        sync();
        return true;
    }

    // ── 皮肤操作 ──

    public Map<String, Integer> getSkins() {
        return Collections.unmodifiableMap(skins);
    }

    public int getSkinCount(String skinId) {
        return skins.getOrDefault(skinId, 0);
    }

    public void addSkin(String skinId, int count) {
        if (count <= 0) return;
        skins.merge(skinId, count, Integer::sum);
        sync();
    }

    public boolean removeSkin(String skinId, int count) {
        int current = skins.getOrDefault(skinId, 0);
        if (current < count) return false;
        int remaining = current - count;
        if (remaining <= 0) {
            skins.remove(skinId);
        } else {
            skins.put(skinId, remaining);
        }
        sync();
        return true;
    }

    public boolean hasSkin(String skinId) {
        return skins.getOrDefault(skinId, 0) > 0;
    }

    // ── 音乐盒操作 ──

    public Map<String, Integer> getMusicBoxes() {
        return Collections.unmodifiableMap(musicBoxes);
    }

    public int getMusicBoxCount(String boxId) {
        return musicBoxes.getOrDefault(boxId, 0);
    }

    public void addMusicBox(String boxId, int count) {
        if (count <= 0) return;
        musicBoxes.merge(boxId, count, Integer::sum);
        sync();
    }

    public boolean removeMusicBox(String boxId, int count) {
        int current = musicBoxes.getOrDefault(boxId, 0);
        if (current < count) return false;
        int remaining = current - count;
        if (remaining <= 0) {
            musicBoxes.remove(boxId);
        } else {
            musicBoxes.put(boxId, remaining);
        }
        sync();
        return true;
    }

    public boolean hasMusicBox(String boxId) {
        return musicBoxes.getOrDefault(boxId, 0) > 0;
    }

    // ── 箱子掉落概率 ──

    public int getBoxDropChance() {
        return boxDropChance;
    }

    public void setBoxDropChance(int chance) {
        this.boxDropChance = Math.max(0, chance);
    }

    /**
     * 增加掉落概率（未掉落时累加）
     */
    public void addBoxDropChance(int delta) {
        this.boxDropChance = Math.max(0, this.boxDropChance + delta);
    }

    /**
     * 重置掉落概率为初始值
     */
    public void resetBoxDropChance() {
        this.boxDropChance = 10;
    }

    // ── 同步 ──

    public void sync() {
        KEY.sync(this.player);
    }

    // ── NBT 持久化 ──

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 箱子
        CompoundTag boxesTag = new CompoundTag();
        for (Map.Entry<String, Integer> entry : boxes.entrySet()) {
            boxesTag.putInt(entry.getKey(), entry.getValue());
        }
        tag.put("Boxes", boxesTag);

        // 钥匙
        CompoundTag keysTag = new CompoundTag();
        for (Map.Entry<String, Integer> entry : keys.entrySet()) {
            keysTag.putInt(entry.getKey(), entry.getValue());
        }
        tag.put("Keys", keysTag);

        // 皮肤
        CompoundTag skinsTag = new CompoundTag();
        for (Map.Entry<String, Integer> entry : skins.entrySet()) {
            skinsTag.putInt(entry.getKey(), entry.getValue());
        }
        tag.put("Skins", skinsTag);

        // 音乐盒
        CompoundTag musicTag = new CompoundTag();
        for (Map.Entry<String, Integer> entry : musicBoxes.entrySet()) {
            musicTag.putInt(entry.getKey(), entry.getValue());
        }
        tag.put("MusicBoxes", musicTag);

        tag.putInt("BoxDropChance", boxDropChance);
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        boxes.clear();
        if (tag.contains("Boxes", Tag.TAG_COMPOUND)) {
            CompoundTag boxesTag = tag.getCompound("Boxes");
            for (String key : boxesTag.getAllKeys()) {
                boxes.put(key, boxesTag.getInt(key));
            }
        }

        keys.clear();
        if (tag.contains("Keys", Tag.TAG_COMPOUND)) {
            CompoundTag keysTag = tag.getCompound("Keys");
            for (String key : keysTag.getAllKeys()) {
                keys.put(key, keysTag.getInt(key));
            }
        }

        skins.clear();
        if (tag.contains("Skins", Tag.TAG_COMPOUND)) {
            CompoundTag skinsTag = tag.getCompound("Skins");
            for (String key : skinsTag.getAllKeys()) {
                skins.put(key, skinsTag.getInt(key));
            }
        }

        musicBoxes.clear();
        if (tag.contains("MusicBoxes", Tag.TAG_COMPOUND)) {
            CompoundTag musicTag = tag.getCompound("MusicBoxes");
            for (String key : musicTag.getAllKeys()) {
                musicBoxes.put(key, musicTag.getInt(key));
            }
        }

        boxDropChance = tag.contains("BoxDropChance") ? tag.getInt("BoxDropChance") : 10;
    }

    // AutoSyncedComponent 默认使用 writeToNbt/readFromNbt 进行同步
}
