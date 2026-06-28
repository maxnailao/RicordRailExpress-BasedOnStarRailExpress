package org.agmas.noellesroles.game.roles.killer.poacher;

import io.wifi.starrailexpress.api.RoleComponent;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.component.ModComponents;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 盗猎者玩家组件 - 存储弓射击冷却状态
 */
public class PoacherPlayerComponent implements RoleComponent, ServerTickingComponent {
    public static final ComponentKey<PoacherPlayerComponent> KEY = ModComponents.POACHER;
    
    private final Player player;
    
    /** 弓射击冷却时间(tick) */
    public int bowShootCooldown = 0;
    
    public PoacherPlayerComponent(Player player) {
        this.player = player;
    }
    
    @Override
    public Player getPlayer() {
        return player;
    }
    
    @Override
    public void init() {
        this.bowShootCooldown = 0;
        this.sync();
    }
    
    @Override
    public void clear() {
        this.init();
    }
    
    public void sync() {
        KEY.sync(this.player);
    }
    
    @Override
    public void serverTick() {
        // 减少冷却时间
        if (this.bowShootCooldown > 0) {
            this.bowShootCooldown--;
            // 每秒同步一次
            if (this.bowShootCooldown % 20 == 0 || this.bowShootCooldown == 0) {
                this.sync();
            }
        }
    }
    
    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("bowShootCooldown", this.bowShootCooldown);
    }
    
    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.bowShootCooldown = tag.getInt("bowShootCooldown");
    }
    
    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 不需要持久化
    }
    
    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 不需要持久化
    }
}
