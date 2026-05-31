package org.agmas.noellesroles.game.roles.Innocent.child;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.game.GameConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;

public class ChildPlayerComponent implements RoleComponent {
    public static final ComponentKey<ChildPlayerComponent> KEY = ModComponents.CHILD;

    private final Player player;
    public int activeSlotIndex = 0;

    public ChildPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        activeSlotIndex = 0;
        sync();
    }

    @Override
    public void clear() {
        init();
    }

    public void switchSlot() {
        int slotCount = Math.max(1, ChildSoundRegistry.getSlotCount());
        activeSlotIndex = Math.floorMod(activeSlotIndex + 1, slotCount);

        if (player instanceof ServerPlayer serverPlayer) {
            ResourceLocation soundId = ChildSoundRegistry.getSoundIdForSlot(activeSlotIndex);
            serverPlayer.displayClientMessage(
                    Component.literal("熊孩子音效槽位: " + (activeSlotIndex + 1) + "/" + slotCount + " (" + soundId + ")")
                            .withStyle(ChatFormatting.YELLOW),
                    true
            );
        }
        sync();
    }

    public void useActiveSound() {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        SREAbilityPlayerComponent ability = SREAbilityPlayerComponent.KEY.get(serverPlayer);
        if (ability.cooldown > 0) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.ability_cooldown").withStyle(ChatFormatting.RED),
                    true
            );
            return;
        }

        ChildSoundRegistry.play(serverPlayer, activeSlotIndex);
        var cfg = NoellesRolesConfig.HANDLER.instance();
        int cooldownSeconds = cfg != null ? Math.max(0, cfg.childSoundCooldownSeconds) : 20;
        ability.cooldown = GameConstants.getInTicks(0, cooldownSeconds);
        ability.sync();
        sync();
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return this.player == player;
    }

    public void sync() {
        KEY.sync(player);
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("activeSlotIndex", activeSlotIndex);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        int slotCount = Math.max(1, ChildSoundRegistry.getSlotCount());
        activeSlotIndex = Math.floorMod(tag.getInt("activeSlotIndex"), slotCount);
    }
}
