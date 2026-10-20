package io.wifi.starrailexpress.client.model;

import io.wifi.starrailexpress.SRE;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * 星光（传承）第一人称自定义手部模型（M3：复用原版手臂，只在拳头处叠加手掌/手指/拇指）。
 * 纯代码建模（CubeListBuilder），UV 落在玩家皮肤"手臂区"，故直接采样玩家皮肤贴图着色。
 * 仅由 {@link io.wifi.starrailexpress.client.XingguangHandRenderer} 在握刀手上渲染。
 */
@Environment(EnvType.CLIENT)
public final class XingguangHandModel {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(SRE.id("xingguang_hand"), "main");

    public final ModelPart hand;      // 根（含手掌 cube），render 时递归画出手指/拇指
    public final ModelPart fingers;   // 四指合块，绕握把前方卷曲
    public final ModelPart thumb;     // 拇指

    public XingguangHandModel(ModelPart root) {
        this.hand = root.getChild("hand");
        this.fingers = this.hand.getChild("fingers");
        this.thumb = this.hand.getChild("thumb");
    }

    public static LayerDefinition getTexturedModelData() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition hand = root.addOrReplaceChild("hand",
                CubeListBuilder.create().texOffs(40, 20).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 4.0F, 4.0F),
                PartPose.offset(0.0F, 0.0F, 0.0F));
        hand.addOrReplaceChild("fingers",
                CubeListBuilder.create().texOffs(44, 24).addBox(-2.0F, -1.0F, -1.0F, 4.0F, 3.0F, 2.0F),
                PartPose.offset(0.0F, 3.0F, 1.5F));
        hand.addOrReplaceChild("thumb",
                CubeListBuilder.create().texOffs(40, 16).mirror().addBox(0.0F, -1.0F, -1.0F, 2.0F, 3.0F, 2.0F),
                PartPose.offset(1.8F, 2.5F, 1.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }
}
