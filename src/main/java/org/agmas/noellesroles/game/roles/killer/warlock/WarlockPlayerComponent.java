package org.agmas.noellesroles.game.roles.killer.warlock;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.data.PlayerEconomyManager;
import io.wifi.starrailexpress.event.OnPlayerDeath;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 咒术师（Warlock，重做版）—— 杀手阵营。
 * <p>
 * 三技能：
 * - 【窃取发肤】瞄准 8 格内存活玩家，悄悄窃取一份「咒物」，每名玩家整局仅能被窃取一次；
 *   目标只收到一句模糊的寒意提示，不暴露咒术师
 * - 【蚀骨之咒】消耗一份咒物诅咒其主人 45 秒：暂时隔离 + 缓慢 + 扣除 SAN；
 *   被诅咒者在此期间死亡（不限死因、不限凶手）→ 咒术师获得咒酬
 * - 【领域展开·灰髓之境】在背包点选一名已被诅咒且存活的目标，把自己与该目标一并拉入
 *   高空灰雾祭场，见 {@link WarlockDomainManager}
 * </p>
 * <p>
 * 移植说明：内层用 {@code RoleData} 的绝对时间戳（{@code getTicksFromGameStart}）计时，
 * 外层没有该全局时钟，故一律改为组件自持的倒计时字段，由 {@link #serverTick()} 驱动；
 * 时停（{@code tickRateManager().isFrozen()}）期间倒计时暂停，与内层「冻结时 tickCount 不前进」等价。
 * </p>
 */
public class WarlockPlayerComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<WarlockPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "warlock"),
            WarlockPlayerComponent.class);

    /** 窃取发肤的最大距离。 */
    public static final double STEAL_RANGE = 8.0D;
    /** 蚀骨之咒（诅咒标记）持续时间（tick）：期间目标可被领域拉入、死亡给咒酬。 */
    public static final int CURSE_DURATION_TICKS = 45 * 20;
    /** 蚀咒目标自动搜索半径（未瞄准咒物主人时取最近者）。 */
    public static final double CURSE_AUTO_RANGE = 40.0D;
    /** 被诅咒者死亡时咒术师获得的咒酬。 */
    public static final int CURSE_REWARD_COINS = 40;
    /** 蚀骨之咒·隔离效果持续时间（tick，"暂时隔离"）。 */
    public static final int CURSE_ISOLATION_TICKS = 8 * 20;
    /** 蚀骨之咒·缓慢效果持续时间（tick）。 */
    public static final int CURSE_SLOW_TICKS = 8 * 20;
    /** 蚀骨之咒·扣除的 SAN 值比例（0~1）。 */
    public static final float CURSE_SAN_DRAIN = 0.30F;
    /** 领域展开冷却（tick）。 */
    public static final int DOMAIN_COOLDOWN_TICKS = 60 * 20;

    static {
        // 被诅咒者死亡（不限死因）→ 咒术师收取咒酬。
        // 两个事件都挂：rewardCurseOnDeath 内部先移除条目再发钱，重复触发时第二次查不到条目即空转，
        // 因此不会重复结算。
        OnPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> rewardCurseOnDeath(victim));
        OnPlayerDeath.EVENT.register((victim, deathReason) -> rewardCurseOnDeath(victim));
    }

    private final Player player;

    /** 已窃取咒物的玩家（发肤主人）。 */
    public final Set<UUID> essences = new LinkedHashSet<>();
    /** 已被窃取过的玩家（包括咒物已消耗的），保证每人整局只能被窃取一次。 */
    public final Set<UUID> everStolen = new LinkedHashSet<>();
    /** 当前被诅咒的玩家 → 诅咒剩余 tick。领域只能拉入其中存活者，被诅咒者死亡给咒酬。 */
    public final Map<UUID, Integer> cursedPlayers = new LinkedHashMap<>();
    /** 领域展开冷却剩余 tick。 */
    public int domainCooldown;
    /** 领域是否展开（由 {@link WarlockDomainManager} 维护，同步给 HUD）。 */
    public boolean domainOpen;
    /** 领域剩余 tick（由 {@link WarlockDomainManager} 维护，同步给 HUD）。 */
    public int domainRemaining;

    public WarlockPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    public void sync() {
        KEY.sync(player);
    }

    // ── 初始化 / 清理 ──────────────────────────────────────────

    @Override
    public void init() {
        essences.clear();
        everStolen.clear();
        cursedPlayers.clear();
        domainCooldown = 0;
        domainOpen = false;
        domainRemaining = 0;
        sync();
    }

    @Override
    public void clear() {
        if (player instanceof ServerPlayer sp && sp.server != null) {
            WarlockDomainManager.forceEnd(sp.getUUID(), sp.server);
        }
        init();
    }

    public boolean isActiveWarlock() {
        if (player == null || player.level().isClientSide()) {
            return false;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        return gameWorld != null && gameWorld.isRole(player, ModRoles.WARLOCK);
    }

    /**
     * 时停判定：外层没有 {@code GameUtils.isTimeFrozen}，直接用原版 tick 冻结状态。
     * 时停期间所有倒计时暂停，与内层「冻结时 getTicksFromGameStart 不前进」等价。
     */
    public static boolean isTimeFrozen(@Nullable MinecraftServer server) {
        return server != null && server.tickRateManager().isFrozen();
    }

    // ── 技能一：窃取发肤 ─────────────────────────────────────────

    public boolean trySteal(@Nullable ServerPlayer target) {
        if (!(player instanceof ServerPlayer sp) || !isActiveWarlock()) {
            return false;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) {
            return false;
        }
        if (target == null || target == sp || !GameUtils.isPlayerAliveAndSurvival(target)) {
            fail(sp, "message.noellesroles.warlock.steal_no_target");
            return false;
        }
        if (sp.distanceTo(target) > STEAL_RANGE) {
            fail(sp, "message.noellesroles.warlock.steal_too_far");
            return false;
        }
        if (everStolen.contains(target.getUUID())) {
            fail(sp, "message.noellesroles.warlock.steal_already");
            return false;
        }

        essences.add(target.getUUID());
        everStolen.add(target.getUUID());
        sync();

        ServerLevel level = sp.serverLevel();
        level.sendParticles(ParticleTypes.SOUL, target.getX(), target.getY() + 1.0D, target.getZ(),
                6, 0.25D, 0.4D, 0.25D, 0.01D);
        level.playSound(null, target.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS,
                0.4F, 0.6F);

        sp.displayClientMessage(Component
                .translatable("message.noellesroles.warlock.stolen", target.getName().getString())
                .withStyle(ChatFormatting.DARK_PURPLE), true);
        // 目标只得到一句模糊的寒意提示，不暴露咒术师
        target.displayClientMessage(Component
                .translatable("message.noellesroles.warlock.steal_victim_hint")
                .withStyle(ChatFormatting.DARK_GRAY), true);
        return true;
    }

    // ── 技能二：蚀骨之咒 ─────────────────────────────────────────

    public boolean tryCurse(@Nullable ServerPlayer crosshair) {
        if (!(player instanceof ServerPlayer sp) || !isActiveWarlock()) {
            return false;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) {
            return false;
        }
        if (essences.isEmpty()) {
            fail(sp, "message.noellesroles.warlock.no_essence");
            return false;
        }

        ServerPlayer target = null;
        if (crosshair != null && essences.contains(crosshair.getUUID())
                && GameUtils.isPlayerAliveAndSurvival(crosshair)) {
            target = crosshair;
        } else {
            double best = CURSE_AUTO_RANGE * CURSE_AUTO_RANGE;
            for (UUID uuid : essences) {
                ServerPlayer candidate = sp.server.getPlayerList().getPlayer(uuid);
                if (candidate == null || !GameUtils.isPlayerAliveAndSurvival(candidate)) {
                    continue;
                }
                double dist = sp.distanceToSqr(candidate);
                if (dist < best) {
                    best = dist;
                    target = candidate;
                }
            }
        }
        if (target == null) {
            fail(sp, "message.noellesroles.warlock.curse_no_target");
            return false;
        }

        essences.remove(target.getUUID());
        cursedPlayers.put(target.getUUID(), CURSE_DURATION_TICKS);
        sync();

        // 蚀骨之咒：暂时隔离 + 缓慢 + 扣除 30% SAN
        target.addEffect(
                new MobEffectInstance(ModEffects.PLAYER_ISOLATION, CURSE_ISOLATION_TICKS, 0, false, false, true));
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, CURSE_SLOW_TICKS, 1, false, false, true));
        SREPlayerMoodComponent.KEY.get(target).addMood(-CURSE_SAN_DRAIN);
        target.serverLevel().playSound(null, target.blockPosition(), SoundEvents.WARDEN_HEARTBEAT,
                SoundSource.PLAYERS, 1.2F, 0.7F);
        target.displayClientMessage(Component
                .translatable("message.noellesroles.warlock.curse_victim_hint")
                .withStyle(ChatFormatting.DARK_PURPLE), true);
        sp.displayClientMessage(Component
                .translatable("message.noellesroles.warlock.cursed", target.getName().getString())
                .withStyle(ChatFormatting.DARK_PURPLE), true);
        return true;
    }

    // ── 技能三：领域展开（在背包 LimitedInventoryScreen 中点选已被诅咒且存活的目标）──────

    /**
     * 对指定的（已被诅咒且存活的）目标展开领域，仅拉入这一人。60s 冷却。
     * 校验：角色 / 存活 / 冷却 / 目标处于诅咒中且存活 / 目标不在其他领域内。
     */
    public boolean tryOpenDomainOn(@Nullable UUID victimUuid) {
        if (!(player instanceof ServerPlayer sp) || !isActiveWarlock()) {
            return false;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) {
            return false;
        }
        if (isTimeFrozen(sp.server)) {
            return false;
        }
        if (domainCooldown > 0) {
            fail(sp, "message.noellesroles.warlock.domain_cooldown");
            return false;
        }
        if (victimUuid == null || !isCursedAlive(sp, victimUuid)) {
            fail(sp, "message.noellesroles.warlock.domain_no_victims");
            return false;
        }
        ServerPlayer victim = sp.server.getPlayerList().getPlayer(victimUuid);
        if (victim == null || !GameUtils.isPlayerAliveAndSurvival(victim)) {
            fail(sp, "message.noellesroles.warlock.domain_no_victims");
            return false;
        }
        if (ModEffects.isInAnyDomain(victim)) {
            fail(sp, "message.noellesroles.domain.already_in_domain");
            return false;
        }
        boolean opened = WarlockDomainManager.open(sp, this, victim);
        if (opened) {
            domainCooldown = DOMAIN_COOLDOWN_TICKS;
            sync();
        }
        return opened;
    }

    /** 判断某玩家当前是否处于（未过期的）诅咒中且存活。 */
    public boolean isCursedAlive(ServerPlayer warlock, UUID uuid) {
        Integer remaining = cursedPlayers.get(uuid);
        if (remaining == null || remaining <= 0) {
            return false;
        }
        ServerPlayer target = warlock.server.getPlayerList().getPlayer(uuid);
        return target != null && GameUtils.isPlayerAliveAndSurvival(target);
    }

    /** 当前处于诅咒中（未过期）的目标数量，供 HUD 使用。 */
    public int getCursedCount() {
        int count = 0;
        for (int remaining : cursedPlayers.values()) {
            if (remaining > 0) {
                count++;
            }
        }
        return count;
    }

    // ── Tick ───────────────────────────────────────────────────

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp) || !isActiveWarlock()) {
            return;
        }
        // 时停期间全部倒计时暂停（领域倒计时由 WarlockDomainManager 同样在时停时跳过）
        if (isTimeFrozen(sp.server)) {
            return;
        }

        boolean changed = false;

        if (domainCooldown > 0) {
            domainCooldown--;
            changed = true;
        }

        if (!cursedPlayers.isEmpty()) {
            long gameTime = sp.level().getGameTime();
            Iterator<Map.Entry<UUID, Integer>> it = cursedPlayers.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Integer> entry = it.next();
                int remaining = entry.getValue() - 1;
                if (remaining <= 0) {
                    it.remove();
                    changed = true;
                    continue;
                }
                entry.setValue(remaining);
                changed = true;
                // 被诅咒者周身萦绕灵魂颗粒
                if (gameTime % 15 == 0) {
                    ServerPlayer target = sp.server.getPlayerList().getPlayer(entry.getKey());
                    if (target != null && GameUtils.isPlayerAliveAndSurvival(target)) {
                        target.serverLevel().sendParticles(ParticleTypes.SOUL,
                                target.getX(), target.getY() + 0.9D, target.getZ(),
                                2, 0.2D, 0.35D, 0.2D, 0.005D);
                    }
                }
            }
        }

        // 每秒同步一次（倒计时逐 tick 变化，按秒同步足够驱动 HUD 且省流量）
        if (changed && sp.tickCount % 20 == 0) {
            sync();
        }
    }

    // ── 咒酬结算 ────────────────────────────────────────────────

    private static void rewardCurseOnDeath(Player victim) {
        if (!(victim instanceof ServerPlayer sv) || sv.server == null) {
            return;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(sv.level());
        if (gameWorld == null) {
            return;
        }
        for (ServerPlayer candidate : sv.server.getPlayerList().getPlayers()) {
            if (!gameWorld.isRole(candidate, ModRoles.WARLOCK)) {
                continue;
            }
            WarlockPlayerComponent comp = KEY.maybeGet(candidate).orElse(null);
            if (comp == null) {
                continue;
            }
            Integer remaining = comp.cursedPlayers.get(sv.getUUID());
            if (remaining == null || remaining <= 0) {
                continue;
            }
            comp.cursedPlayers.remove(sv.getUUID());
            PlayerEconomyManager.addCoinNum(candidate, CURSE_REWARD_COINS);
            candidate.displayClientMessage(Component
                    .translatable("message.noellesroles.warlock.curse_reward", CURSE_REWARD_COINS)
                    .withStyle(ChatFormatting.GOLD), true);
            comp.sync();
        }
    }

    private static void fail(ServerPlayer sp, String key) {
        sp.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), true);
    }

    // ── NBT 同步 ────────────────────────────────────────────────

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider lookup) {
        ListTag list = new ListTag();
        for (UUID uuid : essences) {
            list.add(StringTag.valueOf(uuid.toString()));
        }
        tag.put("essences", list);
        ListTag cursedList = new ListTag();
        for (Map.Entry<UUID, Integer> entry : cursedPlayers.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putString("uuid", entry.getKey().toString());
            c.putInt("remaining", entry.getValue());
            cursedList.add(c);
        }
        tag.put("cursedPlayers", cursedList);
        tag.putInt("domainCooldown", domainCooldown);
        tag.putBoolean("domainOpen", domainOpen);
        tag.putInt("domainRemaining", domainRemaining);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider lookup) {
        essences.clear();
        for (Tag entry : tag.getList("essences", Tag.TAG_STRING)) {
            essences.add(UUID.fromString(entry.getAsString()));
        }
        cursedPlayers.clear();
        for (Tag entry : tag.getList("cursedPlayers", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) entry;
            cursedPlayers.put(UUID.fromString(c.getString("uuid")), c.getInt("remaining"));
        }
        domainCooldown = tag.getInt("domainCooldown");
        domainOpen = tag.getBoolean("domainOpen");
        domainRemaining = tag.getInt("domainRemaining");
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider lookup) {
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider lookup) {
    }
}
