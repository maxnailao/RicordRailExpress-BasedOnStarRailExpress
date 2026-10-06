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

    /** 神话碎片数量（背包右上角展示，不作为皮肤入包） */
    private int mythicShards = 0;

    /** 开箱保底计数器：每开一次 +1，达到 {@link #PITY_THRESHOLD} 或未达时 2% 提前命中会获得碎片并归零 */
    private int boxPityCounter = 0;

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

    /**
     * 批量添加皮肤（各 1 个，仅同步一次，用于指令批量发放等场景）
     */
    public void addSkins(java.util.Collection<String> skinIds) {
        boolean changed = false;
        for (String skinId : skinIds) {
            skins.merge(skinId, 1, Integer::sum);
            changed = true;
        }
        if (changed) {
            sync();
        }
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

    // ── 神话碎片 / 开箱保底 ──

    /** 保底触发阈值：累计开箱达到该次数必得 150 枚神话碎片 */
    public static final int PITY_THRESHOLD = 80;

    /** 未达保底时，每次开箱提前命中神话碎片的概率（百分比） */
    public static final int LUCKY_SHARD_CHANCE_PERCENT = 2;

    /** 神话商店购买一款神话皮肤所需神话碎片 */
    public static final int MYTHIC_SKIN_SHARD_PRICE = 150;

    /** 出售一款神话品质皮肤获得的神话碎片 */
    public static final int MYTHIC_SHARD_SELL_AMOUNT = 125;

    public int getMythicShards() {
        return mythicShards;
    }

    public void addMythicShards(int count) {
        if (count <= 0) return;
        this.mythicShards += count;
    }

    /**
     * 消费神话碎片（供后续兑换所使用）。余额不足时返回 false 且不扣减。
     */
    public boolean spendMythicShards(int count) {
        if (count <= 0) return false;
        if (this.mythicShards < count) return false;
        this.mythicShards -= count;
        return true;
    }

    public int getBoxPityCounter() {
        return boxPityCounter;
    }

    public void increaseBoxPityCounter(int delta) {
        this.boxPityCounter = Math.max(0, this.boxPityCounter + delta);
    }

    public void resetBoxPityCounter() {
        this.boxPityCounter = 0;
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
        tag.putInt("MythicShards", mythicShards);
        tag.putInt("BoxPityCounter", boxPityCounter);
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
        mythicShards = tag.contains("MythicShards") ? tag.getInt("MythicShards") : 0;
        boxPityCounter = tag.contains("BoxPityCounter") ? tag.getInt("BoxPityCounter") : 0;
    }

    // AutoSyncedComponent 默认使用 writeToNbt/readFromNbt 进行同步
}
