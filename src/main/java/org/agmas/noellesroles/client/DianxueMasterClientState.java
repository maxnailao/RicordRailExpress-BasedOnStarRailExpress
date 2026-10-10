package org.agmas.noellesroles.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.packet.DianxueMasterClickC2SPacket;
import org.agmas.noellesroles.packet.DianxueMasterSyncS2CPacket;

import java.util.UUID;

public class DianxueMasterClientState {
    /** 点穴小点的可命中容错半径（判定范围）。 */
    public static final double DOT_TOLERANCE = 0.45;
    public static final int POINT_COUNT = 5;

    public static boolean active = false;
    public static UUID targetId = null;
    public static int activeIndex = 0;
    public static final double[] dotHeightFracs = new double[5];
    public static final double[] dotAzimuths = new double[5];
    public static double dotRadius = 0.35;

    public static void handleSync(DianxueMasterSyncS2CPacket p) {
        switch (p.action()) {
            case 0 -> {
                active = true;
                targetId = p.targetId();
                activeIndex = p.activeIndex();
                applyDots(p);
            }
            case 1 -> {
                if (active) {
                    activeIndex = p.activeIndex();
                    applyDots(p);
                }
            }
            case 2 -> clear();
        }
    }

    private static void applyDots(DianxueMasterSyncS2CPacket p) {
        dotRadius = p.dotRadius();
        for (int i = 0; i < dotHeightFracs.length; i++) {
            dotHeightFracs[i] = i < p.dotHeightFracs().length ? p.dotHeightFracs()[i] : 0.5F;
            dotAzimuths[i] = i < p.dotAzimuths().length ? p.dotAzimuths()[i] : 0.0F;
        }
    }

    public static void clear() {
        active = false;
        targetId = null;
        activeIndex = 0;
    }

    /** 计算第 idx 个小点在目标身体上的世界坐标（跟随身体朝向）。feetX/Y/Z 为脚底世界坐标。 */
    public static Vec3 dotWorldPos(Player target, double feetX, double feetY, double feetZ, int idx) {
        double worldAngle = dotAzimuths[idx] + Math.toRadians(target.getYRot());
        double dx = -Math.sin(worldAngle) * dotRadius;
        double dz = Math.cos(worldAngle) * dotRadius;
        double dy = dotHeightFracs[idx] * target.getBbHeight();
        return new Vec3(feetX + dx, feetY + dy, feetZ + dz);
    }

    public static void registerAttackHook() {
        AttackEntityCallback.EVENT.register((attacker, level, hand, entity, hitResult) -> {
            if (!level.isClientSide) return InteractionResult.PASS;
            if (!active || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || attacker != client.player) return InteractionResult.PASS;
            if (!(entity instanceof Player target) || !target.getUUID().equals(targetId)) return InteractionResult.PASS;
            if (activeIndex < 0 || activeIndex >= dotHeightFracs.length) return InteractionResult.PASS;

            int idx = activeIndex;
            Vec3 dot = dotWorldPos(target, target.getX(), target.getY(), target.getZ(), idx);

            Vec3 hitPoint;
            if (hitResult != null) {
                hitPoint = hitResult.getLocation();
            } else {
                // 回退：取视线射线上距小点最近的点
                Vec3 eye = client.player.getEyePosition(1.0F);
                Vec3 look = client.player.getLookAngle();
                Vec3 toDot = dot.subtract(eye);
                double t = toDot.dot(look);
                hitPoint = eye.add(look.x * t, look.y * t, look.z * t);
            }

            // 仅命中"当前活动"穴位才推进；判定范围已放大
            if (hitPoint.distanceToSqr(dot) <= DOT_TOLERANCE * DOT_TOLERANCE) {
                ClientPlayNetworking.send(new DianxueMasterClickC2SPacket(idx));
            }
            // 点穴期间的近拳击用于瞄准小点，取消普通近战伤害
            return InteractionResult.SUCCESS;
        });
    }
}