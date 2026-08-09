package io.wifi.starrailexpress.client.render.entity;

import io.wifi.starrailexpress.cca.SREPlayerSkinsComponent;
import io.wifi.starrailexpress.util.ItemSkinManager;
import io.wifi.starrailexpress.util.PlayerHatSync;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.vertex.PoseStack;
import org.jetbrains.annotations.Nullable;

/**
 * 帽子皮肤渲染层
 * <p>
 * 通过 PlayerRendererHatLayerMixin 在 PlayerRenderer 构造时 addLayer 注册
 * （与 ratatouille 的 PlushOnHeadFeatureRenderer 相同的注册模式）。
 * 独立于原版 CustomHeadLayer，避免其渲染链路被其他机制干扰。
 * </p>
 */
public class HatFeatureRenderer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    /** 诊断用：每个玩家每种帽子只记录一次渲染 */
    private static final java.util.Set<String> RENDER_LOGGED = new java.util.HashSet<>();

    public HatFeatureRenderer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int light,
            AbstractClientPlayer player, float limbSwing, float limbSwingAmount, float partialTick,
            float ageInTicks, float netHeadYaw, float headPitch) {
        if (player.isInvisible()) {
            return;
        }
        String hat = PlayerHatSync.getHat(player);
        if (PlayerHatSync.DEFAULT.equals(hat) && player == Minecraft.getInstance().player) {
            // 回退：实体数据未到位时，本地玩家改读 CCA 组件（仅自身客户端有数据）
            hat = SREPlayerSkinsComponent.KEY.get(player)
                    .getEquippedSkin(ItemSkinManager.SkinTypes.HAT);
        }
        if (hat == null || PlayerHatSync.DEFAULT.equals(hat)) {
            return;
        }

        // 诊断：每个玩家每种帽子只记录一次实际渲染
        String logKey = player.getUUID() + ":" + hat;
        if (RENDER_LOGGED.size() < 256 && RENDER_LOGGED.add(logKey)) {
            io.wifi.starrailexpress.SRE.LOGGER.info("[HatSync] HatFeatureRenderer rendering '{}' for {}",
                    hat, player.getName().getString());
        }

        poseStack.pushPose();
        this.getParentModel().getHead().translateAndRotate(poseStack);
        HatSkinRenderer.renderOnHead(hat, poseStack, bufferSource, light);
        poseStack.popPose();
    }

    @Nullable
    @Override
    protected ResourceLocation getTextureLocation(AbstractClientPlayer entity) {
        // 不使用固定贴图（模型自带贴图），返回 null 即可
        return null;
    }
}
