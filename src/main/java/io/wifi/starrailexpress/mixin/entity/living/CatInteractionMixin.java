package io.wifi.starrailexpress.mixin.entity.living;

import io.wifi.starrailexpress.index.TMMSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

@Mixin(Cat.class)
public abstract class CatInteractionMixin extends Animal {

    @Unique
    private static final WeakHashMap<Cat, Cat> PARTNERS = new WeakHashMap<>();
    @Unique
    private static final HashMap<Cat, Integer> INTERACTION_TICKS = new HashMap<>();
    @Unique
    private static final HashMap<Cat, Integer> COOLDOWNS = new HashMap<>();

    @Unique
    private static final int COOLDOWN = 20 * 30;
    @Unique
    private static final int DURATION = 20 * 20;
    @Unique
    private static final double RANGE = 1.0;
    @Unique
    private static final int SOUND_INTERVAL = 20 * 20;

    protected CatInteractionMixin(EntityType<? extends Animal> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void sre$onTick(CallbackInfo ci) {
        if (level().isClientSide) return;

        Cat self = (Cat) (Object) this;

        int cooldown = COOLDOWNS.getOrDefault(self, 0);
        if (cooldown > 0) COOLDOWNS.put(self, cooldown - 1);

        int ticks = INTERACTION_TICKS.getOrDefault(self, 0);
        Cat partner = PARTNERS.get(self);

        if (ticks > 0) {
            INTERACTION_TICKS.put(self, ticks - 1);

            if (partner == null || !partner.isAlive()
                    || self.distanceTo(partner) > RANGE * 2
                    || PARTNERS.get(partner) != self) {
                sre$cleanup(self);
                return;
            }

            sre$freezeCat(self);
            sre$freezeCat(partner);
            sre$doLookAndTilt(self, partner);

            int newTicks = INTERACTION_TICKS.getOrDefault(self, 0);
            if (newTicks > 0 && newTicks % SOUND_INTERVAL == 0) {
                sre$playRandomSound();
            }
            if (newTicks == 0) {
                sre$cleanup(self);
            }
            return;
        }

        if (cooldown > 0) return;
        if (partner != null) return;
        if (random.nextInt(20) != 0) return;

        for (net.minecraft.world.entity.Entity entity : level().getEntities(self, self.getBoundingBox().inflate(RANGE))) {
            if (entity instanceof Cat otherCat && otherCat != self && otherCat.isAlive()) {
                if (PARTNERS.get(otherCat) == null
                        && INTERACTION_TICKS.getOrDefault(otherCat, 0) <= 0) {
                    INTERACTION_TICKS.put(self, DURATION);
                    PARTNERS.put(self, otherCat);
                    INTERACTION_TICKS.put(otherCat, DURATION);
                    PARTNERS.put(otherCat, self);
                    sre$freezeCat(self);
                    sre$freezeCat(otherCat);
                    sre$playRandomSound();
                    break;
                }
            }
        }
    }

    @Unique
    private void sre$freezeCat(Cat cat) {
        cat.getNavigation().stop();
        cat.setDeltaMovement(0, cat.getDeltaMovement().y, 0);
        cat.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 255, false, false, false));
    }

    @Unique
    private void sre$cleanup(Cat self) {
        Cat partner = PARTNERS.get(self);
        if (partner != null) {
            INTERACTION_TICKS.put(partner, 0);
            PARTNERS.put(partner, null);
        }
        INTERACTION_TICKS.put(self, 0);
        PARTNERS.put(self, null);
        COOLDOWNS.put(self, COOLDOWN);
    }

    @Unique
    private void sre$doLookAndTilt(Cat self, Cat other) {
        double dx = other.getX() - self.getX();
        double dz = other.getZ() - self.getZ();
        float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);

        self.setYRot(targetYaw);
        self.yRotO = targetYaw;
        self.yBodyRot = targetYaw;
        self.yHeadRot = targetYaw;
        self.yHeadRotO = targetYaw;

        int phase = (INTERACTION_TICKS.getOrDefault(self, 0) / 12) % 4;
        float headTilt;
        switch (phase) {
            case 0: headTilt = 25.0F; break;
            case 1: headTilt = 0.0F; break;
            case 2: headTilt = -25.0F; break;
            default: headTilt = 0.0F; break;
        }
        self.yHeadRot = targetYaw + headTilt;
        self.setXRot(-20.0F);
        self.xRotO = -20.0F;
    }

    @Unique
    private void sre$playRandomSound() {
        var sounds = List.of(
                TMMSounds.LAOWU1,
                TMMSounds.LAOWU2,
                TMMSounds.LAOWU3,
                TMMSounds.LAOWU4
        );
        SoundEvent sound = sounds.get(random.nextInt(sounds.size()));
        playSound(sound,
                0.8F + random.nextFloat() * 0.4F,
                0.9F + random.nextFloat() * 0.2F);
    }
}
