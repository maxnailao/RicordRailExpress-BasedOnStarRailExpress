package org.agmas.noellesroles.game.roles.killer.werewolfkiller;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREWorldBlackoutComponent;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemCooldowns;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.client.blood.BloodMain;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 狼人组件（杀手阵营）
 *
 * <p>非黑灯状态下同普通杀手；黑灯后透视降低为半径7格、获得夜视+速度2，
 * 狼刀举刀加快、击杀CD 18秒。非黑灯状态下击杀玩家减少35%黑灯购买冷却。</p>
 *
 * <p>特殊模式「午夜狼嚎」（商店购买，非疯魔模式）：开启后进入30秒黑灯并播放狼嚎，
 * 期间狼刀举刀落刀无声、击杀CD 6秒、被击杀者出血量增加、狼人获得静步。</p>
 */
public class WerewolfKillerPlayerComponent implements RoleComponent, ServerTickingComponent {
    public static final ComponentKey<WerewolfKillerPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "werewolf_killer"),
            WerewolfKillerPlayerComponent.class);

    /** 午夜狼嚎持续时间：30秒 = 600 ticks */
    public static final int HOWL_DURATION = 30 * 20;
    /** 午夜狼嚎期间狼刀击杀冷却：6秒 = 120 ticks */
    public static final int HOWL_KNIFE_CD = 6 * 20;
    /** 黑灯状态下狼刀击杀冷却：18秒 = 360 ticks */
    public static final int BLACKOUT_KNIFE_CD = 18 * 20;
    /** 黑灯状态下狼人的透视半径 */
    public static final double BLACKOUT_ESP_RADIUS = 7.0;
    /** 非黑灯击杀减少的黑灯购买冷却比例 */
    private static final float BLACKOUT_CD_REDUCTION = 0.35f;

    static {
        registerPassiveEvents();
    }

    private final Player player;
    /** 午夜狼嚎剩余时间（<=0 表示未激活） */
    public int midnightHowlTicks = 0;
    /** 黑灯状态（服务端每 tick 从世界组件维护并同步到客户端，供狼刀/透视在客户端判定） */
    public boolean blackoutActive = false;
    /** 黑灯增益（夜视+速度2）是否已施加，用于黑灯结束时移除 */
    private boolean blackoutBuffsActive = false;

    public WerewolfKillerPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return this.player == player;
    }

    public void sync() {
        KEY.sync(this.player);
    }

    @Override
    public void init() {
        this.midnightHowlTicks = 0;
        this.blackoutActive = false;
        this.blackoutBuffsActive = false;
        this.sync();
    }

    @Override
    public void clear() {
        this.init();
    }

    /**
     * 午夜狼嚎是否激活中
     */
    public boolean isHowlActive() {
        return midnightHowlTicks > 0;
    }

    /**
     * 玩家是否处于午夜狼嚎状态（仅限狼人角色）
     */
    public static boolean isHowling(Player player) {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorld == null || !gameWorld.isRole(player, ModRoles.WEREWOLF_KILLER))
            return false;
        return KEY.get(player).midnightHowlTicks > 0;
    }

    /**
     * 玩家所在世界是否处于黑灯状态（直接读世界组件，仅服务端可靠）
     */
    public static boolean isBlackout(Player player) {
        SREWorldBlackoutComponent blackout = SREWorldBlackoutComponent.KEY.get(player.level());
        return blackout != null && blackout.isBlackoutActive();
    }

    /**
     * 客户端可用的黑灯判定：读取组件内由服务端同步的黑灯标记。
     * 非狼人回退到世界组件读取（服务端调用时始终准确）。
     */
    public static boolean isWerewolfBlackout(Player player) {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorld != null && gameWorld.isRole(player, ModRoles.WEREWOLF_KILLER)) {
            return KEY.get(player).blackoutActive;
        }
        return isBlackout(player);
    }

    /**
     * 开启午夜狼嚎（商店购买入口）
     * - 触发30秒黑灯并播放狼嚎音效
     * - 期间举刀落刀无声（见狼刀与刀捅混入）、击杀CD 6秒、静步、出血量增加
     *
     * @return 是否成功开启
     */
    public boolean startMidnightHowl() {
        if (midnightHowlTicks > 0)
            return false;
        if (!(player instanceof ServerPlayer serverPlayer))
            return false;
        if (serverPlayer.isSpectator() || !GameUtils.isPlayerAliveAndSurvival(serverPlayer))
            return false;
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorld == null || !gameWorld.isRunning() || !gameWorld.isRole(player, ModRoles.WEREWOLF_KILLER))
            return false;

        // 触发30秒黑灯（若已有黑灯则按需延长）
        SREWorldBlackoutComponent blackout = SREWorldBlackoutComponent.KEY.get(player.level());
        blackout.triggerBlackout(true, HOWL_DURATION);
        blackout.lastBlackoutTriggeredBy = player.getUUID();

        // 全场播放狼嚎音效
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WOLF_HOWL, SoundSource.MASTER, 64.0F, 0.9F);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WOLF_AMBIENT, SoundSource.MASTER, 64.0F, 0.6F);

        // 静步（屏蔽脚步声），覆盖整个午夜狼嚎周期
        player.addEffect(new MobEffectInstance(ModEffects.JINGBU, HOWL_DURATION + 20, 0, false, false, false));

        // 清除狼刀冷却，开启后立即可击杀
        player.getCooldowns().removeCooldown(ModItems.WOLF_KNIFE);

        this.midnightHowlTicks = HOWL_DURATION;
        this.sync();

        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.werewolf_killer.howl_start")
                        .withStyle(ChatFormatting.RED),
                true);
        return true;
    }

    /**
     * 结束午夜狼嚎
     */
    private void endHowl() {
        this.midnightHowlTicks = 0;
        player.removeEffect(ModEffects.JINGBU);
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.werewolf_killer.howl_end")
                            .withStyle(ChatFormatting.GRAY),
                    true);
        }
        this.sync();
    }

    @Override
    public void serverTick() {
        // ── 黑灯状态维护与同步（供客户端狼刀/透视使用） ──
        if (!player.level().isClientSide) {
            boolean blackout = isBlackout(player);
            if (blackout != blackoutActive) {
                blackoutActive = blackout;
                this.sync();
            }
        }

        // ── 午夜狼嚎倒计时 ──
        if (midnightHowlTicks > 0) {
            midnightHowlTicks--;
            if (midnightHowlTicks <= 0) {
                endHowl();
            } else if (midnightHowlTicks % 20 == 0) {
                this.sync();
            }
        }

        // ── 黑灯增益：夜视 + 速度2 ──
        if (player instanceof ServerPlayer serverPlayer
                && !serverPlayer.isSpectator()
                && GameUtils.isPlayerAliveAndSurvival(serverPlayer)) {
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
            if (gameWorld == null || !gameWorld.isRunning()
                    || !gameWorld.isRole(player, ModRoles.WEREWOLF_KILLER)) {
                return;
            }
            if (blackoutActive) {
                // 每秒刷新一次，保证效果覆盖黑灯期间
                if (serverPlayer.tickCount % 20 == 0) {
                    serverPlayer.addEffect(new MobEffectInstance(
                            MobEffects.NIGHT_VISION, 60, 0, false, false, false));
                    serverPlayer.addEffect(new MobEffectInstance(
                            MobEffects.MOVEMENT_SPEED, 60, 1, false, false, false));
                }
                blackoutBuffsActive = true;
            } else if (blackoutBuffsActive) {
                // 黑灯结束：移除由本组件维持的夜视与速度
                serverPlayer.removeEffect(MobEffects.NIGHT_VISION);
                serverPlayer.removeEffect(MobEffects.MOVEMENT_SPEED);
                blackoutBuffsActive = false;
            }
        }
    }

    /**
     * 注册被动事件：
     * 1. 非黑灯状态下击杀玩家 → 减少自身35%黑灯购买冷却
     * 2. 午夜狼嚎期间刀击杀 → 被击杀者出血量增加（额外血液粒子）
     */
    private static void registerPassiveEvents() {
        // 非黑灯击杀减少黑灯购买冷却
        OnPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            if (killer == null || !(killer instanceof ServerPlayer serverKiller))
                return;
            if (victim == killer)
                return;
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(killer.level());
            if (gameWorld == null || !gameWorld.isRunning()
                    || !gameWorld.isRole(killer, ModRoles.WEREWOLF_KILLER))
                return;
            if (!GameUtils.isPlayerAliveAndSurvival(killer))
                return;
            // 仅非黑灯状态下生效
            if (isBlackout(killer))
                return;
            if (reduceBlackoutCooldown(serverKiller, BLACKOUT_CD_REDUCTION)) {
                serverKiller.displayClientMessage(
                        Component.translatable("message.noellesroles.werewolf_killer.blackout_cd_reduced")
                                .withStyle(ChatFormatting.DARK_AQUA),
                        true);
            }
        });

        // 午夜狼嚎期间被击杀者出血量增加
        OnPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            if (killer == null || victim == null)
                return;
            if (!GameConstants.DeathReasons.KNIFE.equals(deathReason))
                return;
            if (!isHowling(killer))
                return;
            if (victim.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(BloodMain.BLOOD_PARTICLE,
                        victim.getX(), victim.getY() + 0.5, victim.getZ(),
                        45, 0.5, 0.9, 0.5, 0.18);
                serverLevel.sendParticles(BloodMain.BLOOD_PARTICLE,
                        victim.getX(), victim.getY() + 0.1, victim.getZ(),
                        25, 0.8, 0.1, 0.8, 0.02);
            }
        });
    }

    /**
     * 减少玩家黑灯购买物品的剩余冷却
     *
     * @param percent 减少比例（0.35 = 减少35%）
     * @return 是否实际发生了减少
     */
    private static boolean reduceBlackoutCooldown(ServerPlayer player, float percent) {
        ItemCooldowns cooldowns = player.getCooldowns();
        ItemCooldowns.CooldownInstance instance = cooldowns.cooldowns.get(TMMItems.BLACKOUT);
        if (instance == null)
            return false;
        int remaining = instance.endTime - cooldowns.tickCount;
        if (remaining <= 0)
            return false;
        int newRemaining = Math.max(0, Math.round(remaining * (1.0f - percent)));
        if (newRemaining > 0) {
            cooldowns.addCooldown(TMMItems.BLACKOUT, newRemaining);
        } else {
            cooldowns.removeCooldown(TMMItems.BLACKOUT);
        }
        return true;
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("midnightHowlTicks", this.midnightHowlTicks);
        tag.putBoolean("blackoutActive", this.blackoutActive);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.midnightHowlTicks = tag.getInt("midnightHowlTicks");
        this.blackoutActive = tag.getBoolean("blackoutActive");
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
