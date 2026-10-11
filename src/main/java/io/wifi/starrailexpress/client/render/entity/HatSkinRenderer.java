package io.wifi.starrailexpress.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.wifi.starrailexpress.SRE;
import net.fabricmc.fabric.api.client.model.loading.v1.FabricBakedModelManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 帽子皮肤头顶渲染器
 * <p>
 * 帽子皮肤没有物品载体，装备后直接以模型形式渲染在玩家头顶。
 * 优先使用皮肤规范路径下的烘焙模型（models/item/skins/hat/{name}.json），
 * 获取失败时回退到对应玩偶方块模型。
 * 传入的 PoseStack 应已位于玩家头部枢轴（由 HatFeatureRenderer 应用
 * {@code getHead().translateAndRotate} 后调用）。
 * <p>
 * 两条渲染链路：
 * <ul>
 * <li>模型自带 {@code display.head} 的饰品帽（如瑞科德钢盔）→ 原版头顶物品管线，
 * 作者在 Blockbench「Display → Head」里调好的朝向/位置/缩放原样生效；</li>
 * <li>继承玩偶方块模型的玩偶帽 → 原玩偶缩放链路（底面坐在头顶）。</li>
 * </ul>
 * </p>
 */
public final class HatSkinRenderer {

    /** JiaLe114514 玩偶帽皮肤名（与 SRESkinRegistry 注册名一致） */
    public static final String HAT_JIALE114514 = "hat_jiale114514";

    private HatSkinRenderer() {
    }

    /**
     * 根据帽子皮肤名渲染到头顶（支持所有玩偶帽皮肤）
     *
     * @param hatSkin      已装备的帽子皮肤名（hat_{base}）
     * @param poseStack    已对齐头部枢轴的位姿栈
     * @param bufferSource 渲染缓冲
     * @param light        光照值
     */
    public static void renderOnHead(String hatSkin, PoseStack poseStack, MultiBufferSource bufferSource, int light) {
        if (hatSkin == null || hatSkin.isEmpty()) {
            return;
        }
        renderPlushHat(hatSkin, poseStack, bufferSource, light);
    }

    /**
     * 帽子通用渲染：无对应玩偶方块的饰品帽走原版头顶物品管线（模型自带 display.head 生效），
     * 玩偶帽使用皮肤规范路径下的烘焙模型（建模照搬对应玩偶），缩放后坐在玩家头顶。
     */
    private static void renderPlushHat(String skinName, PoseStack poseStack, MultiBufferSource bufferSource, int light) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        BlockRenderDispatcher blockRenderer = mc.getBlockRenderer();
        Block plushBlock = resolvePlushBlock(skinName);
        // 渲染类型需要一个方块状态，优先用对应玩偶方块，缺失时用羊毛占位
        BlockState state = (plushBlock != null ? plushBlock : Blocks.WHITE_WOOL).defaultBlockState();
        BakedModel model = resolveHatModel(skinName, blockRenderer, plushBlock);
        if (model == null) {
            return;
        }

        if (hasHeadDisplayTransform(model)) {
            // 饰品帽（模型自带 display.head，如瑞科德钢盔）：按原版头顶物品渲染
            renderAccessoryHat(model, poseStack, bufferSource, state, light, blockRenderer);
            return;
        }

        poseStack.pushPose();
        // 与原版 CustomHeadLayer.translateToHead 相同的头顶基准变换；
        // 此处 y 负方向为上，头部枢轴在脖子处，头顶在枢轴上方 0.5 处，
        // 玩偶底面略低于头顶使其"坐实"在头上而非悬浮
        poseStack.translate(0.0F, -0.50F, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        // 方块坐标（y 朝上）→ 渲染层坐标；0.625 为原版头顶方块基准缩放
        // 玩偶帽：适当缩小让玩偶更小巧
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
     * 饰品帽（模型自带 display.head）渲染：完全等价于原版头顶物品渲染链路
     * <p>
     * CustomHeadLayer.translateToHead → ItemRenderer.render(..., ItemDisplayContext.HEAD, ...)，
     * 即 {@code translate(0,-0.25,0)} → {@code rotY(180)} → {@code scale(0.625,-0.625,-0.625)}
     * → 模型 display.head → {@code translate(-0.5,-0.5,-0.5)}。
     * 这样模型作者在 Blockbench「Display → Head」里调好的朝向/位置/缩放会原样生效，
     * 避免自行拼装变换导致的悬浮与朝向错误。
     * </p>
     */
    private static void renderAccessoryHat(BakedModel model, PoseStack poseStack, MultiBufferSource bufferSource,
            BlockState state, int light, BlockRenderDispatcher blockRenderer) {
        poseStack.pushPose();
        // 原版 CustomHeadLayer.translateToHead：头部枢轴在脖子处，向上为 y 负方向
        poseStack.translate(0.0F, -0.25F, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.scale(0.625F, -0.625F, -0.625F);
        // 模型自带的 display.head（旋转/平移/缩放，平移单位为 1/16 方块）
        model.getTransforms().getTransform(ItemDisplayContext.HEAD).apply(false, poseStack);
        // 与 ItemRenderer 相同：把方块中心 (0.5,0.5,0.5) 对齐到坐标原点
        poseStack.translate(-0.5D, -0.5D, -0.5D);

        blockRenderer.getModelRenderer().renderModel(poseStack.last(),
                bufferSource.getBuffer(ItemBlockRenderTypes.getRenderType(state, false)),
                state, model, 1F, 1F, 1F, light, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    /**
     * 模型是否自带 head 显示变换（display.head）。
     * <p>
     * 在 Blockbench「Display → Head」里调好的饰品帽模型会写出该变换，走原版头顶物品管线；
     * 玩偶帽模型是直接继承玩偶方块模型（models/block/*_plush）的，没有 display.head，
     * 继续走原来的玩偶缩放链路，保证已有帽子外观不变。
     * </p>
     */
    private static boolean hasHeadDisplayTransform(BakedModel model) {
        ItemTransform head = model.getTransforms().getTransform(ItemDisplayContext.HEAD);
        if (head == null || head == ItemTransform.NO_TRANSFORM) {
            return false;
        }
        // 未在 JSON 中声明 head 时会回落到 NO_TRANSFORM（即单位变换），此处再按数值兜底判断
        return head.translation.x() != 0.0F || head.translation.y() != 0.0F || head.translation.z() != 0.0F
                || head.rotation.x() != 0.0F || head.rotation.y() != 0.0F || head.rotation.z() != 0.0F
                || head.scale.x() != 1.0F || head.scale.y() != 1.0F || head.scale.z() != 1.0F;
    }

    /** 按皮肤名反查对应玩偶方块（hat_{base} → noellesroles:{base}_plush） */
    private static Block resolvePlushBlock(String skinName) {
        String base = skinName.startsWith("hat_") ? skinName.substring("hat_".length()) : skinName;
        Block block = BuiltInRegistries.BLOCK
                .get(ResourceLocation.fromNamespaceAndPath("noellesroles", base + "_plush"));
        return (block == null || block == Blocks.AIR) ? null : block;
    }

    /**
     * 解析帽子皮肤的烘焙模型：优先取皮肤规范路径下注册的模型，
     * 缺失时回退为对应玩偶方块模型。
     */
    private static BakedModel resolveHatModel(String skinName, BlockRenderDispatcher blockRenderer, Block plushBlock) {
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
        return plushBlock != null ? blockRenderer.getBlockModel(plushBlock.defaultBlockState()) : null;
    }
}
