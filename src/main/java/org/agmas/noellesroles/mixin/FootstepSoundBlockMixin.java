package org.agmas.noellesroles.mixin;

import io.wifi.starrailexpress.SRE;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.init.ModEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 拦截 {@code Entity.playSound} 以屏蔽拥有 {@link ModEffects#JINGBU} 效果玩家的脚步声。
 *
 * <p>{@code Entity.playStepSound} 在 MC 1.21.1 中可能仅负责粒子生成，
 * 实际声音通过 {@code Entity.playSound(SoundEvent, float, float)} 播放。
 * 本 mixin 直接拦截底层声音播放方法，确保脚步声不会在本地播放。</p>
 *
 * <p>与 {@code FootstepVanishServerMixin}（服务端广播拦截）配合，
 * 保证本地和其他玩家都听不到脚步声音。</p>
 */
@Mixin(Entity.class)
public class FootstepSoundBlockMixin {

    @Inject(method = "playSound", at = @At("HEAD"), cancellable = true)
    private void noe$blockFootstepSound(SoundEvent sound, float volume, float pitch,
                                         CallbackInfo ci) {
        if (SRE.isLobby) return;
        if ((Entity) (Object) this instanceof Player player
                && player.hasEffect(ModEffects.JINGBU)) {
            ci.cancel();
        }
    }
}
