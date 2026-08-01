package org.agmas.noellesroles.game.roles.innocence.conductor;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.UUID;

public class ConductorPlayerComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<ConductorPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            Noellesroles.id("conductor"), ConductorPlayerComponent.class);

    private static final int AIM_THRESHOLD_TICKS = 3 * 20;
    private static final int GLOW_DURATION_TICKS = 5 * 20;
    private static final double SPYGLASS_RANGE = 500.0D;

    private final Player player;
    private UUID currentTarget = null;
    private int aimTicks = 0;

    public ConductorPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        this.currentTarget = null;
        this.aimTicks = 0;
    }

    @Override
    public void clear() {
        this.init();
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return this.player == player;
    }

    public void sync() {
        ModComponents.CONDUCTOR.sync(this.player);
    }

    @Override
    public void serverTick() {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorld.isRole(player, ModRoles.CONDUCTOR)) return;
        if (!GameUtils.isPlayerAliveAndSurvival(player)) return;

        boolean usingSpyglass = player.getMainHandItem().is(Items.SPYGLASS) && player.isUsingItem()
                && player.getUseItem().is(Items.SPYGLASS);

        if (!usingSpyglass) {
            currentTarget = null;
            aimTicks = 0;
            return;
        }

        var hitResult = ProjectileUtil.getHitResultOnViewVector(player,
                entity -> entity instanceof Player, SPYGLASS_RANGE);

        if (hitResult instanceof EntityHitResult ehr && ehr.getEntity() instanceof ServerPlayer target) {
            if (target == player) {
                currentTarget = null;
                aimTicks = 0;
                return;
            }

            if (target.getUUID().equals(currentTarget)) {
                aimTicks++;
            } else {
                currentTarget = target.getUUID();
                aimTicks = 1;
            }

            if (aimTicks >= AIM_THRESHOLD_TICKS) {
                if (!target.hasEffect(MobEffects.GLOWING)) {
                    target.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOW_DURATION_TICKS, 0,
                            false, false, true));
                }
                aimTicks = 0;
                currentTarget = null;
            }
        } else {
            currentTarget = null;
            aimTicks = 0;
        }
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("aimTicks", this.aimTicks);
        if (this.currentTarget != null) {
            tag.putUUID("currentTarget", this.currentTarget);
        }
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.aimTicks = tag.getInt("aimTicks");
        this.currentTarget = tag.hasUUID("currentTarget") ? tag.getUUID("currentTarget") : null;
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {}

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {}
}
