package org.agmas.noellesroles.content.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/**
 * 棺材实体渲染器：渲染棺材模型，朝向跟随实体 YRot。
 */
public class CoffinEntityRenderer extends EntityRenderer<CoffinEntity> {
    private static final ResourceLocation TEXTURE = Noellesroles.id("textures/entity/coffin.png");
    private final CoffinEntityModel model;

    public CoffinEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new CoffinEntityModel(context.bakeLayer(CoffinEntityModel.LAYER_LOCATION));
    }

    @Override
    public void render(CoffinEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - entityYaw));
        // 与原版 LivingEntityRenderer 管线保持一致：先翻转 Y（模型方块 y 轴向下约定），
        // 再平移枢轴（-1.501 避免与地面共面闪烁），否则棺材会被渲染到实体脚下 0.65 格埋进地里
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.501F, 0.0F);
        this.model.setupAnim(entity, 0.0F, 0.0F, entity.tickCount + partialTick, 0.0F, 0.0F);
        var vertexConsumer = buffer.getBuffer(this.model.renderType(TEXTURE));
        this.model.renderToBuffer(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(CoffinEntity entity) {
        return TEXTURE;
    }
}
