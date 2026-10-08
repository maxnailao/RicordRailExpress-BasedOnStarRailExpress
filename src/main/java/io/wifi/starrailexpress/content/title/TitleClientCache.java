package io.wifi.starrailexpress.content.title;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户端称号定义缓存。
 *
 * <p>称号定义存在服务端存档（{@link TitleSavedData}），客户端访问不到，
 * 所以服务端在玩家登录、以及发放/删除称号时把定义表推过来，这里存一份。
 *
 * <p>和 {@code CS2ClientBoxCache} 同一套思路。
 */
public final class TitleClientCache {

    /** titleId -> 定义 */
    private static final Map<String, Title> CACHE = new LinkedHashMap<>();

    private TitleClientCache() {
    }

    /** 整体替换（服务器全量推送时用） */
    public static synchronized void setAll(List<Title> titles) {
        CACHE.clear();
        for (Title t : titles) {
            CACHE.put(t.id(), t);
        }
    }

    public static synchronized void put(Title title) {
        if (title != null) {
            CACHE.put(title.id(), title);
        }
    }

    public static synchronized void remove(String id) {
        CACHE.remove(id);
    }

    @Nullable
    public static synchronized Title get(String id) {
        return CACHE.get(id);
    }

    public static synchronized List<Title> all() {
        return new ArrayList<>(CACHE.values());
    }

    public static synchronized void clear() {
        CACHE.clear();
    }

    /** 不可变视图，便于外部遍历 */
    public static synchronized Map<String, Title> view() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(CACHE));
    }
}
