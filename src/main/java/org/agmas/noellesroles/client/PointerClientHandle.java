package org.agmas.noellesroles.client;

import com.mojang.blaze3d.platform.Window;
import net.exmo.sre.camera.client.AdvancedCameraDirector;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.init.ModEffects;
import org.joml.Vector3f;

public final class PointerClientHandle {
    private static final double POINTER_RANGE = 96.0D;
    private static final double ENTITY_PICK_PADDING = 1.0D;
    private static final float ENTITY_PICK_MARGIN = 0.25F;
    private static boolean releasedForPointer;

    private PointerClientHandle() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(PointerClientHandle::tick);
    }

    private static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        boolean active = player != null && client.level != null && player.hasEffect(ModEffects.POINTER);
        if (!active) {
            restoreMouse(client);
            return;
        }
        if (client.screen != null || !client.isWindowActive()) {
            return;
        }
        if (client.mouseHandler.isMouseGrabbed()) {
            client.mouseHandler.releaseMouse();
            releasedForPointer = true;
        }

        PointerTarget target = findPointerTarget(client, player);
        if (target == null) {
            return;
        }
        lookAt(player, target.location());
        client.hitResult = target.hitResult();
        client.crosshairPickEntity = target.entity();
    }

    private static void restoreMouse(Minecraft client) {
        if (!releasedForPointer) {
            return;
        }
        releasedForPointer = false;
        if (client.screen == null && client.isWindowActive()) {
            client.mouseHandler.grabMouse();
        }
    }

    private static PointerTarget findPointerTarget(Minecraft client, LocalPlayer player) {
        Camera camera = client.gameRenderer.getMainCamera();
        if (!camera.isInitialized()) {
            return null;
        }
        Vec3 cameraPos = camera.getPosition();
        Vec3 direction = pointerDirection(client, camera);
        if (direction.lengthSqr() <= 1.0E-7D) {
            return null;
        }

        double startOffset = TwoDimensionalCameraClientHandle.isActive()
                ? TwoDimensionalCameraClientHandle.foregroundClipDistance()
                : 0.05D;
        Vec3 rayStart = cameraPos.add(direction.scale(startOffset));
        Vec3 rayEnd = cameraPos.add(direction.scale(POINTER_RANGE));

        BlockHitResult blockHit = client.level.clip(new ClipContext(
                rayStart,
                rayEnd,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player));
        HitResult bestHit = blockHit;
        Entity bestEntity = null;
        double bestDistance = blockHit.getType() == HitResult.Type.MISS
                ? Double.MAX_VALUE
                : rayStart.distanceToSqr(blockHit.getLocation());

        AABB searchBox = new AABB(rayStart, rayEnd).inflate(ENTITY_PICK_PADDING);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                client.level,
                player,
                rayStart,
                rayEnd,
                searchBox,
                entity -> entity != player && !entity.isSpectator() && entity.isPickable(),
                ENTITY_PICK_MARGIN);
        if (entityHit != null) {
            double entityDistance = rayStart.distanceToSqr(entityHit.getLocation());
            if (entityDistance <= bestDistance) {
                bestHit = entityHit;
                bestEntity = entityHit.getEntity();
            }
        }

        if (bestHit != null && bestHit.getType() != HitResult.Type.MISS) {
            return new PointerTarget(bestHit.getLocation(), bestHit, bestEntity);
        }

        Vec3 fallback = pointerPlaneTarget(cameraPos, direction, camera, player);
        HitResult miss = BlockHitResult.miss(
                fallback,
                Direction.getNearest(direction),
                BlockPos.containing(fallback));
        return new PointerTarget(fallback, miss, null);
    }

    private static Vec3 pointerDirection(Minecraft client, Camera camera) {
        Window window = client.getWindow();
        double width = Math.max(1.0D, window.getScreenWidth());
        double height = Math.max(1.0D, window.getScreenHeight());
        double mouseX = Mth.clamp(client.mouseHandler.xpos(), 0.0D, width);
        double mouseY = Mth.clamp(client.mouseHandler.ypos(), 0.0D, height);
        double ndcX = mouseX / width * 2.0D - 1.0D;
        double ndcY = 1.0D - mouseY / height * 2.0D;
        double fov = currentFov(client);
        double tanY = Math.tan(Math.toRadians(fov) * 0.5D);
        double tanX = tanY * width / height;

        Vec3 look = vec(camera.getLookVector());
        Vec3 left = vec(camera.getLeftVector());
        Vec3 up = vec(camera.getUpVector());
        return look
                .add(left.scale(-ndcX * tanX))
                .add(up.scale(ndcY * tanY))
                .normalize();
    }

    private static double currentFov(Minecraft client) {
        float partialTick = client.getTimer().getGameTimeDeltaPartialTick(false);
        float advancedFov = AdvancedCameraDirector.getFovOverride(partialTick);
        if (advancedFov > 0.0F) {
            return advancedFov;
        }
        return client.options.fov().get();
    }

    private static Vec3 pointerPlaneTarget(Vec3 cameraPos, Vec3 direction, Camera camera, LocalPlayer player) {
        Vec3 cameraLook = vec(camera.getLookVector()).normalize();
        Vec3 playerEye = player.getEyePosition(1.0F);
        double denominator = dot(direction, cameraLook);
        if (Math.abs(denominator) <= 1.0E-5D) {
            return cameraPos.add(direction.scale(16.0D));
        }
        double t = dot(playerEye.subtract(cameraPos), cameraLook) / denominator;
        if (t <= 0.0D || !Double.isFinite(t)) {
            t = 16.0D;
        }
        return cameraPos.add(direction.scale(Mth.clamp(t, 1.0D, POINTER_RANGE)));
    }

    private static void lookAt(LocalPlayer player, Vec3 target) {
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 delta = target.subtract(eye);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (horizontal <= 1.0E-5D && Math.abs(delta.y) <= 1.0E-5D) {
            return;
        }
        float yaw = (float) (Math.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) (-(Math.atan2(delta.y, horizontal) * Mth.RAD_TO_DEG));
        pitch = Mth.clamp(pitch, -90.0F, 90.0F);
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.setYHeadRot(yaw);
        player.setYBodyRot(yaw);
    }

    private static Vec3 vec(Vector3f vector) {
        return new Vec3(vector.x(), vector.y(), vector.z());
    }

    private static double dot(Vec3 a, Vec3 b) {
        return a.x * b.x + a.y * b.y + a.z * b.z;
    }

    private record PointerTarget(Vec3 location, HitResult hitResult, Entity entity) {
    }
}
