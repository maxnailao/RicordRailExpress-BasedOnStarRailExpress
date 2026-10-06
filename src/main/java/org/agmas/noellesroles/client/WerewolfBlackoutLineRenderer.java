package org.agmas.noellesroles.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREWorldBlackoutComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.role.ModRoles;

import java.util.ArrayList;
import java.util.List;

/**
 * 关灯(黑灯)期间：在每只狼人与每个好人之间绘制一根红色连线。
 * - 仅杀手阵营客户端可见（好人/平民看不到）。
 * - 复用 {@link TaskBlockOverlayRenderer#ALWAYS_VISIBLE_THICK_LINES}（无深度测试），
 *   故关灯黑幕与墙体都无法遮挡这根线。
 */
public class WerewolfBlackoutLineRenderer {
    /** 红色线条颜色 */
    private static final float LINE_R = 1.0F;
    private static final float LINE_G = 0.12F;
    private static final float LINE_B = 0.12F;
    private static final float LINE_A = 0.9F;
    /** 连线最大距离（超过则不绘制，避免超远距离干扰；可按需调整） */
    private static final double MAX_DISTANCE_SQR = 256.0 * 256.0;

    public static void render(WorldRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.level == null || context.consumers() == null)
            return;
        if (SREClient.gameComponent == null || !SREClient.gameComponent.isRunning())
            return;
        // 仅真正的杀手可见（排除杀手方中立）
        SRERole selfRole = SREClient.gameComponent.getRole(client.player);
        if (selfRole == null || !selfRole.canUseKiller())
            return;
        // 仅关灯(黑灯)期间绘制
        SREWorldBlackoutComponent blackout = SREWorldBlackoutComponent.KEY.get(client.level);
        if (blackout == null || !blackout.isBlackoutActive())
            return;

        List<Player> wolves = new ArrayList<>();
        List<Player> innocents = new ArrayList<>();
        for (Player p : client.level.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(p))
                continue;
            if (SREClient.gameComponent.isRole(p, ModRoles.WEREWOLF_KILLER)) {
                wolves.add(p);
            } else {
                SRERole role = SREClient.gameComponent.getRole(p);
                if (role != null && role.isInnocent())
                    innocents.add(p);
            }
        }
        if (wolves.isEmpty() || innocents.isEmpty())
            return;

        float partialTick = client.getTimer().getGameTimeDeltaPartialTick(true);
        Vec3 camera = context.camera().getPosition();
        VertexConsumer lines = context.consumers()
                .getBuffer(TaskBlockOverlayRenderer.ALWAYS_VISIBLE_THICK_LINES);
        PoseStack matrices = context.matrixStack();

        for (Player wolf : wolves) {
            Vec3 wolfPos = bodyCenter(wolf, partialTick);
            for (Player innocent : innocents) {
                Vec3 target = bodyCenter(innocent, partialTick);
                if (wolfPos.distanceToSqr(target) > MAX_DISTANCE_SQR)
                    continue;
                matrices.pushPose();
                matrices.translate(wolfPos.x - camera.x, wolfPos.y - camera.y, wolfPos.z - camera.z);
                PoseStack.Pose pose = matrices.last();
                line(pose, lines, Vec3.ZERO, target.subtract(wolfPos), LINE_R, LINE_G, LINE_B, LINE_A);
                matrices.popPose();
            }
        }
    }

    /** 玩家身体中心（插值位置 + 半身高度），线条连接点。 */
    private static Vec3 bodyCenter(Player p, float partialTick) {
        return p.getPosition(partialTick).add(0.0D, p.getBbHeight() * 0.5D, 0.0D);
    }

    private static void line(PoseStack.Pose pose, VertexConsumer vc, Vec3 from, Vec3 to,
            float r, float g, float b, float a) {
        Vec3 normal = to.subtract(from);
        double len = normal.length();
        if (len < 1.0e-4)
            return;
        normal = normal.scale(1.0 / len);
        vc.addVertex(pose, (float) from.x, (float) from.y, (float) from.z)
                .setColor(r, g, b, a)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
        vc.addVertex(pose, (float) to.x, (float) to.y, (float) to.z)
                .setColor(r, g, b, a)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }
}