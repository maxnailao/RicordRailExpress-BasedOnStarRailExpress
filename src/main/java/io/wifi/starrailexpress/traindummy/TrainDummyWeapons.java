/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package io.wifi.starrailexpress.traindummy;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.item.api.SREItemProperties;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.util.SkinUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.content.item.DreamAxeItem;
import org.agmas.noellesroles.content.item.DreamDiamondSwordItem;
import org.agmas.noellesroles.content.item.DreamMaceItem;
import org.agmas.noellesroles.role_data.killer.DreamRoleData;

/**
 * 把会打死或打伤玩家的近战武器，转到列车假人的虚拟血量和护盾上。
 *
 * <p>已移植 Dream 三件套（铁斧 / 钻石剑 / 重锤）的假人结算：
 * 伤害数值、耐久消耗、暴击 / 跳劈 / 横扫与音效都与 {@code DreamAxeItem} /
 * {@code DreamDiamondSwordItem} / {@code DreamMaceItem} 对玩家生效时一致。
 *
 * <p>说明：上游还接入了武士刀、长矛与自定义武器（customitem），
 * 这三者在 RKD 中并不存在，故此处不移植。
 */
public final class TrainDummyWeapons {

    private TrainDummyWeapons() {
    }

    /**
     * @return 是否已经处理，调用方应取消原版攻击
     */
    public static boolean handleAttack(ServerPlayer attacker, TrainDummyEntity dummy, ItemStack stack) {
        if (attacker == null || dummy == null || !dummy.canReceiveHits() || stack == null || stack.isEmpty()) {
            return false;
        }
        if (stack.is(TMMItems.BAT) && !attacker.getCooldowns().isOnCooldown(TMMItems.BAT)
                && attacker.getAttackStrengthScale(0.75F) >= 1.0F) {
            TrainDummyCombat.applyLethal(dummy, attacker, GameConstants.DeathReasons.BAT);
            attacker.level().playSound(null, attacker.blockPosition(), TMMSounds.ITEM_BAT_HIT, SoundSource.PLAYERS, 3.0F,
                    1.0F);
            return true;
        }
        // Dream 三件套：伤害走虚拟血量，与对玩家生效时数值一致
        if (stack.getItem() instanceof DreamAxeItem && hitDreamAxe(attacker, dummy, stack)) {
            return true;
        }
        if (stack.getItem() instanceof DreamDiamondSwordItem && hitDreamSword(attacker, dummy, stack)) {
            return true;
        }
        if (stack.getItem() instanceof DreamMaceItem && hitDreamMace(attacker, dummy, stack)) {
            return true;
        }
        if (stack.getItem() instanceof SREItemProperties.LeftClickKillable
                && attacker.getAttackStrengthScale(0.75F) >= 1.0F) {
            TrainDummyCombat.applyLethal(dummy, attacker, SkinUtils.getItemTypeResourceLocation(stack));
            return true;
        }
        return false;
    }

    /**
     * Dream 铁斧：平A / 跳劈（狂暴时数值更高），与 {@code DreamAxeItem#onServerAttack} 一致。
     *
     * @return 是否已经处理，调用方应取消原版攻击
     */
    private static boolean hitDreamAxe(ServerPlayer attacker, TrainDummyEntity dummy, ItemStack stack) {
        if (!canUseSpecial(attacker, stack) || broken(stack)) {
            return false;
        }
        var config = NoellesRolesConfig.instance();
        boolean berserk = DreamRoleData.isBerserk(attacker);
        // 跳劈：与原版暴击一致 —— 下落中且不在地面
        boolean crit = attacker.fallDistance > 0.0F && !attacker.onGround() && !attacker.isPassenger();
        int damage = berserk
                ? (crit ? config.dreamAxeBerserkCritDamage : config.dreamAxeBerserkDamage)
                : (crit ? config.dreamAxeCritDamage : config.dreamAxeDamage);
        if (!TrainDummyCombat.applyVirtualDamage(dummy, attacker, damage, DreamRoleData.DEATH_REASON_DREAM_AXE, false,
                false)) {
            return true;
        }
        attacker.resetAttackStrengthTicker();
        stack.hurtAndBreak(1, attacker, EquipmentSlot.MAINHAND);
        attacker.level().playSound(null, dummy.blockPosition(),
                crit ? SoundEvents.PLAYER_ATTACK_CRIT : SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F,
                crit ? 0.9F : 1.0F);
        return true;
    }

    /**
     * Dream 钻石剑：原版伤害公式 + 暴击 / 横扫，与 {@code DreamDiamondSwordItem#onServerAttack} 一致。
     *
     * @return 是否已经处理，调用方应取消原版攻击
     */
    private static boolean hitDreamSword(ServerPlayer attacker, TrainDummyEntity dummy, ItemStack stack) {
        if (!canUseSpecial(attacker, stack) || broken(stack)) {
            return false;
        }
        float damage = (float) attacker.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float charge = attacker.getAttackStrengthScale(0.5F);
        damage *= 0.2F + charge * charge * 0.8F;
        attacker.resetAttackStrengthTicker();
        if (damage <= 0.0F) {
            return true;
        }
        boolean fullCharge = charge > 0.9F;
        boolean crit = fullCharge && attacker.fallDistance > 0.0F && !attacker.onGround()
                && !attacker.onClimbable() && !attacker.isInWater()
                && !attacker.hasEffect(MobEffects.BLINDNESS) && !attacker.isPassenger()
                && !attacker.isSprinting();
        if (crit) {
            damage *= 1.5F;
        }
        boolean sweep = fullCharge && !crit && !attacker.isSprinting() && attacker.onGround()
                && (attacker.walkDist - attacker.walkDistO) < attacker.getSpeed();
        TrainDummyCombat.applyVirtualDamage(dummy, attacker, Math.max(1, Math.round(damage)),
                DreamDiamondSwordItem.DEATH_REASON, false, false);
        stack.hurtAndBreak(1, attacker, EquipmentSlot.MAINHAND);
        if (sweep) {
            sweep(attacker, dummy.getBoundingBox().inflate(1.0D, 0.25D, 1.0D), 1, DreamDiamondSwordItem.DEATH_REASON);
            attacker.sweepAttack();
        }
        return true;
    }

    /**
     * Dream 重锤：原版伤害公式 + 坠击加伤，并且<b>无视虚拟护盾</b>，
     * 与 {@code DreamMaceItem#onServerAttack} 一致。
     *
     * @return 是否已经处理，调用方应取消原版攻击
     */
    private static boolean hitDreamMace(ServerPlayer attacker, TrainDummyEntity dummy, ItemStack stack) {
        if (!canUseSpecial(attacker, stack) || broken(stack)) {
            return false;
        }
        float damage = (float) attacker.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float charge = attacker.getAttackStrengthScale(0.5F);
        damage *= 0.2F + charge * charge * 0.8F;
        attacker.resetAttackStrengthTicker();
        boolean smash = attacker.fallDistance > 1.5F && !attacker.isFallFlying();
        if (smash) {
            damage += fallBonus(attacker.fallDistance);
        } else if (charge > 0.9F && attacker.fallDistance > 0.0F && !attacker.onGround()
                && !attacker.onClimbable() && !attacker.isInWater()
                && !attacker.hasEffect(MobEffects.BLINDNESS) && !attacker.isPassenger()
                && !attacker.isSprinting()) {
            damage *= 1.5F;
        }
        if (damage <= 0.0F) {
            return true;
        }
        TrainDummyCombat.applyVirtualDamage(dummy, attacker, Math.max(1, Math.round(damage)),
                DreamMaceItem.DEATH_REASON, true, false);
        stack.hurtAndBreak(1, attacker, EquipmentSlot.MAINHAND);
        if (smash) {
            attacker.resetFallDistance();
        }
        return true;
    }

    /**
     * 对假人结算一次范围伤害（钻石剑横扫）。
     */
    public static void sweep(ServerPlayer attacker, AABB box, int damage,
            net.minecraft.resources.ResourceLocation reason) {
        if (!(attacker.level() instanceof ServerLevel level)) {
            return;
        }
        for (TrainDummyEntity dummy : level.getEntitiesOfClass(TrainDummyEntity.class, box,
                TrainDummyEntity::canReceiveHits)) {
            if (attacker.distanceToSqr(dummy) >= 9.0D) {
                continue;
            }
            TrainDummyCombat.applyVirtualDamage(dummy, attacker, damage, reason, false, false);
        }
    }

    /**
     * Dream 武器共用的使用门禁：物品不在冷却、蓄力已满、且持有者职业开启了
     * {@code canUseSpVanillaWeapon}。
     */
    private static boolean canUseSpecial(ServerPlayer attacker, ItemStack stack) {
        if (attacker.getCooldowns().isOnCooldown(stack.getItem())) {
            return false;
        }
        if (attacker.getAttackStrengthScale(0.5F) < 1.0F) {
            return false;
        }
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(attacker.level());
        var role = game == null ? null : game.getRole(attacker);
        return role != null && role.canUseSpVanillaWeapon();
    }

    /** 耐久耗尽保护。 */
    private static boolean broken(ItemStack stack) {
        return stack.getMaxDamage() > 0 && stack.getDamageValue() >= stack.getMaxDamage();
    }

    /** 重锤坠击加伤曲线（与原版重锤一致）。 */
    private static float fallBonus(float fallDistance) {
        if (fallDistance <= 3.0F) {
            return 4.0F * fallDistance;
        }
        if (fallDistance <= 8.0F) {
            return 12.0F + 2.0F * (fallDistance - 3.0F);
        }
        return 22.0F + fallDistance - 8.0F;
    }
}
