package io.wifi.starrailexpress.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.wifi.starrailexpress.SRE;
import net.fabricmc.fabric.api.client.model.loading.v1.FabricBakedModelManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.level.block.state.BlockState;
import org.agmas.noellesroles.init.SREFumoBlocks;

/**
 * 帽子皮肤头顶渲染器
 * <p>
 * 帽子皮肤没有物品载体，装备后直接以模型形式渲染在玩家头顶。
 * 优先使用皮肤规范路径下的烘焙模型（models/item/skins/hat/{name}.json），
 * 获取失败时回退到复用的玩偶方块模型。
 * 传入的 PoseStack 应已位于玩家头部枢轴（由 CustomHeadLayerMixin 应用
 * {@code getHead().translateAndRotate} 后调用）。
 * </p>
 */
public final class HatSkinRenderer {

    /** JiaLe114514 玩偶帽皮肤名（与 SRESkinRegistry 注册名一致） */
    public static final String HAT_JIALE114514 = "hat_jiale114514";

    private HatSkinRenderer() {
    }

    /**
     * 根据帽子皮肤名渲染到头顶
     *
     * @param hatSkin      已装备的帽子皮肤名
     * @param poseStack    已对齐头部枢轴的位姿栈
     * @param bufferSource 渲染缓冲
     * @param light        光照值
     */
    public static void renderOnHead(String hatSkin, PoseStack poseStack, MultiBufferSource bufferSource, int light) {
        if (HAT_JIALE114514.equals(hatSkin)) {
            renderJiale114514Plush(poseStack, bufferSource, light);
        }
    }

    /**
     * JiaLe114514 玩偶帽：使用复制后的皮肤模型（建模复用 JiaLe114514 玩偶），
     * 缩小后坐在玩家头顶，相当于一个玩偶帽子。
     */
    private static void renderJiale114514Plush(PoseStack poseStack, MultiBufferSource bufferSource, int light) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        BlockRenderDispatcher blockRenderer = mc.getBlockRenderer();
        BlockState state = SREFumoBlocks.JIALE114514_PLUSH.defaultBlockState();
        BakedModel model = resolveHatModel(HAT_JIALE114514, blockRenderer, state);
        if (model == null) {
            return;
        }

        poseStack.pushPose();
        // 与原版 CustomHeadLayer.translateToHead 相同的头顶基准变换；
        // 此处 y 负方向为上，头部枢轴在脖子处，头顶在枢轴上方 0.5 处，
        // 再额外抬高少量让玩偶坐在头顶而非嵌入头内
        poseStack.translate(0.0F, -0.55F, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        // 方块坐标（y 朝上）→ 渲染层坐标；0.625 为原版头顶方块基准缩放，再适当放大让玩偶帽更醒目
        float scale = 0.625F * 0.7F;
        poseStack.scale(scale, -scale, -scale);
        // 方块底面中心对齐头部枢轴
        poseStack.translate(-0.5D, 0.0D, -0.5D);

        blockRenderer.getModelRenderer().renderModel(poseStack.last(),
                bufferSource.getBuffer(ItemBlockRenderTypes.getRenderType(state, false)),
                state, model, 1F, 1F, 1F, light, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    /**
     * 解析帽子皮肤的烘焙模型：优先取皮肤规范路径下注册的模型，
     * 缺失时回退为复用的玩偶方块模型。
     */
    private static BakedModel resolveHatModel(String skinName, BlockRenderDispatcher blockRenderer, BlockState state) {
        try {
            var modelManager = Minecraft.getInstance().getModelManager();
            BakedModel skinModel = ((FabricBakedModelManager) (Object) modelManager)
                    .getModel(SRE.id("item/skins/hat/" + skinName));
            if (skinModel != null && skinModel != modelManager.getMissingModel()) {
                return skinModel;
            }
        } catch (Exception e) {
            SRE.LOGGER.warn("[HatSync] 帽子皮肤模型解析失败，回退到玩偶方块模型: {}", e.toString());
        }
        return blockRenderer.getBlockModel(state);
    }
}
