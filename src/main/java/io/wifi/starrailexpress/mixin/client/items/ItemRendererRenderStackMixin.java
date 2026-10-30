package io.wifi.starrailexpress.mixin.client.items;

import com.mojang.blaze3d.vertex.PoseStack;
import io.wifi.starrailexpress.client.model.GeneralModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在 {@link ItemRenderer#render} 入口记录“当前正在渲染的 stack”，供 {@link GeneralModel#getTransforms()}
 * 按该 stack 自身的皮肤解析手持变换（getTransforms 无 stack 入参）。HEAD 写入、TAIL 清除，
 * 使他人手上的武器使用其自己皮肤的 third_person 变换，消除“本地玩家皮肤变换污染所有人”的 3D→2D 假象。
 */
@Mixin(ItemRenderer.class)
public class ItemRendererRenderStackMixin {

    @Inject(method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;"
            + "ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II"
            + "Lnet/minecraft/client/resources/model/BakedModel;)V", at = @At("HEAD"))
    private void sre$captureStack(@Nullable ItemStack stack, ItemDisplayContext context, boolean leftHand,
            PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay, BakedModel model,
            CallbackInfo ci) {
        GeneralModel.setCurrentRenderStack(stack);
    }

    @Inject(method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;"
            + "ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II"
            + "Lnet/minecraft/client/resources/model/BakedModel;)V", at = @At("TAIL"))
    private void sre$clearStack(@Nullable ItemStack stack, ItemDisplayContext context, boolean leftHand,
            PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay, BakedModel model,
            CallbackInfo ci) {
        GeneralModel.setCurrentRenderStack(null);
    }
}