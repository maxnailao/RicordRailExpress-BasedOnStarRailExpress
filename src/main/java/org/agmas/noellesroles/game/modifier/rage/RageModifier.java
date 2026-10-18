package org.agmas.noellesroles.game.modifier.rage;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import org.agmas.noellesroles.init.ModEffects;

/**
 * 暴怒修饰符：持续挂上暴怒药水。
 *
 * <p>死亡反应由 {@code DeathReactionHandler} 监听此标记效果并触发。</p>
 */
public final class RageModifier {
    private static final int EFFECT_DURATION = 200;
    private static final int REFRESH_THRESHOLD = 40;

    private RageModifier() {
    }

    public static void serverTick(ServerPlayer player) {
        MobEffectInstance current = player.getEffect(ModEffects.RAGE);
        if (current != null
                && (current.isInfiniteDuration() || current.getDuration() > REFRESH_THRESHOLD)) {
            return;
        }
        player.addEffect(new MobEffectInstance(ModEffects.RAGE, EFFECT_DURATION, 0, false, false, true));
    }
}
