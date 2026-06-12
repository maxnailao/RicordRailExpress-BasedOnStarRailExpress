package org.agmas.noellesroles.content.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public class HammerItem extends Item {
    public HammerItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack itemStack = player.getItemInHand(usedHand);
        player.startUsingItem(usedHand);
        // 蓄力时没有声音
        return InteractionResultHolder.consume(itemStack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity livingEntity, int timeCharged) {
        if (!(livingEntity instanceof Player)) {
            return;
        }
        Player player = (Player) livingEntity;

        int duration = this.getUseDuration(stack, livingEntity) - timeCharged;
        // 蓄力时间为0.8秒 (16 ticks)
        if (duration < 16) {
            return;
        }

        if (!level.isClientSide) {
            // 射线检测，距离3格
            double reachDistance = 3.0;
            Vec3 eyePosition = player.getEyePosition();
            Vec3 viewVector = player.getViewVector(1.0F);
            Vec3 reachVector = eyePosition.add(viewVector.x * reachDistance, viewVector.y * reachDistance, viewVector.z * reachDistance);
            AABB aabb = player.getBoundingBox().expandTowards(viewVector.scale(reachDistance)).inflate(1.0D);

            EntityHitResult hitResult = ProjectileUtil.getEntityHitResult(
                player,
                eyePosition,
                reachVector,
                aabb,
                (entity) -> !entity.isSpectator() && entity.isPickable(),
                reachDistance * reachDistance
            );

            if (hitResult != null) {
                Entity entity = hitResult.getEntity();
                if (entity instanceof Player) {
                    Player target = (Player) entity;
                    // 命中玩家后播放重击音效
                    level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 1.0F);

                    // 给予目标3秒缓慢V效果（等级4，60 tick）
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 4));
                    // 给予目标3秒失明效果（60 tick）
                    target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));

                    // 2格击退
                    float yawRad = player.getYRot() * ((float) Math.PI / 180F);
                    float dx = (float) Math.sin(yawRad);
                    float dz = (float) -Math.cos(yawRad);
                    target.knockback(2.0F, dx, dz);

                    // 发送消息提示
                    player.displayClientMessage(Component.translatable("message.noellesroles.hammer.hit", target.getName()).withStyle(ChatFormatting.GREEN), true);
                    target.displayClientMessage(Component.translatable("message.noellesroles.hammer.hit_by", player.getName()).withStyle(ChatFormatting.RED), true);

                    // 使用后物品消失
                    stack.shrink(1);
                }
            }
        }
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR;
    }
}
