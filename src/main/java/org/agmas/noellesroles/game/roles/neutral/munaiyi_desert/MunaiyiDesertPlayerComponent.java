package org.agmas.noellesroles.game.roles.neutral.munaiyi_desert;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.AllowPlayerDeath;
import io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.content.entity.CoffinEntity;
import org.agmas.noellesroles.init.ModEntities;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 木乃伊 (munaiyi_desert) 角色组件 —— 独立中立，无胜利条件。
 * <p>
 * 被动：常驻隐身（释放技能/现身短暂解除）、常驻静步（同特工静步，屏蔽脚步声）、无敌无法被击杀。
 * 技能：诅咒（背包选人叠层）、恐吓（隐身红字/现身恶魂）、现身（条件传送）、
 * 领地确认（放置棺材）、干枯（现身时降低周围玩家口渴值）。
 * </p>
 * <p>
 * 现身状态分为两级：{@link #revealTicks}（短暂现身，仅解除隐身）与
 * {@link #fullRevealTicks}（技能3完整现身：僵尸平举手势 + 可左键击杀被标记玩家）。
 * 两者互不混杂。
 * </p>
 */
public class MunaiyiDesertPlayerComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<MunaiyiDesertPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "munaiyi_desert"),
            MunaiyiDesertPlayerComponent.class);

    /** 统一技能 ID（供技能注册与冷却记账共用） */
    public static final ResourceLocation SKILL_CURSE = ResourceLocation.fromNamespaceAndPath("starrailexpress",
            "munaiyi_curse");
    public static final ResourceLocation SKILL_SCARE = ResourceLocation.fromNamespaceAndPath("starrailexpress",
            "munaiyi_scare");
    public static final ResourceLocation SKILL_REVEAL = ResourceLocation.fromNamespaceAndPath("starrailexpress",
            "munaiyi_reveal");
    public static final ResourceLocation SKILL_TERRITORY = ResourceLocation.fromNamespaceAndPath("starrailexpress",
            "munaiyi_territory");
    public static final ResourceLocation SKILL_WITHER = ResourceLocation.fromNamespaceAndPath("starrailexpress",
            "munaiyi_wither");

    /** 诅咒最大层数 */
    public static final int MAX_CURSE_STACKS = 3;

    private final Player player;

    /** 短暂现身剩余 tick（释放技能触发的短暂解除隐身） */
    public int revealTicks = 0;
    /** 完整现身剩余 tick（技能3，期间僵尸平举手势且可击杀被标记玩家） */
    public int fullRevealTicks = 0;
    /** 诅咒层数：目标玩家 UUID -> 层数(0-3)，不被现身消耗 */
    public final Map<UUID, Integer> curseStacks = new HashMap<>();
    /** 睡觉消层计时：目标玩家 UUID -> 已连续睡觉 tick */
    private final Map<UUID, Integer> sleepTimers = new HashMap<>();
    /** 已放置的棺材实体 UUID（一局上限由技能充能控制，此处双保险） */
    public final List<UUID> coffinUuids = new ArrayList<>();
    /** 玩家在自己棺材周边累计停留 tick */
    private final Map<UUID, Integer> coffinNearTicks = new HashMap<>();
    /** 被标记玩家（棺材旁累计达标）：红色透视 + 现身期间可被左键击杀，整局保持 */
    public final Set<UUID> markedPlayers = new HashSet<>();

    static {
        // ===== 被动无敌：拦截原版伤害系统 =====
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayer serverPlayer)) {
                return true;
            }
            if (!isMummy(serverPlayer)) {
                return true;
            }
            return false;
        });

        // ===== 被动无敌：拦截带击杀者的直接死亡调用 =====
        AllowPlayerDeathWithKiller.EVENT.register((player, killer, deathReason) -> {
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return true;
            }
            return !isMummy(serverPlayer);
        });

        // ===== 被动无敌：拦截无击杀者的死亡 =====
        AllowPlayerDeath.EVENT.register((player, deathReason) -> {
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return true;
            }
            return !isMummy(serverPlayer);
        });

        // ===== 现身击杀：完整现身期间左键击杀被标记玩家 =====
        AttackEntityCallback.EVENT.register((attacker, level, hand, targetEntity, hitResult) -> {
            if (!(attacker instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.PASS;
            }
            if (!isMummy(serverPlayer)) {
                return InteractionResult.PASS;
            }
            MunaiyiDesertPlayerComponent comp = KEY.maybeGet(serverPlayer).orElse(null);
            if (comp == null || comp.fullRevealTicks <= 0) {
                return InteractionResult.PASS;
            }
            if (!(targetEntity instanceof Player victim)) {
                return InteractionResult.PASS;
            }
            if (!comp.markedPlayers.contains(victim.getUUID())) {
                return InteractionResult.PASS;
            }
            if (!GameUtils.isPlayerAliveAndSurvival(victim)) {
                return InteractionResult.PASS;
            }
            GameUtils.killPlayer(victim, true, serverPlayer, GameConstants.DeathReasons.GENERIC);
            return InteractionResult.SUCCESS;
        });

        // ===== 游戏结束：清空状态并丢弃残留棺材（棺材实体另有 EntityClearUtils 兜底） =====
        OnGameEnd.EVENT.register((serverLevel, gameWorldComponent) -> {
            for (ServerPlayer p : serverLevel.players()) {
                KEY.maybeGet(p).ifPresent(MunaiyiDesertPlayerComponent::clear);
            }
        });
    }

    public MunaiyiDesertPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    public static boolean isMummy(Player player) {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        return gameWorld.isRole(player, ModRoles.MUNAIYI_DESERT);
    }

    public void sync() {
        KEY.sync(player);
    }

    /** 是否处于任意现身状态（可见） */
    public boolean isRevealed() {
        return revealTicks > 0 || fullRevealTicks > 0;
    }

    // ==================== 生命周期 ====================

    @Override
    public void init() {
        // 开局角色分配为分波触发，init 可能被多次调用；
        // 已存在激活状态时只同步不清零，避免 mid-round 被框架回调误清
        if (revealTicks > 0 || fullRevealTicks > 0 || !curseStacks.isEmpty() || !coffinUuids.isEmpty()) {
            sync();
            return;
        }
        resetState();
        sync();
    }

    @Override
    public void clear() {
        discardCoffins();
        resetState();
        if (player.hasEffect(MobEffects.INVISIBILITY)) {
            player.removeEffect(MobEffects.INVISIBILITY);
        }
        if (player.hasEffect(ModEffects.JINGBU)) {
            player.removeEffect(ModEffects.JINGBU);
        }
        sync();
    }

    private void resetState() {
        revealTicks = 0;
        fullRevealTicks = 0;
        curseStacks.clear();
        sleepTimers.clear();
        coffinUuids.clear();
        coffinNearTicks.clear();
        markedPlayers.clear();
    }

    private void discardCoffins() {
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        for (UUID uuid : coffinUuids) {
            if (serverLevel.getEntity(uuid) instanceof CoffinEntity coffin) {
                coffin.discard();
            }
        }
    }

    // ==================== 每 tick 逻辑 ====================

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        // 角色被替换/移除时，还原隐身/静步状态并静默退出
        if (!isMummy(serverPlayer)) {
            if (revealTicks > 0 || fullRevealTicks > 0) {
                revealTicks = 0;
                fullRevealTicks = 0;
                if (player.hasEffect(MobEffects.INVISIBILITY)) {
                    player.removeEffect(MobEffects.INVISIBILITY);
                }
                sync();
            }
            if (player.hasEffect(ModEffects.JINGBU)) {
                player.removeEffect(ModEffects.JINGBU);
            }
            return;
        }
        // 守卫基于实体状态（死亡/旁观不推进技能状态），回合结束由框架 clear 统一清理
        if (!GameUtils.isPlayerAliveAndSurvival(serverPlayer)) {
            return;
        }

        boolean changed = false;
        if (fullRevealTicks > 0 && --fullRevealTicks == 0) {
            changed = true;
        }
        if (revealTicks > 0 && --revealTicks == 0) {
            changed = true;
        }

        // 维持/解除隐身
        if (isRevealed()) {
            if (player.hasEffect(MobEffects.INVISIBILITY)) {
                player.removeEffect(MobEffects.INVISIBILITY);
                changed = true;
            }
        } else if (!player.hasEffect(MobEffects.INVISIBILITY)) {
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, Integer.MAX_VALUE, 0, false, false, false));
            changed = true;
        }

        // 维持静步（同特工静步，屏蔽脚步声）：存活且在场期间常驻，不随现身状态变化（参考常驻隐身的维持方式）
        if (!player.hasEffect(ModEffects.JINGBU)) {
            player.addEffect(new MobEffectInstance(ModEffects.JINGBU, Integer.MAX_VALUE, 0, false, false, false));
        }

        tickCurses(serverPlayer);
        tickCoffins(serverPlayer);

        if (changed) {
            sync();
        }
    }

    /** 诅咒维护：目标死亡/离线移除；床上睡觉每 3s 消 1 层 */
    private void tickCurses(ServerPlayer serverPlayer) {
        if (curseStacks.isEmpty()) {
            return;
        }
        Level level = player.level();
        List<UUID> toRemove = new ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : curseStacks.entrySet()) {
            UUID uuid = entry.getKey();
            Player target = level.getPlayerByUUID(uuid);
            if (target == null || !GameUtils.isPlayerAliveAndSurvival(target)) {
                toRemove.add(uuid);
                continue;
            }
            if (target.isSleeping()) {
                int timer = sleepTimers.getOrDefault(uuid, 0) + 1;
                if (timer >= 60) { // 3s
                    timer = 0;
                    int stacks = entry.getValue() - 1;
                    if (stacks <= 0) {
                        toRemove.add(uuid);
                    } else {
                        entry.setValue(stacks);
                        sync();
                    }
                }
                sleepTimers.put(uuid, timer);
            } else {
                sleepTimers.remove(uuid);
            }
        }
        if (!toRemove.isEmpty()) {
            for (UUID uuid : toRemove) {
                curseStacks.remove(uuid);
                sleepTimers.remove(uuid);
            }
            sync();
        }
    }

    /** 棺材维护：清理失效实体；周边玩家累计停留达标后标记（红色透视 + 可被现身击杀） */
    private void tickCoffins(ServerPlayer serverPlayer) {
        ServerLevel serverLevel = serverPlayer.serverLevel();
        boolean removed = coffinUuids.removeIf(uuid -> !(serverLevel.getEntity(uuid) instanceof CoffinEntity));
        if (removed) {
            sync();
        }
        if (coffinUuids.isEmpty()) {
            return;
        }
        NoellesRolesConfig config = NoellesRolesConfig.HANDLER.instance();
        double radiusSqr = config.munaiyiCoffinMarkRadius * config.munaiyiCoffinMarkRadius;
        int neededTicks = config.munaiyiCoffinMarkSeconds * 20;

        List<CoffinEntity> coffins = new ArrayList<>();
        for (UUID uuid : coffinUuids) {
            if (serverLevel.getEntity(uuid) instanceof CoffinEntity coffin) {
                coffins.add(coffin);
            }
        }
        for (ServerPlayer other : serverLevel.players()) {
            if (other == serverPlayer || !GameUtils.isPlayerAliveAndSurvival(other)) {
                continue;
            }
            boolean near = false;
            for (CoffinEntity coffin : coffins) {
                if (coffin.distanceToSqr(other) <= radiusSqr) {
                    near = true;
                    break;
                }
            }
            if (!near) {
                continue; // 累计制：离开不清零
            }
            int ticks = Math.min(neededTicks, coffinNearTicks.getOrDefault(other.getUUID(), 0) + 1);
            coffinNearTicks.put(other.getUUID(), ticks);
            if (ticks >= neededTicks && markedPlayers.add(other.getUUID())) {
                serverPlayer.displayClientMessage(
                        Component.translatable("message.noellesroles.munaiyi.marked", other.getName())
                                .withStyle(ChatFormatting.DARK_RED),
                        false);
                sync();
            }
        }
    }

    // ==================== 技能入口 ====================

    /**
     * 技能1：对目标施加一层诅咒（实际由选人 C2S 包触发）。
     */
    public boolean applyCurse(ServerPlayer target) {
        if (!GameUtils.isPlayerAliveAndSurvival(target) || target == player) {
            return false;
        }
        int stacks = curseStacks.getOrDefault(target.getUUID(), 0);
        if (stacks >= MAX_CURSE_STACKS) {
            return false;
        }
        curseStacks.put(target.getUUID(), stacks + 1);
        briefReveal();
        sync();
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.munaiyi.curse_applied", target.getName(),
                            stacks + 1).withStyle(ChatFormatting.YELLOW),
                    true);
        }
        return true;
    }

    /**
     * 技能2：恐吓。隐身状态 → 3 格内玩家红色字幕；现身状态 → 恶魂音效 + 15 格内缓慢/反胃/黑暗 15s。
     */
    public boolean scare() {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        ServerLevel serverLevel = serverPlayer.serverLevel();
        if (!isRevealed()) {
            String text = serverPlayer.getRandom().nextBoolean()
                    ? "I will kill u....."
                    : "get out!!!";
            for (ServerPlayer target : serverLevel.players()) {
                if (target == serverPlayer || !GameUtils.isPlayerAliveAndSurvival(target)) {
                    continue;
                }
                if (target.distanceToSqr(serverPlayer) <= 9.0D) { // 3 格
                    target.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 10));
                    target.connection.send(new ClientboundSetTitleTextPacket(
                            Component.literal(text).withStyle(ChatFormatting.RED)));
                }
            }
            briefReveal();
            sync();
        } else {
            serverLevel.playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                    SoundEvents.GHAST_SCREAM, SoundSource.HOSTILE, 1.0F, 0.8F);
            for (ServerPlayer target : serverLevel.players()) {
                if (!GameUtils.isPlayerAliveAndSurvival(target)) {
                    continue;
                }
                if (target.distanceToSqr(serverPlayer) <= 225.0D) { // 15 格
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 300, 0, false, false, true));
                    target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 300, 0, false, false, true));
                    target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 300, 0, false, false, true));
                }
            }
        }
        return true;
    }

    /**
     * 技能3：现身。条件2（棺材旁标记玩家）优先，其次条件1（3 层诅咒玩家环形区）。
     * 两条件均不满足返回 false（不进冷却）。
     */
    public boolean tryReveal() {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        ServerLevel serverLevel = serverPlayer.serverLevel();

        // 条件2：存在被标记玩家且其位于某棺材 6 格内 → 现身在该棺材旁
        for (UUID coffinId : coffinUuids) {
            if (!(serverLevel.getEntity(coffinId) instanceof CoffinEntity coffin)) {
                continue;
            }
            for (UUID markedId : markedPlayers) {
                Player marked = serverLevel.getPlayerByUUID(markedId);
                if (marked == null || !GameUtils.isPlayerAliveAndSurvival(marked)) {
                    continue;
                }
                if (marked.distanceToSqr(coffin) <= 36.0D) { // 6 格
                    teleportReveal(serverPlayer, coffin.getX(), coffin.getY(), coffin.getZ());
                    return true;
                }
            }
        }

        // 条件1：随机选取一名 3 层诅咒玩家，在其 4~10 格环形区采样合法落点
        List<UUID> candidates = new ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : curseStacks.entrySet()) {
            if (entry.getValue() >= MAX_CURSE_STACKS) {
                candidates.add(entry.getKey());
            }
        }
        if (candidates.isEmpty()) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.munaiyi.reveal_no_target")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        UUID chosen = candidates.get(serverPlayer.getRandom().nextInt(candidates.size()));
        Player target = serverLevel.getPlayerByUUID(chosen);
        if (target == null || !GameUtils.isPlayerAliveAndSurvival(target)) {
            curseStacks.remove(chosen);
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.munaiyi.reveal_no_target")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        for (int i = 0; i < 24; i++) {
            double angle = serverPlayer.getRandom().nextDouble() * Math.PI * 2.0D;
            double dist = 4.0D + serverPlayer.getRandom().nextDouble() * 6.0D;
            double x = target.getX() + Math.cos(angle) * dist;
            double z = target.getZ() + Math.sin(angle) * dist;
            BlockPos base = BlockPos.containing(x, target.getY(), z);
            for (int dy = 4; dy >= -4; dy--) {
                BlockPos stand = base.offset(0, dy, 0);
                if (isStandable(serverLevel, stand)) {
                    teleportReveal(serverPlayer, stand.getX() + 0.5D, stand.getY(), stand.getZ() + 0.5D);
                    return true;
                }
            }
        }
        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.munaiyi.reveal_no_space")
                        .withStyle(ChatFormatting.RED),
                true);
        return false;
    }

    private static boolean isStandable(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos.below()).isCollisionShapeFullBlock(level, pos.below())
                && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty();
    }

    private void teleportReveal(ServerPlayer serverPlayer, double x, double y, double z) {
        ServerLevel serverLevel = serverPlayer.serverLevel();
        NoellesRolesConfig config = NoellesRolesConfig.HANDLER.instance();
        serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, serverPlayer.getX(), serverPlayer.getY() + 1.0D,
                serverPlayer.getZ(), 20, 0.4D, 0.6D, 0.4D, 0.02D);
        serverPlayer.teleportTo(serverLevel, x, y, z, serverPlayer.getYRot(), serverPlayer.getXRot());
        serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 1.0D, z, 20, 0.4D, 0.6D, 0.4D, 0.02D);
        serverLevel.playSound(null, x, y, z, SoundEvents.HUSK_AMBIENT, SoundSource.HOSTILE, 1.0F, 0.7F);
        fullRevealTicks = config.munaiyiRevealDuration * 20;
        revealTicks = 0;
        if (player.hasEffect(MobEffects.INVISIBILITY)) {
            player.removeEffect(MobEffects.INVISIBILITY);
        }
        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.munaiyi.reveal_active", config.munaiyiRevealDuration)
                        .withStyle(ChatFormatting.GOLD),
                false);
        sync();
    }

    /**
     * 技能4：领地确认 —— 脚下 2x2 平地时放置棺材。
     */
    public boolean placeCoffin() {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        NoellesRolesConfig config = NoellesRolesConfig.HANDLER.instance();
        if (coffinUuids.size() >= config.munaiyiMaxCoffins) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.munaiyi.coffin_limit")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        ServerLevel serverLevel = serverPlayer.serverLevel();
        // 玩家脚下 2x2 区域：四个角下方方块须同一高度且均为实心
        AABB box = serverPlayer.getBoundingBox();
        BlockPos[] corners = {
                BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(Math.max(box.minX, box.maxX - 0.001D), box.minY, box.minZ),
                BlockPos.containing(box.minX, box.minY, Math.max(box.minZ, box.maxZ - 0.001D)),
                BlockPos.containing(Math.max(box.minX, box.maxX - 0.001D), box.minY,
                        Math.max(box.minZ, box.maxZ - 0.001D)),
        };
        int groundY = corners[0].getY() - 1;
        for (BlockPos corner : corners) {
            BlockPos below = corner.below();
            if (below.getY() != groundY
                    || !serverLevel.getBlockState(below).isCollisionShapeFullBlock(serverLevel, below)) {
                serverPlayer.displayClientMessage(
                        Component.translatable("message.noellesroles.munaiyi.not_flat")
                                .withStyle(ChatFormatting.RED),
                        true);
                return false;
            }
        }

        CoffinEntity coffin = new CoffinEntity(ModEntities.COFFIN, serverLevel);
        coffin.setYRot(serverPlayer.getYRot());
        // 直接吸附到 2×2 校验得到的地面顶面并关闭重力，避免棺材下落偏移卡进地里
        coffin.setPos(serverPlayer.getX(), groundY + 1, serverPlayer.getZ());
        coffin.setNoGravity(true);
        serverLevel.addFreshEntity(coffin);
        coffinUuids.add(coffin.getUUID());
        briefReveal();
        sync();
        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.munaiyi.coffin_placed",
                        config.munaiyiMaxCoffins - coffinUuids.size()).withStyle(ChatFormatting.GREEN),
                true);
        return true;
    }

    /**
     * 技能5：干枯 —— 仅完整现身可用，周围玩家口渴值下降 40%（保底剩余 5%）。
     */
    public boolean wither() {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        if (fullRevealTicks <= 0) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.munaiyi.wither_need_reveal")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        NoellesRolesConfig config = NoellesRolesConfig.HANDLER.instance();
        double radiusSqr = (double) config.munaiyiWitherRadius * config.munaiyiWitherRadius;
        for (ServerPlayer target : serverPlayer.serverLevel().players()) {
            if (target == serverPlayer || !GameUtils.isPlayerAliveAndSurvival(target)) {
                continue;
            }
            if (target.distanceToSqr(serverPlayer) <= radiusSqr) {
                org.agmas.noellesroles.scene.MapStatusBarRuntime.drainThirstPercent(target, 0.4F, 1);
            }
        }
        serverLevelPlayWitherFx(serverPlayer);
        return true;
    }

    private void serverLevelPlayWitherFx(ServerPlayer serverPlayer) {
        serverPlayer.serverLevel().sendParticles(ParticleTypes.CRIT, serverPlayer.getX(),
                serverPlayer.getY() + 1.0D, serverPlayer.getZ(), 30, 1.2D, 0.8D, 1.2D, 0.1D);
        serverPlayer.serverLevel().playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                SoundEvents.HUSK_DEATH, SoundSource.HOSTILE, 0.8F, 0.6F);
    }

    /**
     * 被动：释放技能后的短暂现身（仅解除隐身，不赋予击杀能力）。
     */
    public void briefReveal() {
        if (fullRevealTicks > 0) {
            return; // 完整现身期间不覆盖
        }
        int ticks = NoellesRolesConfig.HANDLER.instance().munaiyiBriefRevealSeconds * 20;
        revealTicks = Math.max(revealTicks, ticks);
        if (player.hasEffect(MobEffects.INVISIBILITY)) {
            player.removeEffect(MobEffects.INVISIBILITY);
        }
    }

    /** 客户端/服务端共用：剩余完整现身 tick（供手势 mixin 与 HUD 读取） */
    public int getFullRevealTicks() {
        return fullRevealTicks;
    }

    // ==================== 同步 NBT ====================

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        // 其他客户端需要读取现身状态渲染僵尸手势，全量广播
        return true;
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("revealTicks", revealTicks);
        tag.putInt("fullRevealTicks", fullRevealTicks);

        ListTag curses = new ListTag();
        for (Map.Entry<UUID, Integer> entry : curseStacks.entrySet()) {
            CompoundTag item = new CompoundTag();
            item.putUUID("uuid", entry.getKey());
            item.putInt("stacks", entry.getValue());
            curses.add(item);
        }
        tag.put("curses", curses);

        ListTag coffins = new ListTag();
        for (UUID uuid : coffinUuids) {
            CompoundTag item = new CompoundTag();
            item.putUUID("uuid", uuid);
            coffins.add(item);
        }
        tag.put("coffins", coffins);

        ListTag marked = new ListTag();
        for (UUID uuid : markedPlayers) {
            CompoundTag item = new CompoundTag();
            item.putUUID("uuid", uuid);
            marked.add(item);
        }
        tag.put("marked", marked);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        revealTicks = tag.getInt("revealTicks");
        fullRevealTicks = tag.getInt("fullRevealTicks");

        curseStacks.clear();
        ListTag curses = tag.getList("curses", Tag.TAG_COMPOUND);
        for (int i = 0; i < curses.size(); i++) {
            CompoundTag item = curses.getCompound(i);
            curseStacks.put(item.getUUID("uuid"), item.getInt("stacks"));
        }

        coffinUuids.clear();
        ListTag coffins = tag.getList("coffins", Tag.TAG_COMPOUND);
        for (int i = 0; i < coffins.size(); i++) {
            coffinUuids.add(coffins.getCompound(i).getUUID("uuid"));
        }

        markedPlayers.clear();
        ListTag marked = tag.getList("marked", Tag.TAG_COMPOUND);
        for (int i = 0; i < marked.size(); i++) {
            markedPlayers.add(marked.getCompound(i).getUUID("uuid"));
        }
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 不持久化：回合内状态由框架 init/clear 管理
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 不持久化
    }
}
