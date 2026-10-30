package io.wifi.starrailexpress.game.modes.funny;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.cca.SRERoleWorldComponent;
import io.wifi.starrailexpress.cca.gamemode.CustomRoleGameModeTeamsPlayerComponent;
import io.wifi.starrailexpress.cca.gamemode.CustomRoleGameModeWorldComponent;
import io.wifi.starrailexpress.event.AllowGameEnd;
import io.wifi.starrailexpress.event.OnGameTrueStarted;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.modes.SREMurderGameMode;
import io.wifi.starrailexpress.game.roles.SpecialGameModeRoles;
import io.wifi.starrailexpress.network.CloseUiPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;
import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import org.agmas.harpymodloader.modded_murder.PlayerRoleWeightManager;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.utils.RoleUtils;
import pro.fazeclan.river.stupid_express.role.avaricious.AvariciousGoldHandler;

import java.util.*;

import static io.wifi.starrailexpress.game.GameUtils.addItemCooldowns;

public class SRECustomRoleGameMode extends SREMurderGameMode {
    public SRECustomRoleGameMode(ResourceLocation identifier) {
        super(identifier, 10, 6);
    }

    @Override
    public boolean shouldRecordPlayerStats() {
        return true;
    }

    long roleSelectTimeout = -1;
    int selectionTick = -1;

    @Override
    public void initializeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        gameWorldComponent.clearRoleMap();
        selectionTick = SREConfig.instance().customRoleModeForceSelectTime * 20;
        roleSelectTimeout = serverWorld.getGameTime() + selectionTick;
        ArrayList<ServerPlayer> unassignedPlayers = new ArrayList<>(players);
        for (ServerPlayer player : unassignedPlayers) {
            gameWorldComponent.addRole(player, SpecialGameModeRoles.CUSTOM_PENDING, false);
            var ccca = CustomRoleGameModeTeamsPlayerComponent.KEY.get(player);
            ccca.setSelected(false);
            ccca.setTeam(0);
            player.addEffect(new MobEffectInstance(
                    ModEffects.SAFE_TIME,
                    selectionTick + 20,
                    10,
                    true, // ambient - 环境效果（粒子更少更透明）
                    false, // showParticles - 不显示粒子
                    false // showIcon - 不显示图标
            ));
            player.addEffect(new MobEffectInstance(
                    MobEffects.INVISIBILITY,
                    selectionTick + 20,
                    10,
                    true, // ambient - 环境效果（粒子更少更透明）
                    false, // showParticles - 不显示粒子
                    false // showIcon - 不显示图标
            ));
            player.addEffect(new MobEffectInstance(
                    ModEffects.SKILL_BANED,
                    40,
                    10,
                    true, // ambient - 环境效果（粒子更少更透明）
                    false, // showParticles - 不显示粒子
                    false // showIcon - 不显示图标
            ));
            RoleUtils.sendWelcomeAnnouncement(player);
        }
        getRolesAndAssignTeams(serverWorld, gameWorldComponent, unassignedPlayers);
        gameWorldComponent.syncRoles();
        int modifierRoleCount = (int) ((float) players.size()
                * HarpyModLoaderConfig.HANDLER.instance().modifierMultiplier);
        assignModifiers(modifierRoleCount, serverWorld, gameWorldComponent, players);
        Harpymodloader.FORCED_MODDED_ROLE.clear();
        Harpymodloader.FORCED_MODDED_ROLE_FLIP.clear();
        Harpymodloader.FORCED_MODDED_MODIFIER.clear();
        PlayerRoleWeightManager.ForcePlayerTeam.clear();
    }

    public void getRolesAndAssignTeams(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            ArrayList<ServerPlayer> players) {
        Map<Player, SRERole> assignments = SREMurderGameMode.assignRolesToPlayers(serverWorld, players);
        var selection = CustomRoleGameModeWorldComponent.KEY.get(serverWorld);
        selection.getRoles().clear();
        for (ServerPlayer player : players) {
            SRERole role = assignments.getOrDefault(player, TMMRoles.CIVILIAN);
            var team = CustomRoleGameModeTeamsPlayerComponent.KEY.get(player);
            team.setTeamAndSync(PlayerRoleWeightManager.getRoleType(role));
            if (Harpymodloader.FORCED_MODDED_ROLE_FLIP.get(player.getUUID()) == role) {
                selection.changeRoleButNoEvents(player, role);
            } else {
                team.setSelected(false);
                selection.getRoles().add(role);
            }
        }
        selection.sync();
    }

    @Override
    public void finalizeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        super.finalizeGame(serverWorld, gameWorldComponent);
        roleSelectTimeout = -1;
        CustomRoleGameModeWorldComponent.KEY.get(serverWorld).clear();
    }

    @Override
    public void tickServerGameLoop(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        if (roleSelectTimeout != -1) {
            if (serverWorld.getGameTime() >= roleSelectTimeout) {
                trueStartAfterSelectedRoles(serverWorld, gameWorldComponent);
                roleSelectTimeout = -1;
            }
        }

        super.tickServerGameLoop(serverWorld, gameWorldComponent);
    }

    @Override
    public boolean autoTriggerGameTrueStarted() {
        return false;
    }

    private void trueStartAfterSelectedRoles(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        var gamblerPlayers = serverWorld.getPlayers((p) -> GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(p)
                && gameWorldComponent.isRole(p, SpecialGameModeRoles.CUSTOM_PENDING));
        if (gamblerPlayers != null && !gamblerPlayers.isEmpty()) {
            var crgmwcca = CustomRoleGameModeWorldComponent.KEY.get(serverWorld);
            for (var p : gamblerPlayers) {
                crgmwcca.autoSelect(p);
            }
        }
        SRERoleWorldComponent roleWorldComponent = SRERoleWorldComponent.KEY.get(serverWorld);
        roleWorldComponent.sync();
        List<ServerPlayer> players = serverWorld
                .getPlayers((p) -> GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(p));
        for (ServerPlayer p : players) {
            SRERole role = roleWorldComponent.getRole(p);
            p.removeEffect(ModEffects.SKILL_BANED);
            p.removeEffect(ModEffects.SAFE_TIME);
            if (role != null) {
                RoleUtils.sendWelcomeAnnouncement(p);
                if (role.canUseKiller()) {
                    SREPlayerShopComponent playerShopComponent = SREPlayerShopComponent.KEY.get(p);
                    if (playerShopComponent.balance < GameConstants.getMoneyStart())
                        playerShopComponent.setBalance(GameConstants.getMoneyStart());
                }
                ModdedRoleAssigned.EVENT.invoker().assignModdedRole(p, role);
            }
            ServerPlayNetworking.send(p, new CloseUiPayload());
        }
        GameUtils.recordPlayerStats(serverWorld, gameWorldComponent, new ArrayList<>(serverWorld.players()));
        int SAFE_TIME_COOLDOWN = SREConfig.instance().safeTimeCooldown * 20;
        addItemCooldowns(serverWorld, SAFE_TIME_COOLDOWN);
        SRE.REPLAY_MANAGER.updateReplayInitialRoles(players, gameWorldComponent.getRoles());
        AvariciousGoldHandler.gameStartTime = -1;
        SREGameTimeComponent.KEY.get(serverWorld).addTime(selectionTick);
        OnGameTrueStarted.EVENT.invoker().onGameTrueStarted(serverWorld);
    }

    @Override
    public GameUtils.WinStatus allowGameEnd(ServerLevel serverWorld, GameUtils.WinStatus winStatus,
            boolean isLooseEndsMode, SREGameWorldComponent gameWorldComponent) {
        if (roleSelectTimeout != -1) {
            var gamblerPlayers = serverWorld.getPlayers((p) -> GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(p)
                    && gameWorldComponent.isRole(p, SpecialGameModeRoles.CUSTOM_PENDING));
            if (!gamblerPlayers.isEmpty()) {
                if (serverWorld.getGameTime() <= roleSelectTimeout) {
                    return GameUtils.WinStatus.NONE;
                }
            }
        }

        return AllowGameEnd.EVENT.invoker().allowGameEnd(serverWorld,
                winStatus, false);
    }

    @Override
    public void recordPlayerStats(ServerLevel serverWorld, SREGameWorldComponent gameComponent,
            ArrayList<ServerPlayer> readyPlayerList) {
        // 开始游戏后记录
    }
}
