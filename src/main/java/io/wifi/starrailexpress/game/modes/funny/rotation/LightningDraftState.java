package io.wifi.starrailexpress.game.modes.funny.rotation;

import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.commands.RoleCountManager;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;
import org.agmas.harpymodloader.modded_murder.ForceTeamInfo;
import org.agmas.harpymodloader.modded_murder.PlayerRoleWeightManager;
import org.agmas.harpymodloader.modded_murder.RoleAssignmentPool;
import org.agmas.harpymodloader.modded_murder.ForceTeamInfo.ForceTeamType;
import org.agmas.noellesroles.utils.RoleUtils;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.game.modes.SREMurderGameMode;
import io.wifi.starrailexpress.game.roles.SpecialGameModeRoles;
import io.wifi.starrailexpress.game.utils.RoleInstance;
import io.wifi.starrailexpress.progression.ProgressionDataManager;
import io.wifi.starrailexpress.progression.ProgressionState.FactionCardType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public class LightningDraftState {
    public static HashMap<UUID, Integer> PLAYER_SORT_WEIGHT = new HashMap<>();
    public static final Random random = new Random();
    public static final int PLAYER_SELECT_COUNT = 3;
    public final List<ServerPlayer> allPlayers;
    public final int totalPlayers;
    // 锁定本轮已展示给玩家的具体角色实例（按UUID），防止随机选择时抢走
    private final Set<UUID> lockedInstanceIds = new HashSet<>();

    // ===== 职业池与结果 =====
    public final Set<SRERole> canReplaceRole = new LinkedHashSet<>(); // 可替换职业类型集合（去重）
    public final ArrayList<RoleInstance> rolePool = new ArrayList<>(); // 当前未被选走的角色实例池
    public final Map<UUID, SRERole> selectedRoles = new LinkedHashMap<>();
    public final Set<UUID> randomChoosers = new HashSet<>();

    // ===== 玩家顺序（按阵营权重降序，同权重随机）=====
    public final List<UUID> playerOrder = new ArrayList<>();

    // ===== 轮次控制 =====
    public int remainingPlayerCount;
    public int currentRoundIndex = 0;
    public int playersInThisRound = 0;
    public Map<UUID, List<RoleInstance>> roundCandidates = new HashMap<>();
    public long roundStartTime;
    public int perPlayerTimeLimit;
    public boolean isSelecting = false;
    public int confirmCountdown = -1;

    // ===== 卡片追踪 =====
    private final Map<Integer, Integer> cardUsedCount = new HashMap<>();
    private final Map<Integer, Integer> cardMaxPerType = new HashMap<>();
    private final Set<UUID> cardReturnedPlayers = new HashSet<>();

    public LightningDraftState(List<ServerPlayer> players) {
        this.allPlayers = new ArrayList<>(players);
        this.totalPlayers = players.size();
        this.remainingPlayerCount = totalPlayers;
    }

    /**
     * 处理已离线的、尚未选择职业的玩家。
     */
    public boolean handleOfflinePlayers(ServerLevel world) {
        List<UUID> offlineUnselected = new ArrayList<>();
        for (UUID uuid : playerOrder) {
            if (!selectedRoles.containsKey(uuid)) {
                ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
                if (player == null || player.isRemoved()) {
                    offlineUnselected.add(uuid);
                }
            }
        }
        if (offlineUnselected.isEmpty())
            return false;

        for (UUID uuid : offlineUnselected) {
            List<RoleInstance> oldCandidates = roundCandidates.remove(uuid);
            if (oldCandidates != null) {
                oldCandidates.forEach(ri -> lockedInstanceIds.remove(ri.uuid()));
            }
            RoleInstance randomInstance = selectRandomRole(world);
            selectedRoles.put(uuid, randomInstance.role());
            rolePool.remove(randomInstance);
            remainingPlayerCount--;
            randomChoosers.add(uuid);
        }

        if (isSelecting && roundCandidates.isEmpty()) {
            finishRound(world);
        }
        return true;
    }

    // ---------- 初始化角色池 ----------
    public void initializeRolePool(ServerLevel world) {
        rolePool.clear();
        canReplaceRole.clear();

        int killerCount = Math.max(1, RoleCountManager.getKillerCount(totalPlayers));
        int vigilanteCount = Math.max(0, RoleCountManager.getVigilanteCount(totalPlayers));
        int neutralsCount = Math.max(0, RoleCountManager.getNeutralCount(totalPlayers));

        HarpyModLoaderConfig config = HarpyModLoaderConfig.HANDLER.instance();
        boolean enableCivilianInPool = config.enableCivilianInPool;

        RoleAssignmentPool killerPool = RoleAssignmentPool.create("Killer",
                role -> !Harpymodloader.VANNILA_ROLES.contains(role) &&
                        !role.isOtherModeRole() &&
                        role.canUseKiller() && !role.isNeutrals() && !role.isNeutralForKiller() &&
                        !role.isInnocent() &&
                        role != TMMRoles.CIVILIAN);
        RoleAssignmentPool vigilantePool = RoleAssignmentPool.create("Vigilante",
                role -> !Harpymodloader.VANNILA_ROLES.contains(role) &&
                        role.isVigilanteTeam() &&
                        !role.isOtherModeRole());
        RoleAssignmentPool neutralsPool = RoleAssignmentPool.create("Neutrals",
                role -> !Harpymodloader.VANNILA_ROLES.contains(role) &&
                        !role.isOtherModeRole() &&
                        ((!role.canUseKiller() && !role.isInnocent()) || role.isNeutrals()) &&
                        role != TMMRoles.CIVILIAN);
        RoleAssignmentPool civilianPool = RoleAssignmentPool.create("Civilian",
                role -> !Harpymodloader.VANNILA_ROLES.contains(role) &&
                        !role.isOtherModeRole() &&
                        !role.isVigilanteTeam() &&
                        !role.canUseKiller() &&
                        !role.isNeutrals() &&
                        role.isInnocent() &&
                        (enableCivilianInPool || role != TMMRoles.CIVILIAN));
        civilianPool.ignoreeRoleOccupiedCount = true;

        // 强制职业直接作为实例加入池子
        int forceRoleCount = 0;
        List<SRERole> forcedRoles = new ArrayList<>();
        for (var flip : Harpymodloader.FORCED_MODDED_ROLE_FLIP.entrySet()) {
            var role = flip.getValue();
            if (role == null)
                continue;
            rolePool.add(new RoleInstance(UUID.randomUUID(), role));
            forcedRoles.add(role);
            forceRoleCount++;
            // 按「实际阵营」扣减名额，并且要按 occupiedRoleCount 扣。
            // 之前这里是 `switch (role.getRoleType())`，有两个问题：
            //   ① 只扣 1，忽略了 occupiedRoleCount —— 迪奥压 2 个杀手位
            //      （ModRoles.DIO 设了 setOccupiedRoleCount(2)），配 3 狼的局会变成额外 3 个狼位；
            //   ② type 1 被当成"不占名额"，但 type 1 是乘客（isInnocent），
            //      乘客本该压平民数，口径和 getAllRoles 不一致。
            // 现在统一成 getAllRoles / SREMurderGameMode 用的判断。
            int occupied = Math.max(1, role.getOccupiedRoleCount());
            if (role.canUseKiller()) {
                killerCount -= occupied;
            } else if (role.isVigilanteTeam()) {
                vigilanteCount -= occupied;
            } else if (!role.isInnocent()) {
                neutralsCount -= occupied;
            }
        }

        List<RoleInstance> baseRoles = SREMurderGameMode.getAllRoles(
                killerCount, vigilanteCount, neutralsCount,
                totalPlayers, forceRoleCount,
                killerPool, neutralsPool, vigilantePool, civilianPool, true, forcedRoles);

        for (RoleInstance inst : baseRoles) {
            if (inst.role() != null) {
                rolePool.add(inst); // 保留原有UUID
            }
        }

        // 自选主职业带出来的「绑定职业」（迪奥→承太郎、毒师→医生、悍匪→钳工…）必须补进池子。
        // 普通模式在 SREMurderGameMode.mergeCompanionRoles 里做这件事，轮选模式以前完全没有这一步，
        // 于是有人用自选卡选迪奥时，承太郎根本不会出现。
        mergeCompanionRoles(rolePool);

        // 生成可替换职业列表
        List<SRERole> replaceableTypes = new ArrayList<>(civilianPool.selectRoles(PLAYER_SELECT_COUNT - 1,
                role -> role.canBeRandomed()
                        && role.opposingRoles.isEmpty() && !isSpecialInnocent(role)));
        int size = replaceableTypes.size();
        for (int i = 0; i < PLAYER_SELECT_COUNT - 1 - size; i++) {
            replaceableTypes.add(TMMRoles.CIVILIAN);
        }
        for (SRERole r : replaceableTypes) {
            canReplaceRole.add(r); // 记录可替换的类型
            rolePool.add(new RoleInstance(UUID.randomUUID(), r)); // 为每个类型创建一个实例
        }

        SRE.LOGGER.info("Replaceable role size {}", replaceableTypes.size());
        for (var r : replaceableTypes) {
            SRE.LOGGER.info("Replaceable Role {}", r.getName().getString());
        }

        initializeCardTracking();
    }

    /**
     * 把「自选主职业」带出来的绑定职业补进轮选池。
     *
     * <p>背景：有人用自选卡选了迪奥（{@code ModRoles.DIO}），而迪奥的绑定职业是承太郎
     * （{@code ModRoles.JOJO}，见 {@code Harpymodloader.getOccupationRoles}）。
     * 普通模式由 {@code SREMurderGameMode.mergeCompanionRoles} 负责补位，轮选模式原先漏了这一步，
     * 结果就是"选了迪奥但承太郎不会出现"。
     *
     * <p>做法和普通模式一致：**替换掉一个平民实例**。这样
     * <ul>
     * <li>总角色数不变（角色数 == 玩家数），不会挤掉别人；</li>
     * <li>绑定职业一定出现在本局候选池里，会被分配给某位玩家；</li>
     * <li>该绑定职业名额已满时跳过（"角色上限依然生效"）。</li>
     * </ul>
     *
     * <p>补位后**必须**重新过一次 {@code lockedInstanceIds} 逻辑 ——
     * 这里只动 rolePool，锁定的实例在 {@code startNextRound} 里重算，不受影响。
     */
    private void mergeCompanionRoles(List<RoleInstance> roles) {
        // 收集本局自选主职业带出的绑定职业，按名额上限去重
        // 注意：上限要拿「统计值」去比，不能在遍历中拿正在累加的集合去比 ——
        // 否则第一个主职业会一口气塞进 cap 个，后面的主职业就永远补不进来。
        Map<SRERole, Integer> capOf = new LinkedHashMap<>();
        LinkedHashSet<SRERole> companions = new LinkedHashSet<>();
        // 复制一份再遍历：下面 refundSelfSelectForCompanion 可能会 releaseSelfForcedRole，
        // 而 FORCED_MODDED_ROLE_FLIP 是普通 HashMap —— 直接遍历会在改结构时抛
        // ConcurrentModificationException。
        for (SRERole main : new ArrayList<>(Harpymodloader.FORCED_MODDED_ROLE_FLIP.values())) {
            if (main == null) {
                continue;
            }
            var occ = Harpymodloader.getOccupationRoles(main);
            if (occ == null || occ.isEmpty()) {
                continue;
            }
            for (SRERole companion : occ) {
                if (companion == null) {
                    continue;
                }
                int cap = Math.max(1, Harpymodloader.getRoleCapacity(companion));
                capOf.putIfAbsent(companion, cap);
                // 名额用尽 → 这个绑定职业不再补（"角色上限依然生效"）
                if (capOf.get(companion) <= 0) {
                    continue;
                }
                capOf.put(companion, capOf.get(companion) - 1);
                companions.add(companion);
            }
        }
        if (companions.isEmpty()) {
            return;
        }

        for (SRERole companion : companions) {
            final var cid = companion.identifier();
            // 已经在池子里就不重复补（例如别的玩家自己选/抽到了同一个职业）
            boolean present = roles.stream().anyMatch(ri -> ri.role() != null
                    && ri.role().identifier().equals(cid));
            if (present) {
                continue;
            }
            // 找一个平民空位替换；没有平民位说明排满了，放弃这次自选并退还自选卡
            int idx = -1;
            for (int i = roles.size() - 1; i >= 0; i--) {
                if (roles.get(i).role() == TMMRoles.CIVILIAN) {
                    idx = i;
                    break;
                }
            }
            if (idx < 0) {
                // 补不进去 → 这张自选卡"选了但没生效"，按规则撤销占位并退还
                refundSelfSelectForCompanion(cid);
                Harpymodloader.LOGGER.warn(
                        "[SelfSelect] 轮选模式没有平民空位可替换，绑定职业 {} 无法生成（已撤销自选并退还卡）", cid);
                continue;
            }
            roles.set(idx, new RoleInstance(UUID.randomUUID(), companion));
            Harpymodloader.LOGGER.info("[SelfSelect] 轮选模式因自选主职业而补入绑定职业 {}", cid);
        }
    }

    /**
     * 把自选卡退还给指定玩家，并提示原因。
     *
     * <p>用于「自选卡选了但这一局没生效」的各种情况：强制职业本轮没抽到、
     * 绑定职业挤不进池子等。统一走这里，避免有的路径退、有的路径不退。
     */
    private void refundSelfSelect(UUID playerId, SRERole role) {
        MinecraftServer server = SRE.SERVER;
        if (server == null) {
            return;
        }
        ServerPlayer sp = server.getPlayerList().getPlayer(playerId);
        if (sp == null) {
            return;
        }
        io.wifi.starrailexpress.backpack.BackpackManager.refundSelfSelectCard(sp);
        sp.displayClientMessage(Component.translatable("message.sre.pass.selfselect_companion_failed",
                Harpymodloader.getRoleName(role)).withStyle(ChatFormatting.RED), false);
        Harpymodloader.LOGGER.info("[SelfSelect] 轮选模式下 {} 的自选职业 {} 本局未生效，已退还自选卡",
                playerId, role.identifier());
    }

    /**
     * 绑定职业补不进去时，把「因它而用掉的那张自选卡」退还给对应的玩家。
     *
     * <p>只退给"主职业的绑定职业正好是它"的那些玩家，避免误退给别人。
     * 同时撤销该玩家的自选占位 —— 组合技既然凑不齐，就不该只生效一半。
     */
    private void refundSelfSelectForCompanion(net.minecraft.resources.ResourceLocation companionId) {
        MinecraftServer server = SRE.SERVER;
        if (server == null) {
            return;
        }
        for (var entry : new ArrayList<>(Harpymodloader.FORCED_MODDED_ROLE_FLIP.entrySet())) {
            SRERole main = entry.getValue();
            if (main == null) {
                continue;
            }
            var occ = Harpymodloader.getOccupationRoles(main);
            if (occ == null || occ.stream().noneMatch(c -> c != null && c.identifier().equals(companionId))) {
                continue;
            }
            refundSelfSelect(entry.getKey(), main);
            Harpymodloader.releaseSelfForcedRole(main, entry.getKey());
            Harpymodloader.LOGGER.info("[SelfSelect] 绑定职业 {} 无法生成，已撤销 {} 的自选主职业 {}",
                    companionId, entry.getKey(), main.identifier());
        }
    }

    private void initializeCardTracking() {        cardUsedCount.clear();
        cardMaxPerType.clear();
        cardReturnedPlayers.clear();
        int limit = Math.max(1, totalPlayers / 10);
        cardMaxPerType.put(4, limit);
        cardMaxPerType.put(2, limit);

        Map<Integer, List<UUID>> byType = new HashMap<>();
        final var ppps = new ArrayList<>(allPlayers);
        Collections.shuffle(ppps);
        ppps.sort((a, b) -> {
            int a_team = forceTeamType(PlayerRoleWeightManager.ForcePlayerTeam.getOrDefault(a.getUUID(), null));
            int b_team = forceTeamType(PlayerRoleWeightManager.ForcePlayerTeam.getOrDefault(b.getUUID(), null));
            return -Integer.compare(a_team, b_team);
        });
        for (ServerPlayer p : ppps) {
            var t = PlayerRoleWeightManager.ForcePlayerTeam.get(p.getUUID());
            if (t == null)
                continue;
            Integer forcedType = t.roleType();
            if (forcedType != null) {
                int normalized = normalizeCardType(forcedType);
                byType.computeIfAbsent(normalized, k -> new ArrayList<>()).add(p.getUUID());
            }
        }
        for (Map.Entry<Integer, List<UUID>> entry : byType.entrySet()) {
            int type = entry.getKey();
            List<UUID> uuids = entry.getValue();
            int max = cardMaxPerType.getOrDefault(type, uuids.size());
            cardUsedCount.put(type, Math.min(uuids.size(), max));
            for (int i = max; i < uuids.size(); i++) {
                UUID uid = uuids.get(i);
                ForceTeamInfo info = PlayerRoleWeightManager.ForcePlayerTeam.remove(uid);
                if (info == null)
                    continue;
                cardReturnedPlayers.add(uid);
                ServerPlayer sp = allPlayers.stream().filter(p -> p.getUUID().equals(uid)).findFirst().orElse(null);
                if (sp != null) {
                    if (info.type() == ForceTeamType.CARD) {

                        FactionCardType cardType = FactionCardType.fromRoleType(type);
                        if (cardType != FactionCardType.NONE) {
                            ProgressionDataManager.addFactionCard(sp, cardType, 1);
                            sp.displayClientMessage(Component.translatable("message.sre.role_rotation.card_limit")
                                    .withStyle(ChatFormatting.RED), true);
                        }
                    } else {
                        sp.displayClientMessage(Component.translatable("message.sre.role_rotation.no_force_team")
                                .withStyle(ChatFormatting.RED), true);
                    }
                }
            }
        }
    }

    private int forceTeamType(ForceTeamInfo b) {
        if (b == null)
            return 0; // 或其他默认值
        return switch (b.type()) {
            case CARD -> 1;
            case COMMAND -> 3;
            case ROLE_WEIGHTS -> 2;
            default -> 0;
        };
    }

    private boolean roleMatchesFaction(SRERole role, int type) {
        return role != null && normalizeCardType(role.getRoleType()) == type;
    }

    private static int normalizeCardType(int rawType) {
        return switch (rawType) {
            case 5 -> 1;
            case 3 -> 2;
            default -> rawType;
        };
    }

    // ---------- 玩家顺序 ----------
    public void assignRotationOrder() {
        List<ServerPlayer> sorted = new ArrayList<>(allPlayers);
        Collections.shuffle(sorted);
        sorted.sort((a, b) -> {
            int aW = PLAYER_SORT_WEIGHT.getOrDefault(a.getUUID(), 0);
            int bW = PLAYER_SORT_WEIGHT.getOrDefault(b.getUUID(), 0);
            if (aW != bW)
                return -Integer.compare(aW, bW);

            boolean a_force = Harpymodloader.FORCED_MODDED_ROLE_FLIP.containsKey(a.getUUID());
            boolean b_force = Harpymodloader.FORCED_MODDED_ROLE_FLIP.containsKey(b.getUUID());
            int a_team = normalizeForceRoleSortType(
                    roleType(PlayerRoleWeightManager.ForcePlayerTeam.getOrDefault(a.getUUID(), null)));
            int b_team = normalizeForceRoleSortType(
                    roleType(PlayerRoleWeightManager.ForcePlayerTeam.getOrDefault(b.getUUID(), null)));
            if (a_force && b_force)
                return 0;
            if (a_force)
                return -1;
            if (b_force)
                return 1;
            return -Integer.compare(a_team, b_team);
        });
        playerOrder.clear();
        for (ServerPlayer p : sorted) {
            playerOrder.add(p.getUUID());
        }
        PLAYER_SORT_WEIGHT.clear();
    }

    private int roleType(ForceTeamInfo a) {
        if (a == null)
            return 0;
        return a.roleType();
    }

    private static int normalizeForceRoleSortType(int type) {
        if (type == 1)
            type = -2;
        if (type == 5)
            type = 0;
        return type;
    }

    // ---------- 轮次计算 ----------
    public void startNextRound(ServerLevel world) {
        if (remainingPlayerCount <= 0) {
            adjustRoles(world);
            startConfirmCountdown();
            return;
        }
        if (rolePool.isEmpty()) {
            for (UUID uuid : playerOrder) {
                if (!selectedRoles.containsKey(uuid)) {
                    selectedRoles.put(uuid, TMMRoles.CIVILIAN);
                    remainingPlayerCount--;
                }
            }
            adjustRoles(world);
            startConfirmCountdown();
            return;
        }
        roundCandidates.clear();
        lockedInstanceIds.clear();

        int b = Math.max(1, rolePool.size() / PLAYER_SELECT_COUNT);
        playersInThisRound = b;

        // 本轮参选玩家
        List<UUID> roundPlayers = new ArrayList<>();
        for (UUID uuid : playerOrder) {
            if (!selectedRoles.containsKey(uuid)) {
                roundPlayers.add(uuid);
                if (roundPlayers.size() >= playersInThisRound)
                    break;
            }
        }

        int need = Math.min(rolePool.size(), playersInThisRound * PLAYER_SELECT_COUNT);
        List<RoleInstance> drawn = new ArrayList<>(rolePool);
        Collections.shuffle(drawn, random);
        drawn = new ArrayList<>(drawn.subList(0, need));

        List<RoleInstance> remainingDrawn = new ArrayList<>(drawn); // 可分配池
        Map<UUID, List<RoleInstance>> candidateMap = new LinkedHashMap<>();
        for (UUID id : roundPlayers) {
            candidateMap.put(id, new ArrayList<>());
        }

        // 1. 强制职业预分配
        for (UUID playerId : roundPlayers) {
            SRERole forcedRole = Harpymodloader.FORCED_MODDED_ROLE_FLIP.get(playerId);
            if (forcedRole != null) {
                Optional<RoleInstance> match = remainingDrawn.stream()
                        .filter(ri -> ri.role() == forcedRole).findFirst();
                if (match.isPresent()) {
                    RoleInstance ri = match.get();
                    candidateMap.get(playerId).add(ri);
                    remainingDrawn.remove(ri);
                } else {
                    // 本轮抽签没抽到这个强制职业 → 自选卡本局无法生效。
                    // 以前这里是 ifPresent 静默失败：卡被扣了、职业没拿到，玩家还得自己发现。
                    // 现在撤销占位并把卡退回去（"不生效就退还"）。
                    Harpymodloader.releaseSelfForcedRole(forcedRole, playerId);
                    refundSelfSelect(playerId, forcedRole);
                }
            }
        }

        // 2. 强制阵营预分配
        for (UUID playerId : roundPlayers) {
            var t = PlayerRoleWeightManager.ForcePlayerTeam.get(playerId);
            if (t == null)
                continue;
            Integer forcedType = t.roleType();
            if (forcedType == null || forcedType < 1 || forcedType > 5)
                continue;
            int type = normalizeCardType(forcedType);
            if (candidateMap.get(playerId).size() >= PLAYER_SELECT_COUNT)
                continue;

            Optional<RoleInstance> match = remainingDrawn.stream()
                    .filter(ri -> roleMatchesFaction(ri.role(), type)).findFirst();
            if (match.isPresent()) {
                RoleInstance ri = match.get();
                candidateMap.get(playerId).add(ri);
                remainingDrawn.remove(ri);
            } else {
                // 无法提供匹配职业，移除强制要求，退还卡片
                PlayerRoleWeightManager.ForcePlayerTeam.remove(playerId);
                ServerPlayer sp = world.getServer().getPlayerList().getPlayer(playerId);
                if (sp != null) {
                    if (t.type() == ForceTeamType.CARD) {
                        FactionCardType cardType = FactionCardType.fromRoleType(type);
                        if (cardType != FactionCardType.NONE) {
                            ProgressionDataManager.addFactionCard(sp, cardType, 1);
                            sp.displayClientMessage(Component.translatable("message.sre.role_rotation.card_limit")
                                    .withStyle(ChatFormatting.RED), false);
                        }
                    } else {
                        sp.displayClientMessage(Component.translatable("message.sre.role_rotation.no_force_team")
                                .withStyle(ChatFormatting.RED), true);
                    }
                }
            }
        }

        // 3. 剩余实例依次分配给玩家，每人最多 PLAYER_SELECT_COUNT 个；
        //    硬性强制阵营（职业卡/命令）的玩家只补发本阵营职业，确保卡牌真正生效
        Iterator<RoleInstance> iter = remainingDrawn.iterator();
        for (UUID playerId : roundPlayers) {
            List<RoleInstance> candidates = candidateMap.get(playerId);
            ForceTeamInfo force = PlayerRoleWeightManager.ForcePlayerTeam.get(playerId);
            int hardForcedType = -1;
            if (force != null && force.type() != ForceTeamType.ROLE_WEIGHTS
                    && force.roleType() >= 1 && force.roleType() <= 5) {
                hardForcedType = normalizeCardType(force.roleType());
            }
            while (candidates.size() < PLAYER_SELECT_COUNT && iter.hasNext()) {
                RoleInstance next = iter.next();
                if (hardForcedType >= 0 && !roleMatchesFaction(next.role(), hardForcedType)) {
                    // 非本阵营职业不提供给硬性强制玩家，使其只能选本阵营
                    continue;
                }
                candidates.add(next);
                iter.remove();
            }
        }

        // 过滤出非空候选列表，并记录锁定实例
        for (Map.Entry<UUID, List<RoleInstance>> entry : candidateMap.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                roundCandidates.put(entry.getKey(), entry.getValue());
                entry.getValue().forEach(ri -> lockedInstanceIds.add(ri.uuid()));
            }
        }

        currentRoundIndex++;
        roundStartTime = world.getGameTime();

        int maxCandidates = roundCandidates.values().stream().mapToInt(List::size).max().orElse(0);
        int baseTime = maxCandidates * SREConfig.instance().roleRotationPerPlayerPerRoleTime * 20;
        perPlayerTimeLimit = currentRoundIndex == 1 ? baseTime + 60 : baseTime;

        isSelecting = true;
        confirmCountdown = -1;
    }

    // ---------- 处理玩家选择 ----------
    public boolean processSelection(ServerLevel world, UUID playerUuid, int choiceIndex) {
        if (!isSelecting || !roundCandidates.containsKey(playerUuid))
            return false;

        List<RoleInstance> candidates = roundCandidates.remove(playerUuid);
        candidates.forEach(ri -> lockedInstanceIds.remove(ri.uuid()));

        RoleInstance chosen = null;
        if (choiceIndex >= 0 && choiceIndex < candidates.size()) {
            chosen = candidates.get(choiceIndex);
        }
        if (chosen == null || choiceIndex == PLAYER_SELECT_COUNT) { // 随机
            chosen = selectRandomRole(world);
            randomChoosers.add(playerUuid);
        }

        if (chosen == null)
            return false;

        selectedRoles.put(playerUuid, chosen.role());
        rolePool.remove(chosen);
        remainingPlayerCount--;

        ServerPlayer player = world.getServer().getPlayerList().getPlayer(playerUuid);
        if (player != null) {
            if (canReplaceRole.contains(chosen.role())) {
                player.displayClientMessage(
                        Component.translatable("gui.sre.role_rotation.selected_adjust",
                                RoleUtils.getRoleName(chosen.role()).withColor(chosen.role().getColor()))
                                .withStyle(ChatFormatting.GREEN),
                        true);
            } else {
                player.displayClientMessage(
                        Component.translatable("gui.sre.role_rotation.selected",
                                RoleUtils.getRoleName(chosen.role()).withColor(chosen.role().getColor()))
                                .withStyle(ChatFormatting.GREEN),
                        true);
            }
        }
        RoleUtils.playSound(player, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.MASTER, 1.0f, 1.2f);

        if (roundCandidates.isEmpty()) {
            finishRound(world);
        }
        return true;
    }

    private void finishRound(ServerLevel world) {
        isSelecting = false;
        lockedInstanceIds.clear();
        if (remainingPlayerCount > 0) {
            for (ServerPlayer p : world.players()) {
                RoleUtils.playSound(p, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.MASTER, 1.0f, 1.5f);
            }
            startNextRound(world);
        } else {
            for (ServerPlayer p : world.players()) {
                RoleUtils.playSound(p, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 1f, 1f);
            }
            adjustRoles(world);
            startConfirmCountdown();
        }
    }

    private void startConfirmCountdown() {
        isSelecting = false;
        confirmCountdown = 6 * 20;
    }

    private RoleInstance selectRandomRole(ServerLevel world) {
        List<RoleInstance> available = rolePool.stream()
                .filter(ri -> !lockedInstanceIds.contains(ri.uuid()))
                .collect(Collectors.toList());
        if (available.isEmpty()) {
            // 极端情况：从整个池子随机，若池子为空则生成一个平民实例
            RoleInstance civilian = new RoleInstance(UUID.randomUUID(), TMMRoles.CIVILIAN);
            rolePool.add(civilian);
            SRE.LOGGER.warn("No available role for random selection, assigning civilian.");
            return civilian;
        }
        return available.get(random.nextInt(available.size()));
    }

    public void timeoutUnfinishedPlayers(ServerLevel world) {
        if (!isSelecting)
            return;
        List<UUID> unfinished = new ArrayList<>(roundCandidates.keySet());
        for (UUID uuid : unfinished) {
            List<RoleInstance> oldCandidates = roundCandidates.remove(uuid);
            if (oldCandidates != null) {
                oldCandidates.forEach(ri -> lockedInstanceIds.remove(ri.uuid()));
            }
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
            RoleInstance randomInstance = selectRandomRole(world);
            selectedRoles.put(uuid, randomInstance.role());
            rolePool.remove(randomInstance);
            remainingPlayerCount--;
            randomChoosers.add(uuid);
            if (player != null) {
                player.displayClientMessage(
                        Component.translatable("gui.sre.role_rotation.selection_timeout_noargs")
                                .withStyle(ChatFormatting.RED),
                        true);
            }
        }
        finishRound(world);
    }

    public void adjustRoles(ServerLevel serverWorld) {
        var canReplacePlayers = new ArrayList<UUID>();
        for (Entry<UUID, SRERole> entrySet : selectedRoles.entrySet()) {
            if (canReplaceRole.contains(entrySet.getValue()) || entrySet.getValue().equals(TMMRoles.CIVILIAN)) {
                canReplacePlayers.addFirst(entrySet.getKey());
            }
        }
        var needToReplaceRole = new ArrayList<SRERole>();
        boolean roleRotationForceRoleSettings = SREConfig.instance().roleRotationForceRoleSettings;
        for (RoleInstance ri : rolePool) {
            SRERole role = ri.role();
            if (canReplaceRole.contains(role) || role.equals(TMMRoles.CIVILIAN)) {
                continue;
            }
            if (role.isInnocent() && !isSpecialInnocent(role) && !roleRotationForceRoleSettings) {
                continue;
            }
            needToReplaceRole.add(role);
        }
        if (needToReplaceRole.isEmpty())
            return;

        Collections.shuffle(needToReplaceRole);
        int t = 0;
        for (SRERole r : needToReplaceRole) {
            if (canReplacePlayers.isEmpty()) {
                SRE.LOGGER.error("Need {} more innocent player.", needToReplaceRole.size() - t);
                break;
            }
            var p = canReplacePlayers.getFirst();
            canReplacePlayers.removeFirst();
            var old = selectedRoles.getOrDefault(p, SpecialGameModeRoles.CUSTOM_PENDING);
            selectedRoles.put(p, r);
            var pp = serverWorld.getPlayerByUUID(p);
            SRE.LOGGER.info("Replace {}'s old role {} with new role {}",
                    pp == null ? "null" : pp.getName().getString(), old.getName().getString(),
                    r.getName().getString());
            t++;
        }
    }

    private static boolean isSpecialInnocent(SRERole role) {
        if (!role.occupationRoles.isEmpty())
            return true;
        if (!role.occupationedRoles.isEmpty())
            return true;
        return false;
    }

    public Map<UUID, List<String>> getRoundCandidatesAsStrings() {
        return roundCandidates.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().stream().map(ri -> ri.role().identifier().toString()).toList()));
    }

    public Map<UUID, String> getSelectedRolesAsStrings() {
        return selectedRoles.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().identifier().toString()));
    }
}