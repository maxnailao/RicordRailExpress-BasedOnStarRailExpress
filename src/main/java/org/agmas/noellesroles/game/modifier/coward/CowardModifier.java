package org.agmas.noellesroles.game.modifier.coward;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import org.agmas.noellesroles.init.ModEffects;

/**
 * 胆小鬼修饰符：持续挂上胆小鬼药水。
 *
 * <p>移植自上游的同名修饰符。上游「附近有人死亡时坐下发抖」的濒死反应逻辑
 * （DeathReactionHandler / FearEffects）在 RKD 尚未存在，因此目前该修饰符
 * 只作为可被心理学家治愈的负面修饰符存在，本身不改变玩法。</p>
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
