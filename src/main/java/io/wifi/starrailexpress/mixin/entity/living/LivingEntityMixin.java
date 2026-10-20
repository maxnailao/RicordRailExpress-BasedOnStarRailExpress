package io.wifi.starrailexpress.mixin.entity.living;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.mixin.entity.EntityMixin;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.agmas.noellesroles.game.roles.neutral.priest.PriestHeavenManager;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends EntityMixin {
    @Unique
    private static final AttributeModifier KNIFE_KNOCKBACK_MODIFIER = new AttributeModifier(SRE.id("knife_knockback_modifier"), 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

    @Shadow
    protected boolean jumping;

    @Shadow
    public abstract void makeSound(@Nullable SoundEvent sound);

    @Shadow
    public abstract @Nullable AttributeInstance getAttribute(Holder<Attribute> attribute);

    @Inject(method = "tick", at = @At("HEAD"))
    public void tmm$addKnockbackWithKnife(CallbackInfo ci) {
        if ((Object) this instanceof Player player) {
            AttributeModifier v = new AttributeModifier(SRE.id("knife_knockback_modifier"), .5f, AttributeModifier.Operation.ADD_VALUE);
            updateAttribute(player.getAttribute(Attributes.ATTACK_KNOCKBACK), v, player.getMainHandItem().is(TMMItems.KNIFE));
        }
    }

    @WrapOperation(
            method = "travel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;moveRelative(FLnet/minecraft/world/phys/Vec3;)V"
            )
    )
    private void tmm$applyPriestWaterAcceleration(
            LivingEntity self,
            float speed,
            net.minecraft.world.phys.Vec3 movementInput,
            Operation<Void> original
    ) {
        if (self instanceof Player player
                && self.isInWater()
                && !self.canStandOnFluid(self.level().getFluidState(self.blockPosition()))
                && SREGameWorldComponent.KEY.get(player.level()).isRole(player, ModRoles.PRIEST)
                && self.getAttributeValue(Attributes.WATER_MOVEMENT_EFFICIENCY) <= 0.0D) {
            speed *= PriestHeavenManager.movementSpeedMultiplier(player);
        }
        original.call(self, speed, movementInput);
    }

    @Unique
    private static void updateAttribute(AttributeInstance attribute, AttributeModifier modifier, boolean addOrKeep) {
        if (attribute != null) {
            boolean alreadyHasModifier = attribute.hasModifier(modifier.id());
            if (addOrKeep && !alreadyHasModifier) {
                attribute.addPermanentModifier(modifier);
            } else if (!addOrKeep && alreadyHasModifier) {
                attribute.removeModifier(modifier);
            }
        }
    }
}
