package io.wifi.starrailexpress.mixin.entity;

import io.wifi.starrailexpress.SRE;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.windcharge.WindCharge;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level.ExplosionInteraction;
import net.minecraft.world.level.SimpleExplosionDamageCalculator;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.function.Function;

@Mixin(WindCharge.class)
public class WindChargeMixin {
    /**
     * 风弹的爆炸伤害计算器。
     *
     * <p>在原版的基础上**额外让掉落物免疫**：
     * 风弹爆炸半径 1.2，会把范围内的 {@link ItemEntity}（掉落在地上的物品，包括手枪）
     * 直接炸掉 —— 风精灵就能用风弹清场上的枪。这里把这类实体排除在伤害之外，
     * 但**保留对玩家 / 生物 / 方块交互的原有行为**（击退、开门等都不受影响）。
     */
    @Unique
    private static final ExplosionDamageCalculator EXPLOSION_DAMAGE_CALCULATOR = new SimpleExplosionDamageCalculator(
            false, true, Optional.of(1.22F),
            BuiltInRegistries.BLOCK.getTag(BlockTags.BLOCKS_WIND_CHARGE_EXPLOSIONS).map(Function.identity())) {
        @Override
        public boolean shouldDamageEntity(net.minecraft.world.level.Explosion explosion, Entity entity) {
            // 掉落物：不受风弹爆炸伤害（否则会被直接销毁）
            if (entity instanceof net.minecraft.world.entity.item.ItemEntity) {
                return false;
            }
            // 经验球同理：炸掉没有意义，且会让玩家莫名少经验
            if (entity instanceof net.minecraft.world.entity.ExperienceOrb) {
                return false;
            }
            return super.shouldDamageEntity(explosion, entity);
        }
    };

    @Inject(method = "explode", at = @At("HEAD"), cancellable = true)
    protected void explode(Vec3 vec3, CallbackInfo ci) {
        if (SRE.isLobby)
            return;
        WindCharge windCharge = (WindCharge) (Object) this;
        Entity owner = windCharge.getOwner();
        DamageSource damageSource = null;
        if (owner instanceof LivingEntity le) {
            damageSource = windCharge.damageSources().windCharge(windCharge, le);
        }
        windCharge.level().explode(windCharge, damageSource, EXPLOSION_DAMAGE_CALCULATOR, vec3.x(), vec3.y(), vec3.z(),
                1.2F, false, ExplosionInteraction.TRIGGER, ParticleTypes.GUST_EMITTER_SMALL,
                ParticleTypes.GUST_EMITTER_LARGE, SoundEvents.WIND_CHARGE_BURST);
        ci.cancel();
    }
}