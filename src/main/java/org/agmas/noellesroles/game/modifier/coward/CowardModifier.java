package org.agmas.noellesroles.game.modifier.coward;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import org.agmas.noellesroles.init.ModEffects;

/**
 * 胆小鬼修饰符：持续挂上胆小鬼药水。
 *
 * <p>死亡反应由 {@code DeathReactionHandler} 监听此标记效果并触发。</p>
 */
public final class CowardModifier {
    private static final int EFFECT_DURATION = 200;
    private static final int REFRESH_THRESHOLD = 40;

    private CowardModifier() {
    }

    public static void serverTick(ServerPlayer player) {
        MobEffectInstance current = player.getEffect(ModEffects.COWARD);
        if (current != null
                && (current.isInfiniteDuration() || current.getDuration() > REFRESH_THRESHOLD)) {
            return;
        }
        player.addEffect(new MobEffectInstance(ModEffects.COWARD, EFFECT_DURATION, 0, false, false, true));
    }
}
