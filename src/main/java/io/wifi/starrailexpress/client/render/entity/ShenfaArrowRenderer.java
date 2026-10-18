package io.wifi.starrailexpress.client.render.entity;

import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * 神罚箭渲染器：复用原版箭模型与贴图。
 * 在别人眼里就是一根普通箭，白色拖尾由 ShenfaArrowEntity 的粒子提供。
 */
public class ShenfaArrowRenderer extends ArrowRenderer {
    public ShenfaArrowRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public @Nullable ResourceLocation getTextureLocation(Entity entity) {
        return ResourceLocation.tryParse("minecraft:textures/entity/projectiles/arrow.png");
    }
}