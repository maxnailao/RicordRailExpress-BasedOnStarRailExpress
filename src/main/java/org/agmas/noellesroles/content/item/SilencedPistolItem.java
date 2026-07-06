package org.agmas.noellesroles.content.item;

import io.wifi.StarRailExpressID;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.particle.HandParticle;
import io.wifi.starrailexpress.client.render.TMMRenderLayers;
import io.wifi.starrailexpress.compat.CrosshairaddonsCompat;
import io.wifi.starrailexpress.content.item.SkinableItem;
import io.wifi.starrailexpress.content.item.api.SREItemProperties.HeldLikeRevolver;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.api.SRERole;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import org.agmas.noellesroles.init.ModItems;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 消音手枪
 * - 右键开枪，左键换弹
 * - 射程18格，冷却8秒
 * - 弹容量2发，装填时间2秒
 * - 枪声传播距离极低（半径8格），使用专用消音枪声
 * - 需要专用消音手枪子弹
 */
public class SilencedPistolItem extends SkinableItem implements HeldLikeRevolver {
    public static final int MAX_AMMO = 2;
    public static final float RANGE = 18.0f;

    public SilencedPistolItem(Properties settings) {
        super(settings.stacksTo(1));
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level world, @NotNull Player user,
            @NotNull InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);

        if (user.isSpectator() || !user.isAlive()) {
            return InteractionResultHolder.fail(stack);
        }

        // 检查冷却
        if (user.getCooldowns().isOnCooldown(stack.getItem())) {
            return InteractionResultHolder.fail(stack);
        }

        // 检查弹药
        int currentAmmo = getAmmoCount(stack);
        if (currentAmmo <= 0) {
            if (world.isClientSide) {
                user.displayClientMessage(
                        Component.translatable("message.noellesroles.silenced_pistol.empty")
                                .withStyle(ChatFormatting.RED),
                        true);
            }
            return InteractionResultHolder.fail(stack);
        }

        if (world.isClientSide) {
            // 检查角色是否允许使用枪械
            SREGameWorldComponent gameComponent = SREClient.gameComponent;
            if (gameComponent != null) {
                SRERole role = gameComponent.getRole(user);
                if (role != null && !role.onUseGun(user)) {
                    return InteractionResultHolder.fail(stack);
                }
            }

            // 射线检测目标
            HitResult collision = getGunTarget(user);
            if (collision instanceof EntityHitResult entityHitResult) {
                Entity target = entityHitResult.getEntity();
                ClientPlayNetworking.send(new SilencedPistolShootPayload(SilencedPistolShootPayload.Action.SHOOT,
                        target.getId()));
                CrosshairaddonsCompat.arrowHit();
            } else {
                ClientPlayNetworking
                        .send(new SilencedPistolShootPayload(SilencedPistolShootPayload.Action.SHOOT, -1));
            }

            // 后坐力和枪口火焰粒子
            user.setXRot(user.getXRot() - 2);
            spawnHandParticle();
        } else {
            // 服务端角色检查
            SREGameWorldComponent gameComponent = SREGameWorldComponent.KEY.get(world);
            SRERole role = gameComponent.getRole(user);
            if (role != null && !role.onUseGun(user)) {
                return InteractionResultHolder.fail(stack);
            }
        }

        return InteractionResultHolder.consume(stack);
    }

    public static HitResult getGunTarget(Player user) {
        return ProjectileUtil.getHitResultOnViewVector(user, (entity) -> {
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

    // === 弹药管理 ===

    public static int getAmmoCount(ItemStack stack) {
        return stack.getOrDefault(SREDataComponentTypes.AMMO_COUNT, 0);
    }

    public static void setAmmoCount(ItemStack stack, int count) {
        stack.set(SREDataComponentTypes.AMMO_COUNT, Math.min(count, MAX_AMMO));
    }

    public static void consumeAmmo(ItemStack stack) {
        int currentAmmo = getAmmoCount(stack);
        setAmmoCount(stack, Math.max(0, currentAmmo - 1));
    }

    // === 换弹 ===

    public static void tryReloadFromClient(Player user) {
        ItemStack stack = user.getMainHandItem();
        if (!stack.is(ModItems.SILENCED_PISTOL))
            return;
        if (user.getCooldowns().isOnCooldown(stack.getItem()))
            return;

        int currentAmmo = getAmmoCount(stack);
        if (currentAmmo >= MAX_AMMO)
            return;

        // 检查背包中是否有消音手枪子弹
        boolean hasBullet = false;
        for (int i = 0; i < user.getInventory().getContainerSize(); i++) {
            if (user.getInventory().getItem(i).is(ModItems.SILENCED_PISTOL_BULLET)) {
                hasBullet = true;
                break;
            }
        }
        if (!hasBullet)
            return;

        ClientPlayNetworking
                .send(new SilencedPistolShootPayload(SilencedPistolShootPayload.Action.RELOAD, user.getId()));
        // 2秒装填冷却
        user.getCooldowns().addCooldown(ModItems.SILENCED_PISTOL, 40);
    }

    // === 视觉效果 ===

    public static void spawnHandParticle() {
        HandParticle handParticle = (new HandParticle())
                .setTexture(StarRailExpressID.watheId("textures/particle/gunshot.png"))
                .setPos(0.1F, 0.275F, -0.2F).setMaxAge(3.0F).setSize(0.3F).setVelocity(0.0F, 0.0F, 0.0F)
                .setLight(15, 15).setAlpha(new float[] { 0.5F, 0.05F }).setRenderLayer(TMMRenderLayers::additive);
        SREClient.handParticleManager.spawn(handParticle);
    }

    // === Tooltip 显示弹药数 ===

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context,
            @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        int ammo = getAmmoCount(stack);
        tooltip.add(Component.translatable("item.noellesroles.silenced_pistol.ammo", ammo, MAX_AMMO)
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }

    @Override
    public boolean isBarVisible(@NotNull ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(@NotNull ItemStack stack) {
        int ammo = getAmmoCount(stack);
        return (int) ((double) ammo / MAX_AMMO * 13);
    }

    @Override
    public int getBarColor(@NotNull ItemStack stack) {
        return 0x55FF55;
    }

    @Override
    public String getItemSkinType() {
        return "revolver";
    }
}
