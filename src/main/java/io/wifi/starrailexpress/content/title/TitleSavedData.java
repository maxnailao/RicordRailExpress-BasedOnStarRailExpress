package io.wifi.starrailexpress.content.title;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * 称号定义表（服务器存档）。
 *
 * <p>称号由管理员命令创建，所以要**跨重启持久化**。这里用 SavedData 存，
 * 挂在主世界（overworld）上 —— 和回放屏、小脑榜是同一套做法。
 *
 * <p>玩家"拥有哪些称号""当前装备哪个"记录在
 * {@link TitlePlayerComponent}（随玩家存档走）。
 */
public class TitleSavedData extends SavedData {

    private static final String DATA_NAME = "starrailexpress_titles";

    /** id -> 称号定义 */
    private final Map<String, Title> titles = new LinkedHashMap<>();

    public static TitleSavedData get(MinecraftServer server) {
        if (server == null || server.overworld() == null) {
            return new TitleSavedData();
        }
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(TitleSavedData::new, TitleSavedData::load, null), DATA_NAME);
    }

    // ── 查询 ──

    public Title get(String id) {
        return titles.get(id);
    }

    public boolean contains(String id) {
        return titles.containsKey(id);
    }

    /** 全部称号，按文本排序（仓库显示更稳定） */
    public List<Title> all() {
        List<Title> out = new ArrayList<>(titles.values());
        out.sort(Comparator.comparing(Title::text).thenComparing(Title::id));
        return out;
    }

    public int size() {
        return titles.size();
    }

    // ── 写入 ──

    /**
     * 登记一个称号；已存在（id 相同）则原样返回既有定义。
     *
     * @return 登记后的称号
     */
    public Title putIfAbsent(Title title) {
        Title existing = titles.get(title.id());
        if (existing != null) {
            return existing;
        }
        titles.put(title.id(), title);
        setDirty();
        return title;
    }

    public boolean remove(String id) {
        boolean removed = titles.remove(id) != null;
        if (removed) {
            setDirty();
        }
        return removed;
    }

    // ── 持久化 ──

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Title t : titles.values()) {
            CompoundTag c = new CompoundTag();
            c.putString("Id", t.id());
            c.putString("Text", t.text());
            c.putString("Color", t.colorHex());
            c.putBoolean("Suffix", t.suffix());
            list.add(c);
        }
        tag.put("Titles", list);
        return tag;
    }

    public static TitleSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        TitleSavedData data = new TitleSavedData();
        ListTag list = tag.getList("Titles", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            String id = c.getString("Id");
            String text = c.getString("Text");
            if (id.isEmpty() || text.isEmpty()) {
                continue;
            }
            String color = Title.normalizeColor(c.getString("Color"));
            data.titles.put(id, new Title(id, text,
                    color == null ? Title.DEFAULT_COLOR : color, c.getBoolean("Suffix")));
        }
        return data;
    }
}
