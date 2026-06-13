package org.agmas.noellesroles.mixin;

import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 FishingHook 的私有字段 nibble，用于自定义鱼钩判断是否有鱼上钩。
 */
@Mixin(FishingHook.class)
public interface FishingHookAccessor {
    @Accessor("nibble")
    int getNibble();
}
