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
import net.minecraft.world.entity.player.Player;
import com.mojang.blaze3d.vertex.PoseStack;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.client.ClientSkincrawlerState;
import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.game.roles.killer.morphling.MorphlingPlayerComponent;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

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

    /** 饰品预览覆盖（仅作用于本地玩家）：null=不干预；空串=强制不戴帽（去掉玩偶自带帽）；其他=强制显示该帽子皮肤名 */
    @Nullable
    private static String previewHatOverride = null;

    /** 供饰品界面调用：覆盖本地玩家头顶帽子渲染，实现"帽子戴在头上"的本地预览 */
    public static void setPreviewHat(@Nullable String hatSkin) {
        previewHatOverride = hatSkin;
    }

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
        Player hatOwner = resolveHatOwner(player);
        String hat;
        if (previewHatOverride != null && player == Minecraft.getInstance().player) {
            // 饰品预览：强制覆盖本地玩家头顶帽子（空串=不戴帽，去除玩偶自带帽）
            hat = previewHatOverride;
        } else {
            hat = PlayerHatSync.getHat(hatOwner);
            if (PlayerHatSync.DEFAULT.equals(hat) && player == Minecraft.getInstance().player) {
                // 回退：实体数据未到位时，本地玩家改读 CCA 组件（仅自身客户端有数据）
                hat = SREPlayerSkinsComponent.KEY.get(player)
                        .getEquippedSkin(ItemSkinManager.SkinTypes.HAT);
            }
        }
        if (hat == null || hat.isEmpty() || PlayerHatSync.DEFAULT.equals(hat)) {
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

    /** Resolve the player whose skin is currently shown by the target's appearance modifiers. */
    private static Player resolveHatOwner(AbstractClientPlayer player) {
        UUID ownerId = null;
        var worldModifiers = WorldModifierComponent.KEY.get(player.level());
        if (worldModifiers != null && worldModifiers.isModifier(player, SEModifiers.JEB_)) {
            ownerId = NoellesrolesClient.JEB_SHUFFLED_PLAYER_ENTRIES_CACHE.get(player.getUUID());
        }
        if (ownerId == null) {
            ownerId = ClientSkincrawlerState.stolenSkinFor(player.getUUID());
        }
        if (ownerId == null) {
            MorphlingPlayerComponent morphling = MorphlingPlayerComponent.KEY.get(player);
            if (morphling != null && morphling.getMorphTicks() > 0) {
                ownerId = morphling.disguise;
            }
        }
        if (ownerId == null || ownerId.equals(player.getUUID())) {
            return player;
        }
        Player owner = player.level().getPlayerByUUID(ownerId);
        return owner == null ? player : owner;
    }

    @Nullable
    @Override
    protected ResourceLocation getTextureLocation(AbstractClientPlayer entity) {
        // 不使用固定贴图（模型自带贴图），返回 null 即可
        return null;
    }
}
