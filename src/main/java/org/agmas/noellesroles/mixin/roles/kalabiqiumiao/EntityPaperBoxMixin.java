package org.agmas.noellesroles.mixin.roles.kalabiqiumiao;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.agmas.noellesroles.game.roles.innocence.kalabiqiumiao.KalabiqiumiaoPlayerComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 纸片人判定箱：弦化期间玩家重建边界箱（移动、传送、姿态变化触发的
 * reapplyPosition/refreshDimensions）时返回压扁后的纸片人判定箱。
 */
@Mixin(Entity.class)
public abstract class EntityPaperBoxMixin {

    @ModifyReturnValue(method = "makeBoundingBox", at = @At("RETURN"))
    private AABB noellesroles$paperBoundingBox(AABB original) {
        Entity self = (Entity) (Object) this;
        if (self instanceof Player player && KalabiqiumiaoPlayerComponent.isPaperActive(player)) {
            return KalabiqiumiaoPlayerComponent.createPaperBoundingBox(player);
        }
        return original;
    }
}
