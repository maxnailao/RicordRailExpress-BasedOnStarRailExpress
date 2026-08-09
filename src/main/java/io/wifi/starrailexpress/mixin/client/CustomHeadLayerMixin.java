package io.wifi.starrailexpress.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.wifi.starrailexpress.client.render.entity.EmojiHelmetRenderer;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.PlayerHatSync;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Set;

@Mixin(net.minecraft.client.renderer.entity.layers.CustomHeadLayer.class)
public abstract class CustomHeadLayerMixin<T extends LivingEntity, M extends EntityModel<T> & HeadedModel>
        extends RenderLayer<T, M> {
    public CustomHeadLayerMixin(RenderLayerParent<T, M> renderLayerParent) {
        super(renderLayerParent);
    }

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V", at = @At("HEAD"), cancellable = true)
    private void sre$renderEmojiHelmetOnFace(PoseStack poseStack, MultiBufferSource bufferSource, int light,
            T livingEntity, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
            float netHeadYaw, float headPitch, CallbackInfo ci) {
        ItemStack stack = livingEntity.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
        if (!stack.is(TMMItems.EMOJI_HELMET)) {
            return;
        }

        ci.cancel();
        if (!(livingEntity instanceof Player player) || player.isInvisible()) {
            return;
        }

        poseStack.pushPose();
        this.getParentModel().getHead().translateAndRotate(poseStack);
        EmojiHelmetRenderer.renderOnFace(stack, poseStack, bufferSource);
        poseStack.popPose();
    }

    /** 调试用：记录已打印过帽子日志的玩家，避免刷屏 */
    @Unique
    private static final Set<String> sre$hatLogged = new HashSet<>();

    /**
     * 诊断用：确认原版 CustomHeadLayer 是否被调用（实际帽子渲染已移至 HatFeatureRenderer，
     * 由 PlayerRendererHatLayerMixin 注册，不依赖本层链路）
     */
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V", at = @At("TAIL"))
    private void sre$renderHatSkin(PoseStack poseStack, MultiBufferSource bufferSource, int light,
            T livingEntity, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
            float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (livingEntity instanceof Player loggedPlayer) {
            String currentHat = PlayerHatSync.getHat(loggedPlayer);
            String key = "seen:" + loggedPlayer.getUUID() + ":" + currentHat;
            if (sre$hatLogged.size() < 256 && sre$hatLogged.add(key)) {
                // 诊断：每个玩家每种帽子值只记录一次，捕捉数值变化时刻
                io.wifi.starrailexpress.SRE.LOGGER.info("[HatSync] CustomHeadLayer render reached for {}, hat='{}', invisible={}",
                        loggedPlayer.getName().getString(), currentHat, loggedPlayer.isInvisible());
            }
        }
    }
}
