package org.agmas.noellesroles.game.roles.innocence.child;

import io.wifi.starrailexpress.api.RoleComponent;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.voice.HeliumBuzzPlayerComponent;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

public class ChildPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {

    public static final ComponentKey<ChildPlayerComponent> KEY = ModComponents.CHILD;

    public static final AttributeModifier CHILD_SCALE_MODIFIER = new AttributeModifier(
            Noellesroles.id("child_scale_modifier"),
            -0.33, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

    private final Player player;

    // 当前选择的音效索引
    public int childSoundIdx = 0;

    // 技能冷却
    public int cooldown = 0;

    public ChildPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return this.player;
    }

    @Override
    public void init() {
        this.childSoundIdx = 0;
        this.cooldown = 0;
        player.getAttribute(Attributes.SCALE).removeModifier(CHILD_SCALE_MODIFIER);
        player.getAttribute(Attributes.SCALE).addPermanentModifier(CHILD_SCALE_MODIFIER);
        HeliumBuzzPlayerComponent buzz = HeliumBuzzPlayerComponent.KEY.get(player);
        buzz.apply(Integer.MAX_VALUE, 1);
        this.sync();
    }

    @Override
    public void clear() {
        player.getAttribute(Attributes.SCALE).removeModifier(CHILD_SCALE_MODIFIER);
        HeliumBuzzPlayerComponent buzz = HeliumBuzzPlayerComponent.KEY.get(player);
        buzz.clear();
        this.childSoundIdx = 0;
        this.cooldown = 0;
    }

    public void sync() {
        ModComponents.CHILD.sync(this.player);
    }

    public boolean canUseAbility() {
        return this.cooldown <= 0;
    }

    public void setCooldown(int ticks) {
        this.cooldown = ticks;
        this.sync();
    }

    @Override
    public void serverTick() {
        if (this.cooldown > 0) {
            this.cooldown--;
            if (this.cooldown == 0 || this.cooldown % 20 == 0) {
                this.sync();
            }
        }
    }

    @Override
    public void clientTick() {
        if (this.cooldown > 0) {
            this.cooldown--;
        }
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("childSoundIdx", this.childSoundIdx);
        tag.putInt("cooldown", this.cooldown);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.childSoundIdx = tag.contains("childSoundIdx") ? tag.getInt("childSoundIdx") : 0;
        this.cooldown = tag.contains("cooldown") ? tag.getInt("cooldown") : 0;
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("childSoundIdx", this.childSoundIdx);
        tag.putInt("cooldown", this.cooldown);
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.childSoundIdx = tag.contains("childSoundIdx") ? tag.getInt("childSoundIdx") : 0;
        this.cooldown = tag.contains("cooldown") ? tag.getInt("cooldown") : 0;
    }
}