package org.agmas.noellesroles.client.blindness;

import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.client.event.CommonHudRenderCallback;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.packet.SoundEchoS2CPacket;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 生物声纹 HUD 渲染器（移植自"失明症"模组 SoundEchoMarkerRenderer 的简化形态）
 * <p>
 * 失明玩家收到 {@link SoundEchoS2CPacket} 后，在屏幕中心（准星）周围的环形带上
 * 渲染方向性声纹标记：敌对生物红色、普通生物白色、玩家脚步灰色。
 * 标记大小随声音强度缩放、随时间淡出；墙后的声音（occluded）以半透明呈现，
 * 对应原模组"声音遮挡模糊化"的无障碍设计。
 */
public final class SoundEchoHudRenderer {

    /** 声纹标记存活时长（纳秒） */
    private static final long MARKER_LIFETIME_NANOS = 2_500_000_000L;
    /** 标记环形带的基准半径（GUI 像素） */
    private static final int RING_RADIUS = 36;
    /** 最大同时显示的标记数 */
    private static final int MAX_MARKERS = 12;

    private static final List<EchoMarker> MARKERS = new ArrayList<>();

    private SoundEchoHudRenderer() {
    }

    public static void register() {
        CommonHudRenderCallback.EVENT.register(SoundEchoHudRenderer::render);
    }

    /** 接收服务端声纹包（仅客户端线程调用） */
    public static void accept(Vec3 soundPos, SoundEchoS2CPacket.Category category, float strength,
            boolean occluded) {
        if (MARKERS.size() >= MAX_MARKERS) {
            MARKERS.remove(0);
        }
        MARKERS.add(new EchoMarker(soundPos, category, strength, occluded, System.nanoTime()));
    }

    /** 每客户端 tick 清理到期标记 */
    public static void tick(long now) {
        MARKERS.removeIf(marker -> now - marker.startNanos() >= MARKER_LIFETIME_NANOS);
    }

    public static void clear() {
        MARKERS.clear();
    }

    private static void render(FakeGuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            return;
        }
        if (!player.hasEffect(ModEffects.BLINDNESS_SICKNESS) || player.isSpectator()) {
            return;
        }
        if (MARKERS.isEmpty()) {
            return;
        }
        long now = System.nanoTime();
        int centerX = guiGraphics.guiWidth() / 2;
        int centerY = guiGraphics.guiHeight() / 2;
        Vec3 eye = player.getEyePosition();
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(true);
        float yawRad = (float) Math.toRadians(player.getViewYRot(partialTick));
        // 玩家朝向在 XZ 平面的角度（yaw=0 看向 -Z）
        float forwardAngle = (float) Math.atan2(-Math.sin(yawRad), -Math.cos(yawRad));

        Iterator<EchoMarker> iterator = MARKERS.iterator();
        while (iterator.hasNext()) {
            EchoMarker marker = iterator.next();
            long age = now - marker.startNanos();
            if (age >= MARKER_LIFETIME_NANOS) {
                iterator.remove();
                continue;
            }
            Vec3 delta = marker.soundPos().subtract(eye);
            if (delta.lengthSqr() < 0.01D) {
                continue;
            }
            // 目标相对玩家朝向的角度：正前方=屏幕正上方，顺时针分布
            float targetAngle = (float) Math.atan2(delta.x, delta.z);
            float relative = targetAngle - forwardAngle;
            int offsetX = (int) (Math.sin(relative) * RING_RADIUS);
            int offsetY = (int) (-Math.cos(relative) * RING_RADIUS);

            // 生命周期淡出 + 遮挡半透明
            float fade = 1.0F - (float) age / MARKER_LIFETIME_NANOS;
            float alpha = fade * (marker.occluded() ? 0.5F : 1.0F);
            int color = colorFor(marker.category(), alpha);
            // 标记大小随声音强度缩放
            int size = 3 + (int) (marker.strength() * 4.0F);
            int x = centerX + offsetX - size / 2;
            int y = centerY + offsetY - size / 2;
            guiGraphics.fill(x, y, x + size, y + size, 300, color);
            // 外圈细框增强方向辨识度
            guiGraphics.fill(x - 1, y - 1, x + size + 1, y, 300, withAlpha(0x000000, alpha * 0.6F));
            guiGraphics.fill(x - 1, y + size, x + size + 1, y + size + 1, 300, withAlpha(0x000000, alpha * 0.6F));
        }
    }

    /** 按类别取标记主色并叠加透明度 */
    private static int colorFor(SoundEchoS2CPacket.Category category, float alpha) {
        int base = switch (category) {
            case DANGER -> 0xFF5555;
            case AMBIENT -> 0xEEEEEE;
            case FOOTSTEP -> 0x999999;
        };
        return withAlpha(base, alpha);
    }

    private static int withAlpha(int rgb, float alpha) {
        int clamped = Math.max(0, Math.min(255, (int) (alpha * 255F)));
        return (clamped << 24) | (rgb & 0x00FFFFFF);
    }

    private record EchoMarker(Vec3 soundPos, SoundEchoS2CPacket.Category category, float strength,
            boolean occluded, long startNanos) {
    }
}
