package io.wifi.starrailexpress.content.musicbox;

import dev.doctor4t.ratatouille.util.registrar.SoundEventRegistrar;
import io.wifi.starrailexpress.SRE;
import net.minecraft.sounds.SoundEvent;

/**
 * 音乐盒音效注册。
 * <p>
 * 所有音乐盒音效统一放在 {@code assets/starrailexpress/sounds/musicbox/} 目录下，
 * 并在 {@code sounds.json} 中声明对应条目。
 * </p>
 * <p>回退策略：整个文件删除即可，不影响其他模块。</p>
 */
public interface MusicBoxSounds {
    SoundEventRegistrar registrar = new SoundEventRegistrar(SRE.MOD_ID);

    // ── 内置音乐盒音效 ──
    SoundEvent MUSICBOX_TRIUMPH_01 = registrar.create("musicbox.triumph_01");
    SoundEvent MUSICBOX_TRIUMPH_02 = registrar.create("musicbox.triumph_02");
    SoundEvent MUSICBOX_TRIUMPH_03 = registrar.create("musicbox.triumph_03");
    SoundEvent MUSICBOX_GAOSHOURUYUN = registrar.create("musicbox.gaoshouruyun");
    SoundEvent MUSICBOX_INHUMAN = registrar.create("musicbox.inhuman");

    static void initialize() {
        registrar.registerEntries();
    }
}
