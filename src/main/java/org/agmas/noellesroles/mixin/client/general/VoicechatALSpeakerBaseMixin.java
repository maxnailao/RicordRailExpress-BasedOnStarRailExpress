package org.agmas.noellesroles.mixin.client.general;

import de.maxhenkel.voicechat.voice.client.SoundManager;
import de.maxhenkel.voicechat.voice.client.speaker.ALSpeakerBase;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.client.TwoDimensionalCameraClientHandle;
import org.lwjgl.openal.AL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ALSpeakerBase.class, remap = false)
public class VoicechatALSpeakerBaseMixin {
    @Inject(
            method = "setPositionSync",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/openal/AL11;alListener3f(IFFF)V",
                    shift = At.Shift.AFTER),
            remap = false)
    private void noellesroles$usePlayerAsOpenALListener(Vec3 soundPosition, float distance, CallbackInfo ci) {
        Vec3 listener = TwoDimensionalCameraClientHandle.voiceListenerPosition();
        if (listener == null) {
            return;
        }
        try {
            AL11.alListener3f(AL11.AL_POSITION, (float) listener.x, (float) listener.y, (float) listener.z);
            SoundManager.checkAlError();
        } catch (Throwable ignored) {
            // OpenAL 状态异常时静默处理，避免导致语音播放崩溃
        }
    }
}
