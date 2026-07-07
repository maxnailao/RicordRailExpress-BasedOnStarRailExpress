package org.agmas.noellesroles.content.item;

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
import io.wifi.StarRailExpressID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.core.particles.ParticleTypes;
import org.agmas.noellesroles.init.ModItems;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 沙漠之鹰
 * - 左键开火（由MouseHandlerMixin触发），R键换弹（由InputHandler触发）
 * - 射程25格，射击冷却0.3秒
 * - 弹容量7发，使用沙鹰弹匣装填（2.5秒换弹）
 * - 连续开火后坐力系统（DesertEagleRecoilSystem）
 * - 独特击杀机制：爆头秒杀/致残/累计击杀（DesertEagleKillSystem）
 */
public class DesertEagleItem extends SkinableItem implements HeldLikeRevolver {
    public static final int MAX_AMMO = 7;
    public static final float RANGE = 25.0f;
    /** 射击冷却：0.3秒 = 6 ticks */
    public static final int SHOOT_COOLDOWN = 6;

    // === 移动散布参数 ===
    /** 散布下限（参考潜行速度）：3.75度 */
    private static final float MIN_SPREAD_DEGREES = 3.75f;
    /** 散布上限（最大移动速度时）：15度 */
    private static final float MAX_SPREAD_DEGREES = 15.0f;
    /** 散布下限对应的移动速度（潜行速度 ≈ 0.025） */
    private static final float SPEED_FOR_MIN_SPREAD = 0.025f;
    /** 散布上限对应的移动速度（冲刺速度 ≈ 0.15） */
    private static final float SPEED_FOR_MAX_SPREAD = 0.15f;

    public DesertEagleItem(Properties settings) {
        super(settings.stacksTo(1));
    }

    /**
     * 右键不使用开火（左键开火），仅作基本处理
     */
    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level world, @NotNull Player user,
            @NotNull InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        // 左键武器，右键不做射击处理
        return InteractionResultHolder.pass(stack);
    }

    // === 射线检测 ===

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

    // === 客户端入口方法 ===

    /**
     * 由 MouseHandlerMixin 调用的左键射击入口（客户端）
     */
    public static void tryShootFromClient(Player user) {
        ItemStack stack = user.getMainHandItem();
        if (!stack.is(ModItems.DESERT_EAGLE))
            return;

        // 旁观者/死亡检查
        if (user.isSpectator() || !user.isAlive())
            return;

        // 冷却检查
        if (user.getCooldowns().isOnCooldown(stack.getItem()))
            return;

        // 弹药检查
        if (getAmmoCount(stack) <= 0) {
            user.displayClientMessage(
                    Component.translatable("message.noellesroles.desert_eagle.empty")
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        // 角色检查
        SREGameWorldComponent gameComponent = SREClient.gameComponent;
        if (gameComponent != null) {
            SRERole role = gameComponent.getRole(user);
            if (role != null && !role.onUseGun(user)) {
                return;
            }
        }

        // 计算移动散布
        float spreadAmount = calculateSpreadAmount(user);
        Vec3 shotDir;
        if (spreadAmount > 0) {
            shotDir = applySpread(user.getViewVector(1.0F), spreadAmount, user.getRandom());
        } else {
            shotDir = user.getViewVector(1.0F);
        }

        // 使用散布方向进行射线检测
        HitResult collision = getSpreadRaycast(user, shotDir, RANGE);
        int targetId = -1;
        boolean isHeadshot = false;

        if (collision instanceof EntityHitResult entityHitResult) {
            Entity target = entityHitResult.getEntity();
            targetId = target.getId();

            // 爆头判定：命中点位于玩家边界框上部区域
            if (target instanceof Player playerTarget) {
                double hitY = entityHitResult.getLocation().y;
                double boxMinY = playerTarget.getBoundingBox().minY;
                double boxMaxY = playerTarget.getBoundingBox().maxY;
                double boxHeight = boxMaxY - boxMinY;
                // 命中点位于边界框上郈25%为爆头
                isHeadshot = hitY > boxMinY + boxHeight * 0.75;
            }

            CrosshairaddonsCompat.arrowHit();
        }

        // 客户端火焰弹道粒子
        Vec3 eyePos = user.getEyePosition();
        Vec3 trailEnd = collision.getType() != HitResult.Type.MISS
                ? collision.getLocation()
                : eyePos.add(shotDir.scale(RANGE));
        spawnFlameTrail(user.level(), eyePos, trailEnd);

        // 发送射击网络包
        ClientPlayNetworking.send(new DesertEagleShootPayload(
                DesertEagleShootPayload.Action.SHOOT, targetId, isHeadshot));

        // 客户端后坐力
        DesertEagleRecoilSystem.applyRecoil(user);

        // 枪口火焰粒子
        spawnHandParticle();

        // 客户端冷却
        user.getCooldowns().addCooldown(ModItems.DESERT_EAGLE, SHOOT_COOLDOWN);
    }

    /**
     * 由 InputHandler 调用的R键换弹入口（客户端）
     */
    public static void tryReloadFromClient(Player user) {
        ItemStack stack = user.getMainHandItem();
        if (!stack.is(ModItems.DESERT_EAGLE))
            return;

        // 旁观者/死亡检查
        if (user.isSpectator() || !user.isAlive())
            return;

        // 冷却检查
        if (user.getCooldowns().isOnCooldown(stack.getItem()))
            return;

        int currentAmmo = getAmmoCount(stack);
        if (currentAmmo >= MAX_AMMO)
            return;

        // 检查背包中是否有沙鹰弹匣
        boolean hasMagazine = false;
        for (int i = 0; i < user.getInventory().getContainerSize(); i++) {
            if (user.getInventory().getItem(i).is(ModItems.DESERT_EAGLE_MAGAZINE)) {
                hasMagazine = true;
                break;
            }
        }
        if (!hasMagazine)
            return;

        // 发送换弹网络包
        ClientPlayNetworking.send(new DesertEagleShootPayload(
                DesertEagleShootPayload.Action.RELOAD, user.getId(), false));

        // 客户端冷却（2.5秒 = 50 ticks）
        user.getCooldowns().addCooldown(ModItems.DESERT_EAGLE, 50);
    }

    // === 视觉效果 ===

    public static void spawnHandParticle() {
        HandParticle handParticle = (new HandParticle())
                .setTexture(StarRailExpressID.watheId("textures/particle/gunshot.png"))
                .setPos(0.1F, 0.275F, -0.2F).setMaxAge(3.0F).setSize(0.3F).setVelocity(0.0F, 0.0F, 0.0F)
                .setLight(15, 15).setAlpha(new float[] { 0.5F, 0.05F }).setRenderLayer(TMMRenderLayers::additive);
        SREClient.handParticleManager.spawn(handParticle);
    }

    // === 移动散布系统 ===

    /**
     * 根据玩家当前移动速度计算散布角度（度）
     * 静止时返回0（无散布），移动时按速度线性插值
     * 潜行速度(~0.025)对应下限2度，冲刺速度(~0.15)对应上限8度
     */
    private static float calculateSpreadAmount(Player player) {
        double dx = player.getDeltaMovement().x;
        double dz = player.getDeltaMovement().z;
        double horizontalSpeed = Math.sqrt(dx * dx + dz * dz);

        if (horizontalSpeed < 0.005) return 0f; // 基本静止，无散布

        float t = (float) ((horizontalSpeed - SPEED_FOR_MIN_SPREAD)
                / (SPEED_FOR_MAX_SPREAD - SPEED_FOR_MIN_SPREAD));
        t = Math.max(0f, Math.min(1f, t));

        return MIN_SPREAD_DEGREES + t * (MAX_SPREAD_DEGREES - MIN_SPREAD_DEGREES);
    }

    /**
     * 对射击方向施加随机散布偏移
     */
    private static Vec3 applySpread(Vec3 direction, float spreadDegrees, net.minecraft.util.RandomSource random) {
        float spreadRad = (float) Math.toRadians(spreadDegrees);
        float yawOffset = (random.nextFloat() - 0.5f) * spreadRad;
        float pitchOffset = (random.nextFloat() - 0.5f) * spreadRad;

        double pitch = Math.asin(-direction.y);
        double yaw = Math.atan2(direction.x, direction.z);

        pitch += pitchOffset;
        yaw += yawOffset / Math.cos(pitch);

        pitch = Math.max(-Math.PI / 2 + 0.01, Math.min(Math.PI / 2 - 0.01, pitch));

        return new Vec3(
                Math.cos(pitch) * Math.sin(yaw),
                -Math.sin(pitch),
                Math.cos(pitch) * Math.cos(yaw));
    }

    /**
     * 沿指定方向进行手动射线检测（支持散布）
     */
    private static HitResult getSpreadRaycast(Player shooter, Vec3 direction, float range) {
        Vec3 eyePos = shooter.getEyePosition();
        Vec3 endPos = eyePos.add(direction.scale(range));

        // 方块射线检测
        HitResult blockHit = shooter.level().clip(
                new net.minecraft.world.level.ClipContext(
                        eyePos, endPos,
                        net.minecraft.world.level.ClipContext.Block.COLLIDER,
                        net.minecraft.world.level.ClipContext.Fluid.NONE,
                        shooter));

        Vec3 effectiveEnd = endPos;
        if (blockHit.getType() != HitResult.Type.MISS) {
            effectiveEnd = blockHit.getLocation();
        }

        // 实体射线检测
        AABB searchArea = shooter.getBoundingBox()
                .expandTowards(direction.scale(range))
                .inflate(1.0);

        EntityHitResult entityHit = null;
        double closestDist = Double.MAX_VALUE;

        for (Entity entity : shooter.level().getEntities(shooter, searchArea, e -> {
            if (e instanceof Player player) return GameUtils.isPlayerAliveAndSurvival(player);
            if (e instanceof org.agmas.noellesroles.content.entity.PuppeteerBodyEntity) return true;
            if (e instanceof org.agmas.noellesroles.content.entity.GhostPhantomEntity) return true;
            return false;
        })) {
            AABB entityBB = entity.getBoundingBox().inflate(0.3);
            var clipResult = entityBB.clip(eyePos, effectiveEnd);
            if (clipResult.isPresent()) {
                double dist = eyePos.distanceToSqr(clipResult.get());
                if (dist < closestDist) {
                    closestDist = dist;
                    entityHit = new EntityHitResult(entity, clipResult.get());
                }
            }
        }

        if (entityHit != null) return entityHit;
        return blockHit;
    }

    // === 火焰弹道粒子 ===

    /**
     * 在子弹路径上生成火焰(FLAME)粒子轨迹
     * 通过缩短粒子存在时间使粒子显得更小
     */
    private static void spawnFlameTrail(Level world, Vec3 startPos, Vec3 endPos) {
        Vec3 direction = endPos.subtract(startPos);
        double distance = direction.length();
        if (distance <= 0) return;

        direction = direction.normalize();
        double stepSize = 0.8;
        int particleCount = (int) (distance / stepSize);

        for (int i = 0; i < particleCount; i++) {
            double ratio = (double) i / particleCount;
            Vec3 particlePos = startPos.add(
                    direction.x * distance * ratio,
                    direction.y * distance * ratio,
                    direction.z * distance * ratio);

            double offsetX = (world.random.nextDouble() - 0.5) * 0.05;
            double offsetY = (world.random.nextDouble() - 0.5) * 0.05;
            double offsetZ = (world.random.nextDouble() - 0.5) * 0.05;

            if (world instanceof net.minecraft.client.multiplayer.ClientLevel) {
                net.minecraft.client.particle.Particle p = net.minecraft.client.Minecraft.getInstance()
                        .particleEngine.createParticle(
                                ParticleTypes.FLAME,
                                particlePos.x + offsetX,
                                particlePos.y + offsetY,
                                particlePos.z + offsetZ,
                                0, 0, 0);
                if (p != null) {
                    p.setLifetime(2); // 缩短存在时间使粒子显得更小
                }
            }
        }
    }

    // === Tooltip 显示弹药数 ===

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context,
            @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        int ammo = getAmmoCount(stack);
        tooltip.add(Component.translatable("item.noellesroles.desert_eagle.ammo", ammo, MAX_AMMO)
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
