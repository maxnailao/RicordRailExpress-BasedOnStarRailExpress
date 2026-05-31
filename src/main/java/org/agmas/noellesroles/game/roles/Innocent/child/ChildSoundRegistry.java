package org.agmas.noellesroles.game.roles.Innocent.child;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.init.NRSounds;

import java.util.ArrayList;
import java.util.List;

public final class ChildSoundRegistry {
    private static final List<ResourceLocation> DEFAULT_SOUND_IDS = List.of(NRSounds.CHILD_LAUGH.getLocation());

    private ChildSoundRegistry() {
    }

    public static List<ResourceLocation> getConfiguredSoundIds() {
        var cfg = NoellesRolesConfig.HANDLER.instance();
        List<ResourceLocation> result = new ArrayList<>();
        if (cfg != null && cfg.childSounds != null) {
            for (String raw : cfg.childSounds) {
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                ResourceLocation id = ResourceLocation.tryParse(raw.trim());
                if (id != null) {
                    result.add(id);
                }
            }
        }
        return result.isEmpty() ? DEFAULT_SOUND_IDS : result;
    }

    public static int getSlotCount() {
        return getConfiguredSoundIds().size();
    }

    public static ResourceLocation getSoundIdForSlot(int slot) {
        List<ResourceLocation> sounds = getConfiguredSoundIds();
        return sounds.get(Math.floorMod(slot, sounds.size()));
    }

    public static SoundEvent resolveSoundEvent(ResourceLocation soundId) {
        if (soundId != null && BuiltInRegistries.SOUND_EVENT.containsKey(soundId)) {
            return BuiltInRegistries.SOUND_EVENT.get(soundId);
        }
        return NRSounds.CHILD_LAUGH;
    }

    public static void play(ServerPlayer player, int slot) {
        SoundEvent sound = resolveSoundEvent(getSoundIdForSlot(slot));
        player.serverLevel().playSound(
                null,
                player.getX(), player.getY(), player.getZ(),
                sound,
                SoundSource.PLAYERS,
                1.0F,
                1.0F
        );
    }
}
