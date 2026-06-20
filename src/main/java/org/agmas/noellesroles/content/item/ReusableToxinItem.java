package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerPoisonComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.agmas.noellesroles.init.HSRConstants;
import org.agmas.noellesroles.init.NRSounds;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;

public class ReusableToxinItem extends Item {
    private static final int COOLDOWN_SECONDS = 30;

    public ReusableToxinItem(Item.Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, @NotNull Player user, InteractionHand hand) {
        ItemStack itemStack = user.getItemInHand(hand);

        // 检查是否在冷却中
        if (user.getCooldowns().isOnCooldown(this)) {
            float cooldownPercent = user.getCooldowns().getCooldownPercent(this, 0);
            int seconds = (int) Math.ceil(cooldownPercent * 60);
            user.displayClientMessage(
                    Component.translatable("item.noellesroles.reusable_toxin.cooldown", seconds)
                            .withStyle(ChatFormatting.RED),
                    true);
            return InteractionResultHolder.fail(itemStack);
        }

        user.startUsingItem(hand);
        return InteractionResultHolder.consume(itemStack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level world, LivingEntity user, int remainingUseTicks) {
        if (user.isSpectator()) {
            return;
        }

        if (remainingUseTicks < this.getUseDuration(stack, user) - 6 && user instanceof Player) {
            Player attacker = (Player) user;

            if (!world.isClientSide) {
                HitResult collision = getToxinTarget(attacker);

                if (collision instanceof EntityHitResult entityHitResult) {
                    Entity target1 = entityHitResult.getEntity();

                    if (user instanceof ServerPlayer player) {
                        if (target1 instanceof Player target) {
                            // 给目标施加中毒效果
                            ((SREPlayerPoisonComponent) SREPlayerPoisonComponent.KEY.get(target))
                                    .setPoisonTicks(HSRConstants.toxinPoisonTime, player.getUUID());

                            // 播放音效
                            player.playSound(NRSounds.SYRINGE_STAB, 0.15F, 1.0F);
                            player.swing(InteractionHand.MAIN_HAND);

                            // 添加30秒冷却（不消耗物品）
                            player.getCooldowns().addCooldown(this, COOLDOWN_SECONDS * 20);

                            // 通知附近的医生
                            if (player.level() instanceof ServerLevel slevel) {
                                var gameComponent = SREGameWorldComponent.KEY.get(player.level());
                                slevel.players().forEach((pl) -> {
                                    if (pl.distanceToSqr(player) <= 100) {
                                        if (gameComponent.isRole(pl, ModRoles.DOCTOR)) {
                                            pl.displayClientMessage(
                                                    Component.translatable("message.noellesroles.doctor.someone_toxin")
                                                            .withStyle(ChatFormatting.YELLOW),
                                                    true);
                                        }
                                    }
                                });
                            }
                        }
                    }
                }
            }
        }
    }

    public static HitResult getToxinTarget(Player user) {
        return ProjectileUtil.getHitResultOnViewVector(user, (entity) -> {
            boolean var10000;
            if (entity instanceof Player player) {
                if (GameUtils.isPlayerAliveAndSurvival(player)) {
                    var10000 = true;
                    return var10000;
                }
            }
            var10000 = false;
            return var10000;
        }, (double) 15.0F);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return 72000;
    }
}
