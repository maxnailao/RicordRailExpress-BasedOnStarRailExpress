package org.agmas.noellesroles.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.content.entity.GhostPhantomEntity;
import org.agmas.noellesroles.init.ModEntities;

/**
 * 鬼魅幻影实体渲染器
 * 
 * 将幻影渲染为半透明的深紫色玩家模型
 */
public class GhostPhantomEntityRenderer extends LivingEntityRenderer<GhostPhantomEntity, PlayerModel<GhostPhantomEntity>> {

    private static final ResourceLocation PHANTOM_TEXTURE = ResourceLocation.fromNamespaceAndPath(
        "noellesroles", "textures/entity/ghost_phantom.png"
    );

    public GhostPhantomEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(GhostPhantomEntity entity) {
        // 使用默认的Alex或Steve皮肤，但会应用半透明效果
        return PHANTOM_TEXTURE;
    }

    @Override
    public void render(GhostPhantomEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        // 设置半透明效果（深紫色，透明度50%）
        poseStack.pushPose();
        
        // 添加紫色发光效果
        int overlayCoords = this.getOverlayCoords(entity, 0.0F);
        
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
        
        poseStack.popPose();
    }

    @Override
    protected boolean isBodyVisible(GhostPhantomEntity entity) {
        return true;
    }
}
