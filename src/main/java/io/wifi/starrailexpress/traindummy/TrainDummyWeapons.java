/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package io.wifi.starrailexpress.traindummy;

import io.wifi.starrailexpress.content.item.api.SREItemProperties;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.util.SkinUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/**
 * 把会打死或打伤玩家的近战武器，转到列车假人的虚拟血量和护盾上。
 * 说明：上游还接入了梦境武器（斧/钻石剑/锤）、武士刀、长矛与自定义武器，
 * 这些依赖职业/自定义内容模块，本仓库尚未移植，故此处仅保留原版可用的近战分支。
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
        if (stack.getItem() instanceof SREItemProperties.LeftClickKillable
                && attacker.getAttackStrengthScale(0.75F) >= 1.0F) {
            TrainDummyCombat.applyLethal(dummy, attacker, SkinUtils.getItemTypeResourceLocation(stack));
            return true;
        }
        return false;
    }
}
