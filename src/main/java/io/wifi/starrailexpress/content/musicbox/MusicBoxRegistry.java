package io.wifi.starrailexpress.content.musicbox;

import net.minecraft.network.chat.Component;

import java.util.*;

/**
 * 音乐盒注册表——管理所有可用的音乐盒定义。
 * <p>回退策略：整个文件删除即可。</p>
 */
public final class MusicBoxRegistry {

    private static final Map<String, MusicBox> REGISTRY = new LinkedHashMap<>();

    private MusicBoxRegistry() {}

    /**
     * 注册一个音乐盒。
     */
    public static void register(MusicBox box) {
        REGISTRY.put(box.id(), box);
    }

    /**
     * 按 ID 获取音乐盒，不存在返回 null。
     */
    public static MusicBox get(String id) {
        return REGISTRY.get(id);
    }

    /**
     * 获取所有已注册的音乐盒（不可变视图）。
     */
    public static Collection<MusicBox> getAll() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }

    /**
     * 是否包含指定 ID。
     */
    public static boolean contains(String id) {
        return REGISTRY.containsKey(id);
    }

    /**
     * 注册所有内置音乐盒。在 SRE.onInitialize() 中调用。
     */
    public static void registerBuiltins() {
        register(new MusicBox("triumph_01", MusicBoxSounds.MUSICBOX_TRIUMPH_01,
                Component.translatable("musicbox.starrailexpress.triumph_01"), 3.0f));
        register(new MusicBox("triumph_02", MusicBoxSounds.MUSICBOX_TRIUMPH_02,
                Component.translatable("musicbox.starrailexpress.triumph_02"), 3.0f));
        register(new MusicBox("triumph_03", MusicBoxSounds.MUSICBOX_TRIUMPH_03,
                Component.translatable("musicbox.starrailexpress.triumph_03"), 3.0f));
        register(new MusicBox("gaoshouruyun", MusicBoxSounds.MUSICBOX_GAOSHOURUYUN,
                Component.translatable("musicbox.starrailexpress.gaoshouruyun"), 3.0f));
        register(new MusicBox("inhuman", MusicBoxSounds.MUSICBOX_INHUMAN,
                Component.translatable("musicbox.starrailexpress.inhuman"), 3.9f));
    }
}
