package io.wifi.starrailexpress.content.item;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.compat.CrosshairaddonsCompat;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.KillerKnifeDurability;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.network.original.KnifeStabPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.NotNull;

public class KnifeItem extends SkinableItem {
    public KnifeItem(Properties settings) {
        super(settings);
    }

    /**
     * (target, killer)
     */
    // public static BiConsumer<ServerPlayer, ServerPlayer> PlayerKilledPlayer;
    public static final ResourceLocation ITEM_ID = SRE.TMMId("knife");

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, @NotNull Player user, InteractionHand hand) {
        ItemStack itemStack = user.getItemInHand(hand);
        if (!world.isClientSide) {
            boolean durabilityKnife = KillerKnifeDurability.isDurabilityModeEnabled(user.level())
                    && KillerKnifeDurability.isMarkedKnife(itemStack);
            if (durabilityKnife && KillerKnifeDurability.isDepleted(itemStack)) {
                user.displayClientMessage(
                        Component.translatable("message.sre.knife.depleted").withStyle(ChatFormatting.DARK_RED), true);
                return InteractionResultHolder.fail(itemStack);
            }
        } else {
            if (itemStack.getMaxDamage() > 0 && itemStack.getDamageValue() >= itemStack.getMaxDamage()) {
                return InteractionResultHolder.fail(itemStack);
            }
        }
        // 特别皮肤专属切刀音效：仅对持刀者播放，其他玩家听到正常切刀音效
        boolean hasKunaiSkin = io.wifi.starrailexpress.util.SushuiKunaiSkinHandler
                .hasKunaiSkinEquipped(user, itemStack);
        boolean hasAnxingSkin = io.wifi.starrailexpress.util.AnxingSkinHandler
                .hasAnxingSkinEquipped(user, itemStack);
        if (hasKunaiSkin || hasAnxingSkin) {
            if (user instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                // 服务端：专属音效仅发给持刀者，正常切刀声排除持刀者
                if (hasKunaiSkin) {
                    io.wifi.starrailexpress.util.SushuiKunaiSkinHandler.playSwitchSound(
                            serverPlayer, user.getX(), user.getY(), user.getZ());
                } else {
                    io.wifi.starrailexpress.util.AnxingSkinHandler.playSwitchSound(
                            serverPlayer, user.getX(), user.getY(), user.getZ());
                }
                user.level().playSound(serverPlayer, user.blockPosition(), TMMSounds.ITEM_KNIFE_PREPARE,
                        net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);
            }
            // 客户端：跳过本地普通音效，等待服务端专属音效包，避免双重播放
        } else {
            user.playSound(TMMSounds.ITEM_KNIFE_PREPARE, 1.0f, 1.0f);
        }
        user.startUsingItem(hand);
        return InteractionResultHolder.consume(itemStack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level world, LivingEntity user, int remainingUseTicks) {
        if (user.isSpectator()) {
            return;
        }
        if (remainingUseTicks >= this.getUseDuration(stack, user) - 8 || !(user instanceof Player attacker)
                || !world.isClientSide)
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
            Entity target = entityHitResult.getEntity();
            if (SRE.REPLAY_MANAGER != null) {
                SRE.REPLAY_MANAGER.recordItemUse(user.getUUID(), BuiltInRegistries.ITEM.getKey(this));
            }
            ClientPlayNetworking.send(new KnifeStabPayload(target.getId()));
            CrosshairaddonsCompat.onAttack(target);
        }
    }

    public static HitResult getKnifeTarget(Player user) {
        return ProjectileUtil.getHitResultOnViewVector(user,
                entity ->{
            // 允许选中玩家
            if (entity instanceof Player player) {
                return GameUtils.isPlayerAliveAndSurvival(player);
            }
            // 允许选中傀儡师本体实体
            if (entity instanceof org.agmas.noellesroles.content.entity.PuppeteerBodyEntity) {
                return true;
            }
            // 允许选中鬼魅幻影实体
            if (entity instanceof org.agmas.noellesroles.content.entity.GhostPhantomEntity) {
                return true;
            }
            return false;
                }, 4f);
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
        return "knife";
    }
}