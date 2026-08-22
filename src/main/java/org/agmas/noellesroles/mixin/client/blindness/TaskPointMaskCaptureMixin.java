package org.agmas.noellesroles.mixin.client.blindness;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.core.BlockPos;
import org.agmas.noellesroles.client.TaskBlockOverlayRenderer;
import org.agmas.noellesroles.client.blindness.TaskPointMaskBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.Color;

/**
 * 捕获任务点透视本帧实际绘制的方块位置（不改动原渲染类逻辑），
 * 供失明症遮罩扩展桥接重绘，实现失明效果下的任务点穿墙透视。
 */
@Mixin(TaskBlockOverlayRenderer.class)
public class TaskPointMaskCaptureMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private static void noellesroles$beginTaskPointCapture(WorldRenderContext renderContext, CallbackInfo ci) {
        TaskPointMaskBridge.beginFrame();
    }

    @Inject(method = "renderBlockOverlay", at = @At("HEAD"))
    private static void noellesroles$captureTaskPoint(WorldRenderContext context, BlockPos blockPos, Color color,
            float alpha, boolean colorize, float textScale, CallbackInfo ci) {
        TaskPointMaskBridge.recordFramePosition(blockPos);
    }
}
