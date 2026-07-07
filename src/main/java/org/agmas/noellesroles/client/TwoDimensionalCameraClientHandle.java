package org.agmas.noellesroles.client;

import io.wifi.starrailexpress.event.AllowOtherCameraType;
import net.exmo.sre.camera.client.AdvancedCameraDirector;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.init.ModEffects;

public final class TwoDimensionalCameraClientHandle {
    private static final double DEFAULT_CAMERA_DISTANCE = 28.0D;
    private static final double CAMERA_HEIGHT = 6.0D;
    private static final double TOP_CAMERA_HEIGHT = 34.0D;
    private static final float CAMERA_FOV = 35.0F;
    private static final float DEFAULT_FOREGROUND_CLIP_DISTANCE = 0.05F;
    private static final float MIN_OCCLUDED_FOREGROUND_CLIP_DISTANCE = 3.0F;
    private static final double PLAYER_CLIP_MARGIN = 0.25D;
    private static volatile boolean active;
    private static volatile float foregroundClipDistance = DEFAULT_FOREGROUND_CLIP_DISTANCE;
    private static volatile Vec3 voiceListenerPosition;

    private TwoDimensionalCameraClientHandle() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(TwoDimensionalCameraClientHandle::tick);
        AllowOtherCameraType.EVENT.register((original, localPlayer) -> {
            if (isLocalTwoDimensionalActive(localPlayer)) {
                return AllowOtherCameraType.ReturnCameraType.THIRD_PERSON_BACK;
            }
            return AllowOtherCameraType.ReturnCameraType.NO_CHANGE;
        });
    }

    private static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            deactivate();
            AdvancedCameraDirector.clearFixedOverride();
            return;
        }

        MobEffectInstance effect = player.getEffect(ModEffects.TWO_DIMENSIONAL_CAMERA);
        if (effect == null) {
            deactivate();
            AdvancedCameraDirector.clearFixedOverride();
            return;
        }

        active = true;
        voiceListenerPosition = player.getEyePosition(1.0F);
        Vec3 lookAt = player.getEyePosition(1.0F).add(0.0D, 0.5D, 0.0D);
        Vec3 cameraPos = cameraPosition(lookAt, effect.getAmplifier(), cameraDistance(player));
        foregroundClipDistance = smartForegroundClipDistance(client, player, cameraPos, lookAt);
        Vec3 delta = lookAt.subtract(cameraPos);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) (Math.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) (-(Math.atan2(delta.y, horizontal) * Mth.RAD_TO_DEG));
        AdvancedCameraDirector.setFixedOverride(cameraPos, yaw, pitch, CAMERA_FOV);
    }

    private static void deactivate() {
        active = false;
        foregroundClipDistance = DEFAULT_FOREGROUND_CLIP_DISTANCE;
        voiceListenerPosition = null;
    }

    private static boolean isLocalTwoDimensionalActive(LocalPlayer localPlayer) {
        return localPlayer != null && localPlayer.hasEffect(ModEffects.TWO_DIMENSIONAL_CAMERA);
    }

    private static double cameraDistance(LocalPlayer player) {
        MobEffectInstance distanceEffect = player.getEffect(ModEffects.TWO_DIMENSIONAL_CAMERA_DISTANCE);
        if (distanceEffect == null) {
            return DEFAULT_CAMERA_DISTANCE;
        }
        return ModEffects.getTwoDimensionalCameraDistance(distanceEffect.getAmplifier());
    }

    private static Vec3 cameraPosition(Vec3 lookAt, int amplifier, double cameraDistance) {
        if (amplifier == 4) {
            double height = Math.max(CAMERA_HEIGHT + 2.0D,
                    cameraDistance + (TOP_CAMERA_HEIGHT - DEFAULT_CAMERA_DISTANCE));
            return lookAt.add(0.0D, height, 0.0D);
        }
        if (amplifier >= 5) {
            // 5~8：东西南北平面视线 —— 相机与视点同高的纯侧视（无俯角），区别于 0~3 的 2.5D 俯视
            return lookAt.add(sideVector(amplifier - 5).scale(cameraDistance));
        }
        return lookAt.add(sideVector(amplifier).scale(cameraDistance)).add(0.0D, CAMERA_HEIGHT, 0.0D);
    }

    private static float smartForegroundClipDistance(Minecraft client, LocalPlayer player, Vec3 cameraPos, Vec3 lookAt) {
        if (client.level == null || !hasBlockingSightline(client, player, cameraPos, lookAt)) {
            return DEFAULT_FOREGROUND_CLIP_DISTANCE;
        }

        double targetDistance = cameraPos.distanceTo(lookAt);
        double clipDistance = playerNearClipLimit(player, cameraPos, lookAt) - PLAYER_CLIP_MARGIN;
        if (clipDistance <= DEFAULT_FOREGROUND_CLIP_DISTANCE) {
            return DEFAULT_FOREGROUND_CLIP_DISTANCE;
        }
        return (float) Mth.clamp(clipDistance,
                MIN_OCCLUDED_FOREGROUND_CLIP_DISTANCE,
                Math.max(MIN_OCCLUDED_FOREGROUND_CLIP_DISTANCE, targetDistance - 0.25D));
    }

    private static boolean hasBlockingSightline(Minecraft client, LocalPlayer player, Vec3 cameraPos, Vec3 lookAt) {
        AABB box = player.getBoundingBox().inflate(0.12D);
        Vec3 center = box.getCenter();
        Vec3[] targets = new Vec3[] {
                lookAt,
                center,
                new Vec3(center.x, box.minY + 0.1D, center.z),
                new Vec3(center.x, box.maxY - 0.1D, center.z),
                new Vec3(box.minX, center.y, center.z),
                new Vec3(box.maxX, center.y, center.z),
                new Vec3(center.x, center.y, box.minZ),
                new Vec3(center.x, center.y, box.maxZ)
        };

        for (Vec3 target : targets) {
            double targetDistance = cameraPos.distanceTo(target);
            if (targetDistance <= 0.25D) {
                continue;
            }
            BlockHitResult hit = client.level.clip(new ClipContext(
                    cameraPos,
                    target,
                    ClipContext.Block.OUTLINE,
                    ClipContext.Fluid.NONE,
                    player));
            if (hit.getType() != HitResult.Type.MISS
                    && cameraPos.distanceTo(hit.getLocation()) < targetDistance - 0.35D) {
                return true;
            }
        }
        return false;
    }

    private static double playerNearClipLimit(LocalPlayer player, Vec3 cameraPos, Vec3 lookAt) {
        Vec3 view = lookAt.subtract(cameraPos);
        if (view.lengthSqr() <= 1.0E-7D) {
            return DEFAULT_FOREGROUND_CLIP_DISTANCE;
        }
        view = view.normalize();
        AABB box = player.getBoundingBox().inflate(0.18D);
        double nearest = Double.MAX_VALUE;
        double[] xs = {box.minX, box.maxX};
        double[] ys = {box.minY, box.maxY};
        double[] zs = {box.minZ, box.maxZ};
        for (double x : xs) {
            for (double y : ys) {
                for (double z : zs) {
                    Vec3 point = new Vec3(x, y, z);
                    nearest = Math.min(nearest, dot(point.subtract(cameraPos), view));
                }
            }
        }
        return Double.isFinite(nearest) ? nearest : DEFAULT_FOREGROUND_CLIP_DISTANCE;
    }

    private static double dot(Vec3 a, Vec3 b) {
        return a.x * b.x + a.y * b.y + a.z * b.z;
    }

    private static Vec3 sideVector(int amplifier) {
        return switch (Mth.clamp(amplifier, 0, 3)) {
            case 0 -> new Vec3(-1.0D, 0.0D, 0.0D); // 西边
            case 1 -> new Vec3(1.0D, 0.0D, 0.0D);  // 东边
            case 2 -> new Vec3(0.0D, 0.0D, -1.0D); // 北边
            default -> new Vec3(0.0D, 0.0D, 1.0D); // 南边
        };
    }

    public static boolean isActive() {
        return active;
    }

    public static Vec3 voiceListenerPosition() {
        return active ? voiceListenerPosition : null;
    }

    public static float foregroundClipDistance() {
        return active ? foregroundClipDistance : DEFAULT_FOREGROUND_CLIP_DISTANCE;
    }
}
