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
import io.wifi.starrailexpress.game.utils.RoleInstance;
import io.wifi.starrailexpress.network.CloseUiPayload;
import io.wifi.starrailexpress.progression.ProgressionDataManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.commands.RoleCountManager;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;
import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import org.agmas.harpymodloader.modded_murder.ForceTeamInfo.ForceTeamType;
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
        ArrayList<ServerPlayer> unassignedPlayers = new ArrayList<>(players);
        // 第一步：处理强制分配的角色

        int killerCount = RoleCountManager.getKillerCount(players.size());
        int vigilanteCount = RoleCountManager.getVigilanteCount(players.size());
        int neutralsCount = RoleCountManager.getNeutralCount(players.size());

        // 确保数量不为负数
        killerCount = Math.max(0, killerCount);
        vigilanteCount = Math.max(0, vigilanteCount);
        neutralsCount = Math.max(0, neutralsCount);

        List<RoleInstance> expandedRoles = super.getAllRoles(killerCount, vigilanteCount, neutralsCount, players.size(),
                0);
        HashMap<Integer, Integer> roleTypesCount = new HashMap<>();

        var crgmwc = CustomRoleGameModeWorldComponent.KEY.get(serverWorld);
        crgmwc.getRoles().clear();
        crgmwc.addAllRoles(expandedRoles);
        for (var roleInstance : expandedRoles) {
            SRERole role = roleInstance.role();
            if (role != null) {
                int roleType = PlayerRoleWeightManager.getRoleType(role);
                int nc = roleTypesCount.getOrDefault(roleType, 0);
                roleTypesCount.put(roleType, nc + 1);
            }
        }

        // ── 阵营卡保底 ──
        // 中立名额有人数门槛（getNeutralCount 在玩家数 ≤ neutralMinPlayerCount 时返回 0），
        // 且中立池抽出来的可能是亲杀手（type 3）而非普通中立（type 2），
        // 导致持卡玩家的阵营槽位为 0、卡被退回。
        // 这里模仿强制位逻辑：凡是本局参与的持卡（CARD）玩家，其阵营在池中没有槽位时，
        // 直接从职业注册表补一个该阵营的角色进备选池，保证阵营卡必然可用。
        for (var entry : PlayerRoleWeightManager.ForcePlayerTeam.entrySet()) {
            var t = entry.getValue();
            if (t.type() != ForceTeamType.CARD)
                continue;
            boolean participating = unassignedPlayers.stream()
                    .anyMatch(p -> p.getUUID().equals(entry.getKey()));
            if (!participating)
                continue;
            int roleType = t.roleType();
            if (roleTypesCount.getOrDefault(roleType, 0) > 0)
                continue;
            SRERole spareRole = findSpareRoleByFactionType(roleType, crgmwc.getRoles());
            if (spareRole != null) {
                crgmwc.getRoles().add(spareRole);
                roleTypesCount.merge(roleType, 1, Integer::sum);
                Harpymodloader.LOGGER.info(
                        "[FactionCard] 自选模式阵营 {} 无自然刷新槽位，为持卡玩家补入 {}",
                        roleType, spareRole.identifier());
            }
        }

        // 保底
        for (var p : players) {
            if (PlayerRoleWeightManager.ForcePlayerTeam.containsKey(p.getUUID()))
                continue;
            var manager = PlayerRoleWeightManager.playerWeights.get(p.getUUID());
            if (manager != null) {
                if (manager.getStreakCount() >= serverWorld.random.nextInt(4, 7)) {
                    int highestWeightType = PlayerRoleWeightManager.getHighestScoredType(p.getUUID());
                    if (highestWeightType == manager.getLastAssignedFactionGroup())
                        continue;
                    PlayerRoleWeightManager.forceTeam(p.getUUID(), highestWeightType, ForceTeamType.ROLE_WEIGHTS);
                }
            }
        }

        {
            {
                // 分配forceTeam
                for (var entry : PlayerRoleWeightManager.ForcePlayerTeam.entrySet()) {
                    UUID playerUid = entry.getKey();
                    var selectedPlayer = unassignedPlayers.stream().filter((p) -> p.getUUID().equals(playerUid))
                            .findFirst().orElse(null);
                    if (selectedPlayer == null)
                        continue;
                    var t = entry.getValue();
                    int roleType = t.roleType();
                    Harpymodloader.LOGGER.debug(
                            "Assign player [{}] to {}",
                            playerUid,
                            roleType);
                    int nc = roleTypesCount.getOrDefault(roleType, 0);
                    if (nc > 0) {
                        roleTypesCount.put(roleType, nc - 1);
                        var ccca = CustomRoleGameModeTeamsPlayerComponent.KEY.get(selectedPlayer);
                        ccca.setSelected(false);
                        ccca.setTeamAndSync(roleType);
                        PlayerRoleWeightManager.addWeight(selectedPlayer, roleType, 1);
                        unassignedPlayers.remove(selectedPlayer);
                    } else {
                        PlayerRoleWeightManager.boostKillerSideAfterForceFailure(playerUid);
                        Harpymodloader.LOGGER.warn(
                                "Couldn't force player [{}]'s role to {} because there are no roles available for him.",
                                playerUid,
                                roleType);
                    }

                }
            }
        }

        Collections.shuffle(unassignedPlayers);
        ArrayList<Integer> roleSelector = new ArrayList<>();
        for (var t : roleTypesCount.entrySet()) {
            for (int i = 0; i < t.getValue(); i++) {
                roleSelector.add(t.getKey());
            }
        }
        Collections.shuffle(roleSelector);

        while (unassignedPlayers.size() > 0 && roleSelector.size() > 0) {
            int selectedRoleType = roleSelector.getFirst();
            Player selectedPlayer = super.pickPlayerWithProgressBias(serverWorld, unassignedPlayers, selectedRoleType);
            if (selectedPlayer != null) {
                unassignedPlayers.remove(selectedPlayer);
                var ccca = CustomRoleGameModeTeamsPlayerComponent.KEY.get(selectedPlayer);
                ccca.setSelected(false);
                ccca.setTeamAndSync(selectedRoleType);
                PlayerRoleWeightManager.addWeight(selectedPlayer, selectedRoleType, 1);

                roleSelector.removeFirst();
            }
        }
        for (var up : unassignedPlayers) {
            // 职业不够分配平民
            var ccca = CustomRoleGameModeTeamsPlayerComponent.KEY.get(up);
            ccca.setSelected(false);
            ccca.setTeamAndSync(1);
            PlayerRoleWeightManager.addWeight(up, 1, 1);
        }

        crgmwc.sync();
    }

    /**
     * 从职业注册表中找一个属于指定阵营、且本局尚未入选的角色作为补位。
     * 过滤条件与 SREMurderGameMode 建池一致：排除原版角色 / 其它模式专用角色 / 平民。
     */
    private static SRERole findSpareRoleByFactionType(int roleType, ArrayList<SRERole> currentRoles) {
        for (SRERole role : TMMRoles.ROLES.values()) {
            if (role == null || Harpymodloader.VANNILA_ROLES.contains(role))
                continue;
            if (role == TMMRoles.CIVILIAN || role.isOtherModeRole())
                continue;
            if (currentRoles.contains(role))
                continue;
            if (PlayerRoleWeightManager.getRoleType(role) == roleType)
                return role;
        }
        return null;
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
