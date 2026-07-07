package org.agmas.noellesroles.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.content.entity.RuikePortalEntity;
import org.joml.Matrix4f;

/**
 * 时空旅者传送门渲染器
 *
 * 渲染类似原版地狱传送门的半透明紫色面片
 */
public class RuikePortalRenderer extends EntityRenderer<RuikePortalEntity> {

    private static final ResourceLocation PORTAL_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/block/nether_portal.png");

    public RuikePortalRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(RuikePortalEntity entity) {
        return PORTAL_TEXTURE;
    }

    @Override
    public void render(RuikePortalEntity entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        super.render(entity, entityYaw, partialTicks, poseStack, bufferSource, packedLight);

        poseStack.pushPose();

        // 传送到实体位置上方居中
        poseStack.translate(0, 0, 0);

        // 根据实体朝向旋转面片（默认面向Z轴）
        float yRot = entity.getYRot();
        poseStack.mulPose(Axis.YP.rotationDegrees(-yRot));

        // 面片尺寸：宽0.75格，高1.25格
        float halfWidth = 0.375F;
        float height = 1.25F;

        // 使用半透明实体渲染
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.entityTranslucent(PORTAL_TEXTURE));

        // 动画偏移（基于时间）
        float time = entity.tickCount + partialTicks;
        float animOffset = Mth.sin(time * 0.05F) * 0.1F;

        // 前面
        drawPortalQuad(vertexConsumer, poseStack, halfWidth, height, animOffset);

        // 背面（翻转）
        poseStack.mulPose(Axis.YP.rotationDegrees(180));
        drawPortalQuad(vertexConsumer, poseStack, halfWidth, height, animOffset);

        poseStack.popPose();
    }

    private void drawPortalQuad(VertexConsumer consumer, PoseStack poseStack,
                                float halfWidth, float height, float animOffset) {
        Matrix4f matrix = poseStack.last().pose();

        // 颜色：紫偏粉（带透明度）
        float r = 0.6F + animOffset;
        float g = 0.2F;
        float b = 0.8F - animOffset;
        float a = 0.7F;

        // 纹理坐标
        float u0 = 0.0F;
        float u1 = 1.0F;
        float v0 = 0.0F;
        float v1 = 1.0F;

        // 左上
        consumer.addVertex(matrix, -halfWidth, height, 0.0F)
                .setColor(r, g, b, a)
                .setUv(u0, v0)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(15728880)
                .setNormal(poseStack.last(), 0, 0, -1);

        // 右上
        consumer.addVertex(matrix, halfWidth, height, 0.0F)
                .setColor(r, g, b, a)
                .setUv(u1, v0)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(15728880)
                .setNormal(poseStack.last(), 0, 0, -1);

        // 右下
        consumer.addVertex(matrix, halfWidth, 0.0F, 0.0F)
                .setColor(r, g, b, a)
                .setUv(u1, v1)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(15728880)
                .setNormal(poseStack.last(), 0, 0, -1);

        // 左下
        consumer.addVertex(matrix, -halfWidth, 0.0F, 0.0F)
                .setColor(r, g, b, a)
                .setUv(u0, v1)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(15728880)
                .setNormal(poseStack.last(), 0, 0, -1);
    }
}
