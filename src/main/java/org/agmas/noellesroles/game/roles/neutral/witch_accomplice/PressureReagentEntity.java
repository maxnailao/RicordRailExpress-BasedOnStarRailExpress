package org.agmas.noellesroles.game.roles.neutral.witch_accomplice;

import io.wifi.starrailexpress.content.entity.no_water_influenced.NoHeavyWaterInfluencedThrowableItemProjectile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.init.ModItems;

/**
 * 压力试剂投掷实体。
 * 落点即领域中心：命中方块/实体后，通知投掷者（魔女共犯）在该坐标展开压力领域，
 * 领域内的持续掉理智 / 恢复 / 提示逻辑全部交由 WitchAccomplicePlayerComponent 管理。
 */
public class PressureReagentEntity extends NoHeavyWaterInfluencedThrowableItemProjectile {

    public PressureReagentEntity(EntityType<? extends NoHeavyWaterInfluencedThrowableItemProjectile> entityType, Level world) {
        super(entityType, world);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.PRESSURE_REAGENT;
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (this.level() instanceof ServerLevel) {
            Entity owner = this.getOwner();
            if (owner instanceof ServerPlayer sp) {
                ModComponents.WITCH_ACCOMPLICE.maybeGet(sp)
                        .ifPresent(comp -> comp.startDomain(this.getX(), this.getY(), this.getZ()));
            }
            this.discard();
        }
    }
}
