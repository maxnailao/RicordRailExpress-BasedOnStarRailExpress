package org.agmas.noellesroles.content.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.init.ModItems;

/**
 * RPG-7 火箭弹渲染器：复用火箭弹物品模型（{@code rpg7_ammo.json}），
 * 使抛射物沿飞行方向朝向（弹头朝前）显示。
 */
public class Rpg7RocketRenderer extends EntityRenderer<Rpg7RocketEntity> {

    private final ItemRenderer itemRenderer;
    private final ItemStack ammoStack;

    public Rpg7RocketRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
        this.ammoStack = ModItems.RPG7_AMMO.getDefaultInstance();
        this.shadowRadius = 0.2f;
    }

    @Override
    public void render(Rpg7RocketEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();

        float yaw = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
        float pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());

        // 缩放（火箭模型本身约 0.55 格长，放大到更醒目）
        poseStack.scale(1.5f, 1.5f, 1.5f);
        // 朝向：弹头（碰炸引信）在模型 -Z 方向。先绕 Y 转 -90° 把 -Z 转到 +X，
        // 再按箭矢渲染器的方式（yaw-90 / Z 轴 pitch）对齐飞行方向，使弹头朝前。
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw - 90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(pitch));
        poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
        // 居中：renderStatic 内部会再 translate(-0.5,-0.5,-0.5)，这里按模型中心 (8.5,3.1,-0.4)/16
        // 补偿，即 (0.5 - cx, 0.5 - cy, 0.5 - cz)，使火箭中心正好落在实体位置上。
        poseStack.translate(-0.03125f, 0.30625f, 0.525f);

        this.itemRenderer.renderStatic(ammoStack, ItemDisplayContext.FIXED, packedLight,
                OverlayTexture.NO_OVERLAY, poseStack, bufferSource, entity.level(), entity.getId());

        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @SuppressWarnings("deprecation")
    @Override
    public ResourceLocation getTextureLocation(Rpg7RocketEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
