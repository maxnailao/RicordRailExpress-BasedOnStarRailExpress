package org.agmas.noellesroles.mixin.client.general;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.agmas.noellesroles.init.ModEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 转向受限（TURN_WEAK）效果：在 turnPlayer 执行期间临时降低鼠标灵敏度，
 * 方法结束后恢复原始值，使玩家转向变得迟缓但不至于完全无法转向。
 */
@Mixin(MouseHandler.class)
public class TurnWeakMouseMixin {

    @Unique
    private double noe$originalSensitivity = -1;

    @Inject(method = "turnPlayer", at = @At("HEAD"))
    private void noe$applyTurnWeak(double d, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null)
            return;
        LocalPlayer player = mc.player;
        if (player.hasEffect(ModEffects.TURN_WEAK)) {
            double original = mc.options.sensitivity().get();
            noe$originalSensitivity = original;
            mc.options.sensitivity().set(original * ModEffects.getTurnWeakSensitivityFactor(player));
        }
    }

    @Inject(method = "turnPlayer", at = @At("RETURN"))
    private void noe$restoreTurnWeak(double d, CallbackInfo ci) {
        if (noe$originalSensitivity >= 0) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.options != null) {
                mc.options.sensitivity().set(noe$originalSensitivity);
            }
            noe$originalSensitivity = -1;
        }
    }
}
