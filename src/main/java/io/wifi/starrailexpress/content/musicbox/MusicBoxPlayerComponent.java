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

    /** 音乐盒抽奖次数 */
    private int lotteryTickets = 0;

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

    public int getLotteryTickets() {
        return lotteryTickets;
    }

    /**
     * 增加抽奖次数。
     */
    public void addLotteryTicket() {
        this.lotteryTickets++;
        sync();
    }

    /**
     * 执行抽奖：消耗 1 次机会，20% 概率抽中未拥有的音乐盒。
     * @return 抽中的音乐盒 ID，null 表示未抽中
     */
    @Nullable
    public String drawLottery() {
        if (lotteryTickets <= 0) return null;
        lotteryTickets--;

        // 收集未拥有的音乐盒
        List<String> unowned = new ArrayList<>();
        for (MusicBox box : MusicBoxRegistry.getAll()) {
            if (!ownedBoxes.contains(box.id())) {
                unowned.add(box.id());
            }
        }
        if (unowned.isEmpty()) {
            sync();
            return null; // 已拥有全部
        }

        // 20% 概率抽中
        java.util.Random random = new java.util.Random();
        if (random.nextInt(100) < 20) {
            String wonId = unowned.get(random.nextInt(unowned.size()));
            ownedBoxes.add(wonId);
            sync();
            return wonId;
        }
        sync();
        return null; // 未抽中
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

    /**
     * 清空所有音乐盒数据（拥有列表 + 装备 + 抽奖券），用于系统迁移
     */
    public void clearAllData() {
        ownedBoxes.clear();
        equippedBox = null;
        lotteryTickets = 0;
        sync();
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
        tag.putInt("LotteryTickets", lotteryTickets);
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        ownedBoxes.clear();
        ListTag list = tag.getList("OwnedBoxes", Tag.TAG_STRING);
        for (Tag t : list) {
            ownedBoxes.add(t.getAsString());
        }
        equippedBox = tag.contains("EquippedBox") ? tag.getString("EquippedBox") : null;
        lotteryTickets = tag.getInt("LotteryTickets");
    }

    // AutoSyncedComponent 默认使用 writeToNbt/readFromNbt 进行同步
}
