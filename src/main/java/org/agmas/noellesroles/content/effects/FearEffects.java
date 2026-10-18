package org.agmas.noellesroles.content.effects;

import net.minecraft.world.entity.LivingEntity;
import org.agmas.noellesroles.init.ModEffects;

/** 胆小鬼触发后的坐下和发抖状态。 */
public final class FearEffects {
    private FearEffects() {
    }

    public static boolean isActive(LivingEntity entity) {
        return entity != null && entity.hasEffect(ModEffects.FEAR);
    }

    public static boolean isSitting(LivingEntity entity) {
        return isActive(entity);
    }

    public static boolean isTrembling(LivingEntity entity) {
        return isActive(entity);
    }
}
