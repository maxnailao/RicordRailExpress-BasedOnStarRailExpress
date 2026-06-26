package org.agmas.noellesroles.mixin.roles.photographer;

import io.github.mortuusars.exposure.world.entity.PhotographFrameEntity;
import io.github.mortuusars.exposure.world.item.PhotographFrameItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import org.agmas.noellesroles.game.roles.innocent.photographer.PhotographerFrameEvents;
import org.agmas.noellesroles.game.roles.innocent.photographer.SrePhotographerFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PhotographFrameItem.class)
public class PhotographFrameItemMixin {

    @Unique
    private static final ThreadLocal<Boolean> sre$placingPhotographer = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void sre$bypassAdventurePlacement(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        Player player = context.getPlayer();
        if (player == null) return;
        if (!PhotographerFrameEvents.isPhotographer(player)) return;

        BlockPos clickedPos = context.getClickedPos();
        Direction direction = context.getClickedFace();
        BlockPos resultPos = clickedPos.relative(direction);
        ItemStack itemInHand = context.getItemInHand();
        Level level = context.getLevel();

        if (player.level().isOutsideBuildHeight(resultPos)) {
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }

        PhotographFrameItem self = (PhotographFrameItem) (Object) this;
        PhotographFrameEntity frameEntity = self.createEntity(level, resultPos, direction);

        if (frameEntity instanceof SrePhotographerFrame marker) {
            marker.sre$setPhotographerPlaced(true);
        }

        CustomData customData = itemInHand.getOrDefault(DataComponents.ENTITY_DATA, CustomData.EMPTY);
        if (!customData.isEmpty()) {
            EntityType.updateCustomEntityTag(level, player, frameEntity, customData);
        }

        for (int i = 2; i >= 0; i--) {
            frameEntity.setSize(i);
            if (frameEntity.survives()) {
                if (!level.isClientSide) {
                    frameEntity.playPlacementSound();
                    level.gameEvent(player, GameEvent.ENTITY_PLACE, frameEntity.position());
                    level.addFreshEntity(frameEntity);
                }
                frameEntity.setFrameItem((player.isCreative() ? itemInHand.copy() : itemInHand).split(1));
                cir.setReturnValue(InteractionResult.sidedSuccess(level.isClientSide));
                return;
            }
        }

        cir.setReturnValue(InteractionResult.FAIL);
    }
}
