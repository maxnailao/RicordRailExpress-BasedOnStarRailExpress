package org.agmas.noellesroles.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.packet.DianxueMasterClickC2SPacket;
import org.agmas.noellesroles.packet.DianxueMasterSyncS2CPacket;

import java.util.UUID;

public class DianxueMasterClientState {
    public static boolean active = false;
    public static UUID targetId = null;
    public static int phase = 0;
    public static int activeIndex = 0;

    public static void handleSync(DianxueMasterSyncS2CPacket p) {
        switch (p.action()) {
            case 0 -> {
                active = true;
                targetId = p.targetId();
                phase = p.phase();
                activeIndex = p.activeIndex();
            }
            case 1 -> {
                if (active) {
                    phase = p.phase();
                    activeIndex = p.activeIndex();
                }
            }
            case 2 -> clear();
        }
    }

    public static void clear() {
        active = false;
        targetId = null;
        phase = 0;
        activeIndex = 0;
    }

    /** 穴位高度分区：0=头 1=胸 2=腹 3=大腿 4=小腿，等宽 5 段（自上而下）。localY 为脚底到命中点高度。 */
    public static int bandFromLocalY(double localY, double height) {
        double t = height <= 0.0 ? 0.0 : localY / height;
        if (t < 0.0) t = 0.0;
        if (t > 1.0) t = 1.0;
        int band = (int) Math.floor((1.0 - t) / 0.2);
        if (band < 0) band = 0;
        if (band > 4) band = 4;
        return band;
    }

    /** 某穴位可点区间下沿占比（渲染与命中一致）。 */
    public static double sliceBottomFrac(int band) {
        return 1.0 - (band + 1) * 0.2;
    }

    /** 某穴位可点区间上沿占比。 */
    public static double sliceTopFrac(int band) {
        return 1.0 - band * 0.2;
    }

    public static void registerAttackHook() {
        AttackEntityCallback.EVENT.register((attacker, level, hand, entity, hitResult) -> {
            if (!level.isClientSide) return InteractionResult.PASS;
            if (!active || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || attacker != client.player) return InteractionResult.PASS;
            if (!(entity instanceof Player target) || !target.getUUID().equals(targetId)) return InteractionResult.PASS;

            double localY;
            if (hitResult != null) {
                localY = hitResult.getLocation().y - target.getY();
            } else {
                // 回退：用视线与目标的水平距离推算瞄准高度（不依赖 hitResult）
                Vec3 eye = client.player.getEyePosition(1.0F);
                Vec3 look = client.player.getLookAngle();
                double dx = target.getX() - eye.x;
                double dz = target.getZ() - eye.z;
                double horiz = Math.sqrt(dx * dx + dz * dz);
                double hzLen = Math.hypot(look.x, look.z);
                double scale = hzLen < 1.0e-4 ? 0.0 : horiz / hzLen;
                double aimY = eye.y + look.y * scale;
                localY = aimY - target.getY();
            }
            int band = bandFromLocalY(localY, target.getBbHeight());
            ClientPlayNetworking.send(new DianxueMasterClickC2SPacket(band));
            // 取消普通近战伤害：本击用于点穴
            return InteractionResult.SUCCESS;
        });
    }
}