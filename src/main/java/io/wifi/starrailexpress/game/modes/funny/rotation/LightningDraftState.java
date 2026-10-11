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
        SRE.LOGGER.info("[RolePool] 玩家 {} 人：狼位={} 警长位={} 中立位={}",
                totalPlayers, killerCount, vigilanteCount, neutralsCount);

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
            SRE.LOGGER.info("[RolePool] 自选强制职业 {}（占 {} 个名额，可杀手={} 警长={} 中立={}）→ 剩余狼位={}",
                    role.identifier(), occupied, role.canUseKiller(), role.isVigilanteTeam(),
                    !role.isInnocent() && !role.canUseKiller() && !role.isVigilanteTeam(), killerCount);
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

        // 生成可替换职业列表
        List<SRERole> replaceableTypes = new ArrayList<>(civilianPool.selectRoles(PLAYER_SELECT_COUNT - 1,
                role -> role.canBeRandomed()
                        && role.opposingRoles.isEmpty() && !isSpecialInnocent(role)));
        int size = replaceableTypes.size();
        for (int i = 0; i < PLAYER_SELECT_COUNT - 1 - size; i++) {
            replaceableTypes.add(TMMRoles.CIVILIAN);
        }

        // 自选主职业带出来的「绑定职业」（迪奥→承太郎、毒师→医生、悍匪→钳工…）
        // 必须替换掉一个「可替换位」才能进池 —— 普通模式在 SREMurderGameMode.mergeCompanionRoles
        // 里做这件事（它是替换平民位），轮选模式以前完全没有这一步，于是选迪奥时承太郎根本不出现。
        //
        // ★ 必须在 replaceableTypes 组好之后、且**替换 replaceableTypes 里的元素**：
        //   轮选模式的"平民位"是这里抽出来的可替换职业（实测是「时空旅者」「殡仪员」这类），
        //   并不一定是 TMMRoles.CIVILIAN —— 只认 CIVILIAN 会一个都换不到（踩过这个坑）。
        mergeCompanionRoles(replaceableTypes);

        for (SRERole r : replaceableTypes) {
            canReplaceRole.add(r); // 记录可替换的类型
            rolePool.add(new RoleInstance(UUID.randomUUID(), r)); // 为每个类型创建一个实例
        }

        SRE.LOGGER.info("Replaceable role size {}", replaceableTypes.size());
        for (var r : replaceableTypes) {
            SRE.LOGGER.info("Replaceable Role {}", r.getName().getString());
        }

        // ── 各阵营预算 ──
        final int budgetK = Math.max(1, RoleCountManager.getKillerCount(totalPlayers));
        final int budgetV = Math.max(0, RoleCountManager.getVigilanteCount(totalPlayers));
        final int budgetN = Math.max(0, RoleCountManager.getNeutralCount(totalPlayers));

        // ── 诊断：池内各阵营数量 ──
        {
            long cntK = rolePool.stream().filter(ri -> ri.role() != null && ri.role().canUseKiller()).count();
            long cntV = rolePool.stream().filter(ri -> ri.role() != null && ri.role().isVigilanteTeam()).count();
            long cntN = rolePool.stream().filter(ri -> ri.role() != null && !ri.role().isInnocent()
                    && !ri.role().canUseKiller() && !ri.role().isVigilanteTeam()).count();
            SRE.LOGGER.info(
                    "[RolePool] 池内 {} 个角色：预算(杀手={} 警长={} 中立={}) 池内(杀手={} 警长={} 中立={})",
                    rolePool.size(), budgetK, budgetV, budgetN, cntK, cntV, cntN);
            if (cntV > budgetV || cntK > budgetK || cntN > budgetN) {
                SRE.LOGGER.warn("[RolePool] 池内阵营超出预算，逐个列出：");
                for (var ri : rolePool) {
                    var rr = ri.role();
                    if (rr == null) {
                        continue;
                    }
                    if (rr.canUseKiller() || rr.isVigilanteTeam() || !rr.isInnocent()) {
                        SRE.LOGGER.warn("[RolePool]   {}（杀手={} 警长={} 中立={} 占位={}）",
                                rr.identifier(), rr.canUseKiller(), rr.isVigilanteTeam(),
                                !rr.isInnocent() && !rr.canUseKiller() && !rr.isVigilanteTeam(),
                                rr.getOccupiedRoleCount());
                    }
                }
            }
        }

        // ── 阵营预算硬收敛（对齐谋杀模式的终局校验）──
        //
        // 轮选模式以前**没有**任何阵营校验，而它的池子是「baseRoles + 强制职业 + 绑定职业
        // + 可替换位」拼起来的，各阵营是独立取的，就可能多出一个人。
        // 实测：13 人局自选布袋鬼 → 池内警长 3 个（点穴大师 + 警卫 + 诡客），预算只有 2。
        //
        // 做法：超出预算的**非绑定、非自选**角色从池子里摘掉。
        // 绑定职业（诡客/承太郎/钳工/医生…）和自选主职业一律保留 —— 它们是硬依赖。
        {
            java.util.Set<net.minecraft.resources.ResourceLocation> protect = new java.util.HashSet<>();
            for (SRERole c : collectCompanionRoles()) {
                protect.add(c.identifier());
            }
            for (SRERole f : Harpymodloader.FORCED_MODDED_ROLE_FLIP.values()) {
                if (f != null) {
                    protect.add(f.identifier());
                }
            }
            trimFaction(protect, r -> r.canUseKiller(), budgetK, "杀手");
            trimFaction(protect, r -> r.isVigilanteTeam(), budgetV, "警长");
            trimFaction(protect, r -> !r.isInnocent() && !r.canUseKiller() && !r.isVigilanteTeam(),
                    budgetN, "中立");
        }

        initializeCardTracking();
    }

    /**
     * 把某个阵营在池子里的数量压回预算。
     *
     * <p><b>{@code occupiedRoleCount} 的口径</b>：表示"这个角色占几个名额"，
     * 而不是"占几个玩家位"。{@code occupiedRoleCount=2} 的职业自己仍只占 1 个玩家位，
     * 但吃掉 2 个名额。
     *
     * <p>所以阵营的"人数上限"应该是
     * {@code 预算 - Σ(保护角色的额外占用)}，而不是简单地 {@code 预算}。
     * 否则只要保护角色（绑定职业 / 自选主职业）吃掉额外名额，就会把普通角色多摘掉。
     *
     * <p><b>保护角色一律不摘</b>（这是修回的关键）：绑定职业是主职业的硬依赖 ——
     * 摘掉 {@code JAILER(狱警)} 就会让 {@code CONVICT(重刑犯)} 变成"没有狱警的重刑犯"，
     * 实测表现为"监狱图刷了狱警但没有重刑犯 / 反过来"。
     * 宁可某阵营超出预算 1 个人，也不能拆散成对角色。
     */
    private void trimFaction(java.util.Set<net.minecraft.resources.ResourceLocation> protect,
            java.util.function.Predicate<SRERole> belongs, int budget, String label) {
        // 统计"人头数"与"保护角色吃掉的额外名额"
        int heads = 0;
        int protectedHeads = 0;
        int protectedExtra = 0;
        for (var ri : rolePool) {
            SRERole r = ri.role();
            if (r == null || !belongs.test(r)) {
                continue;
            }
            heads++;
            if (protect.contains(r.identifier())) {
                protectedHeads++;
                // 超出 1 的部分算额外占用
                protectedExtra += Math.max(0, r.getOccupiedRoleCount() - 1);
            }
        }
        // 能给"普通角色"的名额 = 预算 - 额外占用；至少给保护角色留人头
        int allowNormalHeads = Math.max(0, budget - protectedExtra);
        int over = (heads - protectedHeads) - allowNormalHeads;
        if (over <= 0) {
            return;
        }
        SRE.LOGGER.warn(
                "[RolePool] {} 阵营 {} 人（其中保护 {} 人，额外占额 {}）> 可用预算 {}，需摘掉 {} 个普通角色",
                label, heads, protectedHeads, protectedExtra, allowNormalHeads, over);

        // 只摘"非保护"角色；保护角色（绑定职业 / 自选主职业）一律保留
        for (int i = rolePool.size() - 1; i >= 0 && over > 0; i--) {
            SRERole r = rolePool.get(i).role();
            if (r == null || !belongs.test(r) || protect.contains(r.identifier())) {
                continue;
            }
            SRE.LOGGER.warn("[RolePool]   摘掉 {}", r.identifier());
            rolePool.remove(i);
            over--;
        }
        if (over > 0) {
            SRE.LOGGER.warn("[RolePool]   {} 阵营仍有 {} 个超出（全是被保护的），不再强摘", label, over);
        }
    }

    /** 收集本局所有绑定职业（自选主职业带出来的） */
    private java.util.Set<SRERole> collectCompanionRoles() {
        java.util.Set<SRERole> out = new java.util.LinkedHashSet<>();
        for (SRERole main : new ArrayList<>(Harpymodloader.FORCED_MODDED_ROLE_FLIP.values())) {
            if (main == null) {
                continue;
            }
            var occ = Harpymodloader.getOccupationRoles(main);
            if (occ == null) {
                continue;
            }
            for (SRERole c : occ) {
                if (c != null) {
                    out.add(c);
                }
            }
        }
        return out;
    }

    /**
     * 把「自选主职业」带出来的绑定职业放进轮选池。
     *
     * <p>背景：有人用自选卡选了迪奥（{@code ModRoles.DIO}），而迪奥的绑定职业是承太郎
     * （{@code ModRoles.JOJO}，见 {@code Harpymodloader.getOccupationRoles}）。
     * 普通模式由 {@code SREMurderGameMode.mergeCompanionRoles} 负责补位，轮选模式原先漏了这一步，
     * 结果就是"选了迪奥但承太郎不会出现"。
     *
     * <p><b>做法</b>：把绑定职业**替换掉一个「可替换位」**（也就是
     * {@code replaceableTypes} 里的一个元素），这样总角色数不变、不会挤掉别人。
     *
     * <p><b>为什么不能只认 {@code TMMRoles.CIVILIAN}</b>：轮选模式的"平民位"是
     * {@code civilianPool.selectRoles(...)} 抽出来的可替换职业，实测可能是「时空旅者」「殡仪员」
     * 这类职业，并不等于 {@code CIVILIAN}。只匹配 CIVILIAN 会一个都换不到，
     * 于是每次都在这里静默失败（日志：{@code 没有平民空位可替换}）。
     *
     * @param replaceableTypes 可替换位（会被就地修改：其中一个被换成绑定职业）
     */
    private void mergeCompanionRoles(List<SRERole> replaceableTypes) {
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
            // 已经在**池子里**（baseRoles / 强制职业）或在**可替换位**里就不重复补。
            //
            // ★ 只查 replaceableTypes 是不够的：rolePool 里已经有 baseRoles 自然抽到的职业。
            //   如果绑定职业恰好也被自然抽到，只查 replaceableTypes 就会再加一份 →
            //   该阵营多出一个人（实测：自选布袋鬼时，诡客既被自然抽到、又被补入，
            //   于是警长阵营变成 3 个）。
            boolean inPool = rolePool.stream().anyMatch(ri -> ri.role() != null
                    && ri.role().identifier().equals(cid));
            boolean inReplaceable = replaceableTypes.stream().anyMatch(r -> r != null
                    && r.identifier().equals(cid));
            if (inPool || inReplaceable) {
                Harpymodloader.LOGGER.info(
                        "[SelfSelect] 绑定职业 {} 已在{}里，不再重复补",
                        cid, inPool ? "角色池" : "可替换位");
                continue;
            }
            // 从后往前挑一个可替换位来顶掉；没有可替换位说明排满了，撤销这次自选并退还卡
            int idx = -1;
            for (int i = replaceableTypes.size() - 1; i >= 0; i--) {
                if (replaceableTypes.get(i) != null) {
                    idx = i;
                    break;
                }
            }
            if (idx < 0) {
                // 补不进去 → 这张自选卡"选了但没生效"，按规则撤销占位并退还
                refundSelfSelectForCompanion(cid);
                Harpymodloader.LOGGER.warn(
                        "[SelfSelect] 轮选模式没有空位可替换，绑定职业 {} 无法生成（已撤销自选并退还卡）", cid);
                continue;
            }
            SRERole displaced = replaceableTypes.set(idx, companion);
            Harpymodloader.LOGGER.info("[SelfSelect] 轮选模式因自选主职业而补入绑定职业 {}（替换掉 {}）",
                    cid, displaced == null ? "?" : displaced.identifier());
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
        // 统一退还入口：撤销占位 + 退卡 + 「阵营卡同款」红色提示。
        // 以前这里各自 displayClientMessage + 各自退卡，容易漏（玩家体感就是"卡被吃了"）。
        io.wifi.starrailexpress.progression.SelfSelectClaimService.refundAndNotify(
                server.getPlayerList().getPlayer(playerId), role, null);
    }

    /**
     * 绑定职业补不进去时，把「因它而用掉的那张自选卡」退还给对应的玩家。
     *
     * <p>只退给"主职业的绑定职业正好是它"的那些玩家，避免误退给别人。
     * 组合技既然凑不齐，就不该只生效一半 —— 所以一并撤销自选占位
     * （由 {@code refundAndNotify} 内部完成）。
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
            Harpymodloader.LOGGER.info("[SelfSelect] 绑定职业 {} 无法生成，已撤销 {} 的自选主职业 {}",
                    companionId, entry.getKey(), main.identifier());
            refundSelfSelect(entry.getKey(), main);
        }
    }

    private void initializeCardTracking() {
        cardUsedCount.clear();
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
                    // 现在走统一退入口（撤销占位 + 退卡 + 阵营卡同款红色提示）。
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