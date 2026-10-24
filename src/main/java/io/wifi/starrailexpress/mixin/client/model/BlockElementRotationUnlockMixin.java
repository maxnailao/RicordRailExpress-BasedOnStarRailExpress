package io.wifi.starrailexpress.mixin.client.model;

import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 解除原版 elements[].rotation 角度校验（1.21.1 的 BlockElement$Deserializer#getAngle
 * 会对 |angle| > 45 或非 22.5/45 倍数的角度抛 JsonParseException）。
 * 在 HEAD 直接返回 JSON 里的原始角度值，跳过校验；烘焙按单轴任意角度正常应用。
 */
@Mixin(targets = "net.minecraft.client.renderer.block.model.BlockElement$Deserializer")
public class BlockElementRotationUnlockMixin {

    @Inject(method = "getAngle", at = @At("HEAD"), cancellable = true)
    private void happytrain$allowAnyAngle(JsonObject json, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(GsonHelper.getAsFloat(json, "angle"));
    }
}