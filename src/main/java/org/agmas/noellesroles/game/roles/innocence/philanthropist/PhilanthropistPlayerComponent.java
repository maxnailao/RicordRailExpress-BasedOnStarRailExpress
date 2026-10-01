package org.agmas.noellesroles.game.roles.innocence.philanthropist;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

public class PhilanthropistPlayerComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<PhilanthropistPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "philanthropist"),
            PhilanthropistPlayerComponent.class);

    public static final int SKILL_COST = 100;
    public static final int DONATE_AMOUNT = 50;
    public static final int SKILL_COOLDOWN = 30 * 20;

    private int skillCooldown = 0;
    private final Player player;

    public PhilanthropistPlayerComponent(Player player) {
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

    public int getSkillCooldown() {
        return skillCooldown;
    }

    @Override
    public void init() {
        this.skillCooldown = 0;
        sync();
    }

    @Override
    public void clear() {
        this.skillCooldown = 0;
        sync();
    }

    public boolean useSkillOnTarget(ServerPlayer target) {
        if (!(player instanceof ServerPlayer serverPlayer)) return false;

        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorldComponent.isRunning()) return false;
        if (!gameWorldComponent.isRole(player, ModRoles.PHILANTHROPIST)) return false;
        if (!GameUtils.isPlayerAliveAndSurvival(player)) return false;

        if (this.skillCooldown > 0) {
            serverPlayer.displayClientMessage(
                    Component.translatable("tip.noellesroles.cooldown", skillCooldown / 20)
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }

        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
        if (shop.balance < SKILL_COST) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.insufficient_funds_money", SKILL_COST)
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }

        if (!GameUtils.isPlayerAliveAndSurvival(target)) return false;

        shop.addToBalance(-SKILL_COST);
        SREPlayerShopComponent.KEY.get(target).addToBalance(DONATE_AMOUNT);
        this.skillCooldown = SKILL_COOLDOWN;

        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.philanthropist.donated",
                        target.getName().getString()).withStyle(ChatFormatting.GOLD), true);
        target.displayClientMessage(
                Component.translatable("message.noellesroles.philanthropist.received",
                        serverPlayer.getName().getString()).withStyle(ChatFormatting.GOLD), true);

        sync();
        return true;
    }

    @Override
    public void serverTick() {
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorldComponent.isRunning()) return;
        if (!gameWorldComponent.isRole(player, ModRoles.PHILANTHROPIST)) return;

        if (this.skillCooldown > 0) {
            this.skillCooldown--;
            if (this.skillCooldown % 20 == 0 || this.skillCooldown == 0) {
                sync();
            }
        }
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("SkillCooldown", skillCooldown);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.skillCooldown = tag.getInt("SkillCooldown");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {}

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {}
}
