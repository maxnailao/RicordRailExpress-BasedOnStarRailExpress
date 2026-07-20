package io.wifi.starrailexpress.mixin.entity.living;

import io.wifi.starrailexpress.index.TMMSounds;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
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
import java.util.Optional;
import java.util.UUID;

@Mixin(Cat.class)
public abstract class CatInteractionMixin extends Animal {

    @Unique
    private static final EntityDataAccessor<Byte> sre$DATA_TILT_STATE =
            SynchedEntityData.defineId(Cat.class, EntityDataSerializers.BYTE);
    @Unique
    private static final EntityDataAccessor<Integer> sre$DATA_INTERACT_PARTNER_ID =
            SynchedEntityData.defineId(Cat.class, EntityDataSerializers.INT);

    @Unique
    private static final HashMap<Cat, Cat> PARTNERS = new HashMap<>();
    @Unique
    private static final HashMap<Cat, Integer> INTERACTION_TICKS = new HashMap<>();
    @Unique
    private static final HashMap<Cat, Integer> COOLDOWNS = new HashMap<>();
    @Unique
    private static final HashMap<Cat, Cat> APPROACH_TARGETS = new HashMap<>();
    @Unique
    private static final HashMap<Cat, Integer> APPROACH_COOLDOWNS = new HashMap<>();

    @Unique
    private static final int COOLDOWN = 20 * 30;
    @Unique
    private static final int DURATION = 20 * 10;
    @Unique
    private static final double RANGE = 1.5;
    @Unique
    private static final int SOUND_INTERVAL = 20 * 10;
    @Unique
    private static final double DETECT_RANGE = 10.0;
    @Unique
    private static final int APPROACH_COOLDOWN = 20 * 15;

    protected CatInteractionMixin(EntityType<? extends Animal> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void sre$defineSynchedData(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(sre$DATA_TILT_STATE, (byte) 0);
        builder.define(sre$DATA_INTERACT_PARTNER_ID, -1);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void sre$onTick(CallbackInfo ci) {
        Cat self = (Cat) (Object) this;

        if (!level().isClientSide) {
            int cooldown = COOLDOWNS.getOrDefault(self, 0);
            if (cooldown > 0) COOLDOWNS.put(self, cooldown - 1);

            int approachCd = APPROACH_COOLDOWNS.getOrDefault(self, 0);
            if (approachCd > 0) APPROACH_COOLDOWNS.put(self, approachCd - 1);

            int ticks = INTERACTION_TICKS.getOrDefault(self, 0);
            Cat partner = PARTNERS.get(self);

            // === 互动中 ===
            if (ticks > 0) {
                INTERACTION_TICKS.put(self, ticks - 1);

                if (partner == null || !partner.isAlive()
                        || self.distanceTo(partner) > RANGE * 2
                        || PARTNERS.get(partner) != self) {
                    sre$cleanup(self);
                    return;
                }

                sre$doLookAndTilt(self, partner);
                sre$doLookAndTilt(partner, self);

                sre$freezeCat(self);
                sre$freezeCat(partner);

                int newTicks = INTERACTION_TICKS.getOrDefault(self, 0);
                if (newTicks > 0 && newTicks % SOUND_INTERVAL == 0) {
                    sre$playRandomSound();
                }
                if (newTicks == 0) {
                    sre$cleanup(self);
                }
                return;
            }

            // === 自动靠近中 ===
            Cat approachTarget = APPROACH_TARGETS.get(self);
            if (approachTarget != null) {
                if (!approachTarget.isAlive()
                        || PARTNERS.get(approachTarget) != null
                        || COOLDOWNS.getOrDefault(approachTarget, 0) > 0) {
                    APPROACH_TARGETS.remove(self);
                    return;
                }

                if (self.distanceTo(approachTarget) <= RANGE) {
                    APPROACH_TARGETS.remove(self);
                    INTERACTION_TICKS.put(self, DURATION);
                    PARTNERS.put(self, approachTarget);
                    INTERACTION_TICKS.put(approachTarget, DURATION);
                    PARTNERS.put(approachTarget, self);

                    float tiltA = random.nextBoolean() ? 25.0F : -25.0F;
                    float tiltB = random.nextBoolean() ? 25.0F : -25.0F;
                    sre$setTiltState(self, tiltA);
                    sre$setTiltState(approachTarget, tiltB);
                    self.getEntityData().set(sre$DATA_INTERACT_PARTNER_ID, approachTarget.getId());
                    approachTarget.getEntityData().set(sre$DATA_INTERACT_PARTNER_ID, self.getId());

                    sre$doLookAndTilt(self, approachTarget);
                    sre$doLookAndTilt(approachTarget, self);
                    sre$freezeCat(self);
                    sre$freezeCat(approachTarget);
                    sre$playRandomSound();
                    return;
                }

                self.getNavigation().moveTo(approachTarget, 1.0);
                return;
            }

            // === 冷却中 ===
            if (cooldown > 0) return;
            if (partner != null) return;
            if (approachCd > 0) return;
            if (random.nextInt(20) != 0) return;

            // === 检测附近猫 ===
            for (net.minecraft.world.entity.Entity entity : level().getEntities(self, self.getBoundingBox().inflate(DETECT_RANGE))) {
                if (entity instanceof Cat otherCat && otherCat != self && otherCat.isAlive()) {
                    if (PARTNERS.get(otherCat) == null
                            && INTERACTION_TICKS.getOrDefault(otherCat, 0) <= 0
                            && COOLDOWNS.getOrDefault(otherCat, 0) <= 0
                            && !APPROACH_TARGETS.containsKey(otherCat)) {
                        APPROACH_TARGETS.put(self, otherCat);
                        break;
                    }
                }
            }

            return;
        }

        // === 客户端：通过追踪数据读取互动状态，执行对视和歪头 ===
        byte tiltState = self.getEntityData().get(sre$DATA_TILT_STATE);
        if (tiltState != 0) {
            int partnerId = self.getEntityData().get(sre$DATA_INTERACT_PARTNER_ID);
            if (partnerId != -1) {
                net.minecraft.world.entity.Entity partnerEntity = level().getEntity(partnerId);
                if (partnerEntity instanceof Cat partnerCat && partnerCat.isAlive()) {
                    sre$doLookAndTilt(self, partnerCat);
                }
            }
        }
    }

    @Unique
    private void sre$setTiltState(Cat cat, float tilt) {
        cat.getEntityData().set(sre$DATA_TILT_STATE, (byte) (tilt > 0 ? 1 : -1));
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
            PARTNERS.remove(partner);
            COOLDOWNS.put(partner, COOLDOWN);
            APPROACH_COOLDOWNS.put(partner, APPROACH_COOLDOWN);
            partner.getEntityData().set(sre$DATA_TILT_STATE, (byte) 0);
            partner.getEntityData().set(sre$DATA_INTERACT_PARTNER_ID, -1);
        }
        INTERACTION_TICKS.put(self, 0);
        PARTNERS.remove(self);
        COOLDOWNS.put(self, COOLDOWN);
        APPROACH_TARGETS.remove(self);
        APPROACH_COOLDOWNS.put(self, APPROACH_COOLDOWN);
        self.getEntityData().set(sre$DATA_TILT_STATE, (byte) 0);
        self.getEntityData().set(sre$DATA_INTERACT_PARTNER_ID, -1);
    }

    @Unique
    private void sre$doLookAndTilt(Cat self, Cat other) {
        double dx = other.getX() - self.getX();
        double dz = other.getZ() - self.getZ();
        float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);

        self.setYRot(targetYaw);
        self.yRotO = targetYaw;
        self.yBodyRot = targetYaw;

        byte tiltState = self.getEntityData().get(sre$DATA_TILT_STATE);
        float tilt = tiltState > 0 ? 25.0F : (tiltState < 0 ? -25.0F : 0.0F);
        self.yHeadRot = targetYaw + tilt;
        self.yHeadRotO = targetYaw + tilt;
        self.setXRot(-20.0F);
        self.xRotO = -20.0F;
    }

    @Unique
    private void sre$playRandomSound() {
        var sounds = List.of(
                TMMSounds.LAOWU1,
                TMMSounds.LAOWU2,
                TMMSounds.LAOWU3,
                TMMSounds.LAOWU4,
                TMMSounds.LAOWU5,
                TMMSounds.LAOWU6
        );
        SoundEvent sound = sounds.get(random.nextInt(sounds.size()));
        float volume = 0.8F + random.nextFloat() * 0.4F;
        float pitch = 0.9F + random.nextFloat() * 0.2F;
        level().playSound(null, blockPosition(), sound, SoundSource.NEUTRAL, volume, pitch);
    }
}
