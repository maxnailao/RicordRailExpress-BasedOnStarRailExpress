package org.agmas.noellesroles.game.roles.neutral.cipher;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.agmas.noellesroles.Noellesroles;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class CipherDroneEntityRenderer extends GeoEntityRenderer<CipherDroneEntity> {

    public CipherDroneEntityRenderer(EntityRendererProvider.Context context) {
        // 资源约定：noellesroles:geo/entity/cipher_drone.geo.json
        //          noellesroles:animations/entity/cipher_drone.animation.json
        //          noellesroles:textures/entity/cipher_drone.png
        super(context, new DefaultedEntityGeoModel<>(Noellesroles.id("cipher_drone")));
    }

    @Override
    public boolean shouldRender(CipherDroneEntity entity, Frustum frustum, double x, double y, double z) {
        // 本机正被驾驶员当作相机时不渲染自身，避免机身挡住第一人称视线；其它玩家仍能看见
        if (Minecraft.getInstance().getCameraEntity() == entity) return false;
        return super.shouldRender(entity, frustum, x, y, z);
    }
}