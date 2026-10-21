package io.wifi.starrailexpress.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.wifi.starrailexpress.client.model.XingguangHandModel;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;

/**
 * 在握刀手上渲染 {@link XingguangHandModel}。姿态跟随由调用方传入的手臂 ModelPart 提供，
 * 手指开合(curl)由 {@link XingguangPoseManager} 现有相位推导，帧率无关指数平滑。
 */
@Environment(EnvType.CLIENT)
public final class XingguangHandRenderer {

    private XingguangHandRenderer() {}

    /** 自研手部模型总开关：置 false 即完全恢复原状（仅原版手臂持刀），一键回退。 */
    public static boolean ENABLED = true;

    /** 调试探针：置 true 时把手部模型放大2倍，便于确认落点（不再依赖染红，因为 entityTranslucent 顶点色不生效）。 */
    public static boolean DEBUG_FIXED = false;
    private static boolean loggedOnce = false;

    // ===== Phase1 标定旋钮：进游戏按截图微调这几个常量即可 =====
    private static float ANCHOR_X = 0.0F;
    private static float ANCHOR_Y = 9.0F;   // 沿手臂向下到拳头
    private static float ANCHOR_Z = 0.5F;   // 前移贴刀柄

    // ===== renderArmWithItem 相机空间（已施加 applyKnife）的握刀落点标定旋钮 =====
    private static float GRIP_X = 0.0F;
    private static float GRIP_Y = 0.0F;
    private static float GRIP_Z = 0.0F;
    private static boolean gripLoggedOnce = false;

    // ===== renderItem 物品空间（原点≈刀身/拳头，屏幕可见）的落点与缩放旋钮 =====
    // 实测轴向（此空间被旋转过）：ITEM_X=垂直(-上/+下)、ITEM_Y=水平(左右)、ITEM_Z=深度(+后/-前)
    // 1 单位 ≈ 整屏大小，微调必须用 0.05~0.3 量级的小数
    private static float ITEM_X = -0.05F; // 垂直轴：高度已合适，保持
    private static float ITEM_Y = -0.21F; // 水平轴：左右已合适，保持
    private static float ITEM_Z = 0.1F;   // 深度轴：再往里一点（0.3 → 0.4）
    private static float ITEM_SCALE = 0.6F;
    private static boolean itemLoggedOnce = false;

    private static XingguangHandModel model;
    private static float curlShown = 0.5F;
    private static long lastNanos = 0L;

    public static void render(PoseStack poseStack, MultiBufferSource buffer, int light,
            AbstractClientPlayer player, ModelPart arm) {
        if (!ENABLED) {
            return;
        }
        if (!loggedOnce) {
            loggedOnce = true;
            io.wifi.starrailexpress.SRE.LOGGER.info("[XGHand] render() reached. mainArm={} firstPerson={}",
                    player.getMainArm(), Minecraft.getInstance().options.getCameraType().isFirstPerson());
        }
        if (model == null) {
            model = new XingguangHandModel(
                    Minecraft.getInstance().getEntityModels().bakeLayer(XingguangHandModel.LAYER));
        }
        float curl = updateCurl(player);
        model.fingers.xRot = -0.15F - curl * 1.15F;
        model.thumb.xRot = -0.10F - curl * 0.70F;
        float side = player.getMainArm() == HumanoidArm.LEFT ? -1.0F : 1.0F;
        model.thumb.zRot = side * (0.10F + curl * 0.35F);

        ResourceLocation skin = player.getSkin().texture();
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucent(skin));

        poseStack.pushPose();
        arm.translateAndRotate(poseStack);
        poseStack.translate(ANCHOR_X, ANCHOR_Y, ANCHOR_Z);
        if (DEBUG_FIXED) {
            poseStack.scale(3.0F, 3.0F, 3.0F);
        }
        model.hand.render(poseStack, consumer, light, OverlayTexture.NO_OVERLAY,
                DEBUG_FIXED ? 0xFFFF3030 : 0xFFFFFFFF);
        poseStack.popPose();
    }

    /** 在 renderArmWithItem 的相机空间（已施加 applyKnife）里绘制握刀手，和刀同一变换链，保证一定触发且贴住刀柄。 */
    public static void renderGrip(PoseStack poseStack, MultiBufferSource buffer, int light,
            AbstractClientPlayer player) {
        if (!ENABLED) {
            return;
        }
        if (!gripLoggedOnce) {
            gripLoggedOnce = true;
            io.wifi.starrailexpress.SRE.LOGGER.info("[XGHand] renderGrip called");
        }
        if (model == null) {
            model = new XingguangHandModel(
                    Minecraft.getInstance().getEntityModels().bakeLayer(XingguangHandModel.LAYER));
        }
        float curl = updateCurl(player);
        model.fingers.xRot = -0.15F - curl * 1.15F;
        model.thumb.xRot = -0.10F - curl * 0.70F;
        float side = player.getMainArm() == HumanoidArm.LEFT ? -1.0F : 1.0F;
        model.thumb.zRot = side * (0.10F + curl * 0.35F);

        ResourceLocation skin = player.getSkin().texture();
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucent(skin));
        poseStack.pushPose();
        poseStack.translate(GRIP_X, GRIP_Y, GRIP_Z);
        if (DEBUG_FIXED) {
            poseStack.scale(3.0F, 3.0F, 3.0F);
        }
        model.hand.render(poseStack, consumer, light, OverlayTexture.NO_OVERLAY,
                DEBUG_FIXED ? 0xFFFF3030 : 0xFFFFFFFF);
        poseStack.popPose();
    }

    /** 在 renderItem 的物品空间绘制手部：原点≈刀身/拳头，且用永不遮挡的渲染层绘制。 */
    public static void renderInItemSpace(PoseStack poseStack, MultiBufferSource buffer, int light,
            AbstractClientPlayer player) {
        if (!ENABLED) {
            return;
        }
        if (!itemLoggedOnce) {
            itemLoggedOnce = true;
            io.wifi.starrailexpress.SRE.LOGGER.info("[XGHand] renderInItemSpace called");
        }
        if (model == null) {
            model = new XingguangHandModel(
                    Minecraft.getInstance().getEntityModels().bakeLayer(XingguangHandModel.LAYER));
        }
        float curl = updateCurl(player);
        model.fingers.xRot = -0.15F - curl * 1.15F;
        model.thumb.xRot = -0.10F - curl * 0.70F;
        float side = player.getMainArm() == HumanoidArm.LEFT ? -1.0F : 1.0F;
        model.thumb.zRot = side * (0.10F + curl * 0.35F);

        ResourceLocation skin = player.getSkin().texture();
        // 最高显示优先级：先把刀等已提交的物品几何冲刷掉，再单独提交手并立即冲刷，
        // 保证手是本帧最后绘制者，配合 NO_DEPTH 永远盖在刀之上（即使往里嵌进刀柄也可见）。
        if (buffer instanceof MultiBufferSource.BufferSource flushItem) {
            flushItem.endBatch();
        }
        VertexConsumer consumer = buffer.getBuffer(handLayer(skin));
        poseStack.pushPose();
        poseStack.translate(ITEM_X, ITEM_Y, ITEM_Z);
        float s = ITEM_SCALE * (DEBUG_FIXED ? 2.0F : 1.0F);
        poseStack.scale(s, s, s);
        model.hand.render(poseStack, consumer, light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        poseStack.popPose();
        if (buffer instanceof MultiBufferSource.BufferSource flushHand) {
            flushHand.endBatch();
        }
    }

    /** 永不遮挡的手部渲染层：entity 顶点格式 + 皮肤贴图 + NO_DEPTH_TEST，手永远画在刀前面。 */
    private static RenderType handLayer(ResourceLocation skin) {
        return RenderType.create("xingguang_hand_nodepth",
                DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, true, false,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(skin, false, false))
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setLightmapState(RenderStateShard.LIGHTMAP)
                        .setOverlayState(RenderStateShard.OVERLAY)
                        .createCompositeState(true));
    }

    private static void xingguang$probe(PoseStack g, VertexConsumer consumer, int light,
            float x, float y, float z, float scale) {
        g.pushPose();
        g.translate(x, y, z);
        g.scale(scale, scale, scale);
        model.hand.render(g, consumer, light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        g.popPose();
    }

    private static float updateCurl(AbstractClientPlayer player) {
        boolean tight = XingguangPoseManager.isSlashing() || XingguangPoseManager.isFlipping()
                || XingguangPoseManager.isStabOut() || XingguangPoseManager.isStabReturn()
                || XingguangPoseManager.isPossessing() || XingguangPoseManager.itemSpinActive();
        float target = tight ? 1.0F : 0.5F;
        long now = System.nanoTime();
        float dt = lastNanos == 0L ? 0.0F : Math.min(0.1F, (now - lastNanos) * 1.0E-9F);
        lastNanos = now;
        float f = 1.0F - (float) Math.pow(0.5, dt / 0.08F);
        curlShown += (target - curlShown) * f;
        return curlShown;
    }
}