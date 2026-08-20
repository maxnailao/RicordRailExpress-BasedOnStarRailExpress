package org.agmas.noellesroles.game.roles.innocence.wantong;

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

/**
 * 顽童组件 - 平民阵营
 * 与熊孩子相同的被动逻辑：体型缩小 + 氦气变声
 */
public class WantongPlayerComponent implements RoleComponent {

    public static final AttributeModifier WANTONG_SCALE_MODIFIER = new AttributeModifier(
            Noellesroles.id("wantong_scale_modifier"),
            -0.33, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

    private final Player player;

    public WantongPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return this.player;
    }

    @Override
    public void init() {
        player.getAttribute(Attributes.SCALE).removeModifier(WANTONG_SCALE_MODIFIER);
        player.getAttribute(Attributes.SCALE).addPermanentModifier(WANTONG_SCALE_MODIFIER);
        HeliumBuzzPlayerComponent buzz = HeliumBuzzPlayerComponent.KEY.get(player);
        buzz.apply(Integer.MAX_VALUE, 1);
        this.sync();
    }

    @Override
    public void clear() {
        player.getAttribute(Attributes.SCALE).removeModifier(WANTONG_SCALE_MODIFIER);
        HeliumBuzzPlayerComponent buzz = HeliumBuzzPlayerComponent.KEY.get(player);
        buzz.clear();
    }

    public void sync() {
        ModComponents.WANTONG.sync(this.player);
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}