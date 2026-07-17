package org.agmas.noellesroles.game.roles.killer.snow_hunter;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.component.ModComponents;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

public class SnowHunterPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {

    public static final ComponentKey<SnowHunterPlayerComponent> KEY = ModComponents.SNOW_HUNTER;

    private final Player player;

    public int skillActiveTicks = 0;
    public int skillCooldownTicks = 0;

    public static final int SKILL_DURATION = 8 * 20;
    public static final int SKILL_COOLDOWN = 60 * 20;
    public static final int SKILL_COST = 80;

    public SnowHunterPlayerComponent(@NotNull Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        skillActiveTicks = 0;
        skillCooldownTicks = 0;
        sync();
    }

    @Override
    public void clear() {
        init();
    }

    public boolean isSkillActive() {
        return skillActiveTicks > 0;
    }

    public void activateSkill() {
        skillActiveTicks = SKILL_DURATION;
        sync();
    }

    public void sync() {
        ModComponents.SNOW_HUNTER.sync(this.player);
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp)) return;
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(sp.level());
        if (!gameWorld.isRunning()) return;
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) return;

        if (skillActiveTicks > 0) {
            skillActiveTicks--;
            if (skillActiveTicks <= 0) {
                skillCooldownTicks = SKILL_COOLDOWN;
                sync();
            }
        }
        if (skillCooldownTicks > 0) {
            skillCooldownTicks--;
        }

        if (skillActiveTicks > 0 && skillActiveTicks % 20 == 0) {
            sync();
        }
        if (skillCooldownTicks > 0 && skillCooldownTicks % 20 == 0) {
            sync();
        }
    }

    @Override
    public void clientTick() {
        if (skillActiveTicks > 0) {
            skillActiveTicks--;
            if (skillActiveTicks <= 0) {
                skillCooldownTicks = SKILL_COOLDOWN;
            }
        }
        if (skillCooldownTicks > 0) {
            skillCooldownTicks--;
        }
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("skillActiveTicks", skillActiveTicks);
        tag.putInt("skillCooldownTicks", skillCooldownTicks);
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        skillActiveTicks = tag.getInt("skillActiveTicks");
        skillCooldownTicks = tag.getInt("skillCooldownTicks");
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        writeToNbt(tag, registryLookup);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        readFromNbt(tag, registryLookup);
    }
}
