package io.wifi.starrailexpress.client.render.block_entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.wifi.starrailexpress.content.block.ZiplineBlock;
import io.wifi.starrailexpress.content.block_entity.ZiplineBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.Set;

public class ZiplineBlockEntityRenderer implements BlockEntityRenderer<ZiplineBlockEntity> {

    private static final double MAX_RENDER_DISTANCE_SQ = 32.0 * 32.0;

    public ZiplineBlockEntityRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(ZiplineBlockEntity entity, float tickDelta, PoseStack matrices,
                       MultiBufferSource vertexConsumers, int light, int overlay) {
        Set<BlockPos> connections = entity.getConnectedPositions();
        if (connections.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        // 距离检查
        double distSq = player.distanceToSqr(
                entity.getBlockPos().getX() + 0.5,
                entity.getBlockPos().getY() + 0.5,
                entity.getBlockPos().getZ() + 0.5);
        if (distSq > MAX_RENDER_DISTANCE_SQ) return;

        VertexConsumer consumer = vertexConsumers.getBuffer(RenderType.LINES);

        BlockPos fromPos = entity.getBlockPos();

        for (BlockPos toPos : connections) {
            if (fromPos.asLong() > toPos.asLong()) {
                continue;
            }
            double distance = Vec3.atCenterOf(fromPos).distanceTo(Vec3.atCenterOf(toPos));
            int segments = Math.max(8, (int) (distance * 3));
            renderRope(matrices, consumer, fromPos, toPos, segments);
        }
    }

    private void renderRope(PoseStack matrices, VertexConsumer consumer, BlockPos fromPos, BlockPos toPos,
                            int segments) {
        Matrix4f pose = matrices.last().pose();

        Vec3 prevPoint = null;
        for (int i = 0; i <= segments; i++) {
            float t = (float) i / segments;
            Vec3 currentPoint = ZiplineBlock.ropePoint(fromPos, toPos, t);

            if (prevPoint != null) {
                float dx = (float) (prevPoint.x - fromPos.getX());
                float dy = (float) (prevPoint.y - fromPos.getY());
                float dz = (float) (prevPoint.z - fromPos.getZ());
                float dx2 = (float) (currentPoint.x - fromPos.getX());
                float dy2 = (float) (currentPoint.y - fromPos.getY());
                float dz2 = (float) (currentPoint.z - fromPos.getZ());

                consumer.addVertex(pose, dx, dy, dz).setColor(176, 137, 81, 255).setNormal(0, 1, 0);
                consumer.addVertex(pose, dx2, dy2, dz2).setColor(176, 137, 81, 255).setNormal(0, 1, 0);
            }
            prevPoint = currentPoint;
        }
    }

    @Override
    public boolean shouldRenderOffScreen(ZiplineBlockEntity blockEntity) {
        return true;
    }
}
