package org.agmas.noellesroles.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.role_data.killer.ReconBotRoleData;

import java.util.OptionalDouble;

/**
 * 荡索者·钩爪绳索渲染：勾住方块时，在玩家身体与锚点之间画一根黑色粗线。
 * <p>
 * 锚点由 {@link ReconBotRoleData} 同步到客户端（Phase + Ax/Ay/Az）；绳子起点每帧取玩家当前位置，
 * 所以拉取过程中绳索会实时缩短。渲染写法与 {@code GunTracerRenderer} 一致。
 * <p>
 * 只有持有该职业的本人会被同步（{@code shouldSyncWith}），因此绳索只对本人可见。
 */
public final class ReconBotRopeRenderer {

    /** 黑色粗线；宽度 2.5，半透明通道开启，正常深度测试（会被墙挡住）。 */
    private static final RenderType ROPE = RenderType.create("noellesroles_recon_bot_rope",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES, 256, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                    .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(2.5D)))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .createCompositeState(false));

    private ReconBotRopeRenderer() {
    }

    public static void render(WorldRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null || context.consumers() == null) {
            return;
        }
        Player player = client.player;
        ReconBotRoleData data = ReconBotRoleData.of(player);
        if (data == null) {
            return;
        }

        // 用 partial tick 插值位置：player.position() 每 tick 才更新一次，直接用它会让绳子在 20Hz 上跳变
        float partialTick = client.getTimer().getGameTimeDeltaPartialTick(true);
        // 从胸口起画，并沿绳方向前移一点，避免线条从身体内部穿出
        Vec3 body = player.getPosition(partialTick).add(0.0D, 1.2D, 0.0D);

        Vec3 to;
        if (data.isFlyingClient()) {
            // 飞行中：钩爪尖端按进度从手部插值到目标点
            double progress = ((double) (client.level.getGameTime() - data.getFlightStartGameTime()) + partialTick)
                    / Math.max(1, data.getFlightTotalTicks());
            progress = Math.max(0.0D, Math.min(1.0D, progress));
            to = body.lerp(data.getAnchor(), progress);
        } else if (data.phaseIsBlock()) {
            to = data.getAnchor();
        } else {
            return;
        }
        Vec3 dir = to.subtract(body);
        if (dir.lengthSqr() < 1.0E-4D) {
            return;
        }
        Vec3 from = body.add(dir.normalize().scale(0.35D));

        Vec3 cameraPos = context.camera().getPosition();
        PoseStack matrices = context.matrixStack();
        VertexConsumer vertices = context.consumers().getBuffer(ROPE);

        matrices.pushPose();
        matrices.translate(from.x - cameraPos.x, from.y - cameraPos.y, from.z - cameraPos.z);
        PoseStack.Pose pose = matrices.last();
        Vec3 delta = to.subtract(from);
        Vec3 normal = delta.normalize();
        float nx = (float) normal.x;
        float ny = (float) normal.y;
        float nz = (float) normal.z;
        // 纯黑绳子
        vertices.addVertex(pose, 0.0F, 0.0F, 0.0F)
                .setColor(0.02F, 0.02F, 0.02F, 1.0F)
                .setNormal(pose, nx, ny, nz);
        vertices.addVertex(pose, (float) delta.x, (float) delta.y, (float) delta.z)
                .setColor(0.02F, 0.02F, 0.02F, 1.0F)
                .setNormal(pose, nx, ny, nz);
        matrices.popPose();
    }
}
