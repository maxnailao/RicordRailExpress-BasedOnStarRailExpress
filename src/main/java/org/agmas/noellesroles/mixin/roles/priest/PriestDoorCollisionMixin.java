package org.agmas.noellesroles.mixin.roles.priest;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.init.ModEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 天堂制造期间神父无视方块碰撞，可穿过门和墙体。
 */
// BlockStateBase is the common dispatch point for collision shapes. Injecting
// BlockBehaviour alone misses custom blocks that override getCollisionShape.
@Mixin(BlockBehaviour.BlockStateBase.class)
public class PriestDoorCollisionMixin {

    @Inject(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;", at = @At("HEAD"), cancellable = true)
    private void noellesroles$priestPassDoors(BlockGetter world, BlockPos pos,
            CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (!(context instanceof EntityCollisionContext entityContext)) {
            return;
        }
        Entity entity = entityContext.getEntity();
        if (!(entity instanceof Player player)) {
            return;
        }
        SREGameWorldComponent game = SREGameWorldComponent.KEY.maybeGet(player.level()).orElse(null);
        if (game != null && game.isRole(player, ModRoles.PRIEST)
                && player.hasEffect(ModEffects.NO_COLLIDE)) {
            BlockState state = (BlockState) (Object) this;
            // 屏障是地图边界，不允许天堂制造穿过。
            if (state.is(Blocks.BARRIER)) {
                return;
            }
            // Keep the collision below the player's feet so the priest can pass walls without falling through floors.
            if (pos.getY() < player.getY()
                    || state.getBlock() instanceof StairBlock
                    || state.getBlock() instanceof SlabBlock) {
                return;
            }
            cir.setReturnValue(Shapes.empty());
        }
    }
}
