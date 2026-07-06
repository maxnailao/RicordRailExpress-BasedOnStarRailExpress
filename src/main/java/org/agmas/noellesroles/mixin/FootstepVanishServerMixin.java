package org.agmas.noellesroles.mixin;

import io.wifi.starrailexpress.SRE;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.init.ModEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 服务端级别的声音拦截：当玩家拥有 {@link ModEffects#FOOTSTEP_VANISH}、
 * {@link ModEffects#JINGMOQIANXING} 或 {@link ModEffects#JINGBU} 效果时，
 * 阻止该玩家产生的所有声音被广播给其他客户端。
 * <p>
 * 参考 {@code GhostSoundMixin}（拦截小透明的所有声音广播）。
 * 与 {@code FootstepVanishMixin}（客户端 Entity.playStepSound 拦截）配合，
 * 确保脚步声在服务端和客户端两侧都被正确屏蔽。
 */
@Mixin(ServerLevel.class)
public class FootstepVanishServerMixin {

    @Inject(method = "playSeededSound*", at = @At("HEAD"), cancellable = true)
    private void noe$blockSoundForFootstepVanish(Player source, double x, double y, double z,
                                                  Holder<SoundEvent> sound, SoundSource category,
                                                  float volume, float pitch, long seed,
                                                  CallbackInfo ci) {
        if (SRE.isLobby) return;
        if (source != null
                && (source.hasEffect(ModEffects.FOOTSTEP_VANISH)
                    || source.hasEffect(ModEffects.JINGMOQIANXING)
                    || source.hasEffect(ModEffects.JINGBU))) {
            ci.cancel();
        }
    }
}
