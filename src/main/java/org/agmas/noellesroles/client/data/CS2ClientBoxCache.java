package org.agmas.noellesroles.client.data;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 客户端箱子配置名称缓存
 * <p>
 * 服务端在玩家登录时同步所有箱子的 boxName 和 keyName，
 * 供客户端 UI（仓库、商店）展示使用，避免客户端直接访问 CS2BoxManager。
 * </p>
 */
public class CS2ClientBoxCache {

    /** boxId → boxName */
    private static final Map<String, String> boxNames = new HashMap<>();

    /** boxId → keyName */
    private static final Map<String, String> keyNames = new HashMap<>();

    /**
     * 设置缓存（由网络包接收器调用）
     */
    public static void set(Map<String, String> names, Map<String, String> keys) {
        boxNames.clear();
        boxNames.putAll(names);
        keyNames.clear();
        keyNames.putAll(keys);
    }

    public static String getBoxName(String boxId) {
        return boxNames.getOrDefault(boxId, "");
    }

    public static String getKeyName(String boxId) {
        return keyNames.getOrDefault(boxId, "");
    }

    public static boolean has(String boxId) {
        return boxNames.containsKey(boxId);
    }

    public static Map<String, String> getAllBoxNames() {
        return Collections.unmodifiableMap(boxNames);
    }
}
