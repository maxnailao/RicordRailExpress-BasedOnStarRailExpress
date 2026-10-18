package io.wifi.starrailexpress.game.modes;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.GameMode;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.*;
import io.wifi.starrailexpress.event.AllowGameEnd;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.utils.RoleInstance;
import io.wifi.starrailexpress.network.original.AnnounceWelcomePayload;
import io.wifi.starrailexpress.progression.ProgressionDataManager;
import io.wifi.starrailexpress.progression.ProgressionState.FactionCardType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.RoleWeightedUtil;
import org.agmas.harpymodloader.SREDisableManager;
import org.agmas.harpymodloader.commands.RoleCountManager;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;
import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import org.agmas.harpymodloader.events.ModifierAssigned;
import org.agmas.harpymodloader.events.OnGamePlayerRolesConfirm;
import org.agmas.harpymodloader.modded_murder.ForceTeamInfo.ForceTeamType;
import org.agmas.harpymodloader.modded_murder.PlayerRoleAssigner;
import org.agmas.harpymodloader.modded_murder.PlayerRoleWeightManager;
import org.agmas.harpymodloader.modded_murder.RoleAssignmentManager;
import org.agmas.harpymodloader.modded_murder.RoleAssignmentPool;
import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.harpymodloader.modifiers.SREModifier;
import org.agmas.noellesroles.commands.BroadcastCommand;
import org.agmas.noellesroles.role.ModRoles;

import java.util.*;
import java.util.stream.Collectors;

public class SREMurderGameMode extends GameMode {
    private static final int VIGILANTE_PASSIVE_MONEY_AMOUNT = 5;
    private static final int VIGILANTE_PASSIVE_MONEY_INTERVAL_TICKS = 15 * 20;

    public SREMurderGameMode(ResourceLocation identifier) {
        super(identifier, SREConfig.instance().defaultStartTimeForMurder, 6);
    }

    @Override
    public boolean shouldRecordPlayerStats() {
        return true;
    }

    public SREMurderGameMode(ResourceLocation identifier, int defaultStartTime, int minPlayerCount) {
        super(identifier, defaultStartTime, minPlayerCount);
    }

    @Override
    public void finalizeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        // 执行游戏结束时的函数
        executeFunction(serverWorld.getServer().createCommandSourceStack(), "harpymodloader:end_game");
        MurderTimeEventComponent.KEY.get(serverWorld).clear();

        // 将玩家从队伍中移除
        removePlayersFromTeam(serverWorld.getServer().createCommandSourceStack(), "harpymodloader_game");
        super.finalizeGame(serverWorld, gameWorldComponent);
    }

    @Override
    public void initializeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        // if (!Harpymodloader.isMojangVerify) {
        // return;
        // }

        Harpymodloader.refreshRoles();

        gameWorldComponent.clearRoleMap();

        // 将所有玩家添加到队伍中
        addPlayersToTeam(serverWorld.getServer().createCommandSourceStack(), players, "harpymodloader_game");

        // 执行游戏开始时的函数
        executeFunction(serverWorld.getServer().createCommandSourceStack(), "harpymodloader:start_game");
        MurderTimeEventComponent.KEY.get(serverWorld).initializeDefaults();

        Harpymodloader.setRoleMaximum(ModRoles.SHERIFF_ID, 100);
        assignRole(serverWorld, gameWorldComponent, players);
    }

    public static void assignRole(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        // 新的模块化角色分配流程
        Map<Player, SRERole> roleAssignments = assignRolesToPlayers(serverWorld, players);
        OnGamePlayerRolesConfirm.EVENT.invoker().beforeAssignRole(serverWorld, roleAssignments);
        // 计算有特殊角色的玩家数量（用于AnnounceWelcomePayload）
        long killCount = roleAssignments.values().stream()
                .filter(role -> role != null && role != TMMRoles.CIVILIAN && role.canUseKiller())
                .count();

        // ── 诊断日志：定位"13 人局刷出 3 个杀手位/3 个警长" ──
        // 把"预算"（各阵营该分几个）和"实得"（实际分到几个）一起打出来，
        // 下次复现直接把这几行发出来即可定位差距来自哪一步。
        {
            int n = players.size();
            int budgetK = RoleCountManager.getKillerCount(n);
            int budgetV = RoleCountManager.getVigilanteCount(n);
            int budgetN = RoleCountManager.getNeutralCount(n);
            long gotK = roleAssignments.values().stream()
                    .filter(r -> r != null && r.canUseKiller()).count();
            long gotV = roleAssignments.values().stream()
                    .filter(r -> r != null && r.isVigilanteTeam()).count();
            long gotN = roleAssignments.values().stream()
                    .filter(r -> r != null && !r.isInnocent() && !r.canUseKiller()
                            && !r.isVigilanteTeam()).count();
            io.wifi.starrailexpress.SRE.LOGGER.info(
                    "[RoleCount] 玩家 {} 预算(杀手={} 警长={} 中立={}) 实得(杀手={} 警长={} 中立={}) 强制职业={}",
                    n, budgetK, budgetV, budgetN, gotK, gotV, gotN,
                    Harpymodloader.FORCED_MODDED_ROLE_FLIP.size());
            // 逐个列出实际分配，便于核对到底谁多出来了
            roleAssignments.forEach((p, r) -> {
                if (r == null) {
                    return;
                }
                if (r.canUseKiller() || r.isVigilanteTeam() || !r.isInnocent()) {
                    io.wifi.starrailexpress.SRE.LOGGER.info(
                            "[RoleCount]   {} -> {}（杀手={} 警长={} 中立={} 占位={}）",
                            p.getName().getString(), r.identifier(), r.canUseKiller(),
                            r.isVigilanteTeam(), !r.isInnocent() && !r.canUseKiller() && !r.isVigilanteTeam(),
                            r.getOccupiedRoleCount());
                }
            });
        }

        // 统一应用角色分配并触发相应事件
        for (Map.Entry<Player, SRERole> entry : roleAssignments.entrySet()) {
            final var key = entry.getKey();
            final var value = entry.getValue();
            if (value != null) {
                gameWorldComponent.addRole(key, value, false);
                // 所有职业的初始物品已通过 INITIAL_ITEMS_MAP 在 assignModdedRole 事件中发放，此处跳过避免重复
                Harpymodloader.LOGGER.debug("Assigned role " + value.getIdentifier() + " to " + key.getName());
                if (value.canUseKiller()) {
                    SREPlayerShopComponent playerShopComponent = SREPlayerShopComponent.KEY.get(key);
                    if (playerShopComponent.balance < GameConstants.getMoneyStart())
                        playerShopComponent.setBalance(GameConstants.getMoneyStart());
                }
            } else {
                // 如果没有分配角色，则分配默认平民角色
                gameWorldComponent.addRole(key, TMMRoles.CIVILIAN, false);
                Harpymodloader.LOGGER
                        .debug("Assigned role " + TMMRoles.CIVILIAN.getIdentifier() + " to " + key.getName());
            }
        }

        gameWorldComponent.syncRoles();
        // 同步职业

        for (ServerPlayer player : players) {
            var role = gameWorldComponent.getRole(player);
            var roleType = PlayerRoleWeightManager.getRoleType(role);
            PlayerRoleWeightManager.addWeight(player, roleType, 1);
            // PlayerRoleWeightManager.
            ServerPlayNetworking.send(player,
                    new AnnounceWelcomePayload(role.getIdentifier().toString(), (int) killCount,
                            (int) (players.size() - killCount)));
            ModdedRoleAssigned.EVENT.invoker().assignModdedRole(player, role);
        }
        // 分配修饰符（修饰符放在职业分配后）

        int modifierRoleCount = (int) ((float) players.size()
                * HarpyModLoaderConfig.HANDLER.instance().modifierMultiplier);
        assignModifiers(modifierRoleCount, serverWorld, gameWorldComponent, players);

        Harpymodloader.FORCED_MODDED_ROLE.clear();
        Harpymodloader.FORCED_MODDED_ROLE_FLIP.clear();
        Harpymodloader.FORCED_MODDED_MODIFIER.clear();
        PlayerRoleWeightManager.ForcePlayerTeam.clear();
    }

    // 执行指定函数的辅助方法
    public void executeFunction(CommandSourceStack source, String function) {
        try {
            source.getServer().getCommands().performPrefixedCommand(source, "function " + function);
        } catch (Exception e) {
            Log.warn(LogCategory.GENERAL, "Failed to execute function: " + function + ", error: " + e.getMessage());
        }
    }

    // 将玩家添加到队伍的辅助方法
    public void addPlayersToTeam(CommandSourceStack source, List<ServerPlayer> players, String teamName) {
        try {
            var cmd = source.getServer().getCommands();
            // 首先尝试创建队伍（如果不存在）
            cmd.performPrefixedCommand(source,
                    "team add " + teamName);

            // 将所有玩家添加到队伍中
            cmd.performPrefixedCommand(source,
                    "team join " + teamName + " @a");

            cmd.performPrefixedCommand(source,
                    "team modify " + teamName + " seeFriendlyInvisibles false");
        } catch (Exception e) {
            Log.warn(LogCategory.GENERAL, "Failed to manage team: " + teamName + ", error: " + e.getMessage());
        }
    }

    // 将玩家从队伍中移除的辅助方法
    public void removePlayersFromTeam(CommandSourceStack source, String teamName) {
        try {
            // 将所有玩家从队伍中移除
            source.getServer().getCommands().performPrefixedCommand(source, "team empty " + teamName);

            // 删除队伍
            source.getServer().getCommands().performPrefixedCommand(source, "team remove " + teamName);
        } catch (Exception e) {
            Log.warn(LogCategory.GENERAL, "Failed to remove team: " + teamName + ", error: " + e.getMessage());
        }
    }

    public static void assignModifiers(int desiredModifierCount, ServerLevel serverWorld,
            SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        WorldModifierComponent worldModifierComponent = WorldModifierComponent.KEY.get(serverWorld);
        worldModifierComponent.getModifiers().clear();

        // 使用临时映射存储要添加的修饰符，避免在遍历过程中修改数据结构
        Map<UUID, List<SREModifier>> tempModifierAssignments = new HashMap<>();
        int maxModifiersPerPlayer = Math.max(0, HarpyModLoaderConfig.HANDLER.instance().modifierMaximum);
        var allModifiers = new ArrayList<>(HMLModifiers.MODIFIERS);
        int killerMods = (int) allModifiers.stream().filter(modifier -> modifier.killerOnly).count();
        Collections.shuffle(allModifiers);

        allModifiers.sort(Comparator.comparingInt(mod -> {
            if (mod.canOnlyBeAppliedTo != null && !mod.canOnlyBeAppliedTo.isEmpty()) {
                return mod.canOnlyBeAppliedTo.size();
            }
            return Integer.MAX_VALUE;
        }));

        // 修饰符轮换名单接管：仅当名单启用且管理员已在名单中配置了至少一个修饰符时，
        // 才由名单决定修饰符的启用/禁用（取代 disabledModifiers），但数量仍沿用 MODIFIER_MAX，
        // 地图限制也仍然生效。未配置任何修饰符时保持原有行为，避免老名单升级后修饰符全部消失。

        ArrayList<ServerPlayer> shuffledPlayers = new ArrayList<>(players);
        for (var mod : allModifiers) {
            Collections.shuffle(shuffledPlayers);
            Collections.shuffle(shuffledPlayers);

            int playersAssigned = 0;
            int specificDesiredRoleCount = desiredModifierCount;

            if (mod.killerOnly) {
                specificDesiredRoleCount = (int) Math.floor(Math.floor((double) players.size() / 7) / killerMods);
                specificDesiredRoleCount = Math.max(specificDesiredRoleCount, 1);
            }
            if (Harpymodloader.FORCED_MODDED_MODIFIER.containsKey(mod)) {
                for (ServerPlayer player : shuffledPlayers) {
                    if (Harpymodloader.FORCED_MODDED_MODIFIER.get(mod).contains(player.getUUID())) {
                        if (getAssignedModifierCount(tempModifierAssignments,
                                player.getUUID()) >= maxModifiersPerPlayer) {
                            continue;
                        }
                        // 临时存储，稍后统一添加
                        if (addModifierAssignment(tempModifierAssignments, player.getUUID(), mod)) {
                            // ModifierAssigned.EVENT.invoker().assignModifier(player, mod);
                            playersAssigned++;
                        }
                    }
                }
            }
            if (SREDisableManager.isModifierDisabled(mod)) {
                continue;
            }

            // 名单只决定修饰符是否启用（上面的过滤），数量始终沿用 MODIFIER_MAX，名单不再接管数量。
            int m_max = Harpymodloader.MODIFIER_MAX.getOrDefault(mod.identifier(), 1);
            int targetAssignments = specificDesiredRoleCount;

            if (m_max == -1 || m_max == 0)
                continue;
            if (m_max != -2) {
                targetAssignments = Math.min(targetAssignments, m_max);
            }
            if (playersAssigned >= targetAssignments) {
                continue;
            }

            List<ServerPlayer> candidates = new ArrayList<>(shuffledPlayers);
            candidates.removeIf(player -> !canAssignModifierToPlayer(mod, player, gameWorldComponent,
                    tempModifierAssignments, maxModifiersPerPlayer));
            Collections.shuffle(candidates);
            candidates.sort(Comparator.comparingInt(
                    player -> getAssignedModifierCount(tempModifierAssignments, player.getUUID())));

            for (ServerPlayer player : candidates) {
                if (playersAssigned >= targetAssignments) {
                    break;
                }
                // 临时存储，稍后统一添加
                if (addModifierAssignment(tempModifierAssignments, player.getUUID(), mod)) {
                    playersAssigned++;
                }
            }
        }

        // 统一将临时存储的修饰符添加到组件中
        for (Map.Entry<UUID, List<SREModifier>> entry : tempModifierAssignments.entrySet()) {
            UUID playerUuid = entry.getKey();
            for (SREModifier mod : entry.getValue()) {
                var p = serverWorld.getPlayerByUUID(playerUuid);
                worldModifierComponent.addModifier(playerUuid, mod, false);
                ModifierAssigned.EVENT.invoker().assignModifier(p, mod);
            }
        }

        // 等所有修饰符都添加完成后，再同步整个组件
        worldModifierComponent.sync();

        for (ServerPlayer player : players) {
            var modifiers = worldModifierComponent.getDisplayableModifiers(player);
            if (!modifiers.isEmpty()) {
                MutableComponent modifiersText = Component.translatable("announcement.star.modifier")
                        .withStyle(ChatFormatting.GRAY)
                        .append(ComponentUtils.formatList(modifiers, Component.literal(", "),
                                modifier -> modifier.getName(false).withColor(modifier.color)));
                player.displayClientMessage(modifiersText, true);
            } else {
                if (!HMLModifiers.MODIFIERS.isEmpty()) {
                    player.displayClientMessage(
                            Component.translatable("announcement.star.no_modifiers")
                                    .withStyle(ChatFormatting.DARK_GRAY),
                            true);
                }
            }
        }
    }

    private static boolean addModifierAssignment(Map<UUID, List<SREModifier>> modifierAssignments, UUID playerUuid,
            SREModifier modifier) {
        List<SREModifier> playerModifiers = modifierAssignments.computeIfAbsent(playerUuid, k -> new ArrayList<>());
        if (playerModifiers.contains(modifier)) {
            return false;
        }
        playerModifiers.add(modifier);
        return true;
    }

    private static boolean canAssignModifierToPlayer(SREModifier modifier, ServerPlayer player,
            SREGameWorldComponent gameWorldComponent, Map<UUID, List<SREModifier>> modifierAssignments,
            int maxModifiersPerPlayer) {
        UUID playerUuid = player.getUUID();
        if (getAssignedModifierCount(modifierAssignments, playerUuid) >= maxModifiersPerPlayer) {
            return false;
        }
        List<SREModifier> playerModifiers = modifierAssignments.get(playerUuid);
        if (playerModifiers != null) {
            if (playerModifiers.contains(modifier)) {
                return false;
            }
        }

        var role = gameWorldComponent.getRole(player);
        if (modifier.canOnlyBeAppliedTo != null && !modifier.canOnlyBeAppliedTo.isEmpty() && role != null
                && !modifier.canOnlyBeAppliedTo.contains(role)) {
            return false;
        }
        if (modifier.cannotBeAppliedTo != null && role != null && modifier.cannotBeAppliedTo.contains(role)) {
            return false;
        }
        if (modifier.killerOnly && (role == null || !role.canUseKiller())) {
            return false;
        }
        if (modifier.civilianOnly && !gameWorldComponent.isInnocent(player)) {
            return false;
        }
        if (modifier.notVigilante && gameWorldComponent.isVigilanteTeam(player)) {
            return false;
        }
        return true;
    }

    private static int getAssignedModifierCount(Map<UUID, List<SREModifier>> modifierAssignments, UUID playerUuid) {
        List<SREModifier> playerModifiers = modifierAssignments.get(playerUuid);
        return playerModifiers == null ? 0 : playerModifiers.size();
    }

    public static List<RoleInstance> getAllRoles(int killerCount, int vigilanteCount, int neutralsCount, int playerSize,
            int forcedRoleSize) {
        return getAllRoles(killerCount, vigilanteCount, neutralsCount, playerSize, forcedRoleSize, List.of());
    }

    /**
     * 新的模块化角色分配方法
     * 处理强制角色、计算各类型角色数量、创建角色池、分配角色以及处理关联角色
     */
    public static List<RoleInstance> getAllRoles(int killerCount, int vigilanteCount, int neutralsCount, int playerSize,
            int forcedRoleSize, List<SRERole> forcedRoles) {
        return getAllRoles(killerCount, vigilanteCount, neutralsCount, playerSize, forcedRoleSize, forcedRoles,
                List.of());
    }

    /**
     * 建池版本 + 自选主职业的绑定职业。
     *
     * <p>自选的主职业走 {@code forcedRolesMap} 直接指定给玩家、不进角色池，
     * 所以 {@code expandWithCompanionRoles} 看不到它，绑定职业不会生成。
     * 把 {@code selfSelectCompanions} 一起交下去，由 {@code expandWithCompanionRoles}
     * 的 {@code tryRemoveARole} 腾位（分 4 级退让），保证绑定职业一定能挤进去且总数不变。
     */
    public static List<RoleInstance> getAllRoles(int killerCount, int vigilanteCount, int neutralsCount, int playerSize,
            int forcedRoleSize, List<SRERole> forcedRoles, List<SRERole> selfSelectCompanions) {
        HarpyModLoaderConfig config = HarpyModLoaderConfig.HANDLER.instance();
        boolean enableCivilianInPool = config.enableCivilianInPool;
        RoleAssignmentPool killerPool = RoleAssignmentPool.create("Killer",
                role -> !Harpymodloader.VANNILA_ROLES.contains(role) &&
                        !role.isOtherModeRole() &&
                        !role.isNeutrals() && !role.isNeutralForKiller() &&
                        role.canUseKiller() &&
                        !role.isInnocent() &&
                        role != TMMRoles.CIVILIAN);
        RoleAssignmentPool vigilantePool = RoleAssignmentPool.create("Vigilante",
                role -> !Harpymodloader.VANNILA_ROLES.contains(role) &&
                        role.isVigilanteTeam() && !role.isOtherModeRole());
        // 中立池
        RoleAssignmentPool neutralsPool = RoleAssignmentPool.create("Neutrals",
                role -> (!Harpymodloader.VANNILA_ROLES.contains(role) &&
                        !role.isOtherModeRole() &&
                        ((!role.canUseKiller() &&
                                !role.isInnocent()) || role.isNeutrals())
                        &&
                        role != TMMRoles.CIVILIAN));
        // 平民池（只包含真正的"平民"角色，例如医生等）
        // 当 enableCivilianInPool 开启时，允许 sre:civilian 进入池中
        RoleAssignmentPool civilianPool = RoleAssignmentPool.create("Civilian",
                role -> !Harpymodloader.VANNILA_ROLES.contains(role) &&
                        !role.isOtherModeRole() &&
                        !role.isVigilanteTeam() &&
                        !role.canUseKiller() &&
                        !role.isNeutrals() &&
                        role.isInnocent() &&
                        (enableCivilianInPool || role != TMMRoles.CIVILIAN));
        // 如果开启 civilian 进池，设置最大数量为 1
        if (enableCivilianInPool) {
            Harpymodloader.setRoleMaximum(TMMRoles.CIVILIAN.getIdentifier(), 1);
        }
        return getAllRoles(killerCount, vigilanteCount, neutralsCount, playerSize, forcedRoleSize, killerPool,
                neutralsPool, vigilantePool, civilianPool, true, forcedRoles, selfSelectCompanions);
    }

    public static List<RoleInstance> getAllRoles(int killerCount, int vigilanteCount, int neutralsCount, int playerSize,
            int forcedRoleSize, RoleAssignmentPool killerPool, RoleAssignmentPool neutralsPool,
            RoleAssignmentPool vigilantePool, RoleAssignmentPool civilianPool, boolean haveOccupationRoles) {
        return getAllRoles(killerCount, vigilanteCount, neutralsCount, playerSize, forcedRoleSize, killerPool,
                neutralsPool, vigilantePool, civilianPool, haveOccupationRoles, List.of());
    }

    public static List<RoleInstance> getAllRoles(int killerCount, int vigilanteCount, int neutralsCount, int playerSize,
            int forcedRoleSize, RoleAssignmentPool killerPool, RoleAssignmentPool neutralsPool,
            RoleAssignmentPool vigilantePool, RoleAssignmentPool civilianPool, boolean haveOccupationRoles,
            List<SRERole> forcedRoles) {
        return getAllRoles(killerCount, vigilanteCount, neutralsCount, playerSize, forcedRoleSize, killerPool,
                neutralsPool, vigilantePool, civilianPool, haveOccupationRoles, forcedRoles, List.of(), 10);
    }

    /**
     * 带「自选主职业的绑定职业」的版本。
     *
     * <p>自选的主职业走 {@code forcedRolesMap} 直接指定给玩家、不进角色池，
     * 所以 {@code expandWithCompanionRoles} 看不到它，绑定职业不会生成。
     * 这里把自选带出的绑定职业一起交给 {@code expandWithCompanionRoles} ——
     * 它自带腾位逻辑（{@code tryRemoveARole} 分 4 级退让：同阵营 → 忽略中立细分 → 只看杀手标记 → 任意），
     * 能保证绑定职业一定挤得进去，而且总角色数不变。
     */
    public static List<RoleInstance> getAllRoles(int killerCount, int vigilanteCount, int neutralsCount, int playerSize,
            int forcedRoleSize, RoleAssignmentPool killerPool, RoleAssignmentPool neutralsPool,
            RoleAssignmentPool vigilantePool, RoleAssignmentPool civilianPool, boolean haveOccupationRoles,
            List<SRERole> forcedRoles, List<SRERole> selfSelectCompanions) {
        return getAllRoles(killerCount, vigilanteCount, neutralsCount, playerSize, forcedRoleSize, killerPool,
                neutralsPool, vigilantePool, civilianPool, haveOccupationRoles, forcedRoles,
                selfSelectCompanions, 10);
    }

    /**
     * 新的模块化角色分配方法
     * 处理强制角色、计算各类型角色数量、创建角色池、分配角色以及处理关联角色
     */
    public static List<RoleInstance> getAllRoles(int killerCount, int vigilanteCount, int neutralsCount, int playerSize,
            int forcedRoleSize, RoleAssignmentPool killerPool, RoleAssignmentPool neutralsPool,
            RoleAssignmentPool vigilantePool, RoleAssignmentPool civilianPool, boolean haveOccupationRoles,
            List<SRERole> forcedRoles,
            int maxDepth) {
        return getAllRoles(killerCount, vigilanteCount, neutralsCount, playerSize, forcedRoleSize, killerPool,
                neutralsPool, vigilantePool, civilianPool, haveOccupationRoles, forcedRoles, List.of(), maxDepth);
    }

    public static List<RoleInstance> getAllRoles(int killerCount, int vigilanteCount, int neutralsCount, int playerSize,
            int forcedRoleSize, RoleAssignmentPool killerPool, RoleAssignmentPool neutralsPool,
            RoleAssignmentPool vigilantePool, RoleAssignmentPool civilianPool, boolean haveOccupationRoles,
            List<SRERole> forcedRoles, List<SRERole> selfSelectCompanions,
            int maxDepth) {
        // 第一步，减少强制职业
        if (forcedRoles != null) {
            for (var role : forcedRoles) {
                if (role.isKiller()) {
                    killerPool.removeRoleCount(role, 1);
                } else if (role.isNeutrals()) {
                    neutralsPool.removeRoleCount(role, 1);
                } else if (role.isVigilanteTeam()) {
                    vigilantePool.removeRoleCount(role, 1);
                } else {
                    civilianPool.removeRoleCount(role, 1);
                }
            }
        }
        // 第二步：创建角色池并分配角色
        // 杀手池
        if (playerSize - forcedRoleSize <= 0)
            return List.of();
        List<SRERole> assignedKillers = killerPool.selectRoles(killerCount);

        // 警卫池 - 使用无限重复模式，因为警卫职业数量有限
        Harpymodloader.setRoleMaximum(ModRoles.SHERIFF_ID, 100);

        List<SRERole> assignedVigilantes = vigilantePool.selectRoles(vigilanteCount);

        List<SRERole> assignedNatures = neutralsPool.selectRoles(neutralsCount);

        // 处理 setOccupiedRoleCount(0) 的职业：这些职业不占用原有杀手/中立/警长名额，
        // 而是替换一个平民位。统计各阵营中 occupied=0 的数量，额外补充同阵营职业并减少平民数。
        // 注意：平民（非警长阵营）不应该设置 occupiedRoleCount(0)，此处做安全兜底改为1，避免平民自己"替换"平民。
        long zeroKillers = assignedKillers.stream().filter(r -> r.getOccupiedRoleCount() <= 0).count();
        long zeroVigilantes = assignedVigilantes.stream().filter(r -> r.getOccupiedRoleCount() <= 0).count();
        long zeroNeutrals = assignedNatures.stream().filter(r -> r.getOccupiedRoleCount() <= 0).count();

        if (zeroKillers > 0) {
            assignedKillers.addAll(killerPool.selectRoles((int) zeroKillers));
        }
        if (zeroVigilantes > 0) {
            assignedVigilantes.addAll(vigilantePool.selectRoles((int) zeroVigilantes));
        }
        if (zeroNeutrals > 0) {
            assignedNatures.addAll(neutralsPool.selectRoles((int) zeroNeutrals));
        }

        // ===== 领袖特判 =====
        // 领袖需要非杀手方中立（type 2）职业作为追随者。
        // 若本次抽选出的中立职业中，除领袖外没有其它 type 2 职业，说明本局领袖没有可招募对象，
        // 将其从池中移除，并补抽等量的中立职业（排除领袖），保证中立名额数量不变。
        long leaderDrawn = assignedNatures.stream()
                .filter(r -> r != null && ModRoles.LEADER_ID.equals(r.identifier())).count();
        if (leaderDrawn > 0) {
            boolean hasOtherNonKillerNeutral = assignedNatures.stream()
                    .anyMatch(r -> r != null && !ModRoles.LEADER_ID.equals(r.identifier())
                            && PlayerRoleWeightManager.getRoleType(r) == 2);
            if (!hasOtherNonKillerNeutral) {
                assignedNatures.removeIf(r -> r != null && ModRoles.LEADER_ID.equals(r.identifier()));
                assignedNatures.addAll(neutralsPool.selectRoles((int) leaderDrawn,
                        r -> r != null && !ModRoles.LEADER_ID.equals(r.identifier())));
            }
        }

        // 第三步：计算平民数量（只分配基础非平民角色，不包含补充的平民角色）
        int assignedSpecialCount = assignedKillers.size() + assignedVigilantes.size() + assignedNatures.size();
        int civilianCount = playerSize - assignedSpecialCount - forcedRoleSize;

        civilianPool.setIgnoreRoleOccupiedCount(true);
        List<SRERole> assignedCivilians = civilianPool.selectRoles(civilianCount);

        // 平民（非警长阵营）如果设置了 setOccupiedRoleCount(0)，兜底改为1，避免平民自身触发"替换平民"逻辑
        for (SRERole civilian : assignedCivilians) {
            if (!civilian.isVigilanteTeam() && civilian.getOccupiedRoleCount() <= 0) {
                civilian.setOccupiedRoleCount(1);
            }
        }

        // 第四步：合并所有分配的角色（包括处理关联角色）
        List<SRERole> allRoles = new ArrayList<>();
        allRoles.addAll(assignedKillers);
        allRoles.addAll(assignedVigilantes);
        allRoles.addAll(assignedNatures);
        allRoles.addAll(assignedCivilians);

        // 展开关联角色
        List<RoleInstance> roleInstantList = new ArrayList<>();

        for (SRERole role : allRoles) {
            roleInstantList.add(new RoleInstance(UUID.randomUUID(), role));
        }
        // 不展开，仅在父级展开
        List<RoleInstance> newRoleInstantList = RoleAssignmentManager.removeOpposingJobs(roleInstantList, killerPool,
                neutralsPool,
                vigilantePool, civilianPool, false, maxDepth);
        List<RoleInstance> resultRoleInstances = newRoleInstantList;
        if (haveOccupationRoles) {
            resultRoleInstances = RoleAssignmentManager.expandWithCompanionRoles(newRoleInstantList);
        }

        // 自选主职业的绑定职业：先追加进名单，再走一次 expandWithCompanionRoles
        // —— 它内部的 tryRemoveARole 会腾出空位（不会让总角色数变多）。
        // 自选主职业本身不在池里，所以 expandWithCompanionRoles 看不到它，必须在这里手动触发。
        if (selfSelectCompanions != null && !selfSelectCompanions.isEmpty()) {
            List<RoleInstance> withSelfSelect = new ArrayList<>(resultRoleInstances);
            for (SRERole companion : selfSelectCompanions) {
                if (companion == null) {
                    continue;
                }
                if (withSelfSelect.stream().anyMatch(ri -> ri.role() != null
                        && ri.role().identifier().equals(companion.identifier()))) {
                    continue; // 已经在名单里，不重复补
                }
                withSelfSelect.add(new RoleInstance(UUID.randomUUID(), companion));
            }
            if (withSelfSelect.size() != resultRoleInstances.size()) {
                resultRoleInstances = RoleAssignmentManager.expandWithCompanionRoles(withSelfSelect);
                for (SRERole companion : selfSelectCompanions) {
                    if (companion != null) {
                        Harpymodloader.LOGGER.info(
                                "[SelfSelect] 谋杀模式因自选主职业而补入绑定职业 {}", companion.identifier());
                    }
                }
            }
        }

        // ── 关键：剔除「已经直接指定给玩家的职业」在池子里的副本 ──
        //
        // 自选/强制职业不进池子（由 forcedRolesMap 直接指定给玩家），
        // 但绑定展开会顺着"另一半的伙伴"把它再补回来：
        //   自选迪奥（直接给玩家）→ 展开承太郎 → 承太郎的伙伴是迪奥 → 池子里多一个迪奥
        //   → 最后场上出现**两个迪奥**（实测 bug）。
        // expandWithCompanionRoles 内部的按名额去重看不到这种重复，因为被自选的那份
        // 根本不在它的列表里。
        resultRoleInstances = dropRolesAssignedToPlayers(resultRoleInstances, forcedRoles);

        // ── 硬性收敛：池内角色数不得超过 `玩家数 - 强制职业数` ──
        //
        // 这是轮选模式的做法（LightningDraftState 里补位后总量受 rolePool 约束），
        // 谋杀模式以前没有，于是出现"13 人局刷出 3 个杀手/3 个警长"这类超标。
        //
        // 为什么会超标：各阵营槽位数是**独立**算的（killerCount / vigilanteCount /
        // neutralsCount 各自 selectRoles），而池内职业可能带 occupiedRoleCount(2)
        //（布袋鬼 / 诡客 / 迪奥），`selectRoles` 又允许"挤掉已选的换入"（else 分支
        // `selected.remove(0)`），再加上绑定职业补位 / expandWithCompanionRoles 腾位，
        // 最终总数可能比玩家数多出 1。
        //
        // ★ 裁剪时必须**跳过绑定职业**：expandWithCompanionRoles 是把绑定职业
        //   **追加在列表末尾**的，如果无脑从尾部裁，第一个被剪掉的就是刚补进来的绑定职业
        //   （实测：自选布袋鬼 → 补入诡客 → 尾部裁剪把诡客删了 → 警长阵营没有诡客）。
        //   绑定职业是主职业的硬依赖，宁可多留一个也不能剪掉它。
        java.util.Set<net.minecraft.resources.ResourceLocation> companionIds = new java.util.HashSet<>();
        if (selfSelectCompanions != null) {
            for (SRERole c : selfSelectCompanions) {
                if (c != null) {
                    companionIds.add(c.identifier());
                }
            }
        }
        int maxPoolSize = Math.max(0, playerSize - forcedRoleSize);
        if (resultRoleInstances.size() > maxPoolSize) {
            int overflow = resultRoleInstances.size() - maxPoolSize;
            Harpymodloader.LOGGER.warn(
                    "[RoleCount] 角色池 {} 个 > 上限 {} 个（玩家 {} - 强制 {}），需要裁 {} 个",
                    resultRoleInstances.size(), maxPoolSize, playerSize, forcedRoleSize, overflow);
            // 从尾部往前找**非绑定职业**来裁；绑定职业一律保留
            for (int i = 0; i < overflow; i++) {
                int victim = -1;
                for (int j = resultRoleInstances.size() - 1; j >= 0; j--) {
                    SRERole r = resultRoleInstances.get(j).role();
                    if (r != null && !companionIds.contains(r.identifier())) {
                        victim = j;
                        break;
                    }
                }
                if (victim < 0) {
                    Harpymodloader.LOGGER.warn(
                            "[RoleCount]   剩下的都是绑定职业，停止裁剪（宁多勿剪，保护 {}）", companionIds);
                    break;
                }
                Harpymodloader.LOGGER.warn("[RoleCount]   裁掉 {}",
                        resultRoleInstances.get(victim).role().identifier());
                resultRoleInstances.remove(victim);
            }
        }

        int needCivilian = (playerSize - forcedRoleSize) - resultRoleInstances.size();
        for (int i = 0; i < needCivilian; i++)
            resultRoleInstances.add(new RoleInstance(UUID.randomUUID(), TMMRoles.CIVILIAN));
        return resultRoleInstances;
    }

    /**
     * 把「自选职业带出来的绑定职业」合并进本局角色名单。
     *
     * <p><b>已被 {@code getAllRoles(..., selfSelectCompanions)} 取代</b>：
     * 旧实现只会去找 {@code TMMRoles.CIVILIAN} 来替换，一旦本局没有平民位就
     * 直接放弃（日志「没有平民空位可替换」），迪奥→承太郎 这类绑定就永远不生成。
     * 现在统一交给 {@code RoleAssignmentManager.expandWithCompanionRoles}，
     * 它的 {@code tryRemoveARole} 分 4 级退让，任意一个角色都能被腾出来。
     */
    @Deprecated
    private static void mergeCompanionRoles(List<RoleInstance> roles, List<SRERole> companions) {
        if (roles == null || companions == null || companions.isEmpty()) {
            return;
        }
        for (SRERole companion : companions) {
            if (companion == null) {
                continue;
            }
            final var cid = companion.identifier();
            // 已经在名单里就不重复补（例如另一位玩家随机到了同一个职业）
            if (roles.stream().anyMatch(ri -> ri.role().identifier().equals(cid))) {
                continue;
            }
            // 找一个平民空位来替换；没有平民位说明这一局排满了，放弃补位
            int idx = -1;
            for (int i = roles.size() - 1; i >= 0; i--) {
                if (roles.get(i).role() == TMMRoles.CIVILIAN) {
                    idx = i;
                    break;
                }
            }
            if (idx < 0) {
                Harpymodloader.LOGGER.warn(
                        "[SelfSelect] 没有平民空位可替换，绑定职业 {} 无法生成", cid);
                break;
            }
            roles.set(idx, new RoleInstance(UUID.randomUUID(), companion));
            Harpymodloader.LOGGER.info("[SelfSelect] 因自选主职业而补入绑定职业 {}", cid);
        }
    }

    /**
     * 收集本局「自选职业」带出来的绑定职业。
     *
     * <p>只有被自选（强制占位）的主职业才会在这里贡献绑定职业。
     * "角色上限依然生效"：绑定职业自身名额已满时不再补，避免超出上限。
     */
    private static List<SRERole> collectSelfSelectedCompanions(Map<UUID, SRERole> forcedRolesMap) {
        List<SRERole> out = new ArrayList<>();
        for (SRERole main : forcedRolesMap.values()) {
            if (main == null) {
                continue;
            }
            var companions = org.agmas.harpymodloader.Harpymodloader.getOccupationRoles(main);
            if (companions == null) {
                continue;
            }
            for (SRERole companion : companions) {
                if (companion == null) {
                    continue;
                }
                // 上限保护：本局该绑定职业已经够人了就不再补
                int cap = Harpymodloader.getRoleCapacity(companion);
                long already = out.stream().filter(r -> r.identifier().equals(companion.identifier())).count();
                if (already >= cap) {
                    continue;
                }
                if (out.stream().anyMatch(r -> r.identifier().equals(companion.identifier()))) {
                    continue;
                }
                out.add(companion);
            }
        }
        return out;
    }

    /**
     * 剔除「已经直接指定给玩家的职业」在角色池里的副本。
     *
     * <p>自选 / 强制职业不进池子 —— 它们由 {@code forcedRolesMap} 直接指定给玩家，
     * 所以池子里再出现一份同职业，就等于场上多了一个同名角色。
     *
     * <p>为什么会有副本：绑定是双向的，展开时顺着"另一半的伙伴"又补了回来。
     * 实测链路：自选迪奥（直接给玩家）→ 补入伙伴承太郎 → 承太郎的伙伴是迪奥
     * → 池子里多一个迪奥 → 最终场上**两个迪奥**。
     * {@code expandWithCompanionRoles} 内部虽有按名额去重，但它看不到不在列表里的那份。
     *
     * @param roles       角色池（会被复制，不就地修改）
     * @param assignedRoles 已经被玩家占用的职业
     */
    private static List<RoleInstance> dropRolesAssignedToPlayers(List<RoleInstance> roles,
            List<SRERole> assignedRoles) {
        if (roles == null || roles.isEmpty() || assignedRoles == null || assignedRoles.isEmpty()) {
            return roles;
        }
        java.util.Set<net.minecraft.resources.ResourceLocation> taken = new java.util.HashSet<>();
        for (SRERole r : assignedRoles) {
            if (r != null) {
                taken.add(r.identifier());
            }
        }
        List<RoleInstance> out = new ArrayList<>(roles.size());
        for (RoleInstance ri : roles) {
            SRERole r = ri.role();
            if (r != null && taken.contains(r.identifier())) {
                Harpymodloader.LOGGER.warn(
                        "[RolePool] 池内出现已被自选占用的 {}，丢弃（否则场上会有两个同职业）",
                        r.identifier());
                continue;
            }
            out.add(ri);
        }
        return out;
    }

    private static Map<Player, SRERole> assignRolesToPlayers(ServerLevel serverWorld, List<ServerPlayer> players) {
        Map<Player, SRERole> roleAssignments = new HashMap<>();
        for (Player player : players) {
            roleAssignments.put(player, null);
        }

        // 第一步：处理强制分配的角色
        Map<UUID, SRERole> forcedRolesMap = new HashMap<>(Harpymodloader.FORCED_MODDED_ROLE_FLIP);
        List<SRERole> forcedRoles = new ArrayList<>();
        int killerCount = RoleCountManager.getKillerCount(players.size());
        int vigilanteCount = RoleCountManager.getVigilanteCount(players.size());
        int neutralsCount = RoleCountManager.getNeutralCount(players.size());

        // 处理强制分配的角色，减少对应角色类型的数量需求
        //
        // ★ 按 occupiedRoleCount 扣，不是按个数扣 1。
        //   语义就是"占用几个角色槽位"：布袋鬼 / 诡客 / 迪奥 都是 setOccupiedRoleCount(2)，
        //   一个人吃满 2 个名额。
        //   13 人局预算 killer=2 / vigilante=2 / neutral=2：
        //     自选布袋鬼（occ=2）→ killer 2-2=0 → **场上只有布袋鬼一个狼**（期望值）
        //     只扣 1 的话 → killer 剩 1 → 还会再刷一个狼，变成"布袋鬼 + 另一个狼"
        //   角色总数依然守恒：平民 = 13 - (0+2+2) - 1 = 8，合计 1+4+8 = 13。
        for (Map.Entry<UUID, SRERole> entry : forcedRolesMap.entrySet()) {
            Player player = serverWorld.getPlayerByUUID(entry.getKey());
            if (player != null) {
                SRERole role = entry.getValue();
                if (role != null) {
                    forcedRoles.add(role);
                    roleAssignments.put(player, role);

                    // 占用的槽位数（默认 1；布袋鬼这类是 2）
                    int occupied = Math.max(1, role.getOccupiedRoleCount());

                    // 根据角色类型减少对应的数量需求
                    if (role.canUseKiller()) {
                        killerCount -= occupied;
                    } else if (role.isVigilanteTeam()) {
                        vigilanteCount -= occupied;
                    } else if (!role.isInnocent()) {
                        neutralsCount -= occupied;
                    }

                    // ★ 自选的若是「绑定对」的一员，另一半的阵营名额也要一起预留。
                    //   绑定双向（布袋鬼←→诡客、重刑犯←→狱警…），自选任意一方另一方都会补进来；
                    //   只扣自选方的名额，另一半的阵营就会多抽一个普通角色
                    //   （实测：自选诡客 → 多刷一个巫师、却缺布袋鬼）。
                    for (SRERole partner : org.agmas.harpymodloader.Harpymodloader
                            .getOccupationRoles(role)) {
                        if (partner == null || partner == role || forcedRoles.contains(partner)) {
                            continue;
                        }
                        int pOcc = Math.max(1, partner.getOccupiedRoleCount());
                        if (partner.canUseKiller()) {
                            killerCount -= pOcc;
                        } else if (partner.isVigilanteTeam()) {
                            vigilanteCount -= pOcc;
                        } else if (!partner.isInnocent()) {
                            neutralsCount -= pOcc;
                        }
                        Harpymodloader.LOGGER.info(
                                "[SelfSelect] 绑定对另一半 {} 也预留 {} 个名额"
                                        + "（杀手={} 警长={} 中立={}）→ 剩余 {}/{}/{}",
                                partner.identifier(), pOcc, partner.canUseKiller(),
                                partner.isVigilanteTeam(),
                                !partner.isInnocent() && !partner.canUseKiller()
                                        && !partner.isVigilanteTeam(),
                                killerCount, vigilanteCount, neutralsCount);
                    }
                }
            }
        }

        // 确保数量不为负数
        killerCount = Math.max(0, killerCount);
        vigilanteCount = Math.max(0, vigilanteCount);
        neutralsCount = Math.max(0, neutralsCount);

        // 自选职业带出来的绑定职业（毒师→医生、悍匪→钳工、迪奥→承太郎…）
        List<SRERole> selfSelectCompanions = collectSelfSelectedCompanions(forcedRolesMap);

        List<RoleInstance> expandedRoles = getAllRoles(killerCount, vigilanteCount, neutralsCount, players.size(),
                forcedRolesMap.size(), forcedRoles, selfSelectCompanions);
        RandomSource random = serverWorld.random;
        // 第五步：为未分配的玩家分配角色
        List<ServerPlayer> unassignedPlayers = new ArrayList<>();
        for (ServerPlayer player : players) {
            if (roleAssignments.get(player) == null) {
                unassignedPlayers.add(player);
            }
        }
        // 保底
        for (var p : players) {
            if (PlayerRoleWeightManager.ForcePlayerTeam.containsKey(p.getUUID()))
                continue;
            var manager = PlayerRoleWeightManager.playerWeights.get(p.getUUID());
            if (manager != null) {
                if (manager.getStreakCount() >= random.nextInt(4, 7)) {
                    int highestWeightType = PlayerRoleWeightManager.getHighestScoredType(p.getUUID());
                    if (highestWeightType == manager.getLastAssignedFactionGroup())
                        continue;
                    PlayerRoleWeightManager.forceTeam(p.getUUID(), highestWeightType, ForceTeamType.ROLE_WEIGHTS);
                }
            }
        }
        // 创建权重分布用于分配展开后的角色
        List<Map.Entry<RoleInstance, Float>> roleWeights = new ArrayList<>();

        for (var role : expandedRoles) {
            roleWeights.add(new AbstractMap.SimpleEntry<>(role,
                    HarpyModLoaderConfig.HANDLER.instance().roleWeights.getOrDefault(role.role().identifier(), 1f)));
        }
        Collections.shuffle(roleWeights);
        final var collect = roleWeights
                .stream()
                .collect(Collectors.toMap(
                        a -> a.getKey(),
                        a -> a.getValue(),
                        (existing, replacement) -> existing, // 如果键重复，保留第一个值
                        LinkedHashMap::new));
        var hashMap = new LinkedHashMap<>(collect);
        {
            var roleSelectors = new HashMap<Integer, RoleWeightedUtil>();
            {
                var roleIdToRoleMaps = new HashMap<Integer, HashMap<RoleInstance, Float>>();
                for (var entry : hashMap.entrySet()) {
                    var role = entry.getKey();
                    Float roleWeight = entry.getValue();
                    int roleType = PlayerRoleWeightManager.getRoleType(role.role());
                    if (!roleIdToRoleMaps.containsKey(roleType)) {
                        roleIdToRoleMaps.put(roleType, new HashMap<>());
                    }
                    roleIdToRoleMaps.get(roleType).put(role, roleWeight);
                }
                for (var entry : roleIdToRoleMaps.entrySet()) {
                    roleSelectors.putIfAbsent(entry.getKey(), new RoleWeightedUtil(entry.getValue()));
                }
            }
            {
                // 分配forceTeam
                for (var entry : PlayerRoleWeightManager.ForcePlayerTeam.entrySet()) {
                    UUID playerUid = entry.getKey();
                    var selectedPlayer = unassignedPlayers.stream().filter((p) -> p.getUUID().equals(playerUid))
                            .findFirst().orElse(null);
                    if (selectedPlayer == null)
                        continue;
                    var forceTeamInfo = entry.getValue();
                    int roleType = forceTeamInfo.roleType();
                    var roleSelector = roleSelectors.get(roleType);
                    RoleInstance roleInstant = null;
                    if (roleSelector != null) {
                        roleInstant = roleSelector.selectRandomKeyBasedOnWeightsAndRemoved();
                    }
                    SRERole selectedRole = null;
                    if (roleInstant != null) {
                        hashMap.remove(roleInstant);
                        selectedRole = roleInstant.role();
                        roleAssignments.put(selectedPlayer, selectedRole);
                        unassignedPlayers.remove(selectedPlayer);
                        Harpymodloader.LOGGER.debug(
                                "Assign player [{}] to {} ({})",
                                playerUid, selectedRole.getIdentifier().toString(),
                                roleType);
                    } else {
                        PlayerRoleWeightManager.boostKillerSideAfterForceFailure(playerUid);
                        Harpymodloader.LOGGER.warn(
                                "Couldn't force player [{}]'s role to {} because there are no roles available for him.",
                                playerUid,
                                roleType);
                        FactionCardType cardType = FactionCardType.fromInt(roleType);
                        if (cardType != FactionCardType.NONE && forceTeamInfo.type() == ForceTeamType.CARD) {
                            ProgressionDataManager.addFactionCard((ServerPlayer) selectedPlayer, cardType, 1);
                            BroadcastCommand.BroadcastMessage(selectedPlayer,
                                    Component.translatable("message.sre.pass.faction.assign_failed")
                                            .withStyle(ChatFormatting.RED));
                        }
                    }
                }
            }
        }
        RoleWeightedUtil roleSelector = new RoleWeightedUtil(hashMap);
        // 分配展开后的角色给未分配的玩家

        Collections.shuffle(unassignedPlayers);
        while (unassignedPlayers.size() > 0 && roleSelector.size() > 0) {
            RoleInstance roleInstant = roleSelector.selectRandomKeyBasedOnWeightsAndRemoved();
            SRERole selectedRole = null;
            if (roleInstant != null) {
                selectedRole = roleInstant.role();
            }
            if (selectedRole != null) {
                int selectedRoleType = PlayerRoleWeightManager.getRoleType(selectedRole);
                Player selectedPlayer = pickPlayerWithProgressBias(serverWorld, unassignedPlayers, selectedRoleType);
                if (selectedPlayer != null) {
                    unassignedPlayers.remove(selectedPlayer);
                    roleAssignments.put(selectedPlayer, selectedRole);
                    ProgressionDataManager.onRoleAssigned((ServerPlayer) selectedPlayer, selectedRole);
                }
            }
        }
        for (var up : unassignedPlayers) {
            // 职业不够分配平民
            roleAssignments.put(up, TMMRoles.CIVILIAN);
            ProgressionDataManager.onRoleAssigned((ServerPlayer) up, TMMRoles.CIVILIAN);
        }

        // ── 终局校验①：同一职业的人数不得超过它的名额上限（ROLE_MAX）──
        //
        // "使用自选卡也要满足职业刷新规则"：迪奥 / 布袋鬼 这类名额上限为 1 的职业，
        // 一局里只能有一个。绑定展开、自选占位、自然抽取三条路径都可能导致重复
        //（实测：自选迪奥 → 场上两个迪奥），这里统一兜底收敛。
        // 自选占位者与成对职业不动：自选是玩家花卡换来的；成对职业拆开就等于拆配对。
        {
            Map<net.minecraft.resources.ResourceLocation, Integer> counts = new HashMap<>();
            for (SRERole r : roleAssignments.values()) {
                if (r != null) {
                    counts.merge(r.identifier(), 1, Integer::sum);
                }
            }
            for (var e : new ArrayList<>(roleAssignments.entrySet())) {
                SRERole r = e.getValue();
                if (r == null || r == TMMRoles.CIVILIAN) {
                    continue;
                }
                int cap = Math.max(1, Harpymodloader.getRoleCapacity(r));
                int count = counts.getOrDefault(r.identifier(), 0);
                if (count <= cap) {
                    continue;
                }
                if (Harpymodloader.FORCED_MODDED_ROLE_FLIP.containsKey(e.getKey().getUUID())) {
                    continue; // 自选的保留（玩家花了卡）
                }
                // 注意：这里**不做成对保护**。
                // 名额上限（ROLE_MAX）是权威规则 —— 迪奥上限 1 就只允许 1 个。
                // 如果因为"承太郎在场"就认为迪奥成对、可以被豁免，那第二个迪奥
                // 就会躲过收敛，等于这个兜底形同虚设（推演直接暴露过这一点）。
                // 成对保护只用于校验②的阵营预算（那里多 1 个人不拆配对更重要）。
                counts.merge(r.identifier(), -1, Integer::sum);
                roleAssignments.put(e.getKey(), TMMRoles.CIVILIAN);
                Harpymodloader.LOGGER.warn("[RoleCount] {} 超出名额上限 {}（共 {} 个），降级为平民",
                        r.identifier(), cap, count);
            }
        }

        // ── 终局校验②：各阵营实际人数不得超过预算 ──
        //
        // 这是轮选模式的思路搬到谋杀模式：轮选靠 rolePool 的总量约束住人数，
        // 谋杀模式以前没有任何终局校验，于是可能出现"13 人局 3 个杀手 / 3 个警长"。
        // 杀手/警长是**独立**取池的，池内又有 occupiedRoleCount(2) 的职业（布袋鬼/诡客/迪奥），
        // 加上绑定补位腾位，最终可能多出一个人。这里兜底：超出的降级为平民。
        {
            int n = players.size();
            int budgetKiller = RoleCountManager.getKillerCount(n);
            int budgetVigilante = RoleCountManager.getVigilanteCount(n);
            int budgetNeutral = RoleCountManager.getNeutralCount(n);

            int overK = (int) roleAssignments.values().stream()
                    .filter(r -> r != null && r.canUseKiller()).count() - budgetKiller;
            int overV = (int) roleAssignments.values().stream()
                    .filter(r -> r != null && r.isVigilanteTeam()).count() - budgetVigilante;
            int overN = (int) roleAssignments.values().stream()
                    .filter(r -> r != null && !r.isInnocent() && !r.canUseKiller() && !r.isVigilanteTeam())
                    .count() - budgetNeutral;

            if (overK > 0 || overV > 0 || overN > 0) {
                Harpymodloader.LOGGER.warn(
                        "[RoleCount] 超标(杀手+{} 警长+{} 中立+{})，按预算 {}/{}/{} 裁剪超出的部分",
                        Math.max(0, overK), Math.max(0, overV), Math.max(0, overN),
                        budgetKiller, budgetVigilante, budgetNeutral);
                // 受保护角色：**所有**绑定职业（不管是被自选带出来的，还是自然刷新配对的）
                // 加上所有自选主职业。
                //
                // ★ 之前只保护了 selfSelectCompanions，漏掉了"自然配对的绑定职业" ——
                //   于是监狱图上自然刷出的 CONVICT(重刑犯) 的伙伴 JAILER(狱警) 会被降级成平民，
                //   表现就是"刷了重刑犯却没有狱警 / 反过来"。这里把配对关系补全。
                java.util.Set<SRERole> allCompanions = new java.util.HashSet<>();
                if (selfSelectCompanions != null) {
                    allCompanions.addAll(selfSelectCompanions);
                }
                for (SRERole r0 : roleAssignments.values()) {
                    if (r0 == null) {
                        continue;
                    }
                    var occ0 = Harpymodloader.getOccupationRoles(r0);
                    if (occ0 != null) {
                        for (SRERole c0 : occ0) {
                            if (c0 != null) {
                                allCompanions.add(c0);
                            }
                        }
                    }
                }
                for (var e : new ArrayList<>(roleAssignments.entrySet())) {
                    SRERole r = e.getValue();
                    if (r == null || r == TMMRoles.CIVILIAN) {
                        continue;
                    }
                    if (Harpymodloader.FORCED_MODDED_ROLE_FLIP.containsKey(e.getKey().getUUID())) {
                        continue; // 自选的保留
                    }
                    // 绑定职业也保留：它是主职业的硬依赖（布袋鬼→诡客、悍匪→钳工、
                    // 毒师→医生、迪奥→承太郎、重刑犯→狱警…），降级成平民就等于
                    // "主职业没有绑定职业"。宁可某阵营多 1 个，也不能把配对拆掉。
                    if (allCompanions.stream().anyMatch(c -> c != null
                            && c.identifier().equals(r.identifier()))) {
                        continue;
                    }
                    boolean killer = r.canUseKiller();
                    boolean vigil = r.isVigilanteTeam();
                    boolean neutral = !r.isInnocent() && !killer && !vigil;
                    if (killer && overK > 0) {
                        overK--;
                    } else if (vigil && overV > 0) {
                        overV--;
                    } else if (neutral && overN > 0) {
                        overN--;
                    } else {
                        continue;
                    }
                    roleAssignments.put(e.getKey(), TMMRoles.CIVILIAN);
                    Harpymodloader.LOGGER.warn("[RoleCount]   超标的 {} 已降级为平民", r.identifier());
                }
            }
        }
        return roleAssignments;
    }

    public static Player pickPlayerWithProgressBias(ServerLevel serverWorld, List<ServerPlayer> unassignedPlayers,
            int selectedRoleType) {
        // 目前采用forceTeam逻辑所以无需判断
        return PlayerRoleAssigner.pickByInverseWeight(unassignedPlayers, selectedRoleType);
    }

    @Override
    public void tickServerGameLoop(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        super.tickServerGameLoop(serverWorld, gameWorldComponent);
        GameUtils.WinStatus winStatus = GameUtils.WinStatus.NONE;

        boolean civilianAlive = false;
        for (ServerPlayer player : serverWorld.players()) {
            // passive money
            if (gameWorldComponent.canAutoAddMoney(player)) {
                Integer balanceToAdd = GameConstants.getPassiveMoneyTicker().apply(serverWorld.getGameTime());
                if (balanceToAdd > 0)
                    SREPlayerShopComponent.KEY.get(player).addToBalance(balanceToAdd);
            }
            if (gameWorldComponent.isRole(player, TMMRoles.VIGILANTE)
                    && !GameUtils.isPlayerEliminated(player)
                    && serverWorld.getGameTime() % VIGILANTE_PASSIVE_MONEY_INTERVAL_TICKS == 0) {
                SREPlayerShopComponent.KEY.get(player).addToBalance(VIGILANTE_PASSIVE_MONEY_AMOUNT);
            }

            // check if some civilians are still alive
            if (gameWorldComponent.isInnocent(player) && !GameUtils.isPlayerEliminated(player)) {
                civilianAlive = true;
            }
        }

        // check killer win condition (killed all civilians)
        if (!civilianAlive) {
            winStatus = GameUtils.WinStatus.KILLERS;
        }

        // check passenger win condition (all killers are dead)
        if (winStatus == GameUtils.WinStatus.NONE) {
            winStatus = GameUtils.WinStatus.PASSENGERS;
            for (UUID player : gameWorldComponent.getAllKillerPlayers()) {
                if (!GameUtils.isPlayerEliminated(serverWorld.getPlayerByUUID(player))) {
                    winStatus = GameUtils.WinStatus.NONE;
                }
            }
        }

        // check if out of time
        // 注意：亡命徒（含变体）在场时会把 winStatus 重置为 NONE 让游戏继续，
        // 因此超时判定必须放在亡命徒检查之前，避免覆盖掉亡命徒的拦截结果
        if (!SREGameTimeComponent.KEY.get(serverWorld).hasTime())
            winStatus = GameUtils.WinStatus.TIME;

        // 检查场上是否存在亡命徒（含屠夫、清算者等变体）
        if (winStatus != GameUtils.WinStatus.NONE) {
            boolean hasLooseEndAlive = false;
            List<ServerPlayer> lastLooseEnds = new ArrayList<>();

            for (ServerPlayer player : serverWorld.players()) {
                if (ModRoles.isLooseEndVariant(gameWorldComponent.getRole(player))
                        && !GameUtils.isPlayerEliminated(player)) {
                    hasLooseEndAlive = true;
                    lastLooseEnds.add(player);
                }
            }

            // 如果只有亡命徒存活，且没有其他存活玩家，触发亡命徒获胜
            if (hasLooseEndAlive) {
                // 检查是否有其他非亡命徒的存活玩家
                // 注意：坠木(dream)和皮革嘎的(piggod)不计入亡命徒击杀目标，
                // 亡命徒只需击杀除这两个角色外的其他人即可获胜
                boolean hasOtherAlive = false;
                for (ServerPlayer player : serverWorld.players()) {
                    if (!ModRoles.isLooseEndVariant(gameWorldComponent.getRole(player))
                            && !gameWorldComponent.isRole(player, ModRoles.ZHUIMU)
                            && !gameWorldComponent.isRole(player, ModRoles.PIGE)
                            && !GameUtils.isPlayerEliminated(player)) {
                        hasOtherAlive = true;
                        break;
                    }
                }
                if (!hasOtherAlive) {
                    winStatus = GameUtils.WinStatus.LOOSE_END;
                    // 补充 CustomWinnerID: loose_end
                    var roundEnd = SREGameRoundEndComponent.KEY.get(serverWorld);
                    roundEnd.CustomWinnerID = "loose_end";
                    for (ServerPlayer looseEnd : lastLooseEnds)
                        roundEnd.CustomWinnerPlayers.add(looseEnd.getUUID());
                } else {
                    // 有其他玩家存活，游戏继续
                    winStatus = GameUtils.WinStatus.NONE;
                }
            }
        }

        // game end on win and display
        GameUtils.WinStatus modifiedWinStatus = allowGameEnd(serverWorld, winStatus, false, gameWorldComponent);
        if (!modifiedWinStatus.equals(GameUtils.WinStatus.NOT_MODIFY)) {
            winStatus = modifiedWinStatus;
        }
        if (winStatus != GameUtils.WinStatus.NONE
                && gameWorldComponent.getGameStatus() == SREGameWorldComponent.GameStatus.ACTIVE) {
            SREGameRoundEndComponent.KEY.get(serverWorld).setRoundEndData(serverWorld.players(), winStatus);
            GameUtils.stopGame(serverWorld);
        }
    }

    public GameUtils.WinStatus allowGameEnd(ServerLevel serverWorld, GameUtils.WinStatus winStatus,
            boolean isLooseEndsMode, SREGameWorldComponent gameWorldComponent) {
        return AllowGameEnd.EVENT.invoker().allowGameEnd(serverWorld,
                winStatus, false);
    }
}
