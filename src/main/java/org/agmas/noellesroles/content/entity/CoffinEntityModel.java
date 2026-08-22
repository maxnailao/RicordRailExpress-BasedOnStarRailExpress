package org.agmas.noellesroles.content.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import org.agmas.noellesroles.Noellesroles;

/**
 * 棺材实体模型（由 Blockbench 导出转换而来，贴图 256x256）
 */
public class CoffinEntityModel extends EntityModel<CoffinEntity> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(Noellesroles.id("coffin"),
            "main");
    private final ModelPart bb_main;

    public CoffinEntityModel(ModelPart root) {
        this.bb_main = root.getChild("bb_main");
    }

    @SuppressWarnings("unused")
    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition bb_main = partdefinition.addOrReplaceChild("bb_main",
                CubeListBuilder.create().texOffs(82, 6).addBox(-7.0F, -10.0F, -4.0F, 14.0F, 10.0F, 3.0F,
                        new CubeDeformation(0.0F))
                        .texOffs(0, 0).addBox(-5.0F, -10.0F, -10.0F, 10.0F, 10.0F, 31.0F,
                                new CubeDeformation(0.0F))
                        .texOffs(0, 41).addBox(-5.0F, -10.4F, -10.0F, 10.0F, 1.0F, 31.0F,
                                new CubeDeformation(0.0F))
                        .texOffs(82, 19).addBox(-7.0F, -10.35F, -4.0F, 14.0F, 1.0F, 3.0F,
                                new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 24.0F, 0.0F));

        PartDefinition cube_r1 = bb_main.addOrReplaceChild("cube_r1",
                CubeListBuilder.create().texOffs(82, 48).addBox(-8.1F, -10.35F, 5.7F, 7.0F, 1.0F, 2.0F,
                        new CubeDeformation(0.0F))
                        .texOffs(82, 34).addBox(-8.1F, -10.0F, 6.7F, 7.0F, 10.0F, 1.0F,
                                new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, -1.8788F, 0.0F));

        PartDefinition cube_r2 = bb_main.addOrReplaceChild("cube_r2",
                CubeListBuilder.create().texOffs(82, 45).addBox(-8.1F, -10.35F, -7.7F, 7.0F, 1.0F, 2.0F,
                        new CubeDeformation(0.0F))
                        .texOffs(82, 23).addBox(-8.1F, -10.0F, -7.7F, 7.0F, 10.0F, 1.0F,
                                new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, -1.268F, 0.0F));

        PartDefinition cube_r3 = bb_main.addOrReplaceChild("cube_r3",
                CubeListBuilder.create().texOffs(82, 3).addBox(-20.0F, -10.35F, -6.75F, 23.0F, 1.0F, 2.0F,
                        new CubeDeformation(0.0F))
                        .texOffs(48, 73).addBox(-20.0F, -10.0F, -6.75F, 23.0F, 10.0F, 1.0F,
                                new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 1.6624F, 0.0F));

        PartDefinition cube_r4 = bb_main.addOrReplaceChild("cube_r4",
                CubeListBuilder.create().texOffs(82, 0).addBox(-20.0F, -10.35F, 4.75F, 23.0F, 1.0F, 2.0F,
                        new CubeDeformation(0.0F))
                        .texOffs(0, 73).addBox(-20.0F, -10.0F, 5.75F, 23.0F, 10.0F, 1.0F,
                                new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 1.4792F, 0.0F));

        return LayerDefinition.create(meshdefinition, 256, 256);
    }

    @Override
    public void setupAnim(CoffinEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch) {
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight,
            int packedOverlay, int color) {
        bb_main.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
    }
}
