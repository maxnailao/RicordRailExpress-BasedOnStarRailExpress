package io.wifi.starrailexpress.content.musicbox;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/**
 * 音乐盒数据记录。
 *
 * @param id          音乐盒唯一标识符（如 "triumph_01"）
 * @param soundEvent  对应的 SoundEvent（用于播放）
 * @param displayName 显示名称组件（可翻译）
 * @param volume      播放音量倍率（默认 1.0f）
 */
public record MusicBox(String id, SoundEvent soundEvent, Component displayName, float volume) {

    /**
     * 使用默认音量 1.0 的构造器。
     */
    public MusicBox(String id, SoundEvent soundEvent, Component displayName) {
        this(id, soundEvent, displayName, 1.0f);
    }

    /**
     * 获取翻译键。
     */
    public String translationKey() {
        return "musicbox.starrailexpress." + id;
    }
}
