package org.agmas.noellesroles.content.entity;

import net.minecraft.client.renderer.entity.BoatRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.vehicle.Boat;
import org.agmas.noellesroles.Noellesroles;

/**
 * 自定义船渲染器
 * 修复原版 BoatRenderer 硬编码 minecraft 命名空间的问题，
 * 使纹理从 noellesroles 命名空间加载。
 *
 * 纹理路径：assets/noellesroles/textures/entity/boat/oak.png
 */
public class DurabilityBoatRenderer extends BoatRenderer {
    private static final ResourceLocation OAK_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "textures/entity/boat/oak.png");

    public DurabilityBoatRenderer(EntityRendererProvider.Context context, boolean chest) {
        super(context, chest);
    }

    @Override
    public ResourceLocation getTextureLocation(Boat boat) {
        return OAK_TEXTURE;
    }
}
