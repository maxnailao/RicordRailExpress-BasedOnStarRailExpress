package org.agmas.noellesroles.game.roles.innocence.blackke;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.init.ModEffects;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.UUID;

/**
 * Hacker player component
 *
 * Civilian faction, real mood, limited sprint
 *
 * Skill:
 * - Spend 125 coins to select a player in inventory
 * - Apply Audio Interference (TINGJUEGANRAO) and Visual Interference (SHIJUEGANRAO) to the target for 30 seconds each
 * - 60 seconds cooldown
 * - When the target is a Commander, force switch to normal channel for 30 seconds
 * - When the target is a spectator, effects are not applied but coins and cooldown are still consumed
 */
public class BlackkePlayerComponent implements RoleComponent, ServerTickingComponent {

    /** Component key */
    public static final ComponentKey<BlackkePlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "blackke"),
            BlackkePlayerComponent.class);

    /** Skill cost */
    public static final int SKILL_COST = 125;

    /** Interference duration (30 seconds = 600 ticks) */
    public static final int INTERFERENCE_DURATION = 30 * 20;

    /** Skill cooldown (60 seconds = 1200 ticks) */
    public static final int SKILL_COOLDOWN = 60 * 20;

    /** Commander channel forced restore timer (ticks) */
    private int commanderRestoreTimer = 0;

    /** UUID of the commander whose channel was forced to switch */
    private UUID forcedCommanderUuid = null;

    private final Player player;

    public BlackkePlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer p) {
        return p == this.player;
    }

    public void sync() {
        if (!player.level().isClientSide) {
            KEY.sync(player);
        }
    }

    @Override
    public void init() {
        this.commanderRestoreTimer = 0;
        this.forcedCommanderUuid = null;
        sync();
    }

    @Override
    public void clear() {
        // Restore commander channel if forced switch is still active when game ends
        restoreCommanderChannel();
        this.commanderRestoreTimer = 0;
        this.forcedCommanderUuid = null;
        sync();
    }

    /**
     * Use interference skill on target player
     *
     * @param target target player
     * @return true if skill was successfully used (enters cooldown)
     */
    public boolean useSkillOnTarget(ServerPlayer target) {
        if (!(player instanceof ServerPlayer serverPlayer)) return false;

        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorldComponent.isRunning()) return false;
        if (!gameWorldComponent.isRole(player, ModRoles.BLACKKE)) return false;

        // Check if self is alive
        if (!GameUtils.isPlayerAliveAndSurvival(player)) return false;

        // Check cooldown
        SREAbilityPlayerComponent abilityComp = SREAbilityPlayerComponent.KEY.get(player);
        if (abilityComp.cooldown > 0) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.blackke.on_cooldown")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }

        // Check gold balance
        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
        if (shop.balance < SKILL_COST) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.blackke.insufficient_gold", SKILL_COST)
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }

        // Deduct coins
        shop.addToBalance(-SKILL_COST);

        // Set cooldown
        abilityComp.setCooldown(SKILL_COOLDOWN);

        // If target is spectator, skip effects but still deduct coins and enter cooldown
        if (GameUtils.isPlayerSpectator(target)) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.blackke.target_spectator",
                            target.getName().getString())
                            .withStyle(ChatFormatting.YELLOW),
                    true);
            sync();
            return true;
        }

        // Apply Audio Interference and Visual Interference for 30 seconds each
        target.addEffect(new MobEffectInstance(ModEffects.TINGJUEGANRAO, INTERFERENCE_DURATION, 0, false, true, true));
        target.addEffect(new MobEffectInstance(ModEffects.SHIJUEGANRAO, INTERFERENCE_DURATION, 0, false, true, true));

        // Check if target is Commander, if so force switch to normal channel for 30 seconds
        if (gameWorldComponent.isRole(target, ModRoles.COMMANDER)) {
            SREAbilityPlayerComponent targetAbility = SREAbilityPlayerComponent.KEY.get(target);
            if (targetAbility.status == 1) {
                // Force switch to normal channel
                targetAbility.status = -1;
                targetAbility.sync();

                // Record restore timer
                this.forcedCommanderUuid = target.getUUID();
                this.commanderRestoreTimer = INTERFERENCE_DURATION;

                // Notify the commander
                target.displayClientMessage(
                        Component.translatable("message.noellesroles.blackke.commander_forced_normal")
                                .withStyle(ChatFormatting.RED),
                        true);
            }
        }

        // Notify the hacker
        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.blackke.success",
                        target.getName().getString())
                        .withStyle(ChatFormatting.GREEN),
                true);

        sync();
        return true;
    }

    @Override
    public void serverTick() {
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorldComponent.isRunning()) return;
        if (!gameWorldComponent.isRole(player, ModRoles.BLACKKE)) return;

        // Handle commander channel restore countdown
        if (commanderRestoreTimer > 0) {
            commanderRestoreTimer--;
            if (commanderRestoreTimer <= 0) {
                restoreCommanderChannel();
                sync();
            }
        }
    }

    /**
     * Restore the forced-switched commander channel
     */
    private void restoreCommanderChannel() {
        if (forcedCommanderUuid == null) return;

        Player targetPlayer = player.level().getPlayerByUUID(forcedCommanderUuid);
        if (targetPlayer instanceof ServerPlayer targetServerPlayer) {
            SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
            if (gameWorldComponent.isRole(targetServerPlayer, ModRoles.COMMANDER)) {
                SREAbilityPlayerComponent targetAbility = SREAbilityPlayerComponent.KEY.get(targetServerPlayer);
                if (targetAbility.status == -1) {
                    targetAbility.status = 1;
                    targetAbility.sync();
                    targetServerPlayer.displayClientMessage(
                            Component.translatable("message.noellesroles.blackke.commander_channel_restored")
                                    .withStyle(ChatFormatting.GOLD),
                            true);
                }
            }
        }
        forcedCommanderUuid = null;
        commanderRestoreTimer = 0;
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        var gameWorldComponent = SREGameWorldComponent.KEY.get(this.player.level());
        if (!gameWorldComponent.isRunning()) return;
        if (!gameWorldComponent.isRole(this.player, ModRoles.BLACKKE)) return;

        tag.putInt("CommanderRestoreTimer", commanderRestoreTimer);
        if (forcedCommanderUuid != null) {
            tag.putUUID("ForcedCommanderUuid", forcedCommanderUuid);
        }
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        if (!tag.contains("CommanderRestoreTimer")) {
            this.clear();
            return;
        }
        this.commanderRestoreTimer = tag.getInt("CommanderRestoreTimer");
        if (tag.contains("ForcedCommanderUuid")) {
            this.forcedCommanderUuid = tag.getUUID("ForcedCommanderUuid");
        } else {
            this.forcedCommanderUuid = null;
        }
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {}

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {}
}
