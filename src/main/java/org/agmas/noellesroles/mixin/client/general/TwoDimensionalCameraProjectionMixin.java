package org.agmas.noellesroles.mixin.client.general;

import net.minecraft.client.renderer.GameRenderer;
import org.agmas.noellesroles.client.TwoDimensionalCameraClientHandle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(GameRenderer.class)
public class TwoDimensionalCameraProjectionMixin {
    @ModifyConstant(method = "getProjectionMatrix", constant = @Constant(floatValue = 0.05F))
    private float noellesroles$clipForegroundBlocks(float original) {
        if (TwoDimensionalCameraClientHandle.isActive()) {
            return TwoDimensionalCameraClientHandle.foregroundClipDistance();
        }
        return original;
    }
}
