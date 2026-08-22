package io.wifi.starrailexpress.mixin.client.scenery;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.util.CubicSampler;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.init.ModEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.awt.*;

@Mixin(FogRenderer.class)
public class BackgroundRendererMixin {
    @WrapOperation(method = "setupColor", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/CubicSampler;gaussianSampleVec3(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/util/CubicSampler$Vec3Fetcher;)Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 tmm$overrideFogColor(Vec3 pos, CubicSampler.Vec3Fetcher rgbFetcher, Operation<Vec3> original, @Local(argsOnly = true) ClientLevel world) {
        // ── 失明症雾颜色：纯黑，优先级最高 ──
        LocalPlayer blindnessPlayer = Minecraft.getInstance().player;
        if (blindnessPlayer != null && blindnessPlayer.hasEffect(ModEffects.BLINDNESS_SICKNESS)) {
            return Vec3.ZERO;
        }
        if (SREClient.isTrainMoving() && world.getDayTime() == 18000) {
            Color color = new Color(0xE406060B, true);
            return new Vec3(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f);
        }

        // ── 暴风雪雾颜色：淡蓝白色 ──
        if (SREClient.isBlizzardActive && SREClient.gameComponent != null && SREClient.gameComponent.isRunning()) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                boolean isFinalBlizzard = SREClient.blizzardRemainingTicks == Integer.MAX_VALUE;
                boolean isOutdoor = player.level().canSeeSky(player.blockPosition().above());
                if (isFinalBlizzard || isOutdoor) {
                    // 淡蓝白色雾（模拟暴风雪的冰冷感觉）
                    return new Vec3(0.78f, 0.85f, 0.95f);
                }
            }
        }

        return original.call(pos, rgbFetcher);
    }
}
