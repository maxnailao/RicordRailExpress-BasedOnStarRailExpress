package io.wifi.starrailexpress.api.replay.board;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 「小脑 / 被小脑」统计的**持久化**。
 *
 * <p>{@link XiaoNaoBoardStats} 负责内存里的累计与排名，这里是它的存档镜像：
 * 每次记录/重置都会同步过来，所以
 * <ul>
 * <li>服务器重启后榜单画面还能立刻用数据（否则重启后屏幕是空的）；</li>
 * <li>管理员可以事后翻账本。</li>
 * </ul>
 *
 * <p>存档名与回放屏幕 / 航点同风格：{@code starrailexpress_xiaonao_stats}。
 * <p><b>注意</b>：每局开始时会 reset（与「每局统计」的语义一致），
 * 所以这个文件保存的是「当前这一局」的累计值。
 */
public final class XiaoNaoStatsSavedData extends SavedData {

    private static final String DATA_NAME = "starrailexpress_xiaonao_stats";

    /** 误杀者 -> 次数 */
    private Map<UUID, Integer> xiaoNao = new LinkedHashMap<>();
    /** 被误杀者 -> 次数 */
    private Map<UUID, Integer> beiXiaoNao = new LinkedHashMap<>();
    /** UUID -> 名字 */
    private Map<UUID, String> names = new LinkedHashMap<>();

    public static XiaoNaoStatsSavedData get(MinecraftServer server) {
        if (server == null) {
            return new XiaoNaoStatsSavedData();
        }
        // 与 ReplayBoardSavedData 一致：统一存在主世界的数据存储里
        net.minecraft.server.level.ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return new XiaoNaoStatsSavedData();
        }
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(XiaoNaoStatsSavedData::new, XiaoNaoStatsSavedData::load, null),
                DATA_NAME);
    }

    public static XiaoNaoStatsSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        XiaoNaoStatsSavedData data = new XiaoNaoStatsSavedData();
        data.xiaoNao = readCounts(tag.getList("XiaoNao", Tag.TAG_COMPOUND));
        data.beiXiaoNao = readCounts(tag.getList("BeiXiaoNao", Tag.TAG_COMPOUND));
        data.names = readNames(tag.getList("Names", Tag.TAG_COMPOUND));
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.put("XiaoNao", writeCounts(xiaoNao));
        tag.put("BeiXiaoNao", writeCounts(beiXiaoNao));
        tag.put("Names", writeNames(names));
        return tag;
    }

    // ── 读写辅助 ──

    private static Map<UUID, Integer> readCounts(ListTag list) {
        Map<UUID, Integer> out = new LinkedHashMap<>();
        for (Tag t : list) {
            if (t instanceof CompoundTag c && c.hasUUID("Id")) {
                out.put(c.getUUID("Id"), c.getInt("Count"));
            }
        }
        return out;
    }

    private static ListTag writeCounts(Map<UUID, Integer> counts) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Integer> e : counts.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("Id", e.getKey());
            c.putInt("Count", e.getValue() == null ? 0 : e.getValue());
            list.add(c);
        }
        return list;
    }

    private static Map<UUID, String> readNames(ListTag list) {
        Map<UUID, String> out = new LinkedHashMap<>();
        for (Tag t : list) {
            if (t instanceof CompoundTag c && c.hasUUID("Id")) {
                out.put(c.getUUID("Id"), c.getString("Name"));
            }
        }
        return out;
    }

    private static ListTag writeNames(Map<UUID, String> names) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, String> e : names.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("Id", e.getKey());
            c.putString("Name", e.getValue() == null ? "" : e.getValue());
            list.add(c);
        }
        return list;
    }

    // ── 与内存镜像同步 ──

    /** 把内存里的最新数据写进存档镜像（由 {@link XiaoNaoBoardStats} 调用） */
    void mirror(Map<UUID, Integer> xiaoNao, Map<UUID, Integer> beiXiaoNao, Map<UUID, String> names) {
        this.xiaoNao = new LinkedHashMap<>(xiaoNao);
        this.beiXiaoNao = new LinkedHashMap<>(beiXiaoNao);
        this.names = new LinkedHashMap<>(names);
        setDirty(true);
    }

    /** 服务器启动时把上次的数据读回内存（重启后榜单不空） */
    public void restoreInto(Map<UUID, Integer> xiaoNaoTarget, Map<UUID, Integer> beiXiaoNaoTarget,
            Map<UUID, String> namesTarget) {
        xiaoNaoTarget.clear();
        xiaoNaoTarget.putAll(xiaoNao);
        beiXiaoNaoTarget.clear();
        beiXiaoNaoTarget.putAll(beiXiaoNao);
        namesTarget.clear();
        namesTarget.putAll(names);
    }

    public boolean isEmpty() {
        return xiaoNao.isEmpty() && beiXiaoNao.isEmpty();
    }
}
