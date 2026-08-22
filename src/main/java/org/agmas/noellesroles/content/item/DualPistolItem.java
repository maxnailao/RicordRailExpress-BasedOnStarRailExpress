package org.agmas.noellesroles.content.item;

import io.wifi.StarRailExpressID;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.particle.HandParticle;
import io.wifi.starrailexpress.client.render.TMMRenderLayers;
import io.wifi.starrailexpress.compat.CrosshairaddonsCompat;
import io.wifi.starrailexpress.content.item.SkinableItem;
import io.wifi.starrailexpress.content.item.api.SREItemProperties.HeldLikeRevolver;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.api.SRERole;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.agmas.noellesroles.init.ModItems;
import org.jetbrains.annotations.NotNull;

/**
 * 双枪（左手/右手）
 * - 双枪-右手：仅在主手时可以右键开枪
 * - 双枪-左手：仅在副手时可以右键开枪，且只有当双枪-右手处于冷却中时才能开枪
 * - 两枪共用左轮手枪的射程、贴图与冷却，形成轮流开枪的效果：
 *   右手开枪进入冷却 -> 右键放行到副手 -> 左手开枪进入冷却 -> 右手冷却结束后再开枪 ...
 */
public class DualPistolItem extends SkinableItem implements HeldLikeRevolver {
    /** 是否为左手枪（左手枪仅副手可用，且需右手枪处于冷却中） */
    private final boolean left;

    public DualPistolItem(Properties settings, boolean left) {
        super(settings.stacksTo(1));
        this.left = left;
    }

    /** 与左轮手枪相同的冷却时间 */
    public static int getRevolverCooldown() {
        return GameConstants.ITEM_COOLDOWNS.getOrDefault(TMMItems.REVOLVER,
                GameConstants.ITEM_COOLDOWNS.getOrDefault(TMMItems.STANDARD_REVOLVER, 2 * 20));
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level world, @NotNull Player user,
            @NotNull InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);

        if (user.isSpectator() || !user.isAlive()) {
            return InteractionResultHolder.fail(stack);
        }

        if (left) {
            // 左手枪：仅在副手生效
            if (hand != InteractionHand.OFF_HAND) {
                return InteractionResultHolder.pass(stack);
            }
            // 核心判定：只有双枪-右手处于冷却中时左手枪才能开枪，保证两枪轮流开火
            if (!user.getCooldowns().isOnCooldown(ModItems.DUAL_PISTOL_RIGHT)) {
                return InteractionResultHolder.pass(stack);
            }
            if (user.getCooldowns().isOnCooldown(ModItems.DUAL_PISTOL_LEFT)) {
                return InteractionResultHolder.pass(stack);
            }
        } else {
            // 右手枪：仅在主手生效
            if (hand != InteractionHand.MAIN_HAND) {
                return InteractionResultHolder.pass(stack);
            }
            if (user.getCooldowns().isOnCooldown(ModItems.DUAL_PISTOL_RIGHT)) {
                // 冷却中：放行右键，让副手的双枪-左手有机会开枪
                return InteractionResultHolder.pass(stack);
            }
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

            // 射线检测目标（与左轮手枪一致）
            HitResult collision = getGunTarget(user);
            if (collision instanceof EntityHitResult entityHitResult) {
                Entity target = entityHitResult.getEntity();
                ClientPlayNetworking.send(new DualPistolShootPayload(left, target.getId()));
                CrosshairaddonsCompat.arrowHit();
            } else {
                ClientPlayNetworking.send(new DualPistolShootPayload(left, -1));
            }

            // 后坐力与枪口火焰粒子（与左轮手枪一致）
            user.setXRot(user.getXRot() - 4.0F);
            spawnHandParticle();

            // 客户端冷却，防止连点
            user.getCooldowns().addCooldown(stack.getItem(), getRevolverCooldown());
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

    public static void spawnHandParticle() {
        HandParticle handParticle = (new HandParticle())
                .setTexture(StarRailExpressID.watheId("textures/particle/gunshot.png"))
                .setPos(0.1F, 0.275F, -0.2F).setMaxAge(3.0F).setSize(0.5F).setVelocity(0.0F, 0.0F, 0.0F)
                .setLight(15, 15).setAlpha(new float[] { 1.0F, 0.1F }).setRenderLayer(TMMRenderLayers::additive);
        SREClient.handParticleManager.spawn(handParticle);
    }

    /** 射程与判定目标与左轮手枪一致（20格） */
    public static HitResult getGunTarget(Player user) {
        return ProjectileUtil.getHitResultOnViewVector(user,
                entity -> {
                    return entity instanceof Player player && GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(player)
                            || entity instanceof org.agmas.noellesroles.content.entity.PuppeteerBodyEntity
                            || entity instanceof org.agmas.noellesroles.content.entity.PigeonEntity
                            || entity instanceof org.agmas.noellesroles.content.entity.MorphlingKnifeDummyEntity
                            || entity instanceof org.agmas.noellesroles.content.entity.GhostPhantomEntity
                            || entity instanceof org.agmas.noellesroles.content.entity.IllusionDecoyEntity;
                }, 20f);
    }

    @Override
    public String getItemSkinType() {
        return "revolver"; // 继承左轮手枪的皮肤
    }
}
