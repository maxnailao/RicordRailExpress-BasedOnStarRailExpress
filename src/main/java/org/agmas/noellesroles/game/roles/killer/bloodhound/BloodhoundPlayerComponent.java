package org.agmas.noellesroles.game.roles.killer.bloodhound;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.event.AllowShootRevolverDrop;
import io.wifi.starrailexpress.event.OnGiveKillerBalance;
import io.wifi.starrailexpress.event.OnRevolverUsed;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import io.wifi.starrailexpress.util.SRENetworkMessageUtils;
import io.wifi.starrailexpress.util.TrueFalseResult;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.MCItemsUtils;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 寻血猎犬组件（杀手 / 狼人阵营）。
 *
 * <p>本组件只负责技能「众神之眼」的服务端逻辑与「被透视玩家集合」的同步；
 * 透视轮廓的实际渲染在客户端 InstinctRenderer 中完成（读取 revealedPlayers）。
 */
public class BloodhoundPlayerComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<BloodhoundPlayerComponent> KEY = ModComponents.BLOODHOUND;

    /** 红圈扫描粒子（猩红） */
    private static final DustParticleOptions SCAN_DUST =
            new DustParticleOptions(new Vector3f(1.0f, 0.12f, 0.12f), 1.6f);

    /** 每隔多少 tick 生成一圈扫描粒子（降低网络/粒子压力） */
    private static final int RING_PARTICLE_INTERVAL = 4;

    private final Player player;

    // ===== 服务端扫描状态 =====
    private int scanExpandTicks = 0;
    private int scanElapsed = 0;
    private int scanExpandTotal = 1;
    private double scanOriginX, scanOriginY, scanOriginZ;
    private final Map<UUID, Long> revealExpiry = new HashMap<>();

    // ===== 同步到本人客户端 =====
    public final Set<UUID> revealedPlayers = new HashSet<>();

    // ===== 狂野猎人（特殊疯魔）状态 =====
    private int frenzyTicks = 0;
    /** 是否处于「狂野猎人」疯魔中（同步到本人客户端，供 InstinctRenderer 读取） */
    public boolean inFrenzy = false;

    public BloodhoundPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer p) {
        return p == player;
    }

    public void sync() {
        KEY.sync(player);
    }

    @Override
    public void init() {
        scanExpandTicks = 0;
        scanElapsed = 0;
        scanExpandTotal = 1;
        revealExpiry.clear();
        revealedPlayers.clear();
        frenzyTicks = 0;
        inFrenzy = false;
        sync();
    }

    @Override
    public void clear() {
        init();
    }

    /** 技能「众神之眼」入口：开始一次红圈扩散扫描。成功返回 true（消耗冷却）。 */
    public boolean startScan() {
        if (!(player instanceof ServerPlayer sp))
            return false;
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
        if (!game.isRunning() || !game.isRole(player, ModRoles.BLOODHOUND))
            return false;
        if (!GameUtils.isPlayerAliveAndSurvival(player))
            return false;
        if (scanExpandTicks > 0)
            return false;

        NoellesRolesConfig cfg = NoellesRolesConfig.HANDLER.instance();
        this.scanOriginX = sp.getX();
        this.scanOriginY = sp.getY();
        this.scanOriginZ = sp.getZ();
        this.scanElapsed = 0;
        this.scanExpandTotal = Math.max(1, cfg.bloodhoundScanExpandTicks);
        this.scanExpandTicks = this.scanExpandTotal;
        sp.serverLevel().playSound(null, scanOriginX, scanOriginY, scanOriginZ,
                SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 1.0f, 2.0f);
        return true;
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp))
            return;
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
        if (!game.isRunning() || !game.isRole(player, ModRoles.BLOODHOUND)
                || !GameUtils.isPlayerAliveAndSurvival(player)) {
            if (scanExpandTicks > 0 || !revealExpiry.isEmpty()) {
                scanExpandTicks = 0;
                revealExpiry.clear();
                syncRevealState();
            }
            if (inFrenzy || frenzyTicks > 0) {
                stopFrenzy();
            }
            return;
        }

        // ===== 狂野猎人疯魔计时 =====
        if (frenzyTicks > 0) {
            frenzyTicks--;
            if (frenzyTicks <= 0) {
                stopFrenzy();
            }
        }

        NoellesRolesConfig cfg = NoellesRolesConfig.HANDLER.instance();
        ServerLevel level = sp.serverLevel();
        long now = level.getGameTime();
        boolean changed = false;

        // ===== 红圈由内到外扩散 =====
        if (scanExpandTicks > 0) {
            scanElapsed++;
            double progress = Math.min(1.0, (double) scanElapsed / scanExpandTotal);
            double radius = cfg.bloodhoundScanRadius * progress;

            if (scanElapsed % RING_PARTICLE_INTERVAL == 0) {
                spawnScanRing(level, radius);
            }

            double radiusSq = radius * radius;
            AABB box = new AABB(scanOriginX - radius, scanOriginY - radius, scanOriginZ - radius,
                    scanOriginX + radius, scanOriginY + radius, scanOriginZ + radius);
            long expiry = now + cfg.bloodhoundScanRevealSeconds * 20L;
            for (ServerPlayer target : level.getEntitiesOfClass(ServerPlayer.class, box,
                    GameUtils::isPlayerAliveAndSurvival)) {
                if (target == sp || target.isSpectator())
                    continue;
                if (target.distanceToSqr(scanOriginX, scanOriginY, scanOriginZ) > radiusSq)
                    continue;
                if (!revealExpiry.containsKey(target.getUUID())) {
                    revealExpiry.put(target.getUUID(), expiry);
                    notifyScanned(target);
                    changed = true;
                }
            }
            scanExpandTicks--;
        }

        // ===== 移除过期透视 =====
        if (!revealExpiry.isEmpty() && revealExpiry.values().removeIf(exp -> exp <= now)) {
            changed = true;
        }

        if (changed)
            syncRevealState();
    }

    /** 在当前扩散半径处生成一圈猩红粒子（扫描波） */
    private void spawnScanRing(ServerLevel level, double radius) {
        if (radius < 0.5)
            return;
        int count = (int) Math.min(40, Math.max(10, radius * 2.0));
        double y = scanOriginY + 1.0;
        for (int i = 0; i < count; i++) {
            double angle = (2.0 * Math.PI * i) / count;
            double px = scanOriginX + Math.cos(angle) * radius;
            double pz = scanOriginZ + Math.sin(angle) * radius;
            level.sendParticles(SCAN_DUST, px, y, pz, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** 被扫描到的玩家：屏幕正上方标题提示「你已被扫描！」 */
    private void notifyScanned(ServerPlayer target) {
        SRENetworkMessageUtils.sendTitleTime(target, 4, 22, 8);
        SRENetworkMessageUtils.sendTitle(target,
                Component.translatable("message.noellesroles.bloodhound.scanned").withStyle(ChatFormatting.RED));
        target.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.7f, 1.2f);
    }

    /** 把服务端的透视集合同步到本人客户端 */
    private void syncRevealState() {
        revealedPlayers.clear();
        revealedPlayers.addAll(revealExpiry.keySet());
        sync();
    }

    /** 供客户端 InstinctRenderer 调用：目标当前是否被「众神之眼」透视 */
    public boolean isRevealed(UUID uuid) {
        return uuid != null && revealedPlayers.contains(uuid);
    }

    // ===== 狂野猎人（特殊疯魔）=====

    /**
     * 商店「狂野猎人」入口：开启寻血猎犬专属疯魔。成功返回 true。
     * - 屏幕变灰白（BLOODHOUND_FRENZY 效果驱动客户端滤镜）
     * - 向全体玩家播放吼叫
     * - 疯魔期间透视平民/中立（红光）、关闭狼人队友透视（见 InstinctRenderer）
     * - 发放一把巡警手枪（冷却 6.5 秒）
     * - 获得一层护盾
     */
    public boolean startFrenzy() {
        if (!(player instanceof ServerPlayer sp))
            return false;
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
        if (!game.isRunning() || !game.isRole(player, ModRoles.BLOODHOUND))
            return false;
        if (!GameUtils.isPlayerAliveAndSurvival(player))
            return false;
        if (inFrenzy)
            return false;

        NoellesRolesConfig cfg = NoellesRolesConfig.HANDLER.instance();
        int duration = Math.max(1, cfg.bloodhoundFrenzyDurationSeconds) * 20;

        // 发放巡警手枪（疯魔期间 CD 6.5 秒）
        RoleUtils.insertStackInFreeSlot(player, new ItemStack(ModItems.PATROLLER_REVOLVER));

        // 获得一层护盾
        SREArmorPlayerComponent.KEY.get(player).giveArmor();

        // 灰白屏幕：施加隐藏效果，客户端 nostalgist_gray 滤镜识别
        sp.addEffect(new MobEffectInstance(ModEffects.BLOODHOUND_FRENZY, duration + 20, 0, false, false, false));

        this.frenzyTicks = duration;
        this.inFrenzy = true;
        sync();

        // 触发「原版疯魔音乐」：+1 活跃疯魔数，客户端 AMBIENT_PSYCHO_DRONE 背景氛围随即全场响起
        game.setPsychosActive(game.getPsychosActive() + 1);

        // 开启吼叫：向全体玩家播放（保留原版 WARDEN_ROAR 吼声）
        for (ServerPlayer p : sp.serverLevel().players()) {
            p.playNotifySound(SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 1.0f, 1.0f);
        }
        return true;
    }

    /** 结束「狂野猎人」疯魔：回收巡警手枪、移除灰白效果，并停止原版疯魔音乐 */
    public void stopFrenzy() {
        boolean wasActive = inFrenzy || frenzyTicks > 0;
        this.inFrenzy = false;
        this.frenzyTicks = 0;
        if (player instanceof ServerPlayer sp) {
            MCItemsUtils.clearItem(player, ModItems.PATROLLER_REVOLVER);
            sp.removeEffect(ModEffects.BLOODHOUND_FRENZY);
        }
        // 结束「原版疯魔音乐」：-1 活跃疯魔数（与 startFrenzy 成对；setPsychosActive 内部已 Math.max(0,..) 防负）
        if (wasActive) {
            SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
            game.setPsychosActive(game.getPsychosActive() - 1);
            sync();
        }
    }

    // ===== NBT 同步 =====

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (UUID uuid : revealedPlayers) {
            list.add(StringTag.valueOf(uuid.toString()));
        }
        tag.put("revealed", list);
        tag.putBoolean("inFrenzy", inFrenzy);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider provider) {
        revealedPlayers.clear();
        ListTag list = tag.getList("revealed", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            try {
                revealedPlayers.add(UUID.fromString(list.getString(i)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        inFrenzy = tag.getBoolean("inFrenzy");
        SREClient.cachedHighLightMap.clear();
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider provider) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider provider) {
    }

    // ===== 枪械相关事件（在 NRCombatEvents.registerCombatRoleEvents 中调用）=====

    public static void registerEvents() {
        // 开枪命中：按配置概率不掉落枪械（默认 80% 保留枪、20% 掉枪）
        AllowShootRevolverDrop.EVENT.register((shooter, target) -> {
            if (!isBloodhound(shooter))
                return TrueFalseResult.PASS;
            // 狂野猎人疯魔期间：巡警手枪为疯魔专属武器，枪杀好人绝不掉枪。
            // 修复：通用掉枪逻辑会清空主手巡警手枪，并错误掉出一把左轮手枪。
            var comp = ModComponents.BLOODHOUND.maybeGet(shooter).orElse(null);
            if (comp != null && comp.inFrenzy && shooter.getMainHandItem().is(ModItems.PATROLLER_REVOLVER)) {
                return TrueFalseResult.FALSE;
            }
            int chance = clampPercent(NoellesRolesConfig.HANDLER.instance().bloodhoundGunNoDropChance);
            return shooter.getRandom().nextInt(100) < chance
                    ? TrueFalseResult.FALSE
                    : TrueFalseResult.PASS;
        });

        // 开枪后：枪械冷却固定为 20 秒（覆盖通用枪械冷却）
        OnRevolverUsed.EVENT.register((shooter, target) -> {
            if (!isBloodhound(shooter))
                return;
            ItemStack main = shooter.getMainHandItem();
            if (!main.is(TMMItemTags.GUNS))
                return;
            var comp = ModComponents.BLOODHOUND.maybeGet(shooter).orElse(null);
            // 狂野猎人疯魔期间：巡警手枪冷却固定 6.5 秒
            if (comp != null && comp.inFrenzy && main.is(ModItems.PATROLLER_REVOLVER)) {
                shooter.getCooldowns().addCooldown(ModItems.PATROLLER_REVOLVER,
                        NoellesRolesConfig.HANDLER.instance().bloodhoundFrenzyPatrollerCooldownTicks);
                return;
            }
            int cd = NoellesRolesConfig.HANDLER.instance().bloodhoundGunCooldownSeconds * 20;
            shooter.getCooldowns().addCooldown(main.getItem(), cd);
        });

        // 用枪击杀：获得 150 金币（总额）
        OnGiveKillerBalance.EVENT.register((victim, killer, deathReason) -> {
            if (!isBloodhound(killer))
                return 0;
            if (!isGunDeath(deathReason))
                return 0;
            int reward = NoellesRolesConfig.HANDLER.instance().bloodhoundGunKillReward;
            return reward - GameConstants.getMoneyPerKill();
        });
    }

    private static boolean isGunDeath(ResourceLocation deathReason) {
        if (deathReason == null)
            return false;
        return deathReason.equals(GameConstants.DeathReasons.REVOLVER)
                || deathReason.equals(GameConstants.DeathReasons.DERRINGER)
                || deathReason.equals(GameConstants.DeathReasons.GUN_SHOT);
    }

    private static int clampPercent(int v) {
        return Math.max(0, Math.min(100, v));
    }

    private static boolean isBloodhound(Player p) {
        if (p == null)
            return false;
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(p.level());
        return game.isRunning() && game.isRole(p, ModRoles.BLOODHOUND);
    }
}