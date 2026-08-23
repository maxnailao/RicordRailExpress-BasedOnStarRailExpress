package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREWorldBlackoutComponent;
import io.wifi.starrailexpress.compat.CrosshairaddonsCompat;
import io.wifi.starrailexpress.content.item.KnifeItem;
import io.wifi.starrailexpress.network.original.KnifeStabPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.agmas.noellesroles.game.roles.killer.werewolfkiller.WerewolfKillerPlayerComponent;
import org.jetbrains.annotations.NotNull;

/**
 * 狼刀 - 狼人（杀手阵营）专属刀类武器
 *
 * <p>正常情况下与普通刀完全一致（举刀速度与击杀CD相同）。
 * 黑灯状态下举刀速度加快65%，击杀后CD缩短至18秒。
 * 在狼人的午夜狼嚎状态下，举刀与落刀均无声音，击杀后CD缩短至6秒。</p>
 */
public class WolfKnifeItem extends KnifeItem {

    /** 正常举刀所需最小蓄力刻数（与普通刀一致） */
    public static final int NORMAL_MIN_CHARGE_TICKS = 8;
    /** 黑灯状态举刀所需最小蓄力刻数（速度加快65%：8 / 1.65 ≈ 5） */
    public static final int BLACKOUT_MIN_CHARGE_TICKS = 5;
    /** 午夜狼嚎状态举刀所需最小蓄力刻数（出刀极快） */
    public static final int HOWL_MIN_CHARGE_TICKS = 2;

    public WolfKnifeItem(Properties settings) {
        super(settings);
    }

    /**
     * 获取当前状态下的最小举刀蓄力刻数
     */
    public static int getMinChargeTicks(Player player) {
        if (WerewolfKillerPlayerComponent.isHowling(player))
            return HOWL_MIN_CHARGE_TICKS;
        SREWorldBlackoutComponent blackout = SREWorldBlackoutComponent.KEY.get(player.level());
        if (blackout != null && blackout.isBlackoutActive())
            return BLACKOUT_MIN_CHARGE_TICKS;
        return NORMAL_MIN_CHARGE_TICKS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, @NotNull Player user, InteractionHand hand) {
        ItemStack itemStack = user.getItemInHand(hand);
        // 午夜狼嚎：举刀没有声音，跳过普通切刀音效直接进入蓄力
        if (WerewolfKillerPlayerComponent.isHowling(user)) {
            if (user.isSpectator()) {
                return InteractionResultHolder.fail(itemStack);
            }
            user.startUsingItem(hand);
            return InteractionResultHolder.consume(itemStack);
        }
        return super.use(world, user, hand);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level world, LivingEntity user, int remainingUseTicks) {
        if (user.isSpectator()) {
            return;
        }
        if (!(user instanceof Player attacker) || !world.isClientSide)
            return;
        // 举刀速度随状态变化：正常8刻 / 黑灯5刻 / 午夜狼嚎2刻
        int minChargeTicks = getMinChargeTicks(attacker);
        if (remainingUseTicks >= this.getUseDuration(stack, user) - minChargeTicks)
            return;
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(world);
        final var role = game.getRole(attacker);
        if (role != null) {
            if (!role.onUseKnife(attacker)) {
                return;
            }
        }
        HitResult collision = getKnifeTarget(attacker);
        if (collision instanceof EntityHitResult entityHitResult) {
            var target = entityHitResult.getEntity();
            if (SRE.REPLAY_MANAGER != null) {
                SRE.REPLAY_MANAGER.recordItemUse(user.getUUID(), BuiltInRegistries.ITEM.getKey(this));
            }
            ClientPlayNetworking.send(new KnifeStabPayload(target.getId()));
            CrosshairaddonsCompat.onAttack(target);
        }
    }

    @Override
    public String getItemSkinType() {
        return "knife";
    }
}
