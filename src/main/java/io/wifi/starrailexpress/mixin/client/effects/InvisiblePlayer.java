package io.wifi.starrailexpress.mixin.client.effects;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 处理隐身渲染：
 * 原版 LivingEntityRenderer.getRenderType 已经正确处理隐身效果 ——
 * 隐身实体的身体模型透明，但 Feature Renderer（手持物品、盔甲等）仍然可见。
 * 此处不再拦截 shouldRender，避免跳过整个实体渲染管线导致手持物品不可见。
 */
@Mixin(EntityRenderer.class)
public class InvisiblePlayer {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void hideInvisiblePlayer(Entity entity, Frustum frustum, double x, double y, double z,
            CallbackInfoReturnable<Boolean> cir) {
        // 不再拦截：原版已正确处理隐身效果，身体透明但手持物品可见
    }
}