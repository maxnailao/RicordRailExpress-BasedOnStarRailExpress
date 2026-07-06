package org.agmas.noellesroles.content.effects;

import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.flag.FeatureFlagSet;

/**
 * 噩梦效果
 * - 1层 (amplifier=0): 缓慢I + 每秒降低1点理智值，可被心理学家治疗消除
 * - 2层 (amplifier=1): 缓慢I + 每秒降低1点理智值 + 每15秒获得5秒黑暗效果，不可被心理学家治疗消除
 */
public class NightmareEffect extends MobEffect {

    /** 每秒降低的理智值 */
    private static final float MOOD_DRAIN_PER_SECOND = 1.0f;
    /** 2层时每15秒施加黑暗的tick数 (15秒 = 300tick) */
    private static final int DARKNESS_INTERVAL_TICKS = 300;
    /** 黑暗效果持续时间 (5秒 = 100tick) */
    private static final int DARKNESS_DURATION_TICKS = 100;
    /** 缓慢效果刷新持续时间 (2秒 = 40tick) */
    private static final int SLOWNESS_REFRESH_TICKS = 40;

    public NightmareEffect() {
        super(MobEffectCategory.HARMFUL, 0x2D0033); // 暗紫色
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean isEnabled(FeatureFlagSet featureFlagSet) {
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity livingEntity, int amplifier) {
        if (!(livingEntity instanceof ServerPlayer serverPlayer)) {
            return true;
        }

        long gameTime = livingEntity.level().getGameTime();

        // 每秒 (20tick) 降低1点理智值
        if (gameTime % 20 == 0) {
            SREPlayerMoodComponent mood = SREPlayerMoodComponent.KEY.get(serverPlayer);
            // 理智值范围 0.0-1.0，每秒降低 MOOD_DRAIN_PER_SECOND / 100 (假设100点=满)
            // 实际理智系统: 1点理智 = 0.01 mood值
            mood.addMood(-MOOD_DRAIN_PER_SECOND / 100.0f);
        }

        // 每20tick刷新缓慢I效果 (等级0 = 缓慢I)
        if (gameTime % 20 == 0) {
            serverPlayer.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN,
                    SLOWNESS_REFRESH_TICKS,
                    0, // 等级0 = 缓慢I
                    true,  // ambient
                    false, // showParticles
                    false  // showIcon
            ));
        }

        // 2层 (amplifier >= 1): 每15秒施加5秒黑暗效果
        if (amplifier >= 1 && gameTime % DARKNESS_INTERVAL_TICKS == 0) {
            serverPlayer.addEffect(new MobEffectInstance(
                    MobEffects.DARKNESS,
                    DARKNESS_DURATION_TICKS,
                    0,
                    true,
                    false,
                    false
            ));
        }

        return true;
    }

    /**
     * 判断该层级的噩梦效果是否可被心理学家治疗消除
     * @param amplifier 效果等级 (0=1层, 1=2层)
     * @return 1层可被消除，2层不可
     */
    public static boolean isRemovableByPsychologist(int amplifier) {
        return amplifier <= 0; // 仅1层 (amplifier=0) 可被消除
    }
}
