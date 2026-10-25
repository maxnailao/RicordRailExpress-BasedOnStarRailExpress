package io.wifi.starrailexpress.client.data;

import io.wifi.starrailexpress.network.AdminInspectS2CPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理员“资产管理”界面的目标玩家数据缓存（服务端下发的快照）。
 * 与 {@code TitleClientCache} 同思路：客户端读不到的他人数据，靠 S2C 包补一份。
 */
public final class AdminInspectCache {

    public static final class Snapshot {
        public String target = "";
        public boolean found;
        public int balance;
        public int selfSelect;
        public final List<String> cardKeys = new ArrayList<>();
        public final List<Integer> cardCounts = new ArrayList<>();
        public final List<String> whTypes = new ArrayList<>();
        public final List<String> whIds = new ArrayList<>();
        public final List<Integer> whCounts = new ArrayList<>();
        public final List<String> titleIds = new ArrayList<>();
    }

    private static final Snapshot DATA = new Snapshot();
    /** 每收到一次新快照自增，界面据此判断是否需要重建行 */
    private static long version = 0;

    private AdminInspectCache() {
    }

    public static synchronized void apply(AdminInspectS2CPayload p) {
        DATA.target = p.target();
        DATA.found = p.found();
        DATA.balance = p.balance();
        DATA.selfSelect = p.selfSelect();
        DATA.cardKeys.clear();
        DATA.cardKeys.addAll(p.cardKeys());
        DATA.cardCounts.clear();
        DATA.cardCounts.addAll(p.cardCounts());
        DATA.whTypes.clear();
        DATA.whTypes.addAll(p.whTypes());
        DATA.whIds.clear();
        DATA.whIds.addAll(p.whIds());
        DATA.whCounts.clear();
        DATA.whCounts.addAll(p.whCounts());
        DATA.titleIds.clear();
        DATA.titleIds.addAll(p.titleIds());
        version++;
    }

    public static synchronized long version() {
        return version;
    }

    public static synchronized Snapshot snapshot() {
        return DATA;
    }

    public static synchronized void clear() {
        DATA.target = "";
        DATA.found = false;
        DATA.balance = 0;
        DATA.selfSelect = 0;
        DATA.cardKeys.clear();
        DATA.cardCounts.clear();
        DATA.whTypes.clear();
        DATA.whIds.clear();
        DATA.whCounts.clear();
        DATA.titleIds.clear();
        version++;
    }
}