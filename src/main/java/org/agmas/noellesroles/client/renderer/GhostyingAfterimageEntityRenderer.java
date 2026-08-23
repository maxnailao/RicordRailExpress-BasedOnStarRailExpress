package org.agmas.noellesroles.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.game.roles.killer.ghostying.GhostyingAfterimageEntity;

/**
 * 鬼影残影实体渲染器
 *
 * 使用固定的残影贴图渲染假人（参照 GhostPhantomEntityRenderer 实现）
 */
public class GhostyingAfterimageEntityRenderer
        extends LivingEntityRenderer<GhostyingAfterimageEntity, PlayerModel<GhostyingAfterimageEntity>> {

    /** 固定残影贴图 */
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "textures/entity/ghostying_afterimage.png");

    public GhostyingAfterimageEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
    }

    @Override
    protected void renderNameTag(GhostyingAfterimageEntity entity, Component component,
            PoseStack poseStack, MultiBufferSource multiBufferSource, int i, float f) {
        // 不渲染名称标签
    }

    @Override
    public void render(GhostyingAfterimageEntity entity, float yaw, float tickDelta, PoseStack matrices,
            MultiBufferSource vertexConsumers, int light) {
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(GhostyingAfterimageEntity entity) {
        return TEXTURE;
    }
}
