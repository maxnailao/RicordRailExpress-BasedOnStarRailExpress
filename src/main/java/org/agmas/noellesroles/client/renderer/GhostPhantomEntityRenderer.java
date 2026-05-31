package org.agmas.noellesroles.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.content.entity.GhostPhantomEntity;

import java.util.UUID;

/**
 * 鬼魅幻影实体渲染器
 * 
 * 使用玩家皮肤渲染幻影，完全参照PuppeteerBodyEntityRenderer实现
 */
public class GhostPhantomEntityRenderer extends LivingEntityRenderer<GhostPhantomEntity, PlayerModel<GhostPhantomEntity>> {

    public static PlayerModel<GhostPhantomEntity> modelNormal;

    public GhostPhantomEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true), 0.5f);
        modelNormal = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false);
    }

    @Override
    protected void renderNameTag(GhostPhantomEntity entity, Component component, PoseStack poseStack, MultiBufferSource multiBufferSource, int i, float f) {
        // 不渲染名称标签
    }

    @Override
    public void render(GhostPhantomEntity entity, float yaw, float tickDelta, PoseStack matrices,
                       MultiBufferSource vertexConsumers, int light) {
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(GhostPhantomEntity entity) {
        // 首先尝试通过 ownerUuid 从玩家列表获取皮肤
        UUID ownerUuid = entity.getOwnerUuid().orElse(null);

        if (ownerUuid != null) {
            // 通过 UUID 从玩家列表获取皮肤
            PlayerInfo entry = SREClient.PLAYER_ENTRIES_CACHE.get(ownerUuid);
            if (entry != null) {
                if (entry.getSkin().model().equals(PlayerSkin.Model.WIDE)) {
                    model = modelNormal;
                }
                return entry.getSkin().texture();
            }
            // 如果玩家不在列表中（可能离线），使用基于 UUID 的默认皮肤
            return DefaultPlayerSkin.get(ownerUuid).texture();
        }
        // 最后的回退：使用固定的默认皮肤（Steve）
        return DefaultPlayerSkin.get(UUID.fromString("7833c811-436e-40c4-868a-ffb1073f48a2")).texture();
    }
}
