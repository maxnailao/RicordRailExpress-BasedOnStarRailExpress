package io.wifi.starrailexpress.mixin.client;

import io.wifi.starrailexpress.client.render.entity.HatFeatureRenderer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在 PlayerRenderer 构造时注册帽子皮肤渲染层。
 * 采用与 ratatouille PlushOnHeadFeatureRenderer 相同的注册模式，
 * 不依赖原版 CustomHeadLayer 的渲染链路。
 */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererHatLayerMixin
        extends LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    protected PlayerRendererHatLayerMixin(EntityRendererProvider.Context context,
            PlayerModel<AbstractClientPlayer> entityModel, float f) {
        super(context, entityModel, f);
    }

    @Inject(method = "<init>(Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;Z)V", at = @At("TAIL"))
    private void sre$addHatFeatureLayer(EntityRendererProvider.Context context, boolean slim, CallbackInfo ci) {
        this.addLayer(new HatFeatureRenderer(this));
        io.wifi.starrailexpress.SRE.LOGGER.info("[HatSync] HatFeatureRenderer layer registered (slim={})", slim);
    }
}
