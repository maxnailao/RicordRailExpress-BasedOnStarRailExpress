package org.agmas.noellesroles.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class DianxueMasterAcupointRenderer {
    private static final double DOT_HALF = 0.09; // 小点标记半边长

    public static void render(WorldRenderContext context) {
        if (!DianxueMasterClientState.active) return;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || context.consumers() == null) return;
        Player target = null;
        for (Player p : client.level.players()) {
            if (p.getUUID().equals(DianxueMasterClientState.targetId)) {
                target = p;
                break;
            }
        }
        if (target == null || !GameUtils.isPlayerAliveAndSurvival(target)) {
            DianxueMasterClientState.clear();
            return;
        }

        float pt = client.getTimer().getGameTimeDeltaPartialTick(true);
        Vec3 feet = target.getPosition(pt);
        Vec3 cam = context.camera().getPosition();

        boolean blink = (System.currentTimeMillis() / 180L) % 2 == 0;
        int activeIndex = DianxueMasterClientState.activeIndex;

        VertexConsumer lines = context.consumers()
                .getBuffer(TaskBlockOverlayRenderer.ALWAYS_VISIBLE_THICK_LINES);
        PoseStack m = context.matrixStack();

        for (int i = 0; i < DianxueMasterClientState.POINT_COUNT; i++) {
            Vec3 dot = DianxueMasterClientState.dotWorldPos(target, feet.x, feet.y, feet.z, i);
            boolean isActive = i == activeIndex;

            m.pushPose();
            m.translate(dot.x - cam.x, dot.y - cam.y, dot.z - cam.z);

            if (isActive) {
                // 当前活动穴位：闪烁红点
                AABB dotBox = new AABB(-DOT_HALF, -DOT_HALF, -DOT_HALF, DOT_HALF, DOT_HALF, DOT_HALF);
                float g = blink ? 0.85F : 0.25F;
                LevelRenderer.renderLineBox(m, lines, dotBox, 1.0F, g, 0.2F, 0.95F);
            } else {
                // 其余穴位：暗灰色小点
                AABB dotBox = new AABB(-DOT_HALF, -DOT_HALF, -DOT_HALF, DOT_HALF, DOT_HALF, DOT_HALF);
                LevelRenderer.renderLineBox(m, lines, dotBox, 0.35F, 0.35F, 0.45F, 0.5F);
            }

            m.popPose();
        }
    }
}