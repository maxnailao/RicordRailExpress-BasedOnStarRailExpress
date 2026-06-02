package org.agmas.noellesroles.game.roles.neutral.corruptcop;

import io.wifi.starrailexpress.api.RoleComponent;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

public class CorruptCopPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {

    public static final ComponentKey<CorruptCopPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "corrupt_cop"),
            CorruptCopPlayerComponent.class
    );

    private final Player player;
    private int killCount = 0;

    public CorruptCopPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return this.player;
    }

    @Override
    public void init() {
        this.killCount = 0;
        this.sync();
    }

    @Override
    public void clear() {
        this.killCount = 0;
        this.sync();
    }

    public void sync() {
        KEY.sync(this.player);
    }

    public int getKillCount() {
        return this.killCount;
    }

    public void incrementKillCount() {
        this.killCount++;
        this.sync();
    }

    public void reset() {
        this.killCount = 0;
        this.sync();
    }

    @Override
    public void clientTick() {
    }

    @Override
    public void serverTick() {
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return player == this.player;
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("killCount", this.killCount);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.killCount = tag.contains("killCount") ? tag.getInt("killCount") : 0;
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("killCount", this.killCount);
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.killCount = tag.contains("killCount") ? tag.getInt("killCount") : 0;
    }
}