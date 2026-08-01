package org.agmas.noellesroles.game.roles.killer.stalker;

import java.util.ArrayList;
import java.util.List;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.event.OnKillPlayerTriggered;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.network.TriggerStatusBarPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 潜行者疯魔组件（猎影狂奔）
 * 潜行者(STALKER)的商店购买特殊疯魔技能，同其他杀手一样基于psycho系统（type=3）。
 * - 开启条件：必须处于三阶段
 * - 开启后自动退回二阶段，杀戮欲望锁定为0（避免疯魔期间进阶三阶段）
 * - 获得速度IV效果
 * - 开启时清空玩家身上所有刀（主手刀+副手刀）并保存，给予一把CD仅2秒的主手刀并锁定
 * - 疯魔结束后清空手上主手刀，再返还保存的刀
 * - 凝视状态默认关闭且无法开启
 * - 无护盾（armour=0）
 * - 击杀爆出华丽粒子特效（仿刽子手）
 * - 价格：325金币
 * - 购买CD：150秒
 * - 持续时间：30秒
 */
public class StalkerFrenzyPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<StalkerFrenzyPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "stalker_frenzy"),
            StalkerFrenzyPlayerComponent.class);

    /** 疯魔持续时间：30秒 = 600 ticks */
    public static final int FRENZY_DURATION = 30 * 20;

    /** 疯魔期间主手刀冷却：2秒 = 40 ticks */
    public static final int FRENZY_KNIFE_CD = 2 * 20;

    private final Player player;
    public boolean inFrenzy = false;

    /** 疯魔开始前保存的刀（主手刀+副手刀），结束后返还 */
    private final List<ItemStack> savedKnives = new ArrayList<>();

    public StalkerFrenzyPlayerComponent(Player player) {
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

    @Override
    public void init() {
        this.inFrenzy = false;
        this.sync();
    }

    @Override
    public void clear() {
        this.init();
    }

    public void sync() {
        KEY.sync(this.player);
    }

    /**
     * 启动潜行者疯魔模式（猎影狂奔）
     * - 必须处于三阶段
     * - 退回二阶段，杀戮欲望锁定为0
     * - 获得速度IV
     * - 锁定主手刀（psycho系统），无护盾
     */
    public boolean startFrenzy() {
        StalkerPlayerComponent stalker = StalkerPlayerComponent.KEY.get(player);

        // 开启条件：必须处于三阶段
        if (stalker.phase != 3) {
            return false;
        }

        SREPlayerPsychoComponent psychoComponent = SREPlayerPsychoComponent.KEY.get(player);
        if (psychoComponent.getPsychoTicks() > 0) {
            return false;
        }
        if (inFrenzy) {
            return false;
        }

        // 停止凝视并退回二阶段（关闭突进、清除三阶段计时）
        stalker.stopGazing();
        stalker.regressToPhase2();

        // 杀戮欲望锁定为0，避免疯魔期间进阶三阶段
        stalker.energy = 0;
        stalker.sync();

        // 清空并保存所有刀（主手刀+副手刀），疯魔结束后返还
        removeAllStalkerKnives(true);

        // 给予一把主手刀（疯魔期间CD仅2秒），直接放入主手并由psycho系统锁定
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.STALKER_KNIFE));
        // 清除主手刀冷却，确保疯魔开始后立即可左键击杀
        player.getCooldowns().removeCooldown(ModItems.STALKER_KNIFE);

        // 设置psycho模式（不使用startPsycho避免给球棒）
        psychoComponent.setPsychoTicks(FRENZY_DURATION);
        psychoComponent.setArmour(1); // 一层护盾
        psychoComponent.type = 3; // 潜行者专属疯魔类型
        psychoComponent.sync();

        // 更新psycho计数
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
        gameWorldComponent.setPsychosActive(gameWorldComponent.getPsychosActive() + 1);

        // 触发状态栏
        if (player instanceof ServerPlayer serverPlayer) {
            ServerPlayNetworking.send(serverPlayer, new TriggerStatusBarPayload("Psycho"));
        }

        // 获得速度IV效果（30秒，无粒子）
        player.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SPEED, FRENZY_DURATION + 20, 3,
                false, false, false));

        this.inFrenzy = true;
        this.sync();

        // 变身特效：粒子 + 音效（营造氛围）
        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                    player.getX(), player.getY() + 1, player.getZ(),
                    30, 0.5, 1.0, 0.5, 0.08);
            serverLevel.sendParticles(ParticleTypes.ASH,
                    player.getX(), player.getY() + 1, player.getZ(),
                    25, 0.5, 1.0, 0.5, 0.05);
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    player.getX(), player.getY() + 0.5, player.getZ(),
                    20, 0.4, 0.8, 0.4, 0.04);
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PHANTOM_SWOOP, SoundSource.PLAYERS, 1.0F, 0.6F);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WARDEN_AGITATED, SoundSource.PLAYERS, 0.7F, 1.4F);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.AMBIENT_CAVE, SoundSource.PLAYERS, 0.5F, 0.8F);

        return true;
    }

    /**
     * 移除玩家身上所有潜行者刀（主手刀+副手刀），覆盖主手、副手与物品栏。
     * @param save 是否保存被移除的刀（用于疯魔结束后返还）
     */
    private void removeAllStalkerKnives(boolean save) {
        if (save) {
            this.savedKnives.clear();
        }
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(ModItems.STALKER_KNIFE) || stack.is(ModItems.STALKER_KNIFE_OFFHAND)) {
                if (save) {
                    this.savedKnives.add(stack.copy());
                }
                inv.setItem(i, ItemStack.EMPTY);
            }
        }
    }

    /**
     * 返还保存的刀（优先放回原主手/副手，否则放入物品栏）
     */
    private void returnSavedKnives() {
        for (ItemStack stack : this.savedKnives) {
            if (stack.is(ModItems.STALKER_KNIFE) && player.getMainHandItem().isEmpty()) {
                player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            } else if (stack.is(ModItems.STALKER_KNIFE_OFFHAND) && player.getOffhandItem().isEmpty()) {
                player.setItemInHand(InteractionHand.OFF_HAND, stack);
            } else {
                player.addItem(stack);
            }
        }
        this.savedKnives.clear();
    }

    /**
     * 停止潜行者疯魔模式
     */
    public void stopFrenzy() {
        if (!inFrenzy)
            return;

        this.inFrenzy = false;

        // 移除速度效果
        player.removeEffect(MobEffects.MOVEMENT_SPEED);

        // 重置psycho type（解除物品锁定）
        SREPlayerPsychoComponent psychoComponent = SREPlayerPsychoComponent.KEY.get(player);
        psychoComponent.type = -1;
        psychoComponent.sync();

        // 先清空手上的疯魔主手刀，再返还保存的刀
        removeAllStalkerKnives(false);
        returnSavedKnives();

        this.sync();
    }

    /**
     * 检查玩家是否处于潜行者疯魔状态
     */
    public static boolean isInFrenzy(Player player) {
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorldComponent.isRole(player, ModRoles.STALKER))
            return false;
        return KEY.get(player).inFrenzy;
    }

    @Override
    public void serverTick() {
        if (!inFrenzy)
            return;

        SREPlayerPsychoComponent psychoComponent = SREPlayerPsychoComponent.KEY.get(player);
        // 当psycho模式结束时，停止疯魔
        if (psychoComponent.getPsychoTicks() <= 0) {
            stopFrenzy();
            return;
        }

        // 杀戮欲望锁定为0（安全网，避免任何途径积累能量进阶三阶段）
        StalkerPlayerComponent stalker = StalkerPlayerComponent.KEY.get(player);
        if (stalker.energy != 0) {
            stalker.energy = 0;
            stalker.sync();
        }

        // 每2秒发送一次环绕粒子 + 环境音效（营造氛围）
        if (player.level() instanceof ServerLevel serverLevel && player.tickCount % 40 == 0) {
            serverLevel.sendParticles(ParticleTypes.ASH,
                    player.getX(), player.getY() + 0.5, player.getZ(),
                    6, 0.3, 0.5, 0.3, 0.02);
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    player.getX(), player.getY() + 0.5, player.getZ(),
                    3, 0.3, 0.5, 0.3, 0.02);
            serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 0.4F, 0.9F);
        }

        // 每8秒播放一次低沉环境音
        if (player.level() instanceof ServerLevel serverLevel && player.tickCount % 160 == 0) {
            serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.AMBIENT_CAVE, SoundSource.PLAYERS, 0.3F, 1.2F);
        }
    }

    @Override
    public void clientTick() {
        // 客户端跟踪
    }

    /**
     * 注册击杀特效事件
     * 潜行者疯魔期间用刀击杀玩家后爆出华丽粒子（仿刽子手）并发出音效
     */
    public static void registerKillEffectEvent() {
        OnKillPlayerTriggered.EVENT.register((victim, spawnBody, killer, deathReason, forceKill) -> {
            if (killer == null)
                return;
            if (!isInFrenzy(killer))
                return;

            // 只在刀击杀时触发特效
            if (!GameConstants.DeathReasons.KNIFE.equals(deathReason))
                return;

            if (killer.level() instanceof ServerLevel serverLevel) {
                // 火焰爆发特效
                serverLevel.sendParticles(ParticleTypes.FLAME,
                        victim.getX(), victim.getY() + 1, victim.getZ(),
                        15, 0.3, 0.5, 0.3, 0.1);
                // 灵魂火焰特效
                serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        victim.getX(), victim.getY() + 1, victim.getZ(),
                        12, 0.3, 0.5, 0.3, 0.05);
                // 烟花爆炸特效
                serverLevel.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                        victim.getX(), victim.getY() + 1.5, victim.getZ(),
                        20, 0.5, 0.8, 0.5, 0.3);
                // 末影粒子
                serverLevel.sendParticles(ParticleTypes.PORTAL,
                        victim.getX(), victim.getY() + 1, victim.getZ(),
                        25, 0.4, 0.6, 0.4, 0.5);
                // 灰烬粒子
                serverLevel.sendParticles(ParticleTypes.ASH,
                        victim.getX(), victim.getY() + 1, victim.getZ(),
                        20, 0.5, 0.7, 0.5, 0.06);

                // 击杀音效（多层叠加）
                serverLevel.playSound(null, victim.getX(), victim.getY() + 1, victim.getZ(),
                        SoundEvents.ENDER_DRAGON_SHOOT, SoundSource.PLAYERS, 0.8F, 1.2F);
                serverLevel.playSound(null, victim.getX(), victim.getY() + 1, victim.getZ(),
                        SoundEvents.PHANTOM_DEATH, SoundSource.PLAYERS, 1.0F, 0.7F);
                serverLevel.playSound(null, victim.getX(), victim.getY() + 1, victim.getZ(),
                        SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.PLAYERS, 0.5F, 1.8F);
            }
        });
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("inFrenzy", this.inFrenzy);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.inFrenzy = tag.getBoolean("inFrenzy");
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
