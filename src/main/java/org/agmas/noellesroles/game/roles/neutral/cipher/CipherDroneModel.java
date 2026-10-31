package org.agmas.noellesroles.game.roles.neutral.cipher;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import org.agmas.noellesroles.Noellesroles;

public class CipherDroneModel extends EntityModel<CipherDroneEntity> {
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(Noellesroles.id("cipher_drone"), "main");

    private final ModelPart body;
    private final ModelPart rotorNE;
    private final ModelPart rotorNW;
    private final ModelPart rotorSE;
    private final ModelPart rotorSW;

    public CipherDroneModel(ModelPart root) {
        this.body = root.getChild("body");
        this.rotorNE = body.getChild("rotorNE");
        this.rotorNW = body.getChild("rotorNW");
        this.rotorSE = body.getChild("rotorSE");
        this.rotorSW = body.getChild("rotorSW");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-4.0F, -2.0F, -4.0F, 8.0F, 3.0F, 8.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 22.0F, 0.0F));

        body.addOrReplaceChild("rotorNE", CubeListBuilder.create().texOffs(0, 11)
                .addBox(-1.0F, -0.5F, -1.0F, 2.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offset(3.5F, -1.5F, -3.5F));
        body.addOrReplaceChild("rotorNW", CubeListBuilder.create().texOffs(0, 11)
                .addBox(-1.0F, -0.5F, -1.0F, 2.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-3.5F, -1.5F, -3.5F));
        body.addOrReplaceChild("rotorSE", CubeListBuilder.create().texOffs(0, 11)
                .addBox(-1.0F, -0.5F, -1.0F, 2.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offset(3.5F, -1.5F, 3.5F));
        body.addOrReplaceChild("rotorSW", CubeListBuilder.create().texOffs(0, 11)
                .addBox(-1.0F, -0.5F, -1.0F, 2.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-3.5F, -1.5F, 3.5F));

        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override
    public void setupAnim(CipherDroneEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        // 桨叶高速旋转
        float spin = ageInTicks * 2.0F;
        rotorNE.yRot = spin;
        rotorNW.yRot = -spin;
        rotorSE.yRot = spin;
        rotorSW.yRot = -spin;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer,
                               int light, int overlay, int color) {
        body.render(poseStack, buffer, light, overlay, color);
    }
}