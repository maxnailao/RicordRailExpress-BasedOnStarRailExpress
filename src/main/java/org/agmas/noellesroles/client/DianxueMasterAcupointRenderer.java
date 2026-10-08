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
    private static final double HW = 0.5;    // 穴位框水平半径（放宽，仅按高度区分部位）

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
        double h = target.getBbHeight();
        int band = DianxueMasterClientState.activeIndex;
        double y0 = DianxueMasterClientState.sliceBottomFrac(band) * h;
        double y1 = DianxueMasterClientState.sliceTopFrac(band) * h;

        boolean blink = (System.currentTimeMillis() / 180L) % 2 == 0;
        float r = 1.0F;
        float g = blink ? 0.18F : 0.0F;
        float b = 0.12F;
        float a = 0.95F;

        VertexConsumer lines = context.consumers()
                .getBuffer(TaskBlockOverlayRenderer.ALWAYS_VISIBLE_THICK_LINES);
        PoseStack m = context.matrixStack();
        m.pushPose();
        m.translate(feet.x - cam.x, feet.y - cam.y, feet.z - cam.z);
        AABB box = new AABB(-HW, y0, -HW, HW, y1, HW);
        LevelRenderer.renderLineBox(m, lines, box, r, g, b, a);
        m.popPose();
    }
}