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
 * - 双枪-右手：仅在主手时可以右键开枪，冷却独立计算
 * - 双枪-左手：仅在副手时可以右键开枪，冷却独立计算
 * - 一次右键只开一枪。不依赖原版"主手冷却→自动落副手"机制（该链路对模组 item 不可靠）：
 *   双持时右键始终由主手右手枪的 use() 裁决——右手枪自身 CD 就绪则开右手；
 *   右手枪冷却中则在同一入口代开副手左手枪（若左手 CD 就绪）；两枪 CD 都在则不开
 */
public class DualPistolItem extends SkinableItem implements HeldLikeRevolver {
    /** 是否为左手枪（左手枪仅副手可用，冷却独立） */
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
            // 独立冷却：只检查左手枪自己的冷却
            if (user.getCooldowns().isOnCooldown(ModItems.DUAL_PISTOL_LEFT)) {
                return InteractionResultHolder.pass(stack);
            }
        } else {
            // 右手枪：仅在主手生效。
            // 注意：右手枪冷却中不能提前 pass，否则会放弃"代开左手枪"的机会——
            // 原版并不会因为主手 use() 返回 pass 就去调用副手的 use()
            if (hand != InteractionHand.MAIN_HAND) {
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

            // 一次右键只开一枪，左右手各自独立 CD：
            // 右手枪在冷却中时，若副手是左手枪且左手自身 CD 就绪，则由主手入口代开左手
            boolean firingLeft = left;
            if (!left && user.getCooldowns().isOnCooldown(ModItems.DUAL_PISTOL_RIGHT)) {
                if (user.getOffhandItem().is(ModItems.DUAL_PISTOL_LEFT)
                        && !user.getCooldowns().isOnCooldown(ModItems.DUAL_PISTOL_LEFT)) {
                    firingLeft = true;
                } else {
                    // 两枪都在冷却：不开枪
                    return InteractionResultHolder.pass(stack);
                }
            }
            shootOne(user, firingLeft);
        } else {
            // 服务端角色检查（真正的开火逻辑在 DualPistolShootPayload 服务端）
            SREGameWorldComponent gameComponent = SREGameWorldComponent.KEY.get(world);
            SRERole role = gameComponent.getRole(user);
            if (role != null && !role.onUseGun(user)) {
                return InteractionResultHolder.fail(stack);
            }
            if (!left && user.getCooldowns().isOnCooldown(ModItems.DUAL_PISTOL_RIGHT)) {
                return InteractionResultHolder.pass(stack);
            }
        }

        return InteractionResultHolder.consume(stack);
    }

    /** 客户端单枪开火：射线检测、发送射击包、后坐力/枪口粒子，并只给该枪自身加冷却（左右手独立 CD） */
    private static void shootOne(Player user, boolean leftHand) {
        HitResult collision = getGunTarget(user);
        if (collision instanceof EntityHitResult entityHitResult) {
            Entity target = entityHitResult.getEntity();
            ClientPlayNetworking.send(new DualPistolShootPayload(leftHand, target.getId()));
            CrosshairaddonsCompat.arrowHit();
        } else {
            ClientPlayNetworking.send(new DualPistolShootPayload(leftHand, -1));
        }

        user.setXRot(user.getXRot() - 4.0F);
        spawnHandParticle();

        user.getCooldowns().addCooldown(
                leftHand ? ModItems.DUAL_PISTOL_LEFT : ModItems.DUAL_PISTOL_RIGHT,
                getRevolverCooldown());
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
