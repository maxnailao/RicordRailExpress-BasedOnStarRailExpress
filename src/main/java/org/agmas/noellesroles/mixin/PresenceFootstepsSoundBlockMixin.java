package org.agmas.noellesroles.mixin;

import eu.ha3.presencefootsteps.sound.Options;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.init.ModEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 拦截 PresenceFootsteps 模组的自定义脚步声播放。
 * <p>
 * PresenceFootsteps 模组完全替代了原版脚步声系统，通过
 * {@code ImmediateSoundPlayer.playSound(LivingEntity, String, float, float, Options)}
 * 播放自定义脚步声，绕过了原版的 {@code Entity.playSound} 和 {@code Entity.playStepSound}。
 * <p>
 * 本 mixin 使用 {@code @Pseudo} 标记，当 PresenceFootsteps 未安装时不会生效
 * （由 {@code NRMixinPlugin} 条件加载）。
 */
@Pseudo
@Mixin(targets = "eu.ha3.presencefootsteps.sound.player.ImmediateSoundPlayer")
public class PresenceFootstepsSoundBlockMixin {

    @Inject(method = "playSound", at = @At("HEAD"), cancellable = true, remap = false)
    private void noe$blockPFFootsteps(LivingEntity entity, String name,
                                        float volume, float pitch, Options options,
                                        CallbackInfo ci) {
        if (entity instanceof Player player && player.hasEffect(ModEffects.JINGBU)) {
            ci.cancel();
        }
    }
}
