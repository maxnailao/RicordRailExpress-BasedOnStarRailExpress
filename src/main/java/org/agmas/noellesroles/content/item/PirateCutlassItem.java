package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.compat.CrosshairaddonsCompat;
import io.wifi.starrailexpress.content.item.KnifeItem;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.network.original.KnifeStabPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 海盗弯刀
 * - 蓄力3tick后可刺杀
 * - 射程3格
 * - 使用后消失（一次性）
 * - 冷却15秒
 */
public class PirateCutlassItem extends KnifeItem {

    /** 蓄力阈值（tick） */
    private static final int CHARGE_THRESHOLD = 3;
    /** 射程（格） */
    private static final float RANGE = 3f;

    public PirateCutlassItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, @NotNull Player user, InteractionHand hand) {
        ItemStack itemStack = user.getItemInHand(hand);
        user.startUsingItem(hand);
        user.playSound(TMMSounds.ITEM_KNIFE_PREPARE, 1.0f, 1.0f);
        return InteractionResultHolder.consume(itemStack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level world, LivingEntity user, int remainingUseTicks) {
        if (user.isSpectator()) {
            return;
        }
        // 蓄力时间不足3tick则无效
        if (remainingUseTicks >= this.getUseDuration(stack, user) - CHARGE_THRESHOLD
                || !(user instanceof Player attacker)
                || !world.isClientSide) {
            return;
        }

        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(world);
        final var role = game.getRole(attacker);
        if (role != null) {
            if (!role.onUseKnife(attacker)) {
                return;
            }
        }

        HitResult collision = getCutlassTarget(attacker);
        if (collision instanceof EntityHitResult entityHitResult) {
            Entity target = entityHitResult.getEntity();
            if (SRE.REPLAY_MANAGER != null) {
                SRE.REPLAY_MANAGER.recordItemUse(user.getUUID(), BuiltInRegistries.ITEM.getKey(this));
            }
            ClientPlayNetworking.send(new KnifeStabPayload(target.getId()));
            CrosshairaddonsCompat.onAttack(target);
        }
        // 不在客户端消耗，由服务端击杀成功后同步消耗
    }

    /**
     * 海盗弯刀专用目标检测，射程3格
     */
    public static HitResult getCutlassTarget(Player user) {
        return ProjectileUtil.getHitResultOnViewVector(user,
                entity -> {
                    if (entity instanceof Player player) {
                        return GameUtils.isPlayerAliveAndSurvival(player);
                    }
                    if (entity instanceof org.agmas.noellesroles.content.entity.PuppeteerBodyEntity) {
                        return true;
                    }
                    if (entity instanceof org.agmas.noellesroles.content.entity.GhostPhantomEntity) {
                        return true;
                    }
                    return false;
                }, RANGE);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return 110;
    }

    @Override
    public String getItemSkinType() {
        return "haidaowandao";
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.noellesroles.pirate_cutlass.tooltip").withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
