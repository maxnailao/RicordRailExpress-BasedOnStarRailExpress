package org.agmas.noellesroles.content.item;

import io.wifi.StarRailExpressID;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.particle.HandParticle;
import io.wifi.starrailexpress.client.render.TMMRenderLayers;
import io.wifi.starrailexpress.compat.CrosshairaddonsCompat;
import io.wifi.starrailexpress.content.item.SkinableItem;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.agmas.noellesroles.content.entity.DurabilityBoatEntity;
import org.agmas.noellesroles.packet.PirateFlintlockShootPayload;
import org.jetbrains.annotations.NotNull;

import static io.wifi.starrailexpress.content.item.RevolverItem.spawnHandParticle;

/**
 * 海盗燧发枪
 * - 射程15格，当持有玩家坐在耐久橡木船上时射程提升为40格
 * - 冷却30秒
 * - 击杀玩家后80%概率掉落（掉落为左轮手枪）
 */
public class PirateFlintlockItem extends SkinableItem {

    /** 普通射程 */
    private static final float NORMAL_RANGE = 15.0f;
    /** 船上射程 */
    private static final float BOAT_RANGE = 40.0f;

    public PirateFlintlockItem(Item.Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(@NotNull Level world, @NotNull Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);

        if (world.isClientSide) {
            final var gameComponent = SREClient.gameComponent;
            if (gameComponent != null) {
                final var role = gameComponent.getRole(user);
                if (role != null) {
                    if (!role.onUseGun(user)) {
                        return InteractionResultHolder.fail(stack);
                    }
                }
            }

            float range = getEffectiveRange(user);
            HitResult collision = getGunTarget(user, range);
            if (collision instanceof EntityHitResult entityHitResult) {
                Entity target = entityHitResult.getEntity();
                ClientPlayNetworking.send(new PirateFlintlockShootPayload(target.getId()));
                CrosshairaddonsCompat.arrowHit();
            } else {
                ClientPlayNetworking.send(new PirateFlintlockShootPayload(-1));
            }

            user.setXRot(user.getXRot() - 4.0F);
            spawnHandParticle();
        } else {
            SREGameWorldComponent gameComponent = SREGameWorldComponent.KEY.get(world);
            if (gameComponent != null) {
                final var role = gameComponent.getRole(user);
                if (role != null) {
                    if (!role.onUseGun(user)) {
                        return InteractionResultHolder.fail(stack);
                    }
                }
            }
        }
        return InteractionResultHolder.consume(stack);
    }

    /**
     * 获取当前有效射程
     * 如果玩家坐在耐久橡木船上，射程为35格，否则为15格
     */
    public static float getEffectiveRange(Player user) {
        if (user.getVehicle() instanceof DurabilityBoatEntity) {
            return BOAT_RANGE;
        }
        return NORMAL_RANGE;
    }

    public static HitResult getGunTarget(Player user, float range) {
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
        }, range);
    }

    @Override
    public String getItemSkinType() {
        return "revolver";
    }
}
